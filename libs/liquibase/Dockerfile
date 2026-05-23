FROM liquibase/liquibase:latest

# Copy all changelogs to the image so it can be used for any service
COPY libs/liquibase/src/main/resources/db/changelog /liquibase/changelog

# Default changelog file path using SERVICE_NAME environment variable
# This will be overridden at runtime by the GCP Job or gcloud execution
ENV LIQUIBASE_CHANGELOG_FILE=/liquibase/changelog/${SERVICE_NAME}/db.changelog-master.yaml

# The entrypoint is already set in the base image
