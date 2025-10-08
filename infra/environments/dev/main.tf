terraform {
  backend "s3" {
    bucket         = "outreachly-tf-state-158954238800-us-east-1"
    key            = "dev/terraform.tfstate"
    region         = "us-east-1"
    dynamodb_table = "tf-locks"
    profile        = "outreachly"
  }
}

provider "aws" {
  region  = "us-east-1"
  profile = "outreachly"
}

locals {
  project = "outreachly"
  env     = "dev"
}

module "network" {
  source       = "../../modules/network"
  project      = local.project
  env          = local.env
  vpc_cidr     = "10.20.0.0/16"
  public_cidrs = ["10.20.1.0/24", "10.20.2.0/24"]
}

module "ecs_api" {
  source            = "../../modules/ecs_api"
  project           = local.project
  env               = local.env
  vpc_id            = module.network.vpc_id
  public_subnet_ids = module.network.public_subnet_ids
  container_image   = "158954238800.dkr.ecr.us-east-1.amazonaws.com/outreachly/api:latest-v2"
  container_port    = 8080
  domain_name       = "api.outreach-ly.com"
  supabase_session_pooler_secret_arn = aws_secretsmanager_secret.supabase_session_pooler.arn
  db_user_secret_arn                 = aws_secretsmanager_secret.db_user.arn
  db_password_secret_arn             = aws_secretsmanager_secret.db_password.arn
  openai_api_key_secret_arn          = aws_secretsmanager_secret.openai_api_key.arn
  hunter_acc_1_secret_arn            = aws_secretsmanager_secret.hunter_acc_1.arn
  hunter_acc_2_secret_arn            = aws_secretsmanager_secret.hunter_acc_2.arn
}

# Secrets Manager for database credentials
resource "aws_secretsmanager_secret" "supabase_session_pooler" {
  name                    = "outreachly/dev/SUPABASE_SESSION_POOLER"
  recovery_window_in_days = 0
}

resource "aws_secretsmanager_secret" "db_user" {
  name                    = "outreachly/dev/DB_USER"
  recovery_window_in_days = 0
}

resource "aws_secretsmanager_secret" "db_password" {
  name                    = "outreachly/dev/DB_PASSWORD"
  recovery_window_in_days = 0
}

resource "aws_secretsmanager_secret" "openai_api_key" {
  name                    = "outreachly/dev/OPENAI_API_KEY"
  recovery_window_in_days = 0
}

resource "aws_secretsmanager_secret" "hunter_acc_1" {
  name                    = "outreachly/dev/HUNTER_ACC_1"
  recovery_window_in_days = 0
}

resource "aws_secretsmanager_secret" "hunter_acc_2" {
  name                    = "outreachly/dev/HUNTER_ACC_2"
  recovery_window_in_days = 0
}

# module "ses" {
#   source = "../../modules/ses"

#   domain_name = "outreach-ly.com"
#   route53_zone_id = "Z02495573ISDF5X4NF9YA"
#   region = "us-east-1"
# }

resource "aws_route53_record" "api_alias" {
  zone_id = "Z02495573ISDF5X4NF9YA"
  name    = "api.outreach-ly.com"
  type    = "A"

  alias {
    name                   = module.ecs_api.alb_dns_name
    zone_id                = module.ecs_api.alb_zone_id
    evaluate_target_health = false
  }
}

# HTTPS listener on the ALB using our existing ACM cert
resource "aws_lb_listener" "api_https" {
  load_balancer_arn = module.ecs_api.alb_arn
  port              = 443
  protocol          = "HTTPS"
  ssl_policy        = "ELBSecurityPolicy-TLS13-1-2-2021-06"
  certificate_arn   = "arn:aws:acm:us-east-1:158954238800:certificate/39c20c08-6d44-46a8-b07a-9c5dfb2a7d8f"

  default_action {
    type             = "forward"
    target_group_arn = module.ecs_api.tg_arn
  }
}
