const { Server } = require('socket.io');
const jwt = require('jsonwebtoken');
const db = require('../config/database');

const JWT_SECRET = process.env.JWT_SECRET;

/**
 * Single Socket.IO instance for the whole app.
 * Rooms:
 *  - user:<userId>
 *  - team:<teamId>
 */
let io = null;

function initRealtime(httpServer) {
    if (io) return io;

    io = new Server(httpServer, {
        cors: {
            origin: '*',
            methods: ['GET', 'POST', 'PATCH', 'PUT', 'DELETE'],
        },
    });

    io.use((socket, next) => {
        try {
            if (!JWT_SECRET) return next(new Error('JWT_SECRET_MISSING'));

            const rawAuth = socket.handshake.headers?.authorization;
            const bearerToken = rawAuth && rawAuth.startsWith('Bearer ') ? rawAuth.slice('Bearer '.length) : null;
            const token = socket.handshake.auth?.token || bearerToken;
            if (!token) return next(new Error('TOKEN_MISSING'));

            const payload = jwt.verify(token, JWT_SECRET);
            socket.user = {
                id: payload.sub,
                email: payload.email,
                username: payload.username,
            };
            return next();
        } catch (e) {
            return next(new Error('TOKEN_INVALID'));
        }
    });

    io.on('connection', (socket) => {
        const userId = socket.user?.id;
        if (userId) socket.join(`user:${userId}`);

        socket.on('joinTeam', (teamId) => {
            if (!teamId) return;
            socket.join(`team:${teamId}`);
        });

        socket.on('leaveTeam', (teamId) => {
            if (!teamId) return;
            socket.leave(`team:${teamId}`);
        });

        socket.on('sendTeamMessage', async (data) => {
            // { teamId, senderId, senderName, content, type (text/image) }
            if (!data.teamId || !data.content) return;

            try {
                const [convRows] = await db.execute(
                    `SELECT id
                    FROM conversations
                    WHERE team_id = ? AND type = 'team'
                    LIMIT 1`, [data.teamId]
                );
                let conversationId;

                if (convRows.length > 0) {
                    conversationId = convRows[0].id;
                } else {
                    // if no conversation exists, create a new one
                    const [newConv] = await db.execute(
                        `INSERT INTO conversations (team_id, type, created_by) VALUES (?, 'team', ?)`,
                        [data.teamId, data.senderId]
                    );
                    conversationId = newConv.insertId;
                }

                const [msgResult] = await db.execute(
                    `INSERT INTO messages (conversation_id, sender_id, body, created_at) VALUES (?, ?, ?, NOW())`,
                    [conversationId, data.senderId, data.content]
                );

                const savedMessage = {
                    id: msgResult.insertId.toString(),
                    teamId: data.teamId,
                    senderId: data.senderId,
                    senderName: data.senderName, // Client gửi lên để hiển thị nhanh
                    senderAvatar: data.senderAvatar,
                    content: data.content,
                    createdAt: new Date().toISOString(), // Lấy giờ hiện tại chuẩn ISO
                    type: 'text'
                };

                io.to(`team:${data.teamId}`).emit('receiveTeamMessage', savedMessage);
            } catch (err) {
                console.error("Lỗi lưu tin nhắn:", err);
            }
        });

    });

    return io;
}

function getIO() {
    return io;
}

module.exports = {
    initRealtime,
    getIO,
};
