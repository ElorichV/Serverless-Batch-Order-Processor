# ============================================================================
# Amazon EventBridge Rule (S3 Object Created) -> order-event-processor-lambda
# ============================================================================

resource "aws_cloudwatch_event_rule" "s3_order_created_rule" {
  name        = "s3-financial-order-created-rule"
  description = "Triggers order-event-processor-lambda whenever a new order file is uploaded to S3"

  event_pattern = jsonencode({
    source      = ["aws.s3"]
    detail-type = ["Object Created"]
    detail = {
      bucket = {
        name = [aws_s3_bucket.orders_bucket.id]
      }
      object = {
        key = [{
          prefix = "incoming/"
        }]
      }
    }
  })
}

resource "aws_cloudwatch_event_target" "invoke_order_event_processor" {
  rule      = aws_cloudwatch_event_rule.s3_order_created_rule.name
  target_id = "OrderEventProcessorLambdaTarget"
  arn       = aws_lambda_function.order_event_processor_lambda.arn
}

resource "aws_lambda_permission" "allow_eventbridge_invoke" {
  statement_id  = "AllowExecutionFromEventBridge"
  action        = "lambda:InvokeFunction"
  function_name = aws_lambda_function.order_event_processor_lambda.function_name
  principal     = "events.amazonaws.com"
  source_arn    = aws_cloudwatch_event_rule.s3_order_created_rule.arn
}
