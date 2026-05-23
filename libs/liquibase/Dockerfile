FROM liquibase/liquibase:latest

# Install the PostgreSQL driver as it is no longer included by default in 5.0+
RUN lpm add postgresql --global

# Copy the resources directory to maintain the db/changelog/ structure
# This allows Liquibase to find included files using their full paths
COPY libs/liquibase/src/main/resources /liquibase/changelog

# The entrypoint is already set in the base image.
# We add "update" as the default command to apply migrations.
CMD ["update"]
