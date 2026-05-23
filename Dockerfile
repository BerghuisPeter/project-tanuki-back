# Use a lightweight JRE for the final image
FROM eclipse-temurin:25-jre-jammy
WORKDIR /app

# Use an argument to specify which service is being built
ARG SERVICE_NAME

# Copy the pre-built JAR from the GitHub Actions runner
# The JAR is expected to be at services/${SERVICE_NAME}/target/*.jar
COPY services/${SERVICE_NAME}/target/*.jar app.jar

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar"]
