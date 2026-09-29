# Taskly (MOPR) — To_Do_App_Android

Task management app with personal to-dos, team workspaces, team chat, and reminders.
Two parts: native Android app (`app/`) + Node.js/Express API (`backend/`).
Releases are published to GitHub Releases as signed APK/AAB (see `docs/CI_CD.md`).

## Tech stack

| Part | Stack |
|---|---|
| Android | Kotlin 2.2.0, AGP 8.13.2, Gradle 8.13, Java 17, compileSdk/targetSdk 36, minSdk 24 |
| Android UI | Single `MainActivity`, Fragments, Navigation component (2 graphs), ViewBinding/DataBinding |
| Android data | Room (`morp.db`, v8, `fallbackToDestructiveMigration`), Retrofit 2.11 + Gson, socket.io-client 2.1.1, WorkManager, EncryptedSharedPreferences, Biometric, Glide, MPAndroidChart, Cloudinary Android 3.0.2 |
| Backend | Node.js (CI pins 20), Express 4, MySQL 8 (`mysql2`), Redis 5 (`node-redis`), Socket.IO 4, JWT (`jsonwebtoken`), bcryptjs, nodemailer (Gmail SMTP), Cloudinary, sharp, multer |
| CI/CD | GitHub Actions (`ci.yml`, `release.yml`), Dependabot, SemVer tags `vX.Y.Z` |

## Project structure

```
app/src/main/java/com/example/morp_prj/
  MyApplication.kt            # app entry: auth interceptor, session-expiry broadcast, channels, worker schedule
  ui/                         # MainActivity + ~30 fragments, adapters, bottom sheets (see docs/ARCHITECTURE.md)
  ui/dashboard/  ui/teamChat/ # dashboard + team chat (note: teamChat/ files declare package ui.chat)
  data/api/                   # RetrofitClient + 6 ApiService interfaces
  data/db/                    # Room: AppDatabase + 5 DAOs + entities
  data/model/                 # ~32 DTO/entity classes
  data/repository/  data/     # Auth/Task/Team/Notification/Encryption repositories, sync + realtime
  data/remote/                # SocketManager
  notifications/              # TaskDueSoonWorker, WorkScheduler, channels
  security/                   # AuthInterceptor, SecureTokenStorage, KeyManager, CryptoManager, SecurityChecker
  utils/                      # PreferenceManager, DateUtils, CloudinaryHelper
app/src/main/res/navigation/  # main_nav.xml (auth + bottom tabs), team_nav_graph.xml (team workspace)
backend/
  server.js                   # entry: middleware, 6 route mounts, Redis + Socket.IO + due poller
  routes/ controllers/        # auth, tasks, team, team-tasks, notifications, encryption
  services/                   # tokenService, redisOTPService, passwordResetService, realtime, taskDueScheduler, notificationService
  models/                     # thin MySQL wrappers (User, Team)
  middleware/                 # authenticateToken, validation, rateLimiter (imported, not applied), upload (legacy)
  config/                     # database.js (mysql2 pool), redis.js
  database/                   # schema.sql + incremental alters (Task table is created inline by controllers)
  utils/                      # helpers.js, emailService.js
  test/helpers.test.js        # node:test coverage for utils/helpers
.github/workflows/           # ci.yml (lint+unit+build, emulator UI test, backend check), release.yml (manual SemVer release)
scripts/generate-demo-keystore.sh  # MOCK demo keystore generator (see docs/CI_CD.md before real release)
```

## Prerequisites

- Android Studio (or JDK 17 + Android SDK, compileSdk 36) for `app/`.
- Node.js 20 + MySQL 8 + Redis for `backend/`.
- No Android SDK is installed on every machine — local Gradle builds require one (`ANDROID_HOME` or `local.properties` with `sdk.dir`).

## Setup

### Backend

```bash
cd backend
npm ci
cp .env.example .env   # then fill in DB_*, REDIS_*, JWT_*, OTP_*, SMTP_* (see .env.example)
# create MySQL database matching DB_NAME, apply backend/database/*.sql as needed
npm run dev            # or: npm start (PORT defaults to 3001 if unset)
npm test               # runs test/helpers.test.js
```

### Android app

1. Open the repo root in Android Studio; let it sync Gradle 8.13.
2. Point the app at the backend (Retrofit base URL) and set Cloudinary values in `local.properties`
   (defaults are hard-coded demo values in `app/build.gradle.kts` — replace before production).
3. Run the `app` configuration on a device/emulator (API 24+).

## Tests

| Suite | Command | Notes |
|---|---|---|
| Backend unit | `cd backend && npm test` | `node:test`, no extra deps |
| Android unit | `./gradlew testDebugUnitTest` | includes `TaskUiMapperTest` |
| Android lint | `./gradlew lintDebug` | report: `app/build/reports/lint-results-debug.html` |
| Android instrumented | `./gradlew connectedDebugAndroidTest` | needs emulator; CI uses API 30 x86_64 |

## CI/CD

- Every push/PR to `main`/`develop` runs `ci.yml`: `android-check` (lint → unit test → debug APK),
  `android-ui-test` (emulator, API 30), `backend-check` (syntax → test → audit warning-only).
- Release is manual: Actions → Release → Run workflow on `main`, pick `patch`/`minor`/`major`.
  Full procedure, version rules (`versionCode = MAJOR*10000 + MINOR*100 + PATCH`, MINOR/PATCH < 100),
  secrets table, and branch-protection steps: **`docs/CI_CD.md`** (Vietnamese).

## API overview

Base: `http://host:PORT` (`PORT` env, default 3001). Auth: Bearer access JWT (30 min default) + rotating refresh JWT.

| Area | Endpoints |
|---|---|
| Auth (public) | `POST /api/auth/register(-init)` `verify-otp` `resend-otp` `otp-status` `login` `refresh` `logout` `forgot-password` `verify-reset-otp` `resend-reset-otp` `reset-password` |
| Auth (Bearer) | `GET /api/auth/profile` `user/:userId` `sessions`, `POST sessions/revoke`, `PUT profile`, `POST change-password` `cloudinary-signature` |
| Tasks | `POST /api/tasks/sync` (`{tasks[], deletedServerIds[]}` → `{idMap[]}`), `GET /api/tasks/` |
| Team | `POST /api/team/create|join|pin|remove-member|request|update-member-role`, `GET /api/team/user/:userId` `:teamId/members|leaders|:teamId|:teamId/messages`, `PUT /api/team/:teamId|:teamId/invite-code`, `DELETE /api/team/:teamId` |
| Team tasks | `POST /api/team-tasks/`, `GET /api/team-tasks/team/:teamId` `.../assigned`, `PATCH .../:taskId/status`, `PUT`/`DELETE .../:taskId` |
| Notifications | `GET /api/notifications`, `POST .../mark-read|delete|clear` |
| Encryption | `POST /api/encryption/initialize|team-key|team-key/get|team-keys/sync|team-key/rotate`, `GET /api/encryption/salt/:userId` |
| Realtime (Socket.IO) | JWT handshake; rooms `user:<id>`, `team:<teamId>`; `joinTeam/leaveTeam/sendTeamMessage` → `receiveTeamMessage`, `notification*` events |

## Known issues (do not "fix" silently — confirm first)

- `backend/package.json` script `setup-db` points to `setup-database.js`, which does not exist in the repo.
- `app/src/.../ui/teamChat/*.kt` files declare `package ...ui.chat` (folder/package mismatch); nav graphs reference both spellings.
- Cloudinary cloud name / API key / upload preset are hard-coded demo defaults in `app/build.gradle.kts`.
- `ci.yml` backend audit step uses `continue-on-error: true` (warning only, demo mode).
- `middleware/rateLimiter.js` is imported by routes but not applied; `middleware/upload.js` is legacy (profile uses Cloudinary URLs).
