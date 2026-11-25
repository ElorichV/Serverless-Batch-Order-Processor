# ============================================================================
# IAM Roles & Policies - Principle of Least Privilege (PoLP)
# ============================================================================

data "aws_iam_policy_document" "lambda_assume_role" {
  statement {
    sid     = "AllowLambdaServiceAssumeRole"
    effect  = "Allow"
    actions = ["sts:AssumeRole"]

    principals {
      type        = "Service"
      identifiers = ["lambda.amazonaws.com"]
    }
  }
}

# 1. IAM Role for order-api-lambda (Write-only to S3 incoming/* + own CloudWatch Logs)
resource "aws_iam_role" "order_api_lambda_role" {
  name               = "order-api-lambda-role-${var.environment}"
  assume_role_policy = data.aws_iam_policy_document.lambda_assume_role.json
}

data "aws_iam_policy_document" "order_api_lambda_policy_doc" {
  statement {
    sid    = "AllowS3PutOrderObjectsOnly"
    effect = "Allow"
    actions = [
      "s3:PutObject",
      "s3:PutObjectTagging"
    ]
    resources = [
      "${aws_s3_bucket.orders_bucket.arn}/incoming/*"
    ]
  }

  statement {
    sid    = "AllowCloudWatchLogsWrite"
    effect = "Allow"
    actions = [
      "logs:CreateLogStream",
      "logs:PutLogEvents"
    ]
    resources = [
      "${aws_cloudwatch_log_group.order_api_logs.arn}:*"
    ]
  }
}

resource "aws_iam_role_policy" "order_api_lambda_policy" {
  name   = "order-api-lambda-least-privilege-policy"
  role   = aws_iam_role.order_api_lambda_role.id
  policy = data.aws_iam_policy_document.order_api_lambda_policy_doc.json
}

# 2. IAM Role for order-event-processor-lambda (Read-only from S3 incoming/* + own CloudWatch Logs)
resource "aws_iam_role" "order_event_processor_lambda_role" {
  name               = "order-event-processor-lambda-role-${var.environment}"
  assume_role_policy = data.aws_iam_policy_document.lambda_assume_role.json
}

data "aws_iam_policy_document" "order_event_processor_lambda_policy_doc" {
  statement {
    sid    = "AllowS3ReadIncomingOrdersOnly"
    effect = "Allow"
    actions = [
      "s3:GetObject",
      "s3:ListBucket"
    ]
    resources = [
      aws_s3_bucket.orders_bucket.arn,
      "${aws_s3_bucket.orders_bucket.arn}/incoming/*"
    ]
  }

  statement {
    sid    = "AllowCloudWatchLogsWrite"
    effect = "Allow"
    actions = [
      "logs:CreateLogStream",
      "logs:PutLogEvents"
    ]
    resources = [
      "${aws_cloudwatch_log_group.order_event_processor_logs.arn}:*"
    ]
  }
}

resource "aws_iam_role_policy" "order_event_processor_lambda_policy" {
  name   = "order-event-processor-least-privilege-policy"
  role   = aws_iam_role.order_event_processor_lambda_role.id
  policy = data.aws_iam_policy_document.order_event_processor_lambda_policy_doc.json
}
