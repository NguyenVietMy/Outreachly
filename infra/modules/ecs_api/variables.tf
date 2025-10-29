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


# ACM / HTTPS
variable "domain_name" {
  description = "FQDN for the API (e.g., api.dev.yourdomain.com)"
  type        = string
}

# Secrets Manager
variable "supabase_session_pooler_secret_arn" {
  type = string
}

variable "db_user_secret_arn" {
  type = string
}

variable "db_password_secret_arn" {
  type = string
}

variable "openai_api_key_secret_arn" {
  type = string
}

# Hunter API keys (Secrets Manager)
variable "hunter_acc_1_secret_arn" {
  type = string
}

variable "hunter_acc_2_secret_arn" {
  type = string
}

# OAuth2 Secrets (Secrets Manager)
variable "google_client_id_secret_arn" {
  type        = string
  description = "ARN of Secrets Manager secret containing Google OAuth2 Client ID"
}

variable "google_client_secret_secret_arn" {
  type        = string
  description = "ARN of Secrets Manager secret containing Google OAuth2 Client Secret"
}