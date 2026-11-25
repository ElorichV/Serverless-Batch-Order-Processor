variable "environment" {
  type = string
}

variable "aws_region" {
  type = string
}

variable "use_localstack" {
  type = bool
}

variable "orders_bucket_name" {
  type = string
}

variable "order_api_lambda_jar_path" {
  type = string
}

variable "order_event_processor_lambda_jar_path" {
  type = string
}
