#!/bin/bash

# Stop All Services
# This script stops all running view server components
# UPDATED FOR SEPARATED ARCHITECTURE: React UI (3000) + Backend (8080)

echo "🛑 Stopping All Services (Separated Architecture)"
echo "=================================================="

# Stop any Flink jobs if running
if pgrep -f "UnifiedMarketValueJob" > /dev/null; then
    echo "🔄 Stopping Flink jobs..."
    pkill -f "UnifiedMarketValueJob" 2>/dev/null || true
    sleep 2
fi

# Stop React UI (port 3000)
echo "🔄 Stopping React UI Frontend (port 3000)..."
if pgrep -f "npm run dev\|vite" > /dev/null; then
    echo "   └─ Stopping React development server..."
    pkill -f "npm run dev"
    pkill -f "vite"
    sleep 2
fi

# Stop mock data generator (port 8081)
echo "🔄 Stopping Mock Data Generator (port 8081)..."
if pgrep -f "mock-data-generator" > /dev/null; then
    echo "   └─ Stopping mock data generator..."
    pkill -f "mock-data-generator"
    sleep 2
fi

# Stop view server (port 8080)
echo "🔄 Stopping View Server Backend (port 8080)..."
if pgrep -f "view-server" > /dev/null; then
    echo "   └─ Stopping view server..."
    pkill -f "view-server"
    sleep 2
fi

# Stop ViewServerApplication specifically
if pgrep -f "ViewServerApplication" > /dev/null; then
    echo "   └─ Stopping ViewServerApplication..."
    pkill -f "ViewServerApplication"
    sleep 2
fi

# Stop any Maven processes
if pgrep -f "mvn.*run" > /dev/null; then
    echo "🔄 Stopping Maven processes..."
    pkill -f "mvn.*run"
    sleep 3
fi

# Stop any Java processes related to our services
if pgrep -f "com.viewserver" > /dev/null; then
    echo "🔄 Stopping remaining ViewServer Java processes..."
    pkill -f "com.viewserver"
    sleep 2
fi

# Kill processes on specific ports if they're still running
echo "🔧 Freeing ports..."

if lsof -ti:3000 > /dev/null 2>&1; then
    echo "   └─ Freeing port 3000 (React UI)..."
    lsof -ti:3000 | xargs kill -9 2>/dev/null || true
fi

if lsof -ti:8080 > /dev/null 2>&1; then
    echo "   └─ Freeing port 8080 (View Server)..."
    lsof -ti:8080 | xargs kill -9 2>/dev/null || true
fi

if lsof -ti:8081 > /dev/null 2>&1; then
    echo "   └─ Freeing port 8081 (Mock Data Generator)..."
    lsof -ti:8081 | xargs kill -9 2>/dev/null || true
fi

# Clean up any remaining background processes
echo "🔄 Final cleanup..."
sleep 2

# Verify all services are stopped
echo ""
echo "🔍 Verification:"
if ! pgrep -f "ViewServerApplication\|mock-data-generator\|UnifiedMarketValueJob\|npm run dev\|vite" > /dev/null; then
    echo "✅ All application services stopped"
else
    echo "⚠️  Some processes may still be running:"
    pgrep -f "ViewServerApplication\|mock-data-generator\|UnifiedMarketValueJob\|npm run dev\|vite" | while read pid; do
        echo "  PID $pid: $(ps -p $pid -o comm= 2>/dev/null || echo 'unknown')"
    done
fi

# Check ports (Separated Architecture)
echo ""
echo "🔌 Port Status (Separated Architecture):"
PORTS_IN_USE=""

# React UI (port 3000)
if lsof -ti:3000 > /dev/null 2>&1; then
    PORTS_IN_USE="$PORTS_IN_USE 3000"
    echo "  ⚠️  Port 3000 (React UI): Still in use"
else
    echo "  ✅ Port 3000 (React UI): Free"
fi

# View Server (port 8080)
if lsof -ti:8080 > /dev/null 2>&1; then
    PORTS_IN_USE="$PORTS_IN_USE 8080"
    echo "  ⚠️  Port 8080 (View Server): Still in use"
else
    echo "  ✅ Port 8080 (View Server): Free"
fi

# Mock Data Generator (port 8081)
if lsof -ti:8081 > /dev/null 2>&1; then
    PORTS_IN_USE="$PORTS_IN_USE 8081"
    echo "  ⚠️  Port 8081 (Mock Data Generator): Still in use"
else
    echo "  ✅ Port 8081 (Mock Data Generator): Free"
fi

if [ -z "$PORTS_IN_USE" ]; then
    echo ""
    echo "✅ All application ports freed"
else
    echo ""
    echo "⚠️  Some ports still in use:$PORTS_IN_USE"
    echo "   If issues persist, you can manually kill processes:"
    for port in $PORTS_IN_USE; do
        echo "   lsof -ti:$port | xargs kill -9"
    done
fi

echo ""
echo "✅ All services stopped"
echo ""
echo "📝 Infrastructure services (Kafka, Redis) are still running"
echo "   To stop them: docker-compose down"
echo ""
echo "📄 Log files preserved:"
echo "  - view-server.log (Backend API logs)"
echo "  - mock-data-generator.log (Data generation logs)"
echo "  - react-ui-dev.log (Frontend development logs)"
echo ""
echo "🔄 Architecture Status:"
echo "  Frontend (React): localhost:3000 → STOPPED"
echo "  Backend (Spring): localhost:8080 → STOPPED"
echo "  Mock Generator: localhost:8081 → STOPPED"
echo ""
echo "🗑️  To purge Kafka data:"
echo "  ./scripts/show-kafka-data.sh       (view current data)"
echo "  ./scripts/purge-kafka-data.sh      (safe with confirmation)"
echo "  ./scripts/quick-purge-kafka.sh     (fast for development)"
echo ""
echo "🚀 To restart all services: ./scripts/start-all.sh" 