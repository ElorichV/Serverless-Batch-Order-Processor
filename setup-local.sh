#!/usr/bin/env bash
set -euo pipefail

PROJECT_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$PROJECT_ROOT"

echo "=================================================================="
echo "  Serverless & Batch Order Processor - LocalStack Setup (Bash)"
echo "=================================================================="

# 1. Compile Java 21 modules (Lambdas + Spring Batch)
echo ""
echo "[1/4] Compiling Java 21 modules (order-api-lambda, order-event-processor-lambda, order-batch-service)..."
if command -v mvn >/dev/null 2>&1 && java -version 2>&1 | grep -q '"21\.'; then
  echo "Using local Maven + JDK 21..."
  mvn -B clean package
else
  echo "Local Maven/JDK 21 not detected on PATH. Building inside Docker (maven:3.9.8-eclipse-temurin-21-alpine)..."
  docker run --rm \
    -v "$PROJECT_ROOT":/workspace \
    -w /workspace \
    maven:3.9.8-eclipse-temurin-21-alpine \
    mvn -B clean package
fi

# 2. Start LocalStack, PostgreSQL, and Spring Batch Service
echo ""
echo "[2/4] Starting LocalStack, PostgreSQL 16, and Spring Batch container via Docker Compose..."
docker compose up -d --build

echo "Waiting for LocalStack edge gateway (http://localhost:4566/_localstack/health)..."
for i in {1..30}; do
  if curl -fsS http://localhost:4566/_localstack/health >/dev/null 2>&1; then
    echo "LocalStack is healthy!"
    break
  fi
  sleep 2
done

# 3. Apply Terraform against LocalStack
echo ""
echo "[3/4] Provisioning AWS Serverless resources in LocalStack with Terraform..."
if command -v terraform >/dev/null 2>&1; then
  (
    cd "$PROJECT_ROOT/terraform"
    terraform init -input=false
    terraform apply -auto-approve -var="use_localstack=true" -var="enable_paid_resources=false"
  )
else
  echo "Local Terraform CLI not found on PATH. Running via hashicorp/terraform:1.8 container..."
  docker run --rm --network host \
    -v "$PROJECT_ROOT":/workspace \
    -w /workspace/terraform \
    hashicorp/terraform:1.8 \
    init -input=false
  docker run --rm --network host \
    -v "$PROJECT_ROOT":/workspace \
    -w /workspace/terraform \
    hashicorp/terraform:1.8 \
    apply -auto-approve -var="use_localstack=true" -var="enable_paid_resources=false"
fi

# 4. Upload sample batch CSV to LocalStack S3
echo ""
echo "[4/4] Seeding sample CSV (sample-data/orders-batch-sample.csv) into s3://financial-orders-bucket/incoming/csv/..."
if command -v aws >/dev/null 2>&1; then
  AWS_ACCESS_KEY_ID=test AWS_SECRET_ACCESS_KEY=test AWS_DEFAULT_REGION=us-east-1 \
    aws --endpoint-url=http://localhost:4566 s3 cp \
    "$PROJECT_ROOT/sample-data/orders-batch-sample.csv" \
    s3://financial-orders-bucket/incoming/csv/orders-batch-sample.csv
else
  docker exec orders-localstack awslocal s3 cp \
    - s3://financial-orders-bucket/incoming/csv/orders-batch-sample.csv < "$PROJECT_ROOT/sample-data/orders-batch-sample.csv"
fi

echo ""
echo "=================================================================="
echo "  Environment Ready!"
echo "  - LocalStack Endpoint : http://localhost:4566"
echo "  - Spring Batch API    : http://localhost:8080/api/v1/batch/jobs/orders"
echo "  - Interactive Slides  : docs/presentacion-interactiva.html"
echo "=================================================================="
