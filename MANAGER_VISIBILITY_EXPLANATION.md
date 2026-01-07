# Manager/Co-Manager Visibility in Team Info

## Vấn đề

Khi tắt quyền **Allow Member Directory** trong team settings, member không thể xem danh sách manager và co-manager trong phần Team Info.

## Giải pháp đã implement

### Backend (Đã có sẵn) ✅

File: `backend/controllers/teamController.js`

```javascript
// Get team leaders (Manager and Co-Manager) - No permission check needed
exports.getTeamLeaders = async (req, res) => {
    try {
        const teamId = req.params.teamId;
        const userId = req.user ? req.user.id : req.query.userId;

        // Only check if user is a member of the team (not permission-based)
        const membership = await teamModel.findMember(teamId, userId);
        if (!membership) {
            return res.status(403).json({ message: 'Access denied. You are not a member.' });
        }

        // Get all active members and filter for managers and co-managers
        const allMembers = await teamModel.findMembersByTeamId(teamId, 'active');
        const leaders = allMembers.filter(member => {
            const role = String(member.role || '').toLowerCase();
            return role === 'manager' || role === 'co-manager';
        });

        res.json(leaders);
    } catch (error) {
        console.error('Error fetching team leaders:', error);
        res.status(500).json({ message: 'Database error' });
    }
};
```

**Logic backend:**
- ✅ Endpoint `/api/team/:teamId/leaders` **KHÔNG kiểm tra** `allowMemberDirectory`
- ✅ Chỉ kiểm tra user có phải là member của team không
- ✅ Trả về tất cả manager và co-manager

### Frontend

File: `app/src/main/java/com/example/morp_prj/ui/TeamInfoFragment.kt`

```kotlin
private fun loadManagers() {
    if (teamId.isEmpty()) return
    RetrofitClient.teamApiService.getTeamLeaders(teamId).enqueue(object : Callback<List<TeamMember>> {
        override fun onResponse(call: Call<List<TeamMember>>, response: Response<List<TeamMember>>) {
            if (response.isSuccessful) {
                val leaders = response.body() ?: emptyList()
                managerAdapter.submit(leaders)
            }
        }
        override fun onFailure(call: Call<List<TeamMember>>, t: Throwable) {
            Toast.makeText(requireContext(), "Failed to load team leaders", Toast.LENGTH_SHORT).show()
        }
    })
}
```

**Logic frontend:**
- ✅ Gọi endpoint `getTeamLeaders` (không bị block bởi permission)
- ✅ Hiển thị danh sách manager/co-manager

## Kết quả

✅ **Member luôn có thể xem Manager và Co-Manager** trong phần Team Info, bất kể setting `allowMemberDirectory` có được bật hay không.

❌ **Member chỉ KHÔNG thể xem toàn bộ Member Directory** khi `allowMemberDirectory = false`

## So sánh 2 endpoints

| Endpoint | Permission Check | Use Case |
|----------|------------------|----------|
| `/api/team/:teamId/members` | ✅ Kiểm tra `allowMemberDirectory` | Xem toàn bộ member list |
| `/api/team/:teamId/leaders` | ❌ KHÔNG kiểm tra permission | Xem manager/co-manager (luôn cho phép) |

## Testing

1. **Tạo team** với `allowMemberDirectory = false`
2. **Join team** với tư cách member
3. **Mở Team Info**
4. **Kết quả mong đợi:**
   - ✅ Vẫn thấy danh sách Manager và Co-Manager
   - ❌ Không thấy nút "View All Members" hoặc bị block khi click

## Lý do thiết kế

Manager và Co-Manager là **leadership** của team, member cần biết ai là người quản lý để liên hệ khi cần. Đây là thông tin **quan trọng và cần thiết** cho sự vận hành của team, nên không bị ảnh hưởng bởi setting privacy `allowMemberDirectory`.

