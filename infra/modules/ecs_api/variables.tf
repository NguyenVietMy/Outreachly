variable "project" {
  type = string
}

variable "env" {
  type = string
}

variable "vpc_id" {
  type = string
}

variable "public_subnet_ids" {
  type = list(string)
}

variable "container_image" {
  type = string
}

variable "container_port" {
  type    = number
  default = 8080
}

variable "db_secret_arn" { 
  type = string 
}

variable "oauth2_secret_arn" { 
  type = string 
}

# ACM / HTTPS
variable "domain_name" {
  description = "FQDN for the API (e.g., api.dev.yourdomain.com)"
  type        = string
}
