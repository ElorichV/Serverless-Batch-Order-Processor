# ============================================================================
# S3 Bucket for Financial Orders + EventBridge Integration
# ============================================================================

resource "aws_s3_bucket" "orders_bucket" {
  bucket        = var.orders_bucket_name
  force_destroy = true

  tags = {
    Project     = "ServerlessBatchOrderProcessor"
    Environment = var.environment
  }
}

resource "aws_s3_bucket_public_access_block" "orders_bucket_public_block" {
  bucket = aws_s3_bucket.orders_bucket.id

  block_public_acls       = true
  block_public_policy     = true
  ignore_public_acls      = true
  restrict_public_buckets = true
}

# Enables automatic emission of S3 Object Created events to Amazon EventBridge
resource "aws_s3_bucket_notification" "orders_bucket_eventbridge" {
  bucket      = aws_s3_bucket.orders_bucket.id
  eventbridge = true
}
