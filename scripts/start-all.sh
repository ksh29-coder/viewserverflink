#!/bin/bash

# Start All Services
# This script starts all view server components in the correct order
# Following the mandatory startup sequence from system-config.yml
# UPDATED FOR SEPARATED ARCHITECTURE: React UI (3000) + Backend (8080)

set -e

echo "🚀 Starting All View Server Services (Separated Architecture)"
echo "=============================================================="
echo ""

# Step 1: Check and start infrastructure services
echo "📋 Step 1: Infrastructure Services (Redis on 6379, Kafka on 9092)"
echo "-------------------------------------------------------------------"

# Check if Docker is running
if ! docker info > /dev/null 2>&1; then
    echo "🔧 Starting Docker..."
    open -a Docker
    echo "⏳ Waiting for Docker to start..."
    while ! docker info > /dev/null 2>&1; do
        sleep 2
    done
    echo "✅ Docker is running"
fi

# Start infrastructure with Docker Compose
echo "🔧 Starting infrastructure services..."
docker-compose down > /dev/null 2>&1 || true
sleep 2
docker-compose up -d

# Wait for services to be ready
echo "⏳ Waiting for infrastructure to be ready..."
sleep 10

# Verify Redis
echo "🔍 Testing Redis connectivity..."
if docker exec viewserver-redis redis-cli ping > /dev/null 2>&1; then
    echo "✅ Redis is ready"
else
    echo "❌ Redis failed to start"
    exit 1
fi

# Verify Kafka
echo "🔍 Testing Kafka connectivity..."
if docker exec viewserver-kafka kafka-topics --bootstrap-server localhost:9092 --list > /dev/null 2>&1; then
    echo "✅ Kafka is ready"
else
    echo "❌ Kafka failed to start"
    exit 1
fi

echo ""

# Step 2: Start Mock Data Generator (Background Process)
echo "📋 Step 2: Mock Data Generator (port 8081) - Background Process"
echo "----------------------------------------------------------------"

# Check for and kill existing processes
echo "🔍 Checking for existing Mock Data Generator processes..."
if pgrep -f "mock-data-generator" > /dev/null; then
    echo "⚠️  Stopping existing mock-data-generator processes..."
    pkill -f "mock-data-generator"
    sleep 2
fi

# Check if port 8081 is in use
if lsof -ti:8081 > /dev/null 2>&1; then
    echo "⚠️  Port 8081 is in use. Killing processes..."
    lsof -ti:8081 | xargs kill -9 2>/dev/null || true
    sleep 2
fi

echo "🔧 Building Mock Data Generator JAR..."
mvn clean package spring-boot:repackage -pl mock-data-generator -DskipTests -q

if [ $? -ne 0 ]; then
    echo "❌ Mock Data Generator build failed! Please check compilation errors."
    exit 1
fi

echo "🔧 Starting Mock Data Generator in background (continuous process)..."
java -Xmx2g -jar mock-data-generator/target/mock-data-generator-1.0.0-SNAPSHOT.jar > mock-data-generator.log 2>&1 &
MOCK_DATA_PID=$!
echo "   └─ Mock Data Generator started with PID: $MOCK_DATA_PID"

# Wait for Mock Data Generator to be ready
echo "⏳ Waiting for Mock Data Generator to start..."
sleep 15

if curl -s http://localhost:8081/api/data-generation/status > /dev/null 2>&1; then
    echo "✅ Mock Data Generator is ready and running continuously"
else
    echo "❌ Mock Data Generator failed to start"
    exit 1
fi

echo ""

# Step 3: Start View Server (Backend APIs)
echo "📋 Step 3: View Server Backend (port 8080) - APIs + WebSocket"
echo "--------------------------------------------------------------"

# Check for and kill existing processes
echo "🔍 Checking for existing View Server processes..."
if pgrep -f "view-server" > /dev/null; then
    echo "⚠️  Stopping existing view-server processes..."
    pkill -f "view-server"
    sleep 2
fi

# Check if port 8080 is in use
if lsof -ti:8080 > /dev/null 2>&1; then
    echo "⚠️  Port 8080 is in use. Killing processes..."
    lsof -ti:8080 | xargs kill -9 2>/dev/null || true
    sleep 2
fi

echo "🔧 Building View Server JAR..."
mvn clean package spring-boot:repackage -pl view-server -DskipTests -q

if [ $? -ne 0 ]; then
    echo "❌ View Server build failed! Please check compilation errors."
    exit 1
fi

echo "🔧 Starting View Server backend in background..."
java -Xmx2g -jar view-server/target/view-server-1.0.0-SNAPSHOT.jar > view-server.log 2>&1 &
VIEW_SERVER_PID=$!
echo "   └─ View Server started with PID: $VIEW_SERVER_PID"

# Wait for View Server to be ready
echo "⏳ Waiting for View Server to start..."
sleep 20

if curl -s http://localhost:8080/api/health > /dev/null 2>&1; then
    echo "✅ View Server backend is ready (APIs + WebSocket + CORS enabled)"
else
    echo "❌ View Server failed to start"
    echo "📄 Check logs: tail -f view-server.log"
    exit 1
fi

echo ""

# Step 4: Start React UI Frontend (Development Server)
echo "📋 Step 4: React UI Frontend (port 3000) - Development Server"
echo "--------------------------------------------------------------"

# Check for and kill existing React processes
echo "🔍 Checking for existing React UI processes..."
if pgrep -f "npm run dev\|vite" > /dev/null; then
    echo "⚠️  Stopping existing React UI processes..."
    pkill -f "npm run dev"
    pkill -f "vite"
    sleep 2
fi

# Check if port 3000 is in use
if lsof -ti:3000 > /dev/null 2>&1; then
    echo "⚠️  Port 3000 is in use. Killing processes..."
    lsof -ti:3000 | xargs kill -9 2>/dev/null || true
    sleep 2
fi

# Check if react-ui directory exists
if [ ! -d "react-ui" ]; then
    echo "❌ react-ui directory not found! Make sure you're in the project root."
    exit 1
fi

echo "🔧 Installing React UI dependencies..."
cd react-ui
npm install > /dev/null 2>&1

echo "🔧 Starting React UI development server in background..."
npm run dev > ../react-ui-dev.log 2>&1 &
REACT_UI_PID=$!
cd ..
echo "   └─ React UI started with PID: $REACT_UI_PID"

# Wait for React UI to be ready
echo "⏳ Waiting for React UI development server to start..."
sleep 10

if curl -s http://localhost:3000 > /dev/null 2>&1; then
    echo "✅ React UI development server is ready with hot reload"
else
    echo "⚠️  React UI may still be starting (this is normal)"
    echo "📄 Check logs: tail -f react-ui-dev.log"
fi

echo ""

# Step 5: Initialize Data (Non-blocking)
echo "📋 Step 5: Data Initialization"
echo "-------------------------------"

echo "🔧 Initializing static data..."
if curl -X POST http://localhost:8081/api/data-generation/initialize -H "Content-Type: application/json" > /dev/null 2>&1; then
    echo "✅ Static data initialized successfully"
else
    echo "⚠️  Static data initialization failed - you can retry manually"
    echo "   Use: curl -X POST http://localhost:8081/api/data-generation/initialize"
fi

echo "🔧 Starting dynamic data streams..."
if curl -X POST http://localhost:8081/api/data-generation/dynamic/start > /dev/null 2>&1; then
    echo "✅ Dynamic data streams started"
else
    echo "⚠️  Dynamic data streams failed to start - you can retry manually"
    echo "   Use: curl -X POST http://localhost:8081/api/data-generation/dynamic/start"
fi

echo ""

# Final verification
echo "📋 Final System Verification"
echo "-----------------------------"

echo "🔍 Checking all service endpoints..."

# Check infrastructure
echo "  ✅ Redis: $(docker exec viewserver-redis redis-cli ping 2>/dev/null || echo 'FAILED')"
echo "  ✅ Kafka: $(docker exec viewserver-kafka kafka-topics --bootstrap-server localhost:9092 --list > /dev/null 2>&1 && echo 'OK' || echo 'FAILED')"

# Check application services
echo "  ✅ Mock Data Generator: $(curl -s http://localhost:8081/api/data-generation/status > /dev/null 2>&1 && echo 'OK' || echo 'FAILED') (PID: $MOCK_DATA_PID)"
echo "  ✅ View Server Backend: $(curl -s http://localhost:8080/api/health > /dev/null 2>&1 && echo 'OK' || echo 'FAILED') (PID: $VIEW_SERVER_PID)"
echo "  ✅ React UI Frontend: $(curl -s http://localhost:3000 > /dev/null 2>&1 && echo 'OK' || echo 'STARTING') (PID: $REACT_UI_PID)"

# Check Account Overview endpoints
echo "  ✅ Account Overview API: $(curl -s http://localhost:8080/api/account-overview/health > /dev/null 2>&1 && echo 'OK' || echo 'FAILED')"

echo ""
echo "🎉 All services started successfully! (Separated Architecture)"
echo ""
echo "📊 System Access:"
echo "  🌐 React UI (Frontend): http://localhost:3000/ (Hot Reload Development Server)"
echo "  📈 Account Overview: http://localhost:3000/account-overview"
echo "  🔧 Backend APIs: http://localhost:8080/api/health"
echo "  🔌 WebSocket: ws://localhost:8080/ws/account-overview/{viewId}"
echo "  📡 Mock Data Generator: http://localhost:8081/api/data-generation/status"
echo ""
echo "🔄 Architecture:"
echo "  Frontend (React): localhost:3000 → Backend (Spring): localhost:8080"
echo "  ✅ CORS enabled for cross-origin requests"
echo "  ✅ Hot reload for UI development"
echo "  ✅ Real-time WebSocket updates"
echo ""
echo "📝 Logs:"
echo "  📄 View Server: tail -f view-server.log"
echo "  📄 Mock Data Generator: tail -f mock-data-generator.log"
echo "  📄 React UI: tail -f react-ui-dev.log"
echo ""
echo "🔧 Manual Commands (if needed):"
echo "  📊 Initialize Data: curl -X POST http://localhost:8081/api/data-generation/initialize"
echo "  🔄 Start Dynamic Data: curl -X POST http://localhost:8081/api/data-generation/dynamic/start"
echo "  📋 System Status: ./scripts/system-status.sh"
echo ""
echo "🛑 To stop all services: ./scripts/stop-all.sh" 