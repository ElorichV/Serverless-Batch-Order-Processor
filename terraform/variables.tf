variable "aws_region" {
  description = "AWS region for all resources (LocalStack defaults to us-east-1)"
  type        = string
  default     = "us-east-1"
}

variable "environment" {
  description = "Deployment environment name (local, dev, prod)"
  type        = string
  default     = "local"
}

variable "use_localstack" {
  description = "When true, routes all AWS API calls to LocalStack on localhost:4566"
  type        = bool
  default     = true
}

variable "localstack_endpoint" {
  description = "LocalStack edge endpoint URL"
  type        = string
  default     = "http://localhost:4566"
}

variable "enable_paid_resources" {
  description = "Safety switch: keeps EC2 and EKS disabled (false) in LocalStack/Free Tier while allowing terraform validate & plan inspection"
  type        = bool
  default     = false
}

variable "orders_bucket_name" {
  description = "Name of the S3 bucket that stores incoming financial orders (JSON and CSV)"
  type        = string
  default     = "financial-orders-bucket"
}

variable "order_api_lambda_jar_path" {
  description = "Relative path to the compiled shaded JAR for order-api-lambda"
  type        = string
  default     = "../lambdas/order-api-lambda/target/order-api-lambda.jar"
}

variable "order_event_processor_lambda_jar_path" {
  description = "Relative path to the compiled shaded JAR for order-event-processor-lambda"
  type        = string
  default     = "../lambdas/order-event-processor-lambda/target/order-event-processor-lambda.jar"
}
