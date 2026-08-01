output "repository_url" {
  value = aws_ecr_repository.api.repository_url
}

output "repository_arn" {
  value = aws_ecr_repository.api.arn
}

output "agent_repository_url" {
  value = aws_ecr_repository.agent.repository_url
}

output "agent_repository_arn" {
  value = aws_ecr_repository.agent.arn
}
