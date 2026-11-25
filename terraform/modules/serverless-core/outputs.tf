output "s3_bucket_name" {
  value = aws_s3_bucket.orders_bucket.id
}

output "order_api_lambda_arn" {
  value = aws_lambda_function.order_api_lambda.arn
}

output "order_event_processor_lambda_arn" {
  value = aws_lambda_function.order_event_processor_lambda.arn
}

output "api_gateway_id" {
  value = aws_api_gateway_rest_api.orders_api.id
}

output "api_gateway_orders_url" {
  value = var.use_localstack ? "http://localhost:4566/restapis/${aws_api_gateway_rest_api.orders_api.id}/${aws_api_gateway_stage.v1_stage.stage_name}/_user_request_/orders" : "${aws_api_gateway_stage.v1_stage.invoke_url}/orders"
}

output "cloudwatch_log_groups" {
  value = {
    order_api_lambda             = aws_cloudwatch_log_group.order_api_logs.name
    order_event_processor_lambda = aws_cloudwatch_log_group.order_event_processor_logs.name
  }
}
