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

