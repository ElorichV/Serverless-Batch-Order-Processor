# ============================================================================
# CloudWatch Log Groups, Metric Filter (Structured JSON) & Alarm
# ============================================================================

resource "aws_cloudwatch_log_group" "order_api_logs" {
  name              = "/aws/lambda/order-api-lambda"
  retention_in_days = 14
}

resource "aws_cloudwatch_log_group" "order_event_processor_logs" {
  name              = "/aws/lambda/order-event-processor-lambda"
  retention_in_days = 14
}

# Extracts custom CloudWatch metric whenever a JSON log entry contains eventType = "ORDER_COMPLIANCE_ALERT"
resource "aws_cloudwatch_log_metric_filter" "high_risk_orders_metric_filter" {
  name           = "HighRiskOrderComplianceAlerts"
  pattern        = "{ $.eventType = \"ORDER_COMPLIANCE_ALERT\" }"
  log_group_name = aws_cloudwatch_log_group.order_event_processor_logs.name

  metric_transformation {
    name          = "HighValueOrderAlertCount"
    namespace     = "FinancialOrderProcessor/Compliance"
    value         = "1"
    default_value = "0"
  }
}

resource "aws_cloudwatch_metric_alarm" "high_risk_orders_alarm" {
  alarm_name          = "high-value-aml-orders-alarm-${var.environment}"
  comparison_operator = "GreaterThanOrEqualToThreshold"
  evaluation_periods  = 1
  metric_name         = "HighValueOrderAlertCount"
  namespace           = "FinancialOrderProcessor/Compliance"
  period              = 60
  statistic           = "Sum"
  threshold           = 1
  alarm_description   = "Triggers when one or more high-value orders (>= 50,000 USD) require AML compliance review"
  treat_missing_data  = "notBreaching"
}
