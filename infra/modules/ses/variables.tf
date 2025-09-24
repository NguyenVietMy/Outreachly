variable "domain_name" {
  type = string
  description = "The domain name to verify with SES"
}

variable "route53_zone_id" {
  type = string
  description = "The Route53 hosted zone ID"
}

variable "region" {
  type = string
  description = "The AWS region"
}
