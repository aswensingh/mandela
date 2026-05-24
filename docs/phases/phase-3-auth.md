# Phase 3 — Authentication

**Goal:** Add Spring Security with JWT. A user can register, log in, and receive an access token. **No multi-tenancy yet** — all users are flat for now. Multi-tenancy comes in Phase 4.

**Prerequisite:** Phase 2 verified.

---

## Scope

In:
- `users` table (no tenant_id yet — added in Phase 4)
- Spring Security with JWT (stateless)
- `POST /api/auth/register`
- `POST /api/auth/login` → returns `{accessToken, refreshToken, user}`
- `POST /api/auth/refresh`
- `GET /api/auth/me`
- React: Redux Toolkit set up, login page, route guard

Out:
- Tenants (Phase 4)
- Role-based access control (Phase 6 introduces it properly, alongside tenant roles)
- Password reset, email verification (out of scope entirely for now)

---

## Deliverables

### Backend

#### `backend/pom.xml` — add

```xml
<dependency>
  <groupId>org.springframework.boot</groupId>
  <artifactId>spring-boot-starter-security</artifactId>
</dependency>
<dependency>
  <groupId>org.springframework.boot</groupId>
  <artifactId>spring-boot-starter-validation</artifactId>
</dependency>
<dependency>
  <groupId>io.jsonwebtoken</groupId>
  <artifactId>jjwt-api</artifactId>
  <version>0.12.6</version>
</dependency>
<dependency>
  <groupId>io.jsonwebtoken</groupId>
  <artifactId>jjwt-impl</artifactId>
  <version>0.12.6</version>
  <scope>runtime</scope>
</dependency>
<dependency>
  <groupId>io.jsonwebtoken</groupId>
  <artifactId>jjwt-jackson</artifactId>
  <version>0.12.6</version>
  <scope>runtime</scope>
</dependency>
```

#### Flyway migration `V2__auth.sql`

```sql
CREATE TABLE users (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  email citext NOT NULL UNIQUE,
  password_hash text NOT NULL,
  full_name text,
  status text NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE','DISABLED')),
  last_login_at timestamptz,
  created_at timestamptz NOT NULL DEFAULT now(),
  updated_at timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE refresh_tokens (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  user_id uuid NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  token_hash text NOT NULL,
  expires_at timestamptz NOT NULL,
  revoked_at timestamptz,
  created_at timestamptz NOT NULL DEFAULT now()
);

CREATE INDEX idx_refresh_tokens_token_hash ON refresh_tokens(token_hash);
CREATE INDEX idx_refresh_tokens_user_id ON refresh_tokens(user_id);
```

#### Backend code (new package: `com.marketinghub.auth`)

**Entities:**
- `User` (JPA entity): id, email, passwordHash, fullName, status, lastLoginAt, createdAt, updatedAt
- `RefreshToken` (JPA entity): id, userId, tokenHash, expiresAt, revokedAt, createdAt

**Repositories:**
- `UserRepository extends JpaRepository<User, UUID>` with `findByEmail(String email)`
- `RefreshTokenRepository extends JpaRepository<RefreshToken, UUID>` with `findByTokenHash(String hash)`

**DTOs:**
- `RegisterRequest`: email (@Email, @NotBlank), password (@NotBlank, @Size(min=8)), fullName (@NotBlank)
- `LoginRequest`: email, password
- `RefreshRequest`: refreshToken
- `AuthResponse`: accessToken, refreshToken, user (UserDto)
- `UserDto`: id, email, fullName

**Services:**
- `JwtService`:
  - Reads `JWT_SECRET` from `application.yml` (`security.jwt.secret`)
  - Validates secret length ≥ 32 chars at startup (`@PostConstruct`), throws `IllegalStateException` otherwise
  - `issueAccessToken(userId, email)` → 15-minute HS256 JWT with claims `sub` (userId), `email`
  - `issueRefreshToken(userId)` → 7-day random opaque string, returns the plain token (caller hashes it before storing)
  - `parseAndValidate(jwtString)` → returns claims or throws
  - Use `Keys.hmacShaKeyFor(secretBytes)` from JJWT
- `AuthService`:
  - `register(RegisterRequest)`: check email unique, BCrypt hash password (`BCryptPasswordEncoder` strength 12), save user, return UserDto
  - `login(LoginRequest)`: find user, verify password, issue access + refresh, store refresh hashed (`BCryptPasswordEncoder` or SHA-256, hashed not plain), update `last_login_at`, return AuthResponse
  - `refresh(RefreshRequest)`: find matching non-revoked non-expired refresh token by hash, rotate (revoke old, issue new pair), return AuthResponse
  - `logout(RefreshRequest)`: mark refresh token revoked

**Controller:**
- `AuthController` at `/api/auth`:
  - `POST /register` → 201 with UserDto
  - `POST /login` → 200 with AuthResponse
  - `POST /refresh` → 200 with AuthResponse
  - `POST /logout` → 204
  - `GET /me` → 200 with UserDto (requires auth)

**Security:**
- `JwtAuthenticationFilter extends OncePerRequestFilter`: parses Bearer header, validates JWT, sets `SecurityContext` with a simple `AuthenticatedPrincipal(userId, email)` and `UsernamePasswordAuthenticationToken`
- `SecurityConfig` (`@Configuration @EnableWebSecurity`):
  - Stateless session
  - CSRF disabled
  - `permitAll`: `/api/auth/login`, `/api/auth/register`, `/api/auth/refresh`, `/api/health`, `/api/db-info`, `/actuator/health`
  - All other `/api/**`: authenticated
  - Filter chain: add `JwtAuthenticationFilter` before `UsernamePasswordAuthenticationFilter`
  - CORS: allow `http://localhost:5173` with credentials

**Global error handler `@RestControllerAdvice`:**
- Handles `MethodArgumentNotValidException` → 400 `{"error":{"code":"VALIDATION_FAILED","message":"...","traceId":"..."}}`
- Handles `AccessDeniedException` → 403
- Handles `AuthenticationException` → 401
- Fallback → 500
- `traceId` from MDC (set by a simple filter that generates UUID per request if header missing)

#### `application.yml` — add

```yaml
security:
  jwt:
    secret: ${JWT_SECRET}
    access-token-ttl-minutes: 15
    refresh-token-ttl-days: 7
```

#### `.env.example` — add

```
JWT_SECRET=please-generate-a-32-plus-char-secret-here
```

(Tell user: generate with `openssl rand -base64 48` and paste.)

### Frontend

#### `frontend/package.json` — add

```json
"@reduxjs/toolkit": "^2.5.0",
"react-redux": "^9.2.0",
"redux-persist": "^6.0.0",
"react-router-dom": "^6.27.0",
"async-mutex": "^0.5.0"
```

#### New structure

```
src/
  app/
    store.ts
    hooks.ts
  services/
    baseApi.ts
    baseQuery.ts
  features/
    auth/
      authSlice.ts
      authApi.ts
      LoginPage.tsx
      RegisterPage.tsx
      ProtectedRoute.tsx
  shared/
    AppLayout.tsx
  App.tsx
  main.tsx
```

#### `app/store.ts`

- `configureStore` with reducers `auth` (persisted via `redux-persist`, whitelist `['user','accessToken','refreshToken']`) and `[baseApi.reducerPath]: baseApi.reducer`
- Middleware: default + `baseApi.middleware`, with `serializableCheck` ignoring redux-persist actions
- Export `RootState`, `AppDispatch`, `store`, `persistor`

#### `app/hooks.ts`

- Typed `useAppDispatch`, `useAppSelector`

#### `services/baseQuery.ts`

- `fetchBaseQuery({ baseUrl: '/api', prepareHeaders })` that attaches Bearer from `state.auth.accessToken`
- Export a `baseQueryWithReauth` wrapper using `async-mutex`:
  - On 401, acquire mutex
  - Call `/api/auth/refresh` with current refresh token
  - On success: dispatch `credentialsReceived`, retry original
  - On failure: dispatch `loggedOut`
  - If mutex already held, wait then retry

#### `services/baseApi.ts`

- `createApi({ reducerPath: 'api', baseQuery: baseQueryWithReauth, tagTypes: ['Me'], endpoints: () => ({}) })`
- Features extend via `injectEndpoints`

#### `features/auth/authSlice.ts`

- State: `{ user: User | null, accessToken: string | null, refreshToken: string | null }`
- Reducers: `credentialsReceived`, `loggedOut`
- `extraReducers` listens to `authApi.endpoints.login.matchFulfilled` → set state, and `logout.matchFulfilled` → clear

#### `features/auth/authApi.ts`

- `baseApi.injectEndpoints` adding: `login`, `register`, `refresh`, `logout`, `getMe`
- Export hooks `useLoginMutation`, `useRegisterMutation`, `useGetMeQuery`, etc.

#### `features/auth/LoginPage.tsx`

- AntD `Card` with `Form` (email, password)
- `useLoginMutation`
- On success → `navigate('/app')`
- Show error in `Form.ErrorList` or `Alert`

#### `features/auth/RegisterPage.tsx`

- Same shape, calls register, on success auto-login (or redirect to login with a success message — simpler)

#### `features/auth/ProtectedRoute.tsx`

- If `state.auth.accessToken` missing → redirect to `/login`
- Otherwise render `<Outlet />`

#### `shared/AppLayout.tsx`

- AntD `Layout` with `Header` (showing "MarketingHub" + user's name + logout button) and `Content` containing `<Outlet />`
- Logout button calls `useLogoutMutation` then `navigate('/login')`

#### `App.tsx`

- React Router routes:
  - `/login` → `LoginPage`
  - `/register` → `RegisterPage`
  - `/app` → `ProtectedRoute` → `AppLayout` containing nested routes
    - `/app` (index) → simple "Welcome, {fullName}" page
- Catch-all `*` → redirect to `/app`

#### `main.tsx`

- Wrap `<App />` in `<Provider store={store}>` → `<PersistGate>` → `<BrowserRouter>` → AntD `<ConfigProvider>`

---

## Hard rules

- JWT secret minimum 32 chars, validated at startup
- Refresh tokens hashed before storing (never plaintext in DB)
- Refresh token rotation: every refresh revokes the old one and issues a new one
- Password hashing: BCrypt strength 12, no exceptions
- No tenant logic yet — that's Phase 4

---

## Verification gate

```bash
# 1. Backend tests pass (write a basic AuthServiceTest)
cd backend && ./mvnw test
cd ..

# 2. Stack up
docker compose down -v
docker compose up -d --build
sleep 60

# 3. Register a user
curl -sf -X POST http://localhost:8080/api/auth/register \
  -H 'Content-Type: application/json' \
  -d '{"email":"test@example.com","password":"testpass123","fullName":"Test User"}'

# 4. Log in
TOKENS=$(curl -sf -X POST http://localhost:8080/api/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"email":"test@example.com","password":"testpass123"}')
echo $TOKENS
ACCESS=$(echo $TOKENS | jq -r .accessToken)
REFRESH=$(echo $TOKENS | jq -r .refreshToken)

# 5. Hit /me
curl -sf -H "Authorization: Bearer $ACCESS" http://localhost:8080/api/auth/me

# 6. Refresh
curl -sf -X POST http://localhost:8080/api/auth/refresh \
  -H 'Content-Type: application/json' \
  -d "{\"refreshToken\":\"$REFRESH\"}"

# 7. Old refresh should now be revoked → 401
curl -i -X POST http://localhost:8080/api/auth/refresh \
  -H 'Content-Type: application/json' \
  -d "{\"refreshToken\":\"$REFRESH\"}" | head -1

# 8. Unauth call to /me → 401
curl -i http://localhost:8080/api/auth/me | head -1
```

Manual:
- Open http://localhost:5173 → redirected to `/login`
- Register a new user via UI
- Log in via UI → see `/app` welcome page
- Reload page → still logged in (redux-persist)
- Click logout → back to `/login`

---

## Output expected at end of phase

1. Files added/modified
2. Output of every verification command
3. Confirmation that old refresh token is rejected after rotation (step 7 returns 401)
4. "Phase 3 done — ready for Phase 4?"
