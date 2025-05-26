#!/bin/bash

# Start All Services
# This script starts all view server components in the correct order
# Following the mandatory startup sequence from system-config.yml

set -e

echo "🚀 Starting All View Server Services"
echo "===================================="
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

# Step 2: Start Mock Data Generator
echo "📋 Step 2: Mock Data Generator (port 8081)"
echo "-------------------------------------------"

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

echo "🔧 Building Mock Data Generator..."
mvn clean compile -pl mock-data-generator -q

if [ $? -ne 0 ]; then
    echo "❌ Mock Data Generator build failed! Please check compilation errors."
    exit 1
fi

echo "🔧 Starting Mock Data Generator in background..."
nohup mvn spring-boot:run -pl mock-data-generator > mock-data-generator.log 2>&1 &
MOCK_DATA_PID=$!

# Wait for Mock Data Generator to be ready
echo "⏳ Waiting for Mock Data Generator to start..."
sleep 15

if curl -s http://localhost:8081/api/data-generation/status > /dev/null 2>&1; then
    echo "✅ Mock Data Generator is ready (PID: $MOCK_DATA_PID)"
else
    echo "❌ Mock Data Generator failed to start"
    exit 1
fi

echo "ℹ️  Mock Data Generator is ready - data initialization can be done manually if needed"
echo "   Use: curl -X POST http://localhost:8081/api/data-generation/initialize"

echo ""

# Step 3: Start View Server
echo "📋 Step 3: View Server (port 8080)"
echo "-----------------------------------"

echo "🔧 Starting View Server..."
./scripts/start-view-server.sh

# Wait for View Server to be ready
echo "⏳ Waiting for View Server to start..."
sleep 20

if curl -s http://localhost:8080/api/health > /dev/null 2>&1; then
    echo "✅ View Server is ready"
else
    echo "❌ View Server failed to start"
    exit 1
fi

echo ""

# Step 4: Start React UI
echo "📋 Step 4: React UI (port 3000)"
echo "--------------------------------"

echo "🔧 Starting React UI..."
cd react-ui

# Check if node_modules exists, install if needed
if [ ! -d "node_modules" ]; then
    echo "📦 Installing React UI dependencies..."
    npm install
fi

# Start React development server
echo "🔧 Starting React development server..."
nohup npm run dev > ../react-ui-dev.log 2>&1 &
REACT_PID=$!
cd ..

# Wait for React UI to be ready
echo "⏳ Waiting for React UI to start..."
sleep 15

if curl -s http://localhost:3000 > /dev/null 2>&1; then
    echo "✅ React UI is ready"
else
    echo "❌ React UI failed to start"
    exit 1
fi

echo ""

echo "ℹ️  Flink jobs are not started automatically"
echo "   Use dedicated Flink startup scripts when ready"

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
echo "  ✅ View Server: $(curl -s http://localhost:8080/api/health > /dev/null 2>&1 && echo 'OK' || echo 'FAILED')"
echo "  ✅ React UI: $(curl -s http://localhost:3000 > /dev/null 2>&1 && echo 'OK' || echo 'FAILED')"

# Flink jobs not started automatically
echo "  ℹ️  Flink Jobs: Not started (use dedicated scripts when ready)"

echo ""
echo "🎉 All services started successfully!"
echo ""
echo "📊 System Status:"
echo "  🌐 React UI (Modern Dashboard): http://localhost:3000/"
echo "  🔧 Backend API (Spring Boot): http://localhost:8080/"
echo "  📡 Mock Data Generator: http://localhost:8081/api/data-generation/status"
echo "  📋 System Status: ./scripts/system-status.sh"
echo ""
echo "📝 Logs:"
echo "  📄 View Server: view-server-restart.log"
echo "  📄 Mock Data Generator: mock-data-generator.log"
echo "  📄 React UI: react-ui-dev.log"
echo ""
echo "🛑 To stop all services: ./scripts/stop-all.sh" 