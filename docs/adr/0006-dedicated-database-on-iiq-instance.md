# Dedicated database on the IdentityIQ MSSQL instance, never inside the IIQ schema

The app gets its own database on the same MSSQL instance that hosts IdentityIQ, so the same DBA team, backups and monitoring apply. Tables are never placed in the IIQ database, because IIQ upgrades can alter or drop objects they do not own. Schema changes go through Flyway migrations only.
