variable "project"          { type = string }
variable "env"              { type = string }
variable "vpc_id"           { type = string }
variable "public_subnet_ids"{ type = list(string) }
variable "container_image"  { type = string }  # ECR URI:tag
variable "container_port"   { type = number    default = 8080 }
