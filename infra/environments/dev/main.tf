terraform {
  backend "s3" {
    bucket         = "pulse-tf-state-520622116399-us-east-1"
    key            = "dev/terraform.tfstate"
    region         = "us-east-1"
    dynamodb_table = "tf-locks"
    profile        = "pulse"
  }

  required_providers {
    cloudflare = {
      source  = "cloudflare/cloudflare"
      version = "~> 4.0"
    }
  }
}

provider "aws" {
  region  = "us-east-1"
  profile = "pulse"
}

provider "cloudflare" {
  api_token = var.cloudflare_api_token
}

locals {
  project                         = "pulse"
  env                             = "dev"
  domain                          = "pulse-cs.com"
  api_domain                      = "api.pulse-cs.com"
  frontend_url                    = "https://pulse-cs.com"
  observability_otlp_endpoint     = "https://otlp-gateway-prod-us-east-2.grafana.net/otlp/v1/traces"
  observability_otlp_metrics_url  = "https://otlp-gateway-prod-us-east-2.grafana.net/otlp/v1/metrics"
  observability_service_name      = "pulse-api"
  observability_service_namespace = "pulse"
}

variable "image_tag" {
  type        = string
  description = "Docker image tag to deploy"
  default     = "latest"
}

variable "cloudflare_api_token" {
  type      = string
  sensitive = true
}

# ---------- Cloudflare DNS ----------

data "cloudflare_zone" "this" {
  name = local.domain
}

# ---------- ACM certificate (DNS validation via Cloudflare) ----------

resource "aws_acm_certificate" "api" {
  domain_name       = local.api_domain
  validation_method = "DNS"

  lifecycle {
    create_before_destroy = true
  }
}

resource "cloudflare_record" "acm_validation" {
  for_each = {
    for dvo in aws_acm_certificate.api.domain_validation_options : dvo.domain_name => {
      name  = dvo.resource_record_name
      type  = dvo.resource_record_type
      value = dvo.resource_record_value
    }
  }

  zone_id = data.cloudflare_zone.this.id
  name    = each.value.name
  type    = each.value.type
  content = each.value.value
  ttl     = 60
  proxied = false
}

resource "aws_acm_certificate_validation" "api" {
  certificate_arn         = aws_acm_certificate.api.arn
  validation_record_fqdns = [for record in cloudflare_record.acm_validation : record.hostname]
}

resource "cloudflare_record" "api" {
  zone_id = data.cloudflare_zone.this.id
  name    = "api"
  type    = "CNAME"
  content = module.ecs_api.alb_dns_name
  ttl     = 60
  proxied = false
}

# ---------- Network ----------

module "network" {
  source        = "../../modules/network"
  project       = local.project
  env           = local.env
  vpc_cidr      = "10.20.0.0/16"
  public_cidrs  = ["10.20.1.0/24", "10.20.2.0/24"]
  private_cidrs = ["10.20.10.0/24", "10.20.11.0/24"]
}

# ---------- ECR ----------

module "ecr" {
  source  = "../../modules/ecr"
  project = local.project
  env     = local.env
}

# ---------- ECS API ----------

module "ecs_api" {
  source             = "../../modules/ecs_api"
  project            = local.project
  env                = local.env
  vpc_id             = module.network.vpc_id
  public_subnet_ids  = module.network.public_subnet_ids
  private_subnet_ids = module.network.private_subnet_ids
  container_image    = "${module.ecr.repository_url}:${var.image_tag}"
  container_port     = 8080
  api_domain         = local.api_domain
  frontend_url       = local.frontend_url

  secret_arns = {
    SUPABASE_SESSION_POOLER          = aws_secretsmanager_secret.supabase_session_pooler.arn
    DB_USER                          = aws_secretsmanager_secret.db_user.arn
    DB_PASSWORD                      = aws_secretsmanager_secret.db_password.arn
    OPENAI_API_KEY                   = aws_secretsmanager_secret.openai_api_key.arn
    GOOGLE_CLIENT_ID                 = aws_secretsmanager_secret.google_client_id.arn
    GOOGLE_CLIENT_SECRET             = aws_secretsmanager_secret.google_client_secret.arn
    GITHUB_APP_ID                    = aws_secretsmanager_secret.github_app_id.arn
    GITHUB_APP_PRIVATE_KEY           = aws_secretsmanager_secret.github_app_private_key.arn
    GITHUB_APP_INSTALL_URL           = aws_secretsmanager_secret.github_app_install_url.arn
    GITHUB_WEBHOOK_SECRET            = aws_secretsmanager_secret.github_webhook_secret.arn
    SLACK_CLIENT_ID                  = aws_secretsmanager_secret.slack_client_id.arn
    SLACK_CLIENT_SECRET              = aws_secretsmanager_secret.slack_client_secret.arn
    SLACK_SIGNING_SECRET             = aws_secretsmanager_secret.slack_signing_secret.arn
    LINEAR_CLIENT_ID                 = aws_secretsmanager_secret.linear_client_id.arn
    LINEAR_CLIENT_SECRET             = aws_secretsmanager_secret.linear_client_secret.arn
    LINEAR_WEBHOOK_SECRET            = aws_secretsmanager_secret.linear_webhook_secret.arn
    OBSERVABILITY_OTLP_AUTHORIZATION = aws_secretsmanager_secret.observability_otlp_authorization.arn
  }

  extra_environment = {
    OBSERVABILITY_TRACING_ENABLED              = "true"
    OBSERVABILITY_OTLP_ENDPOINT                = local.observability_otlp_endpoint
    OBSERVABILITY_METRICS_ENABLED              = "true"
    OBSERVABILITY_OTLP_METRICS_URL             = local.observability_otlp_metrics_url
    OBSERVABILITY_TRACING_SAMPLING_PROBABILITY = "1.0"
    OBSERVABILITY_SERVICE_NAME                 = local.observability_service_name
    OBSERVABILITY_SERVICE_NAMESPACE            = local.observability_service_namespace
    OBSERVABILITY_ENVIRONMENT                  = local.env
  }
}

# ---------- HTTPS Listener ----------

resource "aws_lb_listener" "api_https" {
  load_balancer_arn = module.ecs_api.alb_arn
  port              = 443
  protocol          = "HTTPS"
  ssl_policy        = "ELBSecurityPolicy-TLS13-1-2-2021-06"
  certificate_arn   = aws_acm_certificate_validation.api.certificate_arn

  default_action {
    type             = "forward"
    target_group_arn = module.ecs_api.tg_arn
  }
}

# ---------- CI/CD: GitHub Actions OIDC ----------

data "aws_caller_identity" "current" {}

resource "aws_iam_openid_connect_provider" "github" {
  url             = "https://token.actions.githubusercontent.com"
  client_id_list  = ["sts.amazonaws.com"]
  thumbprint_list = ["ffffffffffffffffffffffffffffffffffffffff"]
}

resource "aws_iam_role" "github_actions_deploy" {
  name = "${local.project}-${local.env}-github-actions-deploy"

  assume_role_policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Effect = "Allow"
        Principal = {
          Federated = aws_iam_openid_connect_provider.github.arn
        }
        Action = "sts:AssumeRoleWithWebIdentity"
        Condition = {
          StringEquals = {
            "token.actions.githubusercontent.com:aud" = "sts.amazonaws.com"
          }
          StringLike = {
            "token.actions.githubusercontent.com:sub" = "repo:NguyenVietMy/Outreachly:ref:refs/heads/main"
          }
        }
      }
    ]
  })
}

resource "aws_iam_role_policy" "github_actions_deploy" {
  name = "${local.project}-${local.env}-deploy-policy"
  role = aws_iam_role.github_actions_deploy.id

  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Effect = "Allow"
        Action = [
          "ecr:GetAuthorizationToken"
        ]
        Resource = "*"
      },
      {
        Effect = "Allow"
        Action = [
          "ecr:BatchCheckLayerAvailability",
          "ecr:GetDownloadUrlForLayer",
          "ecr:BatchGetImage",
          "ecr:PutImage",
          "ecr:InitiateLayerUpload",
          "ecr:UploadLayerPart",
          "ecr:CompleteLayerUpload"
        ]
        Resource = module.ecr.repository_arn
      },
      {
        Effect = "Allow"
        Action = [
          "ecs:DescribeTaskDefinition",
          "ecs:RegisterTaskDefinition",
          "ecs:UpdateService",
          "ecs:DescribeServices"
        ]
        Resource = "*"
      },
      {
        Effect   = "Allow"
        Action   = "iam:PassRole"
        Resource = module.ecs_api.task_exec_role_arn
      }
    ]
  })
}

# ---------- Secrets Manager ----------

resource "aws_secretsmanager_secret" "supabase_session_pooler" {
  name                    = "pulse/dev/SUPABASE_SESSION_POOLER"
  recovery_window_in_days = 0
}

resource "aws_secretsmanager_secret" "db_user" {
  name                    = "pulse/dev/DB_USER"
  recovery_window_in_days = 0
}

resource "aws_secretsmanager_secret" "db_password" {
  name                    = "pulse/dev/DB_PASSWORD"
  recovery_window_in_days = 0
}

resource "aws_secretsmanager_secret" "openai_api_key" {
  name                    = "pulse/dev/OPENAI_API_KEY"
  recovery_window_in_days = 0
}

resource "aws_secretsmanager_secret" "google_client_id" {
  name                    = "pulse/dev/GOOGLE_CLIENT_ID"
  recovery_window_in_days = 0
}

resource "aws_secretsmanager_secret" "google_client_secret" {
  name                    = "pulse/dev/GOOGLE_CLIENT_SECRET"
  recovery_window_in_days = 0
}

resource "aws_secretsmanager_secret" "github_app_id" {
  name                    = "pulse/dev/GITHUB_APP_ID"
  recovery_window_in_days = 0
}

resource "aws_secretsmanager_secret" "github_app_private_key" {
  name                    = "pulse/dev/GITHUB_APP_PRIVATE_KEY"
  recovery_window_in_days = 0
}

resource "aws_secretsmanager_secret" "github_app_install_url" {
  name                    = "pulse/dev/GITHUB_APP_INSTALL_URL"
  recovery_window_in_days = 0
}

resource "aws_secretsmanager_secret" "github_webhook_secret" {
  name                    = "pulse/dev/GITHUB_WEBHOOK_SECRET"
  recovery_window_in_days = 0
}

resource "aws_secretsmanager_secret" "slack_client_id" {
  name                    = "pulse/dev/SLACK_CLIENT_ID"
  recovery_window_in_days = 0
}

resource "aws_secretsmanager_secret" "slack_client_secret" {
  name                    = "pulse/dev/SLACK_CLIENT_SECRET"
  recovery_window_in_days = 0
}

resource "aws_secretsmanager_secret" "slack_signing_secret" {
  name                    = "pulse/dev/SLACK_SIGNING_SECRET"
  recovery_window_in_days = 0
}

resource "aws_secretsmanager_secret" "linear_client_id" {
  name                    = "pulse/dev/LINEAR_CLIENT_ID"
  recovery_window_in_days = 0
}

resource "aws_secretsmanager_secret" "linear_client_secret" {
  name                    = "pulse/dev/LINEAR_CLIENT_SECRET"
  recovery_window_in_days = 0
}

resource "aws_secretsmanager_secret" "linear_webhook_secret" {
  name                    = "pulse/dev/LINEAR_WEBHOOK_SECRET"
  recovery_window_in_days = 0
}

resource "aws_secretsmanager_secret" "observability_otlp_authorization" {
  name                    = "pulse/dev/OBSERVABILITY_OTLP_AUTHORIZATION"
  recovery_window_in_days = 0
}
