# Use Node.js 20 slim as base image
FROM node:20-bullseye-slim

# Install system dependencies, including Python 3 and build tools
RUN apt-get update && apt-get install -y --no-install-recommends \
    python3 \
    python3-pip \
    python3-venv \
    build-essential \
    && rm -rf /var/lib/apt/lists/*

# Set working directory
WORKDIR /app

# Copy backend package files and install dependencies
COPY backend/package*.json ./backend/
RUN cd backend && npm install --production

# Create virtual environment for Python and install ML dependencies
COPY ml/requirements.txt ./ml/
RUN python3 -m venv .venv && \
    .venv/bin/pip install --upgrade pip && \
    .venv/bin/pip install -r ml/requirements.txt fastapi uvicorn

# Copy the rest of the application files
COPY backend/ ./backend/
COPY ml/ ./ml/

# Set environment variables
ENV NODE_ENV=production
ENV PORT=4000
ENV PYTHON_BIN=/app/.venv/bin/python
ENV START_MODEL_SERVICE=1
ENV MODEL_SERVICE_PORT=8001

# Expose the Express server port
EXPOSE 4000

# Start the Node.js application
WORKDIR /app/backend
CMD ["node", "index.js"]
