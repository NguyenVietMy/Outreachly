variable "project" { type = string }
variable "env" { type = string }
variable "vpc_cidr" { type = string }
variable "public_cidrs" { type = list(string) }
variable "private_cidrs" { type = list(string) }
