# ============================================================================
# AWS Lambda Functions (Java 21 + SnapStart Enabled)
# ============================================================================

resource "aws_lambda_function" "order_api_lambda" {
  function_name    = "order-api-lambda"
  role             = aws_iam_role.order_api_lambda_role.arn
  handler          = "com.cloudmentor.orders.api.OrderApiHandler::handleRequest"
  runtime          = "java21"
  filename         = var.order_api_lambda_jar_path
  source_code_hash = fileexists(var.order_api_lambda_jar_path) ? filebase64sha256(var.order_api_lambda_jar_path) : null
  memory_size      = 512
  timeout          = 15
  publish          = true

  snap_start {
    apply_on = "PublishedVersions"
  }

  environment {
    variables = {
      ORDERS_BUCKET_NAME = aws_s3_bucket.orders_bucket.id
      JAVA_TOOL_OPTIONS  = "-XX:+TieredCompilation -XX:TieredStopAtLevel=1"
    }
  }

  depends_on = [
    aws_cloudwatch_log_group.order_api_logs,
    aws_iam_role_policy.order_api_lambda_policy
  ]
}

resource "aws_lambda_function" "order_event_processor_lambda" {
  function_name    = "order-event-processor-lambda"
  role             = aws_iam_role.order_event_processor_lambda_role.arn
  handler          = "com.cloudmentor.orders.processor.OrderEventProcessorHandler::handleRequest"
  runtime          = "java21"
  filename         = var.order_event_processor_lambda_jar_path
  source_code_hash = fileexists(var.order_event_processor_lambda_jar_path) ? filebase64sha256(var.order_event_processor_lambda_jar_path) : null
  memory_size      = 512
  timeout          = 20
  publish          = true

  snap_start {
    apply_on = "PublishedVersions"
  }

  environment {
    variables = {
      ORDERS_BUCKET_NAME = aws_s3_bucket.orders_bucket.id
      JAVA_TOOL_OPTIONS  = "-XX:+TieredCompilation -XX:TieredStopAtLevel=1"
    }
  }

  depends_on = [
    aws_cloudwatch_log_group.order_event_processor_logs,
    aws_iam_role_policy.order_event_processor_lambda_policy
  ]
}
