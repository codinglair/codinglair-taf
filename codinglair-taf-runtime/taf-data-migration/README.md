# TAF Data Migration

This optional module applies Git-managed Flyway migrations to named JDBC SUT connections during
consumer preflight. It is inert unless `taf.migration.enabled=true`; consumers without this module
or without enablement are unaffected.

```yaml
taf:
  migration:
    enabled: true
    connections:
      orders:
        policy: validate-and-migrate
        locations: [classpath:db/migration/orders]
```

`DISABLED` is the default. `VALIDATE_ONLY` checks history and checksums without applying pending
scripts. `VALIDATE_AND_MIGRATE` validates first and then applies pending scripts. Baseline-on-migrate,
repair, clean, and destructive reset are always disabled.

Testcontainers targets require the explicit non-disabled per-connection policy above. External or
shared targets additionally require an application-owned `MigrationAuthorization` bean; properties
alone cannot authorize them. Production targets are denied by the default authorization bean and
must never be authorized without a separately approved environment policy.

Compatibility baseline: Java 25, Spring Boot 4.1.0, Flyway 11.14.1, PostgreSQL JDBC 42.7.7,
PostgreSQL 17, and Testcontainers 2.0.5. Flyway Community is Apache-2.0; it is isolated to this
optional adapter. The PostgreSQL database adapter is required by Flyway 11 for PostgreSQL support.
