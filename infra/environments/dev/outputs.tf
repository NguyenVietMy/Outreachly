output "alb_dns_name" {
  value = module.ecs_api.alb_dns_name
}

output "ecr_repository_url" {
  value = module.ecr.repository_url
}

output "ecs_cluster_name" {
  value = module.ecs_api.cluster_name
}

output "ecs_service_name" {
  value = module.ecs_api.service_name
}

output "github_actions_deploy_role_arn" {
  value = aws_iam_role.github_actions_deploy.arn
}

output "acm_validation_records" {
  description = "Add these CNAME records in Cloudflare to validate the ACM certificate"
  value = {
    for dvo in aws_acm_certificate.api.domain_validation_options : dvo.domain_name => {
      name  = dvo.resource_record_name
      type  = dvo.resource_record_type
      value = dvo.resource_record_value
    }
  }
}
