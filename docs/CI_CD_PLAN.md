# Kế hoạch: Xây dựng CI/CD cho dự án To_Do_App_Android (Taskly / MOPR)

> Tài liệu này dành cho **agent thực thi (opencode CLI)** và cho **thành viên trong nhóm**.
> Mỗi bước ghi rõ: làm gì, ở file nào, và kiểm tra thế nào là xong.

---

## 0. Bối cảnh (vì sao cần làm)

- Dự án hiện **chưa có CI/CD**: chưa có thư mục `.github/workflows/`, chưa có tag phiên bản nào, `versionCode = 1` và `versionName = "1.0"` đang ghi cứng trong `app/build.gradle.kts`.
- Mã nguồn gồm 2 phần:
  - **App Android** (Kotlin, Gradle 8.13, AGP 8.13.2, Java 17, compileSdk 36). Đã có 2 unit test (`app/src/test/...`) và 1 instrumented test (`app/src/androidTest/...`).
  - **Backend Node.js** (thư mục `backend/`, Express, có `package-lock.json`). Chưa có test nào, chưa có script `test`.
- Repo nằm trên GitHub: `https://github.com/ppk205/To_Do_App_Android`. Nhánh chính: `main`, nhánh phát triển: `develop`, các nhánh tính năng (feature).
- File `gradlew` đang **không có quyền thực thi** trong git (mode `100644`) → sẽ lỗi trên máy Linux của GitHub Actions nếu không sửa.

**Mục tiêu:** Mỗi lần đẩy code hoặc mở Pull Request thì tự động kiểm tra (lint, test, build). Khi muốn phát hành, người phát hành bấm 1 nút → hệ thống tự tính số phiên bản, tạo tag `vX.Y.Z`, build APK/AAB đã ký, và đăng lên GitHub Releases.

**Quyết định đã chốt với người dùng:**
- Phạm vi: App Android + kiểm tra backend (CI). **Không** tự deploy backend lên server.
- Nơi phát hành: **GitHub Releases**.
- Cách tạo phiên bản: **bấm chạy thủ công** workflow "Release", chọn `major` / `minor` / `patch`.
- Thông tin bí mật (keystore, mật khẩu): **dùng giá trị giả (mock) để demo**. Tài liệu phải có mục nhắc rõ chỗ cần thay thế sau.

---

## 1. Thuật ngữ (dùng thống nhất trong mọi file)

| Thuật ngữ | Nghĩa trong dự án này |
|---|---|
| **CI** (Continuous Integration – Tích hợp liên tục) | Tự động lint + test + build mỗi khi có push hoặc Pull Request. |
| **CD** (Continuous Delivery – Phát hành liên tục) | Tự động tạo tag, build bản đã ký, đăng lên GitHub Releases khi người phát hành bấm chạy. |
| **Workflow** | Một file `.yml` trong `.github/workflows/`, mô tả một quy trình tự động. |
| **Job** | Một nhóm bước trong workflow, chạy trên một máy ảo riêng. |
| **Artifact** | File kết quả được lưu lại sau khi workflow chạy (APK, báo cáo test…). |
| **Tag** | Nhãn gắn vào một commit, dạng `v1.2.3`, đánh dấu một bản phát hành. |
| **SemVer** | Quy tắc số phiên bản `MAJOR.MINOR.PATCH`. `major`: thay đổi lớn, không tương thích. `minor`: thêm tính năng. `patch`: sửa lỗi. |
| **versionName** | Tên phiên bản người dùng thấy, ví dụ `1.2.3`. |
| **versionCode** | Số nguyên Android dùng để so sánh bản mới/cũ. Công thức: `MAJOR*10000 + MINOR*100 + PATCH` (ví dụ `1.2.3` → `10203`). |
| **Keystore** | File chứa khoá dùng để ký APK. Bản release bắt buộc phải ký. |
| **Secret** | Giá trị bí mật lưu trong GitHub (Settings → Secrets and variables → Actions), không ghi vào code. |
| **Mock** | Giá trị giả, chỉ để demo. **Phải thay bằng giá trị thật trước khi dùng thật.** |

---

## 2. Tổng quan pipeline

```
[Push / Pull Request vào main hoặc develop]
        │
        ▼
   ci.yml ──┬── android-check : lint → unit test → build debug APK
            ├── android-ui-test : instrumented test trên emulator (API 30)
            └── backend-check : npm ci → kiểm tra cú pháp → npm test → npm audit

[Người phát hành bấm "Run workflow" trên tab Actions, chọn patch/minor/major]
        │
        ▼
   release.yml ── 1. Chạy lại toàn bộ ci.yml (bắt buộc pass)
                  2. Tính phiên bản mới từ tag gần nhất
                  3. Build APK + AAB release đã ký
                  4. Tạo tag vX.Y.Z + GitHub Release (kèm changelog tự sinh + file APK/AAB)
```

---

## 3. Các bước thực hiện (cho agent opencode)

> Thực hiện trên một nhánh mới tên `ci/setup-pipeline` tách từ `develop`. **Không** commit thẳng vào `main`.

### Bước 1 — Sửa quyền thực thi của `gradlew`
- Lệnh: `git update-index --chmod=+x gradlew`
- Kiểm tra: `git ls-files -s gradlew` phải hiện `100755`.

### Bước 2 — Cho phép truyền phiên bản và thông tin ký vào Gradle
File: `app/build.gradle.kts`
1. Trong `defaultConfig`, thay dòng ghi cứng:
   ```kotlin
   versionCode = (project.findProperty("versionCode") as String?)?.toInt() ?: 1
   versionName = (project.findProperty("versionName") as String?) ?: "1.0"
   ```
   → Khi build ở máy cá nhân, giá trị giữ nguyên như cũ (`1` / `"1.0"`). Khi CI truyền `-PversionCode=... -PversionName=...` thì dùng giá trị CI.
2. Thêm khối `signingConfigs` trong `android { }` (đặt trước `buildTypes`), đọc từ **biến môi trường**:
   ```kotlin
   signingConfigs {
       create("release") {
           val ksPath = System.getenv("ANDROID_KEYSTORE_PATH")
           if (ksPath != null) {
               storeFile = file(ksPath)
               storePassword = System.getenv("ANDROID_KEYSTORE_PASSWORD")
               keyAlias = System.getenv("ANDROID_KEY_ALIAS")
               keyPassword = System.getenv("ANDROID_KEY_PASSWORD")
           }
       }
   }
   ```
3. Trong `buildTypes.release`, thêm: chỉ gán `signingConfig = signingConfigs.getByName("release")` **khi** biến `ANDROID_KEYSTORE_PATH` tồn tại. Như vậy máy cá nhân không có keystore vẫn build được như trước.
- **Không** thay đổi `isMinifyEnabled` hay bất kỳ phần nào khác.
- Kiểm tra: `./gradlew assembleDebug` vẫn chạy thành công trên máy local.

### Bước 3 — Thêm test tối thiểu cho backend
1. File `backend/package.json`: thêm vào `scripts`: `"test": "node --test test/"`. Không thêm thư viện mới (dùng test runner có sẵn của Node 20).
2. Tạo file `backend/test/helpers.test.js`, dùng `node:test` + `node:assert`, test 2 hàm trong `backend/utils/helpers.js`:
   - `generateUserId()` trả về chuỗi hex dài 32 ký tự, 2 lần gọi cho 2 kết quả khác nhau.
   - `sanitizeUser()` **không** trả về trường `password` / `passwordHash` dù đầu vào có.
- Kiểm tra: `cd backend && npm ci && npm test` → pass.

### Bước 4 — Tạo workflow CI: `.github/workflows/ci.yml`
- **Kích hoạt (`on`)**: `push` và `pull_request` vào nhánh `main`, `develop`; và `workflow_call` (để `release.yml` gọi lại).
- `concurrency`: nhóm theo `${{ github.workflow }}-${{ github.ref }}`, `cancel-in-progress: true` (push mới sẽ huỷ lần chạy cũ trên cùng nhánh).
- `permissions: contents: read`.

**Job `android-check`** (runs-on `ubuntu-latest`, timeout 30 phút):
1. `actions/checkout@v4`
2. `actions/setup-java@v4` — `distribution: temurin`, `java-version: 17`
3. `gradle/actions/setup-gradle@v4` (tự cache Gradle)
4. `./gradlew lintDebug testDebugUnitTest assembleDebug --stacktrace`
5. Luôn chạy (`if: always()`): upload artifact `lint-report` (`app/build/reports/lint-results-debug.html`) và `unit-test-report` (`app/build/reports/tests/`), giữ 7 ngày.
6. Upload artifact `debug-apk` (`app/build/outputs/apk/debug/*.apk`), giữ 7 ngày.
- Nếu `lintDebug` báo lỗi (error) có sẵn trong code hiện tại: agent **ghi lại danh sách lỗi**, tạo `app/lint-baseline.xml` bằng `./gradlew updateLintBaseline` và thêm `lint { baseline = file("lint-baseline.xml") }` vào `android { }`. Không tự sửa code app để né lint.

**Job `android-ui-test`** (runs-on `ubuntu-latest`, timeout 45 phút, `needs: android-check`):
1. Checkout, setup-java 17, setup-gradle (như trên).
2. Bật KVM (bước chuẩn cho emulator trên Ubuntu runner):
   ```bash
   echo 'KERNEL=="kvm", GROUP="kvm", MODE="0666", OPTIONS+="static_node=kvm"' | sudo tee /etc/udev/rules.d/99-kvm4all.rules
   sudo udevadm control --reload-rules && sudo udevadm trigger --name-match=kvm
   ```
3. `reactivecircus/android-emulator-runner@v2` với `api-level: 30`, `arch: x86_64`, `target: google_apis`, `disable-animations: true`, `script: ./gradlew connectedDebugAndroidTest`.
4. Luôn upload artifact `ui-test-report` (`app/build/reports/androidTests/`).

**Job `backend-check`** (runs-on `ubuntu-latest`, timeout 10 phút, `defaults.run.working-directory: backend`):
1. Checkout.
2. `actions/setup-node@v4` — `node-version: 20`, `cache: npm`, `cache-dependency-path: backend/package-lock.json`.
3. `npm ci`
4. Kiểm tra cú pháp mọi file JS: `find . -name "*.js" -not -path "./node_modules/*" -print0 | xargs -0 -n1 node --check`
5. `npm test`
6. `npm audit --omit=dev --audit-level=high` với `continue-on-error: true` (chỉ **cảnh báo**, không chặn merge — vì đây là bản demo; ghi chú trong tài liệu để sau này bỏ `continue-on-error`).

### Bước 5 — Tạo workflow phát hành: `.github/workflows/release.yml`
- **Kích hoạt**: chỉ `workflow_dispatch`, có 1 input:
  - `bump`: kiểu `choice`, các lựa chọn `patch` / `minor` / `major`, mặc định `patch`.
- `concurrency: release` (không cho 2 lần phát hành chạy cùng lúc).
- `permissions: contents: write` (cần để tạo tag và Release).

**Job `verify`**: `uses: ./.github/workflows/ci.yml` (chạy lại toàn bộ CI). Nếu fail → dừng, không phát hành.

**Job `release`** (`needs: verify`, runs-on `ubuntu-latest`):
1. Chặn chạy sai nhánh: nếu `github.ref != 'refs/heads/main'` thì `exit 1` với thông báo "Chỉ được phát hành từ nhánh main".
2. `actions/checkout@v4` với `fetch-depth: 0` (lấy đủ lịch sử và tag).
3. **Tính phiên bản** (bước có `id: version`):
   - Lấy tag gần nhất: `git tag --list 'v*' --sort=-v:refname | head -n1`; nếu chưa có tag nào thì coi là `v0.0.0`.
   - Tăng theo input `bump` (major → `X+1.0.0`; minor → `X.Y+1.0`; patch → `X.Y.Z+1`).
   - Nếu MINOR hoặc PATCH ≥ 100 → `exit 1` (vì công thức versionCode sẽ bị trùng).
   - Tính `versionCode = MAJOR*10000 + MINOR*100 + PATCH`.
   - Nếu tag mới đã tồn tại → `exit 1`.
   - Ghi ra `GITHUB_OUTPUT`: `version` (vd `1.2.3`), `tag` (vd `v1.2.3`), `code` (vd `10203`).
4. Setup-java 17 + setup-gradle.
5. **Giải mã keystore**: kiểm tra 4 secret (`ANDROID_KEYSTORE_BASE64`, `ANDROID_KEYSTORE_PASSWORD`, `ANDROID_KEY_ALIAS`, `ANDROID_KEY_PASSWORD`) — thiếu cái nào thì `exit 1` và in rõ tên secret bị thiếu. Sau đó: `echo "$ANDROID_KEYSTORE_BASE64" | base64 -d > "$RUNNER_TEMP/release.jks"` và đặt `ANDROID_KEYSTORE_PATH=$RUNNER_TEMP/release.jks` vào `GITHUB_ENV`.
6. Build: `./gradlew assembleRelease bundleRelease -PversionName=${{ steps.version.outputs.version }} -PversionCode=${{ steps.version.outputs.code }}` (truyền 3 secret mật khẩu/alias qua `env:`).
7. Đổi tên file: `taskly-v1.2.3.apk` và `taskly-v1.2.3.aab` (lấy từ `app/build/outputs/apk/release/` và `app/build/outputs/bundle/release/`), tạo thêm file `SHA256SUMS.txt` bằng `sha256sum`.
8. **Tạo tag + Release** bằng GitHub CLI (có sẵn trên runner, dùng `GH_TOKEN: ${{ github.token }}`):
   ```bash
   gh release create "$TAG" taskly-*.apk taskly-*.aab SHA256SUMS.txt \
     --target "$GITHUB_SHA" --title "Taskly $TAG" --generate-notes
   ```
   Lệnh này tự tạo tag `vX.Y.Z` trỏ đúng commit vừa test, và tự sinh changelog từ các Pull Request đã merge.
9. Luôn xoá file keystore tạm ở bước cuối (`if: always()`).

### Bước 6 — Tạo script sinh keystore demo: `scripts/generate-demo-keystore.sh`
- Dùng `keytool` (có sẵn khi cài JDK) tạo file `demo-release.jks` với **giá trị MOCK**:
  - alias: `taskly-demo`
  - mật khẩu keystore và mật khẩu key: `demo-password-CHANGE-ME`
  - `-dname "CN=Taskly Demo, O=Demo, C=VN"`, `-validity 10000`, `-keyalg RSA -keysize 2048`
- Sau khi tạo, script in ra chuỗi base64 của file (`base64 -w0 demo-release.jks`) và in hướng dẫn copy vào GitHub Secrets.
- Thêm `*.jks` và `*.keystore` vào `.gitignore` gốc để **không bao giờ** commit keystore lên git.
- Đầu script có comment in hoa: `# MOCK – CHỈ DÙNG ĐỂ DEMO. THAY TRƯỚC KHI PHÁT HÀNH THẬT.`

### Bước 7 — Cập nhật thư viện tự động: `.github/dependabot.yml`
- 3 mục, lịch `weekly`, `target-branch: develop`:
  - `gradle` tại thư mục `/`
  - `npm` tại thư mục `/backend`
  - `github-actions` tại thư mục `/`
- Giới hạn `open-pull-requests-limit: 5` mỗi mục.

### Bước 8 — Viết tài liệu cho nhóm: `docs/CI_CD.md` (tiếng Việt)
Viết bằng câu ngắn, mỗi câu một ý, không dùng từ mơ hồ ("có thể", "tuỳ", "nên chăng") khi mô tả quy trình bắt buộc. Nội dung theo đúng thứ tự:
1. **Tóm tắt 5 dòng**: pipeline làm gì.
2. **Bảng thuật ngữ** (copy từ mục 1 của kế hoạch này).
3. **Sơ đồ luồng** (copy từ mục 2).
4. **Quy trình cho lập trình viên hằng ngày**: tạo nhánh từ `develop` → push → mở PR vào `develop` → chờ 3 dấu tích xanh (`android-check`, `android-ui-test`, `backend-check`) → được review → merge. Cách xem log lỗi và tải báo cáo test (tab Actions → chọn lần chạy → mục Artifacts).
5. **Quy trình phát hành (từng bước có số thứ tự)**: merge `develop` vào `main` qua PR → vào tab **Actions** → chọn **Release** → **Run workflow** → nhánh `main` → chọn `patch`/`minor`/`major` (kèm bảng ví dụ: đang `v1.2.3` chọn `patch` → `v1.2.4`, `minor` → `v1.3.0`, `major` → `v2.0.0`) → chờ xong → vào tab **Releases** để tải APK.
6. **Quy tắc phiên bản**: SemVer, công thức versionCode, giới hạn MINOR/PATCH < 100, không tự tạo tag bằng tay.
7. **Bảng Secrets cần cài** (tên, ý nghĩa, giá trị mock).
8. **⚠️ MỤC NHẮC THAY THẾ GIÁ TRỊ MOCK** (đặt nổi bật, dùng khối cảnh báo) — xem mục 4 bên dưới.
9. **Cài đặt bảo vệ nhánh (làm thủ công trên GitHub)**: Settings → Branches → thêm rule cho `main` và `develop`: bắt buộc PR, bắt buộc 3 check trên pass, cấm force push.
10. **Xử lý lỗi thường gặp**: `Permission denied: ./gradlew`; thiếu secret khi release; tag đã tồn tại; emulator chạy quá thời gian; `npm ci` lỗi do `package-lock.json` lệch.

Không sửa tài liệu nào khác ngoài `docs/CI_CD.md`.

### Bước 9 — Commit và mở Pull Request
- Commit theo từng nhóm: (1) gradlew + gradle, (2) backend test, (3) workflows + dependabot, (4) script keystore + .gitignore, (5) docs.
- Push nhánh `ci/setup-pipeline`, mở PR vào `develop`. Mô tả PR nhắc lại mục 4 (các giá trị mock cần thay).

---

## 4. ⚠️ Nhắc nhở: các giá trị MOCK người dùng phải tự thay thế

| # | Ở đâu | Giá trị mock hiện tại | Cần thay bằng |
|---|---|---|---|
| 1 | GitHub Secret `ANDROID_KEYSTORE_BASE64` | base64 của `demo-release.jks` (tạo bằng script demo) | base64 của keystore thật |
| 2 | GitHub Secret `ANDROID_KEYSTORE_PASSWORD` | `demo-password-CHANGE-ME` | mật khẩu keystore thật |
| 3 | GitHub Secret `ANDROID_KEY_ALIAS` | `taskly-demo` | alias thật |
| 4 | GitHub Secret `ANDROID_KEY_PASSWORD` | `demo-password-CHANGE-ME` | mật khẩu key thật |
| 5 | `app/build.gradle.kts` — giá trị mặc định Cloudinary (`dxohngowm`, `687411225619873`, `upload_project`) | Đang ghi cứng trong code (có từ trước, **kế hoạch này không sửa**) | Chuyển sang GitHub Secret / `local.properties` khi dùng thật |
| 6 | `ci.yml` — bước `npm audit` có `continue-on-error: true` | Chỉ cảnh báo | Bỏ dòng này để lỗi bảo mật chặn merge |

**Lưu ý quan trọng:** Nếu đổi keystore sau khi đã phát hành APK, người dùng đã cài bản cũ **không cập nhật đè được** (phải gỡ ra cài lại). Vì vậy hãy thay keystore thật **trước** lần phát hành chính thức đầu tiên.

---

## 5. Danh sách file sẽ tạo / sửa

| File | Hành động |
|---|---|
| `gradlew` | Sửa quyền thực thi (không sửa nội dung) |
| `app/build.gradle.kts` | Sửa: version từ property, thêm signingConfig |
| `backend/package.json` | Sửa: thêm script `test` |
| `backend/test/helpers.test.js` | Tạo mới |
| `.github/workflows/ci.yml` | Tạo mới |
| `.github/workflows/release.yml` | Tạo mới |
| `.github/dependabot.yml` | Tạo mới |
| `scripts/generate-demo-keystore.sh` | Tạo mới |
| `.gitignore` | Sửa: thêm `*.jks`, `*.keystore` |
| `docs/CI_CD.md` | Tạo mới (tài liệu chia sẻ, tiếng Việt) |
| `app/lint-baseline.xml` | Chỉ tạo nếu lint báo lỗi có sẵn (Bước 4) |

---

## 6. Kiểm tra sau khi làm xong (Verification)

1. **Local**:
   - `./gradlew lintDebug testDebugUnitTest assembleDebug` → BUILD SUCCESSFUL.
   - `cd backend && npm ci && npm test` → tất cả test pass.
   - `bash scripts/generate-demo-keystore.sh` → tạo được file `.jks` và in ra base64; `git status` **không** thấy file `.jks`.
   - Build thử bản release ký bằng keystore demo: đặt 4 biến môi trường rồi chạy `./gradlew assembleRelease -PversionName=0.0.1 -PversionCode=1` → có APK; kiểm tra chữ ký bằng `apksigner verify --print-certs <apk>`.
   - Kiểm tra cú pháp YAML (ví dụ `npx --yes yaml-lint .github/workflows/*.yml` hoặc `actionlint` nếu có).
2. **Trên GitHub**:
   - Mở PR `ci/setup-pipeline` → `develop`: 3 job `android-check`, `android-ui-test`, `backend-check` đều xanh; tải được artifact `debug-apk` và báo cáo test.
   - Người dùng cài 4 secret mock (theo `docs/CI_CD.md`).
   - Sau khi merge vào `main`: chạy **Release** với `minor` → tạo tag `v0.1.0`, có GitHub Release chứa `taskly-v0.1.0.apk`, `taskly-v0.1.0.aab`, `SHA256SUMS.txt`, changelog tự sinh.
   - Chạy thử **Release** trên nhánh `develop` → phải bị chặn với thông báo "Chỉ được phát hành từ nhánh main".
   - Xoá tạm 1 secret rồi chạy Release → phải fail với thông báo nêu đúng tên secret bị thiếu.
