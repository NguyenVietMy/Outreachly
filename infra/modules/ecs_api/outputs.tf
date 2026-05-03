output "alb_dns_name" {
  value = aws_lb.this.dns_name
}

output "alb_zone_id" {
  value = aws_lb.this.zone_id
}

output "alb_arn" {
  value = aws_lb.this.arn
}

output "tg_arn" {
  value = aws_lb_target_group.api.arn
}

output "cluster_name" {
  value = aws_ecs_cluster.this.name
}

output "service_name" {
  value = aws_ecs_service.api.name
}

output "task_exec_role_arn" {
  value = aws_iam_role.task_exec.arn
}
