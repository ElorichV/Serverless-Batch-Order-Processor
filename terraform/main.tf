module "serverless_core" {
  source = "./modules/serverless-core"

  environment                           = var.environment
  aws_region                            = var.aws_region
  use_localstack                        = var.use_localstack
  orders_bucket_name                    = var.orders_bucket_name
  order_api_lambda_jar_path             = var.order_api_lambda_jar_path
  order_event_processor_lambda_jar_path = var.order_event_processor_lambda_jar_path
}

# Reference Enterprise Compute Module (EC2 + EKS)
# Disabled by default (enable_paid_resources = false) so local & AWS Free Tier deployments cost $0.00.
module "aws_only_compute" {
  source = "./aws-only"
  count  = var.enable_paid_resources ? 1 : 0

  environment = var.environment
  aws_region  = var.aws_region
}
