# CI/CD cho dự án Taskly (To_Do_App_Android)

## 1. Tóm tắt

- Push hoặc Pull Request vào `main` và `develop` kích hoạt kiểm tra tự động.
- Workflow `ci.yml` chạy 3 job kiểm tra song song.
- Ba job bắt buộc xanh trước khi merge.
- Phát hành thực hiện thủ công bằng workflow `Release`.
- Bản phát hành đăng lên GitHub Releases kèm APK và AAB đã ký.

## 2. Bảng thuật ngữ

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

## 3. Sơ đồ luồng

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

## 4. Quy trình cho lập trình viên hằng ngày

- Tạo nhánh mới từ nhánh `develop`.
- Đặt tên nhánh theo dạng `feature/ten-tinh-nang`.
- Push nhánh lên GitHub.
- Mở Pull Request vào nhánh `develop`.
- Chờ 3 job chạy xong.
- Ba dấu tích xanh bắt buộc: `android-check`, `android-ui-test`, `backend-check`.
- Merge chỉ thực hiện sau khi đủ 3 tích xanh.
- Merge chỉ thực hiện sau khi được review.
- Xem log lỗi trong tab Actions.
- Chọn lần chạy tương ứng trong tab Actions.
- Tải báo cáo test trong mục Artifacts của lần chạy.
- Tải file `debug-apk` trong mục Artifacts của lần chạy.

## 5. Quy trình phát hành

1. Merge nhánh `develop` vào nhánh `main` bằng Pull Request.
2. Chờ Pull Request merge xong.
3. Vào tab **Actions** trên GitHub.
4. Chọn workflow **Release** trong danh sách bên trái.
5. Bấm nút **Run workflow**.
6. Chọn nhánh `main` trong ô Branch.
7. Chọn loại tăng phiên bản: `patch`, `minor`, hoặc `major`.
8. Bấm **Run workflow** để bắt đầu.
9. Chờ job `verify` chạy xong toàn bộ CI.
10. Chờ job `release` tính phiên bản, build và đăng Release.
11. Vào tab **Releases** để tải APK.

Bảng chọn loại tăng phiên bản (ví dụ đang ở `v1.2.3`):

| Chọn | Kết quả | Giải thích |
|---|---|---|
| `patch` | `v1.2.4` | Sửa lỗi nhỏ. |
| `minor` | `v1.3.0` | Thêm tính năng mới. Số patch reset về 0. |
| `major` | `v2.0.0` | Thay đổi lớn, không tương thích. Số minor và patch reset về 0. |

## 6. Quy tắc phiên bản

- Số phiên bản tuân theo SemVer `MAJOR.MINOR.PATCH`.
- `versionName` là chuỗi người dùng thấy, ví dụ `1.2.3`.
- `versionCode` tính bằng công thức `MAJOR*10000 + MINOR*100 + PATCH`.
- Ví dụ `1.2.3` cho `versionCode` `10203`.
- `MINOR` luôn nhỏ hơn 100.
- `PATCH` luôn nhỏ hơn 100.
- Workflow từ chối chạy khi `MINOR` hoặc `PATCH` đạt 100.
- Không tự tạo tag bằng tay.
- Tag do workflow `Release` tạo tự động.
- Tag luôn có dạng `vX.Y.Z`.
- Lần phát hành đầu tiên khi chưa có tag sẽ tính từ `v0.0.0`.

## 7. Bảng Secrets cần cài

Cài đặt trong Settings → Secrets and variables → Actions.

| Tên Secret | Ý nghĩa | Giá trị mock để demo |
|---|---|---|
| `ANDROID_KEYSTORE_BASE64` | Nội dung keystore mã hoá base64 | base64 của `demo-release.jks` tạo bằng `scripts/generate-demo-keystore.sh` |
| `ANDROID_KEYSTORE_PASSWORD` | Mật khẩu keystore | `demo-password-CHANGE-ME` |
| `ANDROID_KEY_ALIAS` | Alias của key trong keystore | `taskly-demo` |
| `ANDROID_KEY_PASSWORD` | Mật khẩu của key | `demo-password-CHANGE-ME` |

Cách tạo keystore demo:

```bash
bash scripts/generate-demo-keystore.sh
```

- Script tạo file `demo-release.jks` trong thư mục hiện tại.
- Script in ra chuỗi base64 của file.
- Copy chuỗi base64 vào Secret `ANDROID_KEYSTORE_BASE64`.
- File `.jks` không bao giờ commit lên git.

## 8. ⚠️ MỤC NHẮC THAY THẾ GIÁ TRỊ MOCK

> [!WARNING]
> Các giá trị dưới đây chỉ dùng để demo. Thay toàn bộ bằng giá trị thật trước lần phát hành chính thức đầu tiên.
> Nếu đổi keystore sau khi đã phát hành APK, người dùng đã cài bản cũ không cập nhật đè được. Người dùng phải gỡ ra cài lại.

| # | Ở đâu | Giá trị mock hiện tại | Cần thay bằng |
|---|---|---|---|
| 1 | GitHub Secret `ANDROID_KEYSTORE_BASE64` | base64 của `demo-release.jks` (tạo bằng script demo) | base64 của keystore thật |
| 2 | GitHub Secret `ANDROID_KEYSTORE_PASSWORD` | `demo-password-CHANGE-ME` | mật khẩu keystore thật |
| 3 | GitHub Secret `ANDROID_KEY_ALIAS` | `taskly-demo` | alias thật |
| 4 | GitHub Secret `ANDROID_KEY_PASSWORD` | `demo-password-CHANGE-ME` | mật khẩu key thật |
| 5 | `app/build.gradle.kts` — giá trị mặc định Cloudinary (`dxohngowm`, `687411225619873`, `upload_project`) | Đang ghi cứng trong code (có từ trước, kế hoạch này không sửa) | Chuyển sang GitHub Secret / `local.properties` khi dùng thật |
| 6 | `ci.yml` — bước `npm audit` có `continue-on-error: true` | Chỉ cảnh báo | Bỏ dòng này để lỗi bảo mật chặn merge |

Lưu ý quan trọng: thay keystore thật trước lần phát hành chính thức đầu tiên.

## 9. Cài đặt bảo vệ nhánh (làm thủ công trên GitHub)

- Vào Settings → Branches.
- Bấm Add branch protection rule.
- Tạo rule cho nhánh `main`.
- Bật Require a pull request before merging.
- Bật Require status checks to pass before merging.
- Chọn 3 check bắt buộc: `android-check`, `android-ui-test`, `backend-check`.
- Bật Do not allow bypassing the above settings.
- Cấm force push vào `main`.
- Lặp lại các bước trên cho nhánh `develop`.
- Cấm force push vào `develop`.

## 10. Xử lý lỗi thường gặp

- Lỗi `Permission denied: ./gradlew`: chạy `git update-index --chmod=+x gradlew` rồi commit lại.
- Release báo thiếu secret: mở log job `release`, đọc tên secret bị thiếu, bổ sung trong Settings → Secrets and variables → Actions, chạy lại workflow.
- Release báo tag đã tồn tại: tag `vX.Y.Z` đã có trên remote. Kiểm tra tab Releases. Chọn loại bump khác hoặc xoá tag sai nếu tag sai.
- Release báo chỉ được phát hành từ nhánh main: workflow đang chạy trên nhánh khác. Chạy lại workflow với nhánh `main`.
- Emulator chạy quá thời gian: chạy lại workflow. Kiểm tra job `android-ui-test` dùng `api-level: 30` và `arch: x86_64`.
- Lỗi `npm ci` do `package-lock.json` lệch: chạy `npm install` trong thư mục `backend` trên máy cá nhân, commit file `package-lock.json` mới, push lại.
- Lint báo lỗi mới: mở artifact `lint-report`, đọc file `lint-results-debug.html`, sửa lỗi trong code, push lại.
