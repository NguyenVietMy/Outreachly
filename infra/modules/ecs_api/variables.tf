variable "project" { type = string }
variable "env" { type = string }
variable "vpc_id" { type = string }

variable "public_subnet_ids" {
  type        = list(string)
  description = "Subnets for the ALB"
}

variable "private_subnet_ids" {
  type        = list(string)
  description = "Subnets for ECS tasks"
}

variable "container_image" { type = string }

variable "container_port" {
  type    = number
  default = 8080
}

variable "api_domain" {
  type        = string
  description = "FQDN for the API (e.g. api.pulse-cs.com)"
}

variable "frontend_url" {
  type        = string
  description = "Frontend URL with scheme (e.g. https://pulse-cs.com)"
}

variable "secret_arns" {
  type        = map(string)
  description = "Map of env var name to Secrets Manager ARN"
}

variable "extra_environment" {
  type        = map(string)
  description = "Additional plain-text environment variables for the ECS task"
  default     = {}
}

variable "alarm_actions" {
  type        = list(string)
  description = "Optional CloudWatch alarm action ARNs"
  default     = []
}

variable "internal_ingress_cidrs" {
  type        = list(string)
  description = "CIDRs allowed to reach the API on container_port from inside the VPC (the agent)"
  default     = []
}

variable "service_registry_arn" {
  type        = string
  description = "Cloud Map service ARN to register tasks with. Empty disables registration."
  default     = ""
}
