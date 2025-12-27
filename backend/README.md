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


