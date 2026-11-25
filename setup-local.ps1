#Requires -Version 5.1
$ErrorActionPreference = "Stop"

$ProjectRoot = Split-Path -Parent $MyInvocation.MyCommand.Path
Set-Location $ProjectRoot

Write-Host "==================================================================" -ForegroundColor Cyan
Write-Host "  Serverless & Batch Order Processor - LocalStack Setup (Windows)" -ForegroundColor Cyan
Write-Host "==================================================================" -ForegroundColor Cyan

# 1. Compile Java 21 modules (Lambdas + Spring Batch)
Write-Host "`n[1/4] Compiling Java 21 modules (order-api-lambda, order-event-processor-lambda, order-batch-service)..." -ForegroundColor Yellow

$HasLocalMaven = $null -ne (Get-Command mvn -ErrorAction SilentlyContinue)
$JavaVersionOutput = ""
try { $JavaVersionOutput = (& java -version 2>&1) | Out-String } catch {}
$HasJava21 = $JavaVersionOutput -match '"21\.'

if ($HasLocalMaven -and $HasJava21) {
    Write-Host "Using local Maven + JDK 21..." -ForegroundColor Green
    mvn -B clean package
} else {
    Write-Host "Local Maven/JDK 21 not detected on PATH. Building inside Docker (maven:3.9.8-eclipse-temurin-21-alpine)..." -ForegroundColor Green
    docker run --rm `
        -v "${ProjectRoot}:/workspace" `
        -w /workspace `
        maven:3.9.8-eclipse-temurin-21-alpine `
        mvn -B clean package
}

# 2. Start LocalStack, PostgreSQL, and Spring Batch Service
Write-Host "`n[2/4] Starting LocalStack, PostgreSQL 16, and Spring Batch container via Docker Compose..." -ForegroundColor Yellow
docker compose up -d --build

Write-Host "Waiting for LocalStack edge gateway (http://localhost:4566/_localstack/health)..."
for ($i = 1; $i -le 30; $i++) {
    try {
        $resp = Invoke-RestMethod -Uri "http://localhost:4566/_localstack/health" -Method Get -TimeoutSec 3
        if ($null -ne $resp) {
            Write-Host "LocalStack is healthy!" -ForegroundColor Green
            break
        }
    } catch {
        Start-Sleep -Seconds 2
    }
}

# 3. Apply Terraform against LocalStack
Write-Host "`n[3/4] Provisioning AWS Serverless resources in LocalStack with Terraform..." -ForegroundColor Yellow
$HasTerraform = $null -ne (Get-Command terraform -ErrorAction SilentlyContinue)

if ($HasTerraform) {
    Push-Location "$ProjectRoot\terraform"
    try {
        terraform init -input=false
        terraform apply -auto-approve -var="use_localstack=true" -var="enable_paid_resources=false"
    } finally {
        Pop-Location
    }
} else {
    Write-Host "Local Terraform CLI not found on PATH. Running via hashicorp/terraform:1.8 container..." -ForegroundColor Green
    docker run --rm --network host `
        -v "${ProjectRoot}:/workspace" `
        -w /workspace/terraform `
        hashicorp/terraform:1.8 `
        init -input=false
    docker run --rm --network host `
        -v "${ProjectRoot}:/workspace" `
        -w /workspace/terraform `
        hashicorp/terraform:1.8 `
        apply -auto-approve -var="use_localstack=true" -var="enable_paid_resources=false"
}

# 4. Upload sample batch CSV to LocalStack S3
Write-Host "`n[4/4] Seeding sample CSV (sample-data/orders-batch-sample.csv) into s3://financial-orders-bucket/incoming/csv/..." -ForegroundColor Yellow
$HasAwsCli = $null -ne (Get-Command aws -ErrorAction SilentlyContinue)

if ($HasAwsCli) {
    $env:AWS_ACCESS_KEY_ID = "test"
    $env:AWS_SECRET_ACCESS_KEY = "test"
    $env:AWS_DEFAULT_REGION = "us-east-1"
    aws --endpoint-url=http://localhost:4566 s3 cp `
        "$ProjectRoot\sample-data\orders-batch-sample.csv" `
        s3://financial-orders-bucket/incoming/csv/orders-batch-sample.csv
} else {
    Get-Content "$ProjectRoot\sample-data\orders-batch-sample.csv" -Raw | `
        docker exec -i orders-localstack awslocal s3 cp - s3://financial-orders-bucket/incoming/csv/orders-batch-sample.csv
}

Write-Host "`n==================================================================" -ForegroundColor Cyan
Write-Host "  Environment Ready!" -ForegroundColor Green
Write-Host "  - LocalStack Endpoint : http://localhost:4566"
Write-Host "  - Spring Batch API    : http://localhost:8080/api/v1/batch/jobs/orders"
Write-Host "  - Interactive Slides  : docs/presentacion-interactiva.html"
Write-Host "==================================================================" -ForegroundColor Cyan
