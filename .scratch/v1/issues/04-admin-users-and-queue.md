# 04 Admin: user management, Admin queue, password reset

Blocked by: 03

Spec §1.7, §1.10.

- Approve/reject, disable/enable, unlock, promote/demote; last active Admin protected.
- Forgot-password request from the sign-in screen goes to the Admin queue.
- Admin Reset shows a one-time temporary password once; User must change it at next sign-in.
- Admin queue page lists pending registrations and reset requests.
- Every action audit-logged.
