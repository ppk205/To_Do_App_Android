const User = require('./models/User');

async function testUpdate() {
    try {
        const userId = 'd0e21315b83516fca0c0029ae01fba42'; // User ID từ log
        const testUrl = 'https://res.cloudinary.com/dxohngowm/image/upload/v1767069279/sq5igzmrxxdc9bcrzge8.png';

        console.log('🧪 Testing User.update()...');
        console.log('User ID:', userId);
        console.log('Test Cloudinary URL:', testUrl);

        // Test update
        const updateData = {
            avatarUrl: testUrl
        };

        console.log('\n📝 Calling User.update()...');
        const result = await User.update(userId, updateData);
        console.log('Update result:', result);

        // Fetch updated user
        console.log('\n📥 Fetching updated user...');
        const updatedUser = await User.findById(userId);
        console.log('Updated user avatarUrl:', updatedUser.avatarUrl);

        if (updatedUser.avatarUrl === testUrl) {
            console.log('\n✅ SUCCESS: Database updated correctly!');
        } else {
            console.log('\n❌ FAIL: Database not updated!');
            console.log('Expected:', testUrl);
            console.log('Got:', updatedUser.avatarUrl);
        }

        process.exit(0);
    } catch (error) {
        console.error('❌ Error:', error);
        process.exit(1);
    }
}

testUpdate();

