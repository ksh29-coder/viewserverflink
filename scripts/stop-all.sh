#!/bin/bash

# Stop All Services
# This script stops all running view server components

echo "🛑 Stopping All Services"
echo "========================"

# Stop any Flink jobs if running
if pgrep -f "UnifiedMarketValueJob" > /dev/null; then
    echo "🔄 Stopping Flink jobs..."
    pkill -f "UnifiedMarketValueJob" 2>/dev/null || true
    sleep 2
fi

# Stop React UI
if pgrep -f "npm run dev\|vite" > /dev/null; then
    echo "🔄 Stopping React UI..."
    pkill -f "npm run dev"
    pkill -f "vite"
    sleep 2
fi

# Stop mock data generator
if pgrep -f "mock-data-generator" > /dev/null; then
    echo "🔄 Stopping mock-data-generator..."
    pkill -f "mock-data-generator"
    sleep 2
fi

# Stop view server
if pgrep -f "view-server" > /dev/null; then
    echo "🔄 Stopping view-server..."
    pkill -f "view-server"
    sleep 2
fi

# Stop ViewServerApplication specifically
if pgrep -f "ViewServerApplication" > /dev/null; then
    echo "🔄 Stopping ViewServerApplication..."
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
if lsof -ti:3000 > /dev/null 2>&1; then
    echo "🔧 Freeing port 3000..."
    lsof -ti:3000 | xargs kill -9 2>/dev/null || true
fi

if lsof -ti:8080 > /dev/null 2>&1; then
    echo "🔧 Freeing port 8080..."
    lsof -ti:8080 | xargs kill -9 2>/dev/null || true
fi

if lsof -ti:8081 > /dev/null 2>&1; then
    echo "🔧 Freeing port 8081..."
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

# Check ports
PORTS_IN_USE=""
if lsof -ti:3000 > /dev/null 2>&1; then
    PORTS_IN_USE="$PORTS_IN_USE 3000"
fi
if lsof -ti:8080 > /dev/null 2>&1; then
    PORTS_IN_USE="$PORTS_IN_USE 8080"
fi
if lsof -ti:8081 > /dev/null 2>&1; then
    PORTS_IN_USE="$PORTS_IN_USE 8081"
fi

if [ -z "$PORTS_IN_USE" ]; then
    echo "✅ All application ports freed"
else
    echo "⚠️  Ports still in use:$PORTS_IN_USE"
fi

echo ""
echo "✅ All services stopped"
echo ""
echo "📝 Infrastructure services (Kafka, Redis) are still running"
echo "   To stop them: docker-compose down"
echo ""
echo "📄 Log files preserved:"
echo "  - view-server-restart.log"
echo "  - mock-data-generator.log"
echo "  - react-ui-dev.log"
echo ""
echo "🗑️  To purge Kafka data:"
echo "  ./scripts/show-kafka-data.sh       (view current data)"
echo "  ./scripts/purge-kafka-data.sh      (safe with confirmation)"
echo "  ./scripts/quick-purge-kafka.sh     (fast for development)" 