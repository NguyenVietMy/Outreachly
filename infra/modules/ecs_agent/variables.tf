variable "project" { type = string }
variable "env" { type = string }
variable "vpc_id" { type = string }

variable "private_subnet_ids" {
  type        = list(string)
  description = "Subnets for the agent task. Private — the agent has no public listener."
}

variable "cluster_id" {
  type        = string
  description = "Existing ECS cluster to run in (shared with the API)"
}

variable "cluster_name" {
  type        = string
  description = "Cluster name, for the CloudWatch alarm dimensions"
}

variable "container_image" { type = string }

variable "container_port" {
  type    = number
  default = 8001
}

variable "ingress_security_group_ids" {
  type        = list(string)
  description = "Security groups allowed to reach the agent on container_port (the API task)"
}

variable "service_registry_arn" {
  type        = string
  description = "Cloud Map service ARN to register tasks with, so the API can resolve them by name"
}

variable "secret_arns" {
  type        = map(string)
  description = "Map of env var name to Secrets Manager ARN"
}

variable "extra_environment" {
  type        = map(string)
  description = "Plain-text environment variables for the ECS task"
  default     = {}
}

variable "alarm_actions" {
  type        = list(string)
  description = "Optional CloudWatch alarm action ARNs"
  default     = []
}
