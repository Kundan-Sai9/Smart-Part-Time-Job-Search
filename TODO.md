# TODO

## OAuth login + Unified JWT (No Roles)

### Step 1 — Verify no role-based authorization
- [x] Read SecurityConfig: `/api/**` only requires authentication; no role checks found.
- [x] Read JwtAuthFilter: creates principal with empty authorities (unified user).
- [ ] Read other controllers/services for any `@JwtAuthz`/`Role` usage (none found via tool limitations; to be confirmed).

### Step 2 — Remove role machinery (if unused)
- [ ] If `JwtAuthz`/`Role` are unused in code paths, remove them.
- [ ] If used anywhere, delete those annotations and replace with plain authentication.

### Step 3 — Add OAuth2 login and issue JWT
- [ ] Add Spring Security OAuth2 Client dependency.
- [ ] Add application.yml/properties entries for chosen OAuth provider.
- [ ] Implement OAuth success handler:
  - find/create unified `User`
  - issue `access_token` JWT
- [ ] Provide frontend-compatible response format.

### Step 4 — Ensure JWT claims support user identification
- [ ] Optionally include `userId` claim (subject already set).
- [ ] Ensure filter/principal can expose userId to controllers (if needed).

### Step 5 — Tests / verification
- [ ] Run backend build/tests.
- [ ] Verify OAuth login -> JWT returned.
- [ ] Verify role-less protected endpoints work with JWT.

