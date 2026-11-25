# ============================================================================
# Stage 1: Build with Maven & Eclipse Temurin 21 JDK
# ============================================================================
FROM maven:3.9.8-eclipse-temurin-21-alpine AS builder
WORKDIR /workspace

# Copy parent and module POMs first to leverage Docker layer cache for dependencies
COPY pom.xml ./pom.xml
COPY lambdas/order-api-lambda/pom.xml ./lambdas/order-api-lambda/pom.xml
COPY lambdas/order-event-processor-lambda/pom.xml ./lambdas/order-event-processor-lambda/pom.xml
COPY order-batch-service/pom.xml ./order-batch-service/pom.xml

RUN mvn -B -pl order-batch-service dependency:go-offline

# Copy Spring Batch microservice sources and package executable JAR
COPY order-batch-service/src ./order-batch-service/src
RUN mvn -B -pl order-batch-service clean package -DskipTests

# Extract Spring Boot layered JAR for optimal container startup & caching
WORKDIR /workspace/order-batch-service/target
RUN java -Djarmode=layertools -jar order-batch-service.jar extract

# ============================================================================
# Stage 2: Minimal Hardened Runtime with Eclipse Temurin 21 JRE Alpine
# ============================================================================
FROM eclipse-temurin:21-jre-alpine AS runtime
WORKDIR /app

# Security best practice: run container as non-root user
RUN addgroup -S springgroup && adduser -S springuser -G springgroup

COPY --from=builder /workspace/order-batch-service/target/dependencies/ ./
COPY --from=builder /workspace/order-batch-service/target/spring-boot-loader/ ./
COPY --from=builder /workspace/order-batch-service/target/snapshot-dependencies/ ./
COPY --from=builder /workspace/order-batch-service/target/application/ ./

RUN chown -R springuser:springgroup /app
USER springuser

EXPOSE 8080

ENV JAVA_OPTS="-XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0 -XX:+UseG1GC"

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS org.springframework.boot.loader.launch.JarLauncher"]
