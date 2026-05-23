FROM liquibase/liquibase:latest

# Install the PostgreSQL driver as it is no longer included by default in 5.0+
RUN lpm add postgresql --global

# Copy all changelogs to the image so it can be used for any service
COPY libs/liquibase/src/main/resources/db/changelog /liquibase/changelog

# The entrypoint is already set in the base image.
# We add "update" as the default command to apply migrations.
# Environment variables like LIQUIBASE_COMMAND_CHANGELOG_FILE 
# should be passed at runtime to specify which service to migrate.
CMD ["update"]
