# ============================================================================
# Reference EC2 + Security Group (Controlled by enable_paid_resources = false)
# ============================================================================

resource "aws_security_group" "batch_worker_sg" {
  name        = "order-batch-worker-sg-${var.environment}"
  description = "Least-privilege Security Group for Spring Batch EC2 worker host"
  vpc_id      = var.vpc_id

  ingress {
    description = "Allow internal Spring Boot REST & Actuator health port from VPC CIDR only"
    from_port   = 8080
    to_port     = 8080
    protocol    = "tcp"
    cidr_blocks = ["10.0.0.0/16"]
  }

  egress {
    description = "Allow outbound HTTPS to S3 and PostgreSQL RDS within VPC"
    from_port   = 443
    to_port     = 443
    protocol    = "tcp"
    cidr_blocks = ["0.0.0.0/0"]
  }

  egress {
    description = "Allow outbound PostgreSQL connection"
    from_port   = 5432
    to_port     = 5432
    protocol    = "tcp"
    cidr_blocks = ["10.0.0.0/16"]
  }

  tags = {
    Name        = "order-batch-worker-sg-${var.environment}"
    Environment = var.environment
  }
}

resource "aws_instance" "order_batch_ec2" {
  ami                    = var.ec2_ami_id
  instance_type          = "t3.micro"
  subnet_id              = var.subnet_ids[0]
  vpc_security_group_ids = [aws_security_group.batch_worker_sg.id]

  metadata_options {
    http_endpoint               = "enabled"
    http_tokens                 = "required" # Enforces IMDSv2 security best practice
    http_put_response_hop_limit = 1
  }

  root_block_device {
    volume_size           = 20
    volume_type           = "gp3"
    encrypted             = true
    delete_on_termination = true
  }

  tags = {
    Name        = "order-batch-worker-${var.environment}"
    Environment = var.environment
    Role        = "SpringBatchSettlementNode"
  }
}
