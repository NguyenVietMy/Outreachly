variable "project"      { type = string }
variable "env"          { type = string }
variable "vpc_id"       { type = string }
variable "subnet_ids"   { type = list(string) }
variable "ecs_sg_id"    { type = string }   # <— new
