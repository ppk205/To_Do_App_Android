const axios = require('axios');

const BASE_URL = 'http://192.168.2.247:3001/api';

// Test data
const testData = {
    displayName: 'Test User Social',
    phone: '0987654321',
    githubUrl: 'https://github.com/testuser',
    linkedinUrl: 'https://linkedin.com/in/testuser',
    websiteUrl: 'https://testuser.com'
};

async function testUpdateProfileWithSocialLinks() {
    console.log('=== TEST UPDATE PROFILE WITH SOCIAL LINKS ===\n');

    try {
        // Bước 1: Login để lấy token
        console.log('1. Đăng nhập...');
        const loginResponse = await axios.post(`${BASE_URL}/auth/login`, {
            usernameOrEmail: 'testuser',  // Thay bằng username thật
            password: 'password123'       // Thay bằng password thật
        });

        if (!loginResponse.data.success) {
            console.error('❌ Login thất bại:', loginResponse.data.message);
            return;
        }

        const token = loginResponse.data.tokens.accessToken;
        console.log('✅ Login thành công\n');

        // Bước 2: Update profile với social links
        console.log('2. Cập nhật profile với social links...');
        console.log('📤 Request data:', JSON.stringify(testData, null, 2));

        const updateResponse = await axios.put(
            `${BASE_URL}/auth/profile`,
            testData,
            {
                headers: {
                    'Authorization': `Bearer ${token}`,
                    'Content-Type': 'application/json'
                }
            }
        );

        console.log('\n📥 Response:', JSON.stringify(updateResponse.data, null, 2));

        // Bước 3: Verify các field social links có trong response
        if (updateResponse.data.success) {
            const user = updateResponse.data.user;

            console.log('\n=== VERIFICATION ===');
            console.log('✓ githubUrl:', user.githubUrl === testData.githubUrl ? '✅ PASS' : '❌ FAIL');
            console.log('✓ linkedinUrl:', user.linkedinUrl === testData.linkedinUrl ? '✅ PASS' : '❌ FAIL');
            console.log('✓ websiteUrl:', user.websiteUrl === testData.websiteUrl ? '✅ PASS' : '❌ FAIL');

            if (user.githubUrl && user.linkedinUrl && user.websiteUrl) {
                console.log('\n🎉 TEST PASSED: Backend đã trả về đầy đủ social links!');
            } else {
                console.log('\n❌ TEST FAILED: Response thiếu một số field social links');
            }
        } else {
            console.error('❌ Update thất bại:', updateResponse.data.message);
        }

    } catch (error) {
        console.error('❌ Error:', error.response?.data || error.message);
        if (error.response) {
            console.error('Status:', error.response.status);
            console.error('Data:', error.response.data);
        }
    }
}

// Chạy test
testUpdateProfileWithSocialLinks();

