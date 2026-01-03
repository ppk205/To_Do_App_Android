const { Server } = require('socket.io');
const jwt = require('jsonwebtoken');

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