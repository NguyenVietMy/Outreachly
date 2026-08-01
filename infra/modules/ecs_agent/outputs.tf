output "security_group_id" {
  value = aws_security_group.ecs.id
}

output "service_name" {
  value = aws_ecs_service.agent.name
}

output "task_exec_role_arn" {
  value = aws_iam_role.task_exec.arn
}

output "log_group_name" {
  value = aws_cloudwatch_log_group.agent.name
}
