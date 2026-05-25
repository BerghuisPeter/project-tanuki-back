FROM liquibase/liquibase:latest

# Install the PostgreSQL driver as it is no longer included by default in 5.0+
RUN lpm add postgresql --global

# Copy the resources directory to the classpath
# This allows Liquibase to find files using the "db/changelog/" prefix
COPY libs/liquibase/src/main/resources /liquibase/changelog

# The entrypoint is already set in the base image.
# We add "update" as the default command to apply migrations.
CMD ["update"]
