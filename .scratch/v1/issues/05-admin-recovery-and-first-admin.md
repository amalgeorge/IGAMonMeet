# 05 First Admin setup and DBA recovery procedure

Blocked by: 04

Spec §1.8–1.9, ADR 0002.

- One-time first-Admin creation, forced password change at first sign-in.
- `dbo.usp_AdminRecovery @username`: unlock, random token via CRYPT_GEN_RANDOM, store SHA2_256 hash with 1-hour expiry, audit row, return /recover link.
- /recover page: valid unused token lets the Admin set a new password; token is then spent.
- Runbook in docs/ for the DBA.
