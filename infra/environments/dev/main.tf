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
  source        = "../../modules/network"
  project       = local.project
  env           = local.env
  vpc_cidr      = "10.20.0.0/16"
  public_cidrs  = ["10.20.1.0/24","10.20.2.0/24"]
}

# TODO: replace <ACCOUNT_ID> and keep your region/tag
module "ecs_api" {
  source             = "../../modules/ecs_api"
  project            = local.project
  env                = local.env
  vpc_id             = module.network.vpc_id
  public_subnet_ids  = module.network.public_subnet_ids
  container_image    = "158954238800.dkr.ecr.us-east-1.amazonaws.com/outreachly/api:dev"
}
