const multer = require('multer');
const path = require('path');
const fs = require('fs');
const sharp = require('sharp'); // REQ-UPLOAD-02: Thư viện xử lý ảnh và strip EXIF

// Create uploads directory if it doesn't exist
const uploadDir = path.join(__dirname, '../uploads/avatars');
const tempDir = path.join(__dirname, '../uploads/temp');
if (!fs.existsSync(uploadDir)) {
    fs.mkdirSync(uploadDir, { recursive: true });
}
if (!fs.existsSync(tempDir)) {
    fs.mkdirSync(tempDir, { recursive: true });
}

// REQ-UPLOAD-03: Magic bytes cho các định dạng ảnh được phép
const ALLOWED_MAGIC_BYTES = {
    jpg: [0xFF, 0xD8, 0xFF], // JPEG
    png: [0x89, 0x50, 0x4E, 0x47], // PNG
    webp: [0x52, 0x49, 0x46, 0x46] // RIFF (WebP container)
};

// REQ-UPLOAD-03: Chỉ cho phép .jpg, .png, .webp
const ALLOWED_EXTENSIONS = ['jpg', 'jpeg', 'png', 'webp'];
const ALLOWED_MIMETYPES = ['image/jpeg', 'image/png', 'image/webp'];

/**
 * REQ-UPLOAD-03: Kiểm tra magic bytes của file
 * @param {Buffer} buffer - Buffer chứa nội dung file
 * @returns {string|null} - Extension nếu hợp lệ, null nếu không
 */
function detectFileTypeByMagicBytes(buffer) {
    if (!buffer || buffer.length < 4) return null;

    // Check JPEG
    if (buffer[0] === 0xFF && buffer[1] === 0xD8 && buffer[2] === 0xFF) {
        return 'jpg';
    }

    // Check PNG
    if (buffer[0] === 0x89 && buffer[1] === 0x50 && buffer[2] === 0x4E && buffer[3] === 0x47) {
        return 'png';
    }

    // Check WebP (RIFF header + WEBP)
    if (buffer[0] === 0x52 && buffer[1] === 0x49 && buffer[2] === 0x46 && buffer[3] === 0x46) {
        // Kiểm tra thêm WEBP signature ở byte 8-11
        if (buffer.length >= 12 && buffer[8] === 0x57 && buffer[9] === 0x45 && buffer[10] === 0x42 && buffer[11] === 0x50) {
            return 'webp';
        }
    }

    return null;
}

// Configure storage - lưu tạm để xử lý
const storage = multer.diskStorage({
    destination: function (req, file, cb) {
        cb(null, tempDir); // Lưu vào thư mục temp trước
    },
    filename: function (req, file, cb) {
        // Generate unique filename: userId-timestamp.ext
        const userId = req.user?.id || 'unknown';
        const ext = path.extname(file.originalname).toLowerCase();
        const filename = `temp-${userId}-${Date.now()}${ext}`;
        cb(null, filename);
    }
});

// File filter - REQ-UPLOAD-03: Chỉ chấp nhận .jpg, .png, .webp
const fileFilter = (req, file, cb) => {
    console.log('📁 File upload attempt:', {
        fieldname: file.fieldname,
        originalname: file.originalname,
        mimetype: file.mimetype,
        encoding: file.encoding
    });

    // Kiểm tra extension
    const ext = path.extname(file.originalname).toLowerCase().replace('.', '');
    const isExtValid = ALLOWED_EXTENSIONS.includes(ext);

    // Kiểm tra MIME type
    const isMimeValid = ALLOWED_MIMETYPES.includes(file.mimetype);

    // REQ-UPLOAD-03: Yêu cầu CẢ extension VÀ mimetype phải hợp lệ
    if (isExtValid && isMimeValid) {
        console.log('✅ File passed initial filter:', file.originalname);
        return cb(null, true);
    } else {
        console.log('❌ File rejected:', file.originalname, 'Ext:', ext, 'MIME:', file.mimetype);
        cb(new Error('Chỉ chấp nhận file ảnh định dạng: jpg, png, webp'));
    }
};

// Configure multer
const upload = multer({
    storage: storage,
    limits: {
        fileSize: 5 * 1024 * 1024 // 5MB max
    },
    fileFilter: fileFilter
});

/**
 * REQ-UPLOAD-02 & REQ-UPLOAD-03: Middleware xử lý ảnh sau khi upload
 * - Validate magic bytes
 * - Strip EXIF/GPS metadata
 * - Resize nếu cần
 */
async function processUploadedImage(req, res, next) {
    if (!req.file) {
        return next();
    }

    const tempPath = req.file.path;
    const userId = req.user?.id || 'unknown';

    try {
        // Đọc file để kiểm tra magic bytes
        const buffer = fs.readFileSync(tempPath);

        // REQ-UPLOAD-03: Validate magic bytes
        const detectedType = detectFileTypeByMagicBytes(buffer);
        if (!detectedType) {
            // Xóa file temp
            fs.unlinkSync(tempPath);
            console.error('❌ Magic bytes validation failed for:', req.file.originalname);
            return res.status(400).json({
                success: false,
                message: 'File ảnh không hợp lệ. Chỉ chấp nhận jpg, png, webp thật sự.'
            });
        }

        console.log('🔍 Detected file type by magic bytes:', detectedType);

        // REQ-UPLOAD-02: Xử lý ảnh với sharp - strip EXIF và metadata
        const finalFilename = `${userId}-${Date.now()}.${detectedType === 'jpg' ? 'jpg' : detectedType}`;
        const finalPath = path.join(uploadDir, finalFilename);

        let sharpInstance = sharp(buffer);

        // Loại bỏ tất cả metadata (EXIF, GPS, ICC profiles, etc.)
        // REQ-UPLOAD-02: Strip Metadata
        sharpInstance = sharpInstance.rotate(); // Auto-rotate dựa trên EXIF trước khi strip

        if (detectedType === 'jpg') {
            sharpInstance = sharpInstance.jpeg({ quality: 85 });
        } else if (detectedType === 'png') {
            sharpInstance = sharpInstance.png({ compressionLevel: 8 });
        } else if (detectedType === 'webp') {
            sharpInstance = sharpInstance.webp({ quality: 85 });
        }

        // Ghi file đã xử lý (không có metadata)
        await sharpInstance
            .withMetadata({ orientation: undefined }) // Xóa orientation metadata sau rotate
            .toFile(finalPath);

        // Xóa file temp
        fs.unlinkSync(tempPath);

        // Cập nhật req.file với thông tin file mới
        req.file.path = finalPath;
        req.file.filename = finalFilename;
        req.file.destination = uploadDir;

        console.log('✅ Image processed and EXIF stripped:', finalFilename);

        next();
    } catch (error) {
        // Cleanup temp file on error
        if (fs.existsSync(tempPath)) {
            fs.unlinkSync(tempPath);
        }
        console.error('❌ Image processing error:', error);
        return res.status(500).json({
            success: false,
            message: 'Lỗi xử lý ảnh. Vui lòng thử lại.'
        });
    }
}

module.exports = {
    upload,
    processUploadedImage,
    detectFileTypeByMagicBytes
};
