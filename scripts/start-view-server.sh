#!/bin/bash

# Start View Server
# This script starts the view server that consumes Kafka events and caches them in Redis

echo "🚀 Starting View Server"
echo "======================="

# Navigate to project root
cd "$(dirname "$0")/.."

# Check for and kill existing processes
echo "🔍 Checking for existing processes..."
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

echo "🔧 Building view-server..."
mvn clean compile -pl view-server -q

if [ $? -ne 0 ]; then
    echo "❌ Build failed! Please check compilation errors."
    exit 1
fi

echo "📡 Starting View Server on http://localhost:8080"
echo "🔍 Available endpoints:"
echo "   • GET  /api/stats - Cache statistics"
echo "   • GET  /api/accounts - All accounts"
echo "   • GET  /api/instruments - All instruments"
echo "   • GET  /api/prices - All prices"
echo "   • GET  /api/orders - All orders"
echo "   • GET  /api/holdings/{accountId} - Holdings for account"
echo "   • GET  /api/cash/{accountId} - Cash movements for account"
echo ""

# Start the view server
exec mvn spring-boot:run -pl view-server 