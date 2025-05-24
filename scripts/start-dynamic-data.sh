#!/bin/bash

# Start Dynamic Data Generation
# This script starts the mock data generator for continuous dynamic data streams:
# - Prices (every 5 seconds)  
# - Orders (new orders every 30s, updates every 10s)
# - Cash movements (every 2 minutes)
#
# Static data (accounts, instruments) and SOD holdings must be triggered manually via REST API

echo "🚀 Starting Mock Data Generator (Dynamic Data Only)"
echo "====================================================="

# Navigate to project root
cd "$(dirname "$0")/.."

# Check for and kill existing processes
echo "🔍 Checking for existing processes..."
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

echo "🔧 Building mock-data-generator..."
mvn clean compile -pl mock-data-generator -q

if [ $? -ne 0 ]; then
    echo "❌ Build failed! Please check compilation errors."
    exit 1
fi

echo "📊 Dynamic data streams:"
echo "   • Prices: Updated every 5 seconds"
echo "   • Orders: New orders every 30s, updates every 10s" 
echo "   • Cash movements: Every 2 minutes"
echo ""
echo "💡 To initialize static data and SOD holdings:"
echo "   POST http://localhost:8081/api/data-generation/initialize"
echo ""
echo "🌐 Data generation control: http://localhost:8081/api/data-generation/status"
echo ""

# Start the mock data generator
exec mvn spring-boot:run -pl mock-data-generator 