variable "environment" {
  description = "Environment name"
  type        = string
}

variable "aws_region" {
  description = "AWS region"
  type        = string
}

variable "vpc_id" {
  description = "VPC ID for EC2 and EKS deployment"
  type        = string
  default     = "vpc-00000000"
}

variable "subnet_ids" {
  description = "Private Subnet IDs for EKS cluster and EC2 instance"
  type        = list(string)
  default     = ["subnet-11111111", "subnet-22222222"]
}

variable "ec2_ami_id" {
  description = "Amazon Linux 2023 AMI ID (Free Tier eligible t3.micro)"
  type        = string
  default     = "ami-0c101f26f147fa7fd"
}
