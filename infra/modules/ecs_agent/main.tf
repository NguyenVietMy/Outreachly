locals {
  name = "${var.project}-${var.env}-agent"
}

# ---------- Security Group ----------

# No load balancer and no public listener: the only way in is from the API task's security group,
# over the private subnets. This is what keeps the agent off the public internet — a property of the
# topology, not of a rule that could be misordered.
resource "aws_security_group" "ecs" {
  name   = "${local.name}-ecs-sg"
  vpc_id = var.vpc_id

  ingress {
    from_port       = var.container_port
    to_port         = var.container_port
    protocol        = "tcp"
    security_groups = var.ingress_security_group_ids
  }

  egress {
    from_port   = 0
    to_port     = 0
    protocol    = "-1"
    cidr_blocks = ["0.0.0.0/0"]
  }
}

# ---------- IAM ----------

data "aws_iam_policy_document" "task_assume" {
  statement {
    actions = ["sts:AssumeRole"]

    principals {
      type        = "Service"
      identifiers = ["ecs-tasks.amazonaws.com"]
    }
  }
}

resource "aws_iam_role" "task_exec" {
  name               = "${local.name}-exec-role"
  assume_role_policy = data.aws_iam_policy_document.task_assume.json
}

resource "aws_iam_role_policy_attachment" "task_exec_attach" {
  role       = aws_iam_role.task_exec.name
  policy_arn = "arn:aws:iam::aws:policy/service-role/AmazonECSTaskExecutionRolePolicy"
}

resource "aws_iam_role_policy" "secrets_access" {
  name = "${local.name}-secrets-access"
  role = aws_iam_role.task_exec.id

  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Effect = "Allow"
        Action = [
          "secretsmanager:GetSecretValue",
          "secretsmanager:DescribeSecret"
        ]
        Resource = values(var.secret_arns)
      }
    ]
  })
}

# ---------- Task and Service ----------

resource "aws_cloudwatch_log_group" "agent" {
  name              = "/ecs/${local.name}"
  retention_in_days = 14
}

data "aws_region" "current" {}

resource "aws_ecs_task_definition" "agent" {
  family                   = "${local.name}-td"
  requires_compatibilities = ["FARGATE"]
  cpu                      = "256"
  memory                   = "512"
  network_mode             = "awsvpc"
  execution_role_arn       = aws_iam_role.task_exec.arn

  container_definitions = jsonencode([
    {
      name      = "agent"
      image     = var.container_image
      essential = true

      portMappings = [
        {
          containerPort = var.container_port
          protocol      = "tcp"
        }
      ]

      logConfiguration = {
        logDriver = "awslogs"
        options = {
          awslogs-group         = aws_cloudwatch_log_group.agent.name
          awslogs-region        = data.aws_region.current.region
          awslogs-stream-prefix = "ecs"
        }
      }

      # Fargate ignores the Dockerfile's HEALTHCHECK — only this field counts. /health is
      # deliberately unauthenticated so probes like this one need no header.
      healthCheck = {
        command     = ["CMD-SHELL", "python -c \"import urllib.request; urllib.request.urlopen('http://localhost:${var.container_port}/health')\" || exit 1"]
        interval    = 30
        timeout     = 10
        retries     = 3
        startPeriod = 30
      }

      environment = [for name, value in var.extra_environment : {
        name  = name
        value = value
      }]

      secrets = [for name, arn in var.secret_arns : { name = name, valueFrom = arn }]
    }
  ])
}

resource "aws_ecs_service" "agent" {
  name            = "${local.name}-svc"
  cluster         = var.cluster_id
  task_definition = aws_ecs_task_definition.agent.arn
  desired_count   = 1
  launch_type     = "FARGATE"

  # CI registers new task definition revisions; Terraform must not roll them back.
  lifecycle {
    ignore_changes = [task_definition]
  }

  network_configuration {
    subnets          = var.private_subnet_ids
    security_groups  = [aws_security_group.ecs.id]
    assign_public_ip = false
  }

  service_registries {
    registry_arn = var.service_registry_arn
  }
}

resource "aws_cloudwatch_metric_alarm" "ecs_high_cpu" {
  alarm_name          = "${local.name}-high-cpu"
  alarm_description   = "High ECS service CPU utilization for ${local.name}"
  namespace           = "AWS/ECS"
  metric_name         = "CPUUtilization"
  statistic           = "Average"
  period              = 300
  evaluation_periods  = 2
  threshold           = 80
  comparison_operator = "GreaterThanThreshold"
  treat_missing_data  = "notBreaching"
  alarm_actions       = var.alarm_actions
  ok_actions          = var.alarm_actions

  dimensions = {
    ClusterName = var.cluster_name
    ServiceName = aws_ecs_service.agent.name
  }
}

resource "aws_cloudwatch_metric_alarm" "ecs_high_memory" {
  alarm_name          = "${local.name}-high-memory"
  alarm_description   = "High ECS service memory utilization for ${local.name}"
  namespace           = "AWS/ECS"
  metric_name         = "MemoryUtilization"
  statistic           = "Average"
  period              = 300
  evaluation_periods  = 2
  threshold           = 80
  comparison_operator = "GreaterThanThreshold"
  treat_missing_data  = "notBreaching"
  alarm_actions       = var.alarm_actions
  ok_actions          = var.alarm_actions

  dimensions = {
    ClusterName = var.cluster_name
    ServiceName = aws_ecs_service.agent.name
  }
}
