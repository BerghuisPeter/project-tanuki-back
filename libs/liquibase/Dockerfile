FROM liquibase/liquibase:5.0.4

# The pinned base tag lags behind Ubuntu security updates, so patch the OS packages at build time. `lpm` (Liquibase's
# package manager) is only invoked by the entrypoint when INSTALL_MYSQL is set, which we never do, and its bundled Go
# runtime carries known CVEs, so drop it.
USER root
RUN apt-get update \
    && apt-get upgrade -y --no-install-recommends \
    && rm -rf /var/lib/apt/lists/* \
    && rm -f /liquibase/bin/lpm
USER liquibase

# Install the PostgreSQL driver as it is no longer included by default in 5.0+.
# Downloaded straight from Maven Central instead of `lpm add postgresql --global`, which fails
# with "Checksum validation failed" whenever lpm's package manifest lags behind the driver release.
ARG POSTGRES_DRIVER_VERSION=42.7.14
ADD --chown=liquibase:liquibase \
    https://repo1.maven.org/maven2/org/postgresql/postgresql/${POSTGRES_DRIVER_VERSION}/postgresql-${POSTGRES_DRIVER_VERSION}.jar \
    /liquibase/lib/postgresql.jar

# Copy the resources directory to the classpath
# This allows Liquibase to find files using the "db/changelog/" prefix
COPY libs/liquibase/src/main/resources /liquibase/changelog

# The entrypoint is already set in the base image.
# We add "update" as the default command to apply migrations.
CMD ["update"]
