# Architecture — Taskly (MOPR)

## 1. System overview

```
┌─────────────────────────────┐            HTTPS / Socket.IO            ┌──────────────────────────────┐
│  Android app (Kotlin)       │ ──────────────────────────────────────▶ │  Backend (Node.js/Express)   │
│  offline-first, Room cache  │ ◀────────────────────────────────────── │  MySQL (state) + Redis (OTP, │
│  single Activity + Fragments│            JSON + realtime events       │  due-job schedule) + SMTP    │
└─────────────────────────────┘                                         └──────────────────────────────┘
```

- Source of truth for shared data is the backend (MySQL).
- The app keeps a Room cache (`morp.db`) and syncs personal tasks up/down; team data is fetched per screen.
- Realtime (chat, notifications) flows over Socket.IO rooms; everything else is request/response REST.
- Release flow (CI → tag → signed APK/AAB → GitHub Releases) is described in `docs/CI_CD.md` and omitted here.

## 2. Android app (`app/`)

Base package `com.example.morp_prj`. Single activity (`ui.MainActivity`), ~30 fragments, 2 navigation graphs.

### 2.1 UI layer

- `MainActivity`: hosts `NavHostFragment` + `BottomNavigationView`. Owns guest-gating
  (`menu_team`/`menu_profile` reroute guests to `guest_prompt_fragment`), bottom-bar visibility rules,
  `SocketManager.connect()`, `NotificationRealtimeRepository.start()`, and a session-expired receiver
  that routes back to `onboarding_fragment`.
- Graphs: `res/navigation/main_nav.xml` (start `onboarding_fragment`; auth flow + 5 bottom tabs + team
  detail/chat) and `res/navigation/team_nav_graph.xml` (start `teamDashboardFragment`; all destinations
  scoped by `teamId`: detail, members, tasks, chat, settings, info, directory).
- Fragments hold most screen logic; only 3 ViewModels exist (`TeamTaskViewModel`,
  `dashboard/TaskDashboardViewModel`, `teamChat/TeamChatViewModel`). No Home/Personal/Profile ViewModels —
  those screens drive repositories directly.
- Adapters (~16) are per-screen (`ToDoAdapter`, `TeamAdapter`, `MemberAdapter`, `ChatAdapter`, …);
  plus `CalendarPickerBottomSheet` / `TaskFilterBottomSheet` and UI mapper helpers
  (`TaskUiMapper`, `NotificationUiMapper`, `RelativeTime`, `DueCategory`, `TaskStatus`, `PriorityLevel`).
- Known wrinkle: `ui/teamChat/` folder vs `package ...ui.chat` mismatch; both spellings appear in nav graphs.

### 2.2 Data layer

```
Fragment ──▶ Repository ──┬──▶ Retrofit ApiService ──▶ Backend REST
                          └──▶ Room DAO ──▶ morp.db (offline cache)
SocketManager / TeamRealtimeRepository ──▶ Socket.IO ──▶ Backend realtime
WorkManager (TaskDueSoonWorker) ──▶ local due reminders + notification channels
```

- Network: `data/api/RetrofitClient` (Gson + lenient booleans + `AuthInterceptor` with auto-refresh)
  exposes `auth/task/team/teamTask/notification/encryptionApiService`.
- Cache: `data/db/AppDatabase` (`TaskEntity`, `TeamEntity`, `DeletedTaskEntity`, `NotificationEntity`,
  `User`; v8, `exportSchema=true`, `fallbackToDestructiveMigration()`).
- Sync: `TaskSyncRepository` / `TaskMutationRepository` / `SessionTaskManager` push local changes
  (`POST /api/tasks/sync` with `{tasks[], deletedServerIds[]}` → `{idMap[]}`) and pull server state.
- Realtime: `data/remote/SocketManager` (`connect(token)/disconnect()`); team chat and notification
  streams handled by `TeamRealtimeRepository` / `NotificationRealtimeRepository`.
- Reminders: `notifications/WorkScheduler.scheduleTaskDueSoon()` + `TaskDueSoonWorker` (CoroutineWorker)
  + `NotificationChannels` + `DeviceNotificationHelper`. Manifest declares only INTERNET,
  ACCESS_NETWORK_STATE, POST_NOTIFICATIONS; no services/receivers.
- Security: `security/SecureTokenStorage` (EncryptedSharedPreferences/Keystore), `AuthInterceptor`
  (attaches access JWT, refreshes via `MyApplication` hook that wipes storage and broadcasts
  `ACTION_SESSION_EXPIRED` on failure), `KeyManager`/`CryptoManager`/`PasswordManager`,
  `SecurityChecker.validateDeviceSecurity()`. Cloudinary uploads go through `utils/CloudinaryHelper`
  with a server-signed signature (`POST /api/auth/cloudinary-signature`).
- Startup (`MyApplication`): sets auth interceptor, validates device security, creates channels,
  schedules due-soon worker.

### 2.3 Key flows (app side)

- Login: `LoginFragment` → `AuthRepository` → `AuthApiService` → stores tokens in `SecureTokenStorage`,
  `SocketManager.connect()`, navigates to home tab.
- Personal task edit: Fragment writes Room via `TaskRepository` → `TaskMutationRepository` queues sync →
  `POST /api/tasks/sync` → applies `idMap` to local rows.
- Team chat: `TeamChatFragment` joins `team:<id>` room via `SocketManager`, sends `sendTeamMessage`,
  receives `receiveTeamMessage`, persists via realtime repository.
- Session expiry: `AuthInterceptor` refresh fails → `MyApplication` clears `SecureTokenStorage` +
  `PreferenceManager` login data → broadcast → `MainActivity` routes to onboarding.

## 3. Backend (`backend/`)

Entry `server.js`: Express app, open CORS, 10 MB JSON/urlencoded bodies, static `/uploads`,
6 route mounts, final error handler; boots Redis (non-fatal), listens (`PORT` env or 3001,
auto-retries port+1 on EADDRINUSE ×3), then `initRealtime(server)` + `startDuePoller()`.

### 3.1 Request path

```
routes/*.js ──▶ middleware/authenticateToken ──▶ validation/sanitize ──▶ controllers/*.js
      ──▶ services/*.js ──▶ models/*.js ──▶ config/database.js (mysql2 pool) / config/redis.js
```

- `routes/`: thin mapping. `authRoutes` (public: register/login/OTP/reset; protected: profile/sessions),
  `taskRoutes` (`POST /sync`, `GET /`), `teamRoutes` (CRUD + membership + invite codes + messages),
  `teamTaskRoutes` (CRUD + status + assigned), `notificationRoutes` (inline handlers, no controller),
  `encryptionRoutes` (E2EE key blobs).
- `controllers/`: business logic per area; `taskController` also runs `CREATE TABLE IF NOT EXISTS`
  for the `Task` table (so `database/tasks.sql` is empty by design, not by omission).
- `services/`: `tokenService` (session/token lifecycle), `redisOTPService` (OTP lifecycle),
  `passwordResetService` (transactional reset), `realtime` (Socket.IO server), `taskDueScheduler`
  (Redis zset + 15 s poller → `notificationService`), `notificationService` (dedupe upsert + emits).
- `models/`: thin MySQL wrappers (`User`, `Team`); no ORM.
- `middleware/`: `authenticateToken` (Bearer → `jwt.verify(JWT_SECRET)` → `req.user{id,sessionId,...}`;
  401 `TOKEN_MISSING/EXPIRED/INVALID`; blacklist check is a TODO stub), `validation`
  (express-validator + sanitize-html chains), `rateLimiter` (defined presets, currently not applied
  in routes), `upload` (legacy local-avatar pipeline, superseded by Cloudinary URLs).

### 3.2 Auth design (JWT access + stateful sessions)

- Register: create inactive user → Redis OTP (6-digit, HMAC-hashed, 2 min TTL, 60 s resend cooldown,
  5 attempts → 60 s lockout) → email via `utils/emailService` (Gmail SMTP) → `verify-otp` activates.
- Login: bcryptjs check + `verified` flag → `tokenService.issueTokens()` creates a `user_sessions`
  row (UUID sessionId, device/IP/UA) and returns short access JWT (`JWT_SECRET`, ~30 min) +
  long refresh JWT (`REFRESH_TOKEN_SECRET`, HMAC-peppered hash stored, ~30 d).
- Refresh rotates both tokens atomically (`SELECT … FOR UPDATE`, timing-safe compare);
  reuse revokes the session (`TOKEN_REUSE_DETECTED`). Logout / password change / reset revoke
  one or all sessions. Socket.IO handshake verifies the same `JWT_SECRET`.

### 3.3 Data stores

- MySQL (primary): `users`/`User`, `user_sessions`, `Task` (inline DDL), `team`/`teammember`,
  `conversations`/`messages`, `notifications`, `user_encryption`/`team_encryption_keys`/`team_key_access`.
  Canonical/incremental DDL lives in `database/*.sql`; pool is Azure-compatible (SSL, limit 10).
- Redis (ephemeral): OTP keys (`otp:`, `otp_attempts:`, `otp_fail_cooldown:`, `otp_resend_cooldown:`,
  `pending_reg:`), due-job keys (`due:user:*:task:*:offset:*` + `due_jobs` zset). Server boots without it.
- Email: Gmail SMTP for OTP/reset/password-change mails. Storage: Cloudinary (signed) for avatars;
  legacy local `uploads/` still served statically.

## 4. Cross-cutting flows

| Flow | Path |
|---|---|
| Registration + OTP | App → `POST register(-init)` → Redis OTP + SMTP mail → `POST verify-otp` → active user |
| Login + refresh | `POST login` → access+refresh JWT → Bearer calls → `POST refresh` rotates pair |
| Personal tasks | Room ↔ `POST /api/tasks/sync` (`tasks[]`, `deletedServerIds[]` → `idMap[]`) ↔ MySQL `Task` |
| Team chat | App joins `team:<id>` → `sendTeamMessage` → saved to `conversations/messages` → `receiveTeamMessage` broadcast |
| Due reminders | `taskDueScheduler.scheduleForTask` (immediate if <24h/1h window else Redis zset) → 15 s poller → `notificationService` → Socket.IO `notification` + stored row → app WorkManager mirrors locally |
| E2EE keys | Client-derived keys; server stores only salts/encrypted team blobs (`encryptionController`) |

## 5. Configuration

Backend env is documented in `backend/.env.example`: `DB_HOST/DB_USER/DB_PASSWORD/DB_NAME`
(+ optional `DB_PORT`, `REDIS_URL` or `REDIS_HOST/PORT/...`), `JWT_SECRET`, `REFRESH_TOKEN_SECRET`,
`REFRESH_TOKEN_PEPPER`, `ACCESS/REFRESH_TOKEN_TTL_SECONDS`, `OTP_HMAC_SECRET`, `SMTP_EMAIL/SMTP_PASSWORD`,
`PORT`, `NODE_ENV`, `ALLOWED_ORIGINS`. Generate secrets with
`node -e "console.log(require('crypto').randomBytes(32).toString('hex'))"`.
Android side: JDK 17, SDK with API 36, `local.properties` (`sdk.dir`, Cloudinary values).

## 6. Decisions and constraints

- Offline-first personal tasks (Room + idMap sync) vs server-driven team data (fetch per screen).
- Stateful sessions in MySQL (revocable, reuse-detecting) instead of pure stateless JWT.
- OTP and scheduling state in Redis (ephemeral, non-fatal if down) instead of MySQL polling tables.
- E2EE: server never sees plaintext team keys.
- `usesCleartextTraffic=true` is set (HTTP backends work on LAN); switch to HTTPS-only before production.
- Demo-mode trade-offs pending replacement: hard-coded Cloudinary defaults, `continue-on-error`
  on `npm audit`, unapplied rate limiters — see `README.md` known issues and `docs/CI_CD.md` §8.
