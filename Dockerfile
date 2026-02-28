# Stage 1: Build
FROM eclipse-temurin:17-jdk-jammy AS build
WORKDIR /app

ARG APP_VERSION

# Copy maven executable and pom
COPY .mvn/ .mvn
COPY mvnw pom.xml ./
RUN ./mvnw dependency:go-offline

# Copy source and build
COPY src ./src
ARG COMMIT_SHA
RUN ./mvnw clean package -DskipTests

# Stage 2: Runtime
# Using Google's Distroless for a minimal, non-root, secure base
FROM gcr.io/distroless/java17-debian12:nonroot
WORKDIR /app

# Metadata labels
LABEL maintainer="raghu.porumamilla@gmail.com"
LABEL org.opencontainers.image.source="https://github.com/raghuporumamila/hello-platform-service"

ARG APP_VERSION
ENV VERSION=${APP_VERSION}

# Copy the built artifact from the build stage
COPY --from=build /app/target/hello-platform-service-${APP_VERSION}.jar app.jar

# Environment defaults
ENV PORT=8080
ENV APP_COMMIT_SHA=${COMMIT_SHA}

USER nonroot
EXPOSE 8080

# Run with optimized memory settings and bind to the dynamic PORT variable
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75.0", "-Dserver.port=${PORT}", "-jar", "app.jar"]