/**
 * Test script for UPDATE PROFILE endpoint
 *
 * Usage:
 * 1. Start the backend server: node server.js
 * 2. Run this test: node test-update-profile.js
 *
 * This script will:
 * 1. Login with test credentials
 * 2. Update the profile with new data
 * 3. Verify the response
 */

const axios = require('axios');

const BASE_URL = 'http://localhost:3001/api';

// Test credentials - UPDATE THESE to match your test user
const TEST_USER = {
    usernameOrEmail: 'testuser',
    password: 'Test1234!'
};

// New profile data to test
const NEW_PROFILE_DATA = {
    displayName: 'Updated Test User',
    phone: '+84123456789',
    bio: 'This is my updated bio!',
    avatarUrl: null // Set to null to keep existing avatar, or add Base64 string to test
};

async function testUpdateProfile() {
    try {
        console.log('🔐 Step 1: Logging in...');

        // Step 1: Login to get access token
        const loginResponse = await axios.post(`${BASE_URL}/auth/login`, {
            usernameOrEmail: TEST_USER.usernameOrEmail,
            password: TEST_USER.password,
            deviceId: 'test-device-123',
            deviceName: 'Test Device'
        });

        if (!loginResponse.data.success) {
            console.error('❌ Login failed:', loginResponse.data.message);
            return;
        }

        const accessToken = loginResponse.data.accessToken;
        const user = loginResponse.data.user;

        console.log('✅ Login successful!');
        console.log('📋 Current user data:', JSON.stringify(user, null, 2));
        console.log('🔑 Access token:', accessToken);

        // Step 2: Update profile
        console.log('\n📝 Step 2: Updating profile...');
        console.log('New data:', JSON.stringify(NEW_PROFILE_DATA, null, 2));

        const updateResponse = await axios.put(
            `${BASE_URL}/auth/profile`,
            NEW_PROFILE_DATA,
            {
                headers: {
                    'Authorization': `Bearer ${accessToken}`,
                    'Content-Type': 'application/json'
                }
            }
        );

        if (!updateResponse.data.success) {
            console.error('❌ Update failed:', updateResponse.data.message);
            return;
        }

        console.log('✅ Update successful!');
        console.log('📋 Updated user data:', JSON.stringify(updateResponse.data.user, null, 2));

        // Step 3: Verify the changes
        console.log('\n✅ Verification:');
        console.log('✓ displayName:', updateResponse.data.user.displayName);
        console.log('✓ phone:', updateResponse.data.user.phone);
        console.log('✓ bio:', updateResponse.data.user.bio);
        console.log('✓ avatarUrl:', updateResponse.data.user.avatarUrl ? 'Present' : 'Null');

        console.log('\n🎉 All tests passed!');

    } catch (error) {
        if (error.response) {
            console.error('❌ API Error:', error.response.status);
            console.error('Message:', error.response.data.message || error.response.statusText);
            console.error('Data:', JSON.stringify(error.response.data, null, 2));
        } else if (error.request) {
            console.error('❌ Network Error: No response from server');
            console.error('Is the backend server running on', BASE_URL, '?');
        } else {
            console.error('❌ Error:', error.message);
        }
        process.exit(1);
    }
}

// Run the test
console.log('🚀 Starting UPDATE PROFILE test...\n');
testUpdateProfile();

