#!/bin/bash

# Stop All Services
# This script stops all running view server components

echo "🛑 Stopping All Services"
echo "========================"

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

# Stop any Maven processes
if pgrep -f "mvn.*run" > /dev/null; then
    echo "🔄 Stopping Maven processes..."
    pkill -f "mvn.*run"
    sleep 2
fi

# Kill processes on specific ports if they're still running
if lsof -ti:8080 > /dev/null 2>&1; then
    echo "🔧 Freeing port 8080..."
    lsof -ti:8080 | xargs kill -9 2>/dev/null || true
fi

if lsof -ti:8081 > /dev/null 2>&1; then
    echo "🔧 Freeing port 8081..."
    lsof -ti:8081 | xargs kill -9 2>/dev/null || true
fi

echo "✅ All services stopped"
echo ""
echo "📝 Infrastructure services (Kafka, Redis) are still running"
echo "   To stop them: docker-compose down" 