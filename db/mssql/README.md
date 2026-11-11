# SQL Server database scripts (for the DBA)

| Script | Use |
|---|---|
| `igamonmeet-schema.sql` | New, empty database: creates everything. |
| `igamonmeet-upgrade.sql` | Existing app database: applies only what is missing (section 5). |

`igamonmeet-schema.sql` creates the whole IAM Monthly Meeting database on SQL Server 2019 or later in one run. It is generated from the app's Flyway migrations (`tools/db/build_mssql_script.py`) and checked in CI: every build runs it on an empty SQL Server 2022 database and then starts Flyway on the result, which must find nothing left to apply. The upgrade script is tested the same way, on a database built at an older version.

## 1. Create the database and run the script

1. Create an empty database, for example `igamonmeet` (collation of your standard; the app stores text as NVARCHAR).
2. Run the script as a login that can create tables, triggers and procedures in it (for example db_owner):

   ```
   sqlcmd -S <server> -d igamonmeet -E -b -i igamonmeet-schema.sql
   ```

   `-b` stops at the first error. The script refuses to run on a database that already has a Flyway history.

The script contains, in order: every table, index and constraint; the append-only trigger on `audit_log`; the procedure `dbo.usp_AdminRecovery`; seed rows (the default Template, the default Theme, settings); and the table `dbo.flyway_schema_history` filled the way Flyway fills it.

## 2. The app's login

Create a SQL login and database user for the app (its name and password go into the app server's settings).

- **Recommended:** make it `db_owner` of this database. At every start the app checks the Flyway history and applies migrations newer than the script, which needs DDL rights.
- **If DDL must stay with the DBA:** grant `db_datareader`, `db_datawriter` and `EXECUTE` on `dbo.usp_AdminRecovery`. For each new app version the DBA then runs that version's new migrations before the app starts with `igamonmeet-upgrade.sql` (section 5), or the app will fail its start-up check.

The app connects with `encrypt=true`. The server's certificate must be trusted by the app server, unless the test-only `trustServerCertificate` setting is used.

## 3. First Admin

After the app is up, run the recovery procedure with the app's address and give the one-time link to the first Admin:

```sql
EXEC dbo.usp_AdminRecovery @username = 'admin.user', @fullName = N'First Admin', @email = 'admin@example.com',
     @baseUrl = 'https://<app-host>:8443/igamonmeet';
```

The link works once and expires after 1 hour. The same procedure unlocks an Admin later (docs/admin-recovery.md).

## 4. Notes

- The app's first start after the script also runs two one-time data upgrades in code (SITREP replaces Incidents, the Programme Sections are added to the Template). They only insert Template rows and settings.
- `audit_log` cannot be updated or deleted, even by db_owner, because of the trigger.
- Times are stored in UTC.

## 5. Upgrading an existing database

Each app release ships a new `igamonmeet-upgrade.sql`. It is needed only when the app's login has no DDL rights; with `db_owner` the app applies the same changes itself at start-up.

1. Stop the app and back up the database.
2. Run the script as a login that can change tables, triggers and procedures:

   ```
   sqlcmd -S <server> -d igamonmeet -E -b -i igamonmeet-upgrade.sql
   ```

3. Start the new app version.

How it behaves:

- It reads `dbo.flyway_schema_history` and applies only the migrations not listed there, in version order. It prints one `Applied V<n>` line for each, and the schema version at the end.
- Each migration runs in its own transaction together with its history row. If one fails it is rolled back as a whole, the script stops, and nothing after it is applied. Fix the cause and run the script again: it continues from the failed migration.
- Running it on a database that is already current changes nothing.
- It refuses a database that has no Flyway history (use `igamonmeet-schema.sql`) or whose history has a failed migration.
- It works from any earlier version, whether the database was made by `igamonmeet-schema.sql` or by the app.
