output "s3_orders_bucket_name" {
  description = "S3 Bucket storing JSON and CSV financial orders"
  value       = module.serverless_core.s3_bucket_name
}

output "order_api_lambda_arn" {
  description = "ARN of the API Gateway handler Lambda"
  value       = module.serverless_core.order_api_lambda_arn
}

output "order_event_processor_lambda_arn" {
  description = "ARN of the EventBridge S3 processor Lambda"
  value       = module.serverless_core.order_event_processor_lambda_arn
}

output "api_gateway_id" {
  description = "REST API Gateway ID"
  value       = module.serverless_core.api_gateway_id
}

output "api_gateway_orders_url" {
  description = "Endpoint URL for POST /orders (LocalStack path-style and standard AWS invoke URL)"
  value       = module.serverless_core.api_gateway_orders_url
}

output "cloudwatch_log_groups" {
  description = "CloudWatch Log Groups for both Lambda functions"
  value       = module.serverless_core.cloudwatch_log_groups
}
