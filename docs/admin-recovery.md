# First Admin setup and Admin recovery (DBA runbook)

One stored procedure does both jobs (ADR 0002). It never handles a password: it returns a one-time link, and the Admin sets their own password there.

| Situation | What the procedure does |
|---|---|
| No active Admin exists yet (first install) | Creates an Admin with that username, then issues a link |
| The username is an existing, active Admin who is locked out or forgot the password | Unlocks the account, then issues a link |
| Any other case | Refuses with an error (51001, 51002 or 51003) |

## Steps

1. Confirm the request with the Admin through a channel you trust (in person or a known phone number).
2. In SSMS, connected to the `igamonmeet` database:
   ```sql
   EXEC dbo.usp_AdminRecovery
        @username = 'jtan',
        @fullName = N'J. Tan',          -- used only when creating the first Admin
        @email    = 'jtan@company.com', -- used only when creating the first Admin
        @baseUrl  = 'https://iam-meeting.company.local:8443/igamonmeet';
   ```
3. Send the returned `recovery_link` to the Admin directly. It works once and expires after 1 hour (`expires_at_utc`).
4. The Admin opens the link and sets a password (12 to 64 characters).

Running the procedure again for the same Admin cancels the earlier link.

## What is recorded

- `RECOVERY_ISSUED` in the audit log, with the DBA's SQL login as the actor.
- `RECOVERY_COMPLETED` when the Admin sets the password.

The token sits after `#` in the link, so browsers do not send it to the server and it does not appear in Tomcat access logs. The database stores only its SHA2-256 hash.
