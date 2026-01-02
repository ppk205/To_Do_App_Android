// Simple smoke test for Socket.IO auth + notification event.
// Requires a valid JWT in env TEST_JWT.

require('dotenv').config();
const { io } = require('socket.io-client');

const token = process.env.TEST_JWT;
if (!token) {
  console.error('Set TEST_JWT env var to run this test.');
  process.exit(1);
}

const url = process.env.TEST_URL || 'http://localhost:3001';

const socket = io(url, {
  auth: { token },
  transports: ['websocket'],
});

socket.on('connect', () => {
  console.log('connected', socket.id);
  const teamId = process.env.TEST_TEAM_ID;
  if (teamId) socket.emit('joinTeam', teamId);
});

socket.on('notification', (payload) => {
  console.log('notification event:', payload);
});

socket.on('teamTaskCreated', (payload) => {
  console.log('teamTaskCreated event:', payload);
});

socket.on('connect_error', (err) => {
  console.error('connect_error:', err.message);
  process.exit(2);
});

setTimeout(() => {
  console.log('done');
  socket.close();
  process.exit(0);
}, 15000);

