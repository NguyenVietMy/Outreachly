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
  container_image   = "158954238800.dkr.ecr.us-east-1.amazonaws.com/outreachly/api:dev"
  container_port    = 8080
  domain_name       = "api.outreach-ly.com"
}

module "ses" {
  source = "../../modules/ses"

  domain_name = "outreach-ly.com"
  route53_zone_id = "Z02495573ISDF5X4NF9YA"
  region = "us-east-1"
}

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
