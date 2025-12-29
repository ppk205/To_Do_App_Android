# Backend (MOPR)

Short guide to run the backend inside WSL (Ubuntu) with local Redis.

Prerequisites (on Windows):
- WSL2 with Ubuntu installed (you already have).
- Node.js (>=16) installed in WSL Ubuntu.

Quick steps (copy/paste):

1) Open Ubuntu (WSL) and go to project folder mounted under /mnt/d

```bash
# inside WSL Ubuntu shell
cd /mnt/d/MORP_NOP_2/MOPR_CK/MOPR-PRJ/backend
```

2) Install Node and build tools (if not yet):

```bash
# inside WSL
curl -fsSL https://deb.nodesource.com/setup_18.x | sudo -E bash -
sudo apt update && sudo apt install -y nodejs build-essential
node -v
npm -v
```

3) Install Redis server in WSL and start it (dev only):

```bash
sudo apt update
sudo apt install -y redis-server
# start redis
sudo service redis-server start
# verify
redis-cli ping
# expected: PONG
```

4) Install project dependencies and run backend

```bash
# inside backend folder
npm install
# optional: set env vars for MySQL/Redis etc or create a .env file
# to run with local Redis (no TLS, default port), just run:
npm start
# or in dev mode with auto-reload
npm run dev
```

5) To connect backend to Redis Cloud instead of local Redis, set REDIS_URL before starting:

```bash
# example (inside WSL)
export REDIS_URL='redis://default:YOUR_PASSWORD@redis-13400.c15.us-east-1-4.ec2.cloud.redislabs.com:13400'
npm start
```

Security notes:
- Never commit secrets; use env vars or secret managers.
- For production, use TLS (rediss://) and do not disable certificate validation.

Troubleshooting:
- If `npm install` fails, check Node version and network.
- If Redis connection fails, ensure Redis server running and `REDIS_URL`/host/port correct.

## Redis Connection Scenarios

### Scenario 1: Running Node backend in WSL, Redis in WSL (same machine)
```bash
# In WSL Ubuntu:
sudo service redis-server start
redis-cli ping  # Should return PONG

# Set REDIS_URL to localhost
export REDIS_URL=redis://127.0.0.1:6379
npm run dev
```

### Scenario 2: Running Node backend on Windows, Redis in WSL
```bash
# In WSL Ubuntu - Check your WSL IP:
ip addr show eth0 | grep "inet "
# Example output: inet 172.18.191.249/20

# Start Redis in WSL:
sudo service redis-server start

# On Windows PowerShell - Set REDIS_URL to WSL IP:
$env:REDIS_URL="redis://172.18.191.249:6379"
npm run dev
```

### Scenario 3: Redis in Docker (if port 6379 is free)
```bash
# Windows PowerShell or WSL:
docker run -d --name redis-mopr -p 6379:6379 redis:alpine
export REDIS_URL=redis://127.0.0.1:6379  # or $env:REDIS_URL on PowerShell
npm run dev
```

### Scenario 4: Redis in Docker on different port (if 6379 is in use)
```bash
# Map to port 6380 instead:
docker run -d --name redis-mopr -p 6380:6379 redis:alpine
export REDIS_URL=redis://127.0.0.1:6380  # or $env:REDIS_URL on PowerShell
npm run dev
```

## Verifying Redis Connection
When the backend starts successfully, you should see:
```
Redis connecting...
✅ Redis connected and ready
🚀 Server is running on port 3001
```

If Redis fails to connect, you'll see:
```
❌ Redis connection error: [error details]
Redis initialization failed (non-fatal): [error message]
🚀 Server is running on port 3001
```
Note: The server will still start even if Redis fails (non-fatal), but OTP features won't work.


