# Stage 1: Build stage
FROM eclipse-temurin:21-jdk-alpine AS builder

WORKDIR /build

# Install maven
RUN apk add --no-cache maven

# Cache maven dependencies layer
COPY .mvn/ .mvn
COPY pom.xml ./
RUN mvn dependency:go-offline -B -s .mvn/settings.xml || true

# Copy source code and build jar
COPY src ./src
RUN mvn clean package -DskipTests -B -s .mvn/settings.xml

# Stage 2: Runtime image
FROM eclipse-temurin:21-jre-alpine AS runner

WORKDIR /app

# Create non-root system user for security
RUN addgroup -S spring && adduser -S spring -G spring

# Copy jar from builder
COPY --from=builder --chown=spring:spring /build/target/*.jar app.jar

USER spring:spring

ENV PORT=8080
ENV JAVA_OPTS="-XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0 -Djava.security.egd=file:/dev/./urandom"

EXPOSE 8080

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
