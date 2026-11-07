# 02 Local accounts: register, approve, sign in

Blocked by: 01

Spec §1.1–1.6. Tracer bullet from sign-in screen to database.

- Tables: users, password history, roles (Admin, End user).
- Register creates a Pending user; Admin approval (temporary API is fine until 04) makes it active.
- Sign in with BCrypt; generic error; 'Awaiting admin approval' for pending.
- Password rules: 12+ chars, not last 5.
- Lockout: 5 failures, 15 minutes.
- 30-minute idle session, session ID rotated at sign-in, CSRF on state changes, secure cookies.
- React screens: Sign in, Register, confirmation (as in the mockup).
