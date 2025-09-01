locals { name = "${var.project}-${var.env}-db" }

resource "aws_db_subnet_group" "this" {
  name       = "${local.name}-subnets"
  subnet_ids = var.subnet_ids
}

resource "aws_security_group" "db" {
  name   = "${local.name}-sg"
  vpc_id = var.vpc_id
  # allow only from ECS SG
  ingress { 
    from_port = 5432 
    to_port = 5432 
    protocol = "tcp" 
    security_groups = [var.ecs_sg_id] 
  }
  egress  { 
    from_port = 0 
    to_port = 0 
    protocol = "-1" 
    cidr_blocks = ["0.0.0.0/0"] 
  }
}

resource "random_password" "master" {
  length  = 24
  special = true
}

resource "aws_rds_cluster" "this" {
  engine         = "aurora-postgresql"
  engine_version = "15"                 # check available versions in your region
  database_name  = "outreachly"
  master_username = "masteruser"
  master_password = random_password.master.result
  storage_encrypted = true
  db_subnet_group_name = aws_db_subnet_group.this.name
  vpc_security_group_ids = [aws_security_group.db.id]
  enable_http_endpoint = true # (Data API for serverless if needed)
  skip_final_snapshot = true
  
  # Serverless v2 configuration
  serverlessv2_scaling_configuration {
    min_capacity = 0.5
    max_capacity = 1.0
  }
}


resource "aws_rds_cluster_instance" "serverless" {
  count               = 1
  identifier          = "${local.name}-instance-${count.index}"
  cluster_identifier  = aws_rds_cluster.this.id
  instance_class      = "db.serverless"
  engine              = aws_rds_cluster.this.engine
  engine_version      = aws_rds_cluster.this.engine_version
  publicly_accessible = false
}
# ---- Secrets Manager (JSON secret) ----
resource "aws_secretsmanager_secret" "db" {
  name = "${local.name}-creds"
}

resource "aws_secretsmanager_secret_version" "db" {
  secret_id     = aws_secretsmanager_secret.db.id
  secret_string = jsonencode({
    username = "masteruser"
    password = random_password.master.result
    host     = aws_rds_cluster.this.endpoint
    port     = 5432
    dbname   = "outreachly"
    jdbc_url = "jdbc:postgresql://${aws_rds_cluster.this.endpoint}:5432/outreachly"
  })
}

output "db_endpoint"     { value = aws_rds_cluster.this.endpoint }
output "db_secret_arn"   { value = aws_secretsmanager_secret.db.arn }
output "db_name"         { value = "outreachly" }