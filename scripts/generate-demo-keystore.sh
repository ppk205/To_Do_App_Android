# MOCK – CHỈ DÙNG ĐỂ DEMO. THAY TRƯỚC KHI PHÁT HÀNH THẬT.
#!/usr/bin/env bash
set -euo pipefail

# Tạo keystore demo để thử pipeline ký APK/AAB.
# KHÔNG dùng keystore này cho phát hành thật.

OUTPUT="${1:-demo-release.jks}"
ALIAS="taskly-demo"
PASSWORD="demo-password-CHANGE-ME"
DNAME="CN=Taskly Demo, O=Demo, C=VN"

if [ -f "$OUTPUT" ]; then
  echo "File $OUTPUT đã tồn tại. Xoá trước khi tạo mới hoặc truyền tên file khác."
  exit 1
fi

keytool -genkeypair \
  -keystore "$OUTPUT" \
  -alias "$ALIAS" \
  -keyalg RSA -keysize 2048 -validity 10000 \
  -storepass "$PASSWORD" -keypass "$PASSWORD" \
  -dname "$DNAME"

echo ""
echo "Đã tạo keystore demo: $OUTPUT"
echo ""
echo "Chuỗi base64 để copy vào GitHub Secret ANDROID_KEYSTORE_BASE64:"
base64 -w0 "$OUTPUT"
echo ""
echo ""
echo "Cài Secrets trên GitHub (Settings → Secrets and variables → Actions):"
echo "  ANDROID_KEYSTORE_BASE64 = (chuỗi base64 in ra ở trên)"
echo "  ANDROID_KEYSTORE_PASSWORD = $PASSWORD"
echo "  ANDROID_KEY_ALIAS = $ALIAS"
echo "  ANDROID_KEY_PASSWORD = $PASSWORD"
echo ""
echo "⚠️ Đây là giá trị MOCK. Thay bằng keystore thật trước khi phát hành chính thức."
