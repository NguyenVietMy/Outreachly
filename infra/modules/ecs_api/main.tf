locals {
  name = "${var.project}-${var.env}-api"
}

# ---------- Security Groups ----------
resource "aws_security_group" "alb" {
  name   = "${local.name}-alb-sg"
  vpc_id = var.vpc_id

  ingress {
    from_port   = 80
    to_port     = 80
    protocol    = "tcp"
    cidr_blocks = ["0.0.0.0/0"]
  }

  ingress {
    from_port   = 443
    to_port     = 443
    protocol    = "tcp"
    cidr_blocks = ["0.0.0.0/0"]
  }

  egress {
    from_port   = 0
    to_port     = 0
    protocol    = "-1"
    cidr_blocks = ["0.0.0.0/0"]
  }
}

resource "aws_security_group" "ecs" {
  name   = "${local.name}-ecs-sg"
  vpc_id = var.vpc_id

  ingress {
    from_port       = var.container_port
    to_port         = var.container_port
    protocol        = "tcp"
    security_groups = [aws_security_group.alb.id]
  }

  egress {
    from_port   = 0
    to_port     = 0
    protocol    = "-1"
    cidr_blocks = ["0.0.0.0/0"]
  }
}

# ---------- Load Balancer ----------
resource "aws_lb" "this" {
  name               = "${local.name}-alb"
  load_balancer_type = "application"
  security_groups    = [aws_security_group.alb.id]
  subnets            = var.public_subnet_ids
}

resource "aws_lb_target_group" "api" {
  name        = "${local.name}-tg"
  port        = var.container_port
  protocol    = "HTTP"
  vpc_id      = var.vpc_id
  target_type = "ip"

  health_check {
    path                = "/health"
    healthy_threshold   = 2
    unhealthy_threshold = 10
    timeout             = 30
    interval            = 60
    matcher             = "200-399"
  }
}

resource "aws_lb_listener" "http" {
  load_balancer_arn = aws_lb.this.arn
  port              = 80
  protocol          = "HTTP"

  default_action {
    type = "redirect"
    redirect {
      port        = "443"
      protocol    = "HTTPS"
      status_code = "HTTP_301"
    }
  }
}

# ---------- IAM for ECS ----------
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

# SES permissions for the ECS task
resource "aws_iam_role_policy" "ses_policy" {
  name = "${local.name}-ses-policy"
  role = aws_iam_role.task_exec.id

  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Effect = "Allow"
        Action = [
          "ses:SendEmail",
          "ses:SendRawEmail",
          "ses:SendBulkTemplatedEmail",
          "ses:SendTemplatedEmail",
          "ses:VerifyEmailIdentity",
          "ses:GetIdentityVerificationAttributes",
          "ses:GetSendQuota",
          "ses:GetSendStatistics",
          "ses:ListIdentities",
          "ses:GetIdentityDkimAttributes",
          "ses:GetIdentityMailFromDomainAttributes",
          "ses:GetIdentityNotificationAttributes",
          "ses:GetIdentityPolicies",
          "ses:GetIdentityVerificationAttributes"
        ]
        Resource = "*"
      }
    ]
  })
}

# Secrets Manager permissions for the ECS task
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
        Resource = [
          var.supabase_session_pooler_secret_arn,
          var.db_user_secret_arn,
          var.db_password_secret_arn,
          var.openai_api_key_secret_arn,
          var.hunter_acc_1_secret_arn,
          var.hunter_acc_2_secret_arn
        ]
      }
    ]
  })
}


# ---------- Cluster, Task, Service ----------
resource "aws_ecs_cluster" "this" {
  name = "${local.name}-cluster"
}

resource "aws_cloudwatch_log_group" "api" {
  name              = "/ecs/${local.name}"
  retention_in_days = 14
}

data "aws_region" "current" {}

resource "aws_ecs_task_definition" "api" {
  family                   = "${local.name}-td"
  requires_compatibilities = ["FARGATE"]
  cpu                      = "256"
  memory                   = "512"
  network_mode             = "awsvpc"
  execution_role_arn       = aws_iam_role.task_exec.arn

  container_definitions = jsonencode([
    {
      name  = "api"
      image = var.container_image
      essential = true
      portMappings = [
        {
          containerPort = var.container_port
          protocol      = "tcp"
        }
      ]
      
      # OAuth2 configuration via environment variables
      # Note: In production, consider using AWS Secrets Manager for these values

      logConfiguration = {
        logDriver = "awslogs"
        options = {
          awslogs-group         = aws_cloudwatch_log_group.api.name
          awslogs-region        = data.aws_region.current.name
          awslogs-stream-prefix = "ecs"
        }
      }
      environment = [
        { name = "JAVA_OPTS", value = "-XX:+ExitOnOutOfMemoryError" },
        { name = "GOOGLE_CLIENT_ID", value = "11164249925-1d31lg1eibv43f910rs068aq4f3a30tu.apps.googleusercontent.com" },
        { name = "GOOGLE_CLIENT_SECRET", value = "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" },
        { name = "GOOGLE_REDIRECT_URI", value = "https://api.outreach-ly.com/login/oauth2/code/google" },
        { name = "FRONTEND_URL", value = "https://www.outreach-ly.com" },
        { name = "AWS_FROM_EMAIL", value = "noreply@outreach-ly.com" },
        { name = "AWS_FROM_NAME", value = "Outreachly" },
        { name = "AWS_BOUNCE_EMAIL", value = "bounces@outreach-ly.com" },
        { name = "AWS_COMPLAINT_EMAIL", value = "complaints@outreach-ly.com" }
      ]

      secrets = [
        { name = "SUPABASE_SESSION_POOLER", valueFrom = var.supabase_session_pooler_secret_arn },
        { name = "DB_USER", valueFrom = var.db_user_secret_arn },
        { name = "DB_PASSWORD", valueFrom = var.db_password_secret_arn },
        { name = "OPENAI_API_KEY", valueFrom = var.openai_api_key_secret_arn },
        { name = "HUNTER_ACC_1", valueFrom = var.hunter_acc_1_secret_arn },
        { name = "HUNTER_ACC_2", valueFrom = var.hunter_acc_2_secret_arn }
      ]
    }
  ])
}

resource "aws_ecs_service" "api" {
  name            = "${local.name}-svc"
  cluster         = aws_ecs_cluster.this.id
  task_definition = aws_ecs_task_definition.api.arn
  desired_count   = 1
  launch_type     = "FARGATE"

  health_check_grace_period_seconds = 180

  network_configuration {
    subnets          = var.public_subnet_ids
    security_groups  = [aws_security_group.ecs.id]
    assign_public_ip = true
  }

  load_balancer {
    target_group_arn = aws_lb_target_group.api.arn
    container_name   = "api"
    container_port   = var.container_port
  }

  depends_on = [aws_lb_listener.http]
}