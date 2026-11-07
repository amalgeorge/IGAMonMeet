# Local accounts with self-registration, Admin approval and BCrypt

Users register themselves with a username and password; there is no SSO. Every registration stays a Pending user until an Admin approves it, because the app holds IAM data and open registration would expose it to anyone who can reach the URL. Passwords are stored only as BCrypt hashes, never in plain text or with reversible encryption.

## Consequences

- No SMTP is assumed. A forgotten password becomes a request in the Admin queue; the Admin issues a one-time temporary password that must be changed at next sign-in.
- If no Admin can sign in, a DBA runs `EXEC dbo.usp_AdminRecovery @username = '...'`. It unlocks the account, stores only the SHA2-256 hash of a random one-time token (valid 1 hour), writes an Audit log row and returns a `/recover?token=...` link. No password passes through the DBA.
- The approval step can be switched off by an Admin (Feature toggle). That change asks for confirmation and is audit-logged.
- There are exactly two roles, Admin and End user. Team tags are not permissions.
