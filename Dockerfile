# Stage 1: Build the application
FROM maven:3.9.9-eclipse-temurin-25 AS build
WORKDIR /app

# Use an argument to specify which service to build
ARG SERVICE_NAME

# Copy the parent pom and all module poms to cache dependencies
COPY pom.xml .
COPY libs/common/pom.xml libs/common/pom.xml
COPY libs/security/pom.xml libs/security/pom.xml
COPY libs/observability/pom.xml libs/observability/pom.xml
COPY services/auth-service/pom.xml services/auth-service/pom.xml
COPY services/profile-service/pom.xml services/profile-service/pom.xml

# Download dependencies (this will be cached if poms don't change)
RUN mvn dependency:go-offline -B

# Copy the source code
COPY libs/ libs/
COPY services/ services/

# Build only the requested service and its dependencies
RUN mvn clean package -pl services/${SERVICE_NAME} -am -DskipTests

# Stage 2: Runtime stage
FROM eclipse-temurin:25-jre-jammy
WORKDIR /app

# Use an argument to specify which service to run
ARG SERVICE_NAME
ENV SERVICE_NAME=${SERVICE_NAME}

# Copy the built jar from the build stage
# Note: The jar name usually follows the pattern artifactId-version.jar
COPY --from=build /app/services/${SERVICE_NAME}/target/*.jar app.jar

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar"]
