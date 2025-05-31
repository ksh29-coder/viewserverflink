#!/bin/bash

# System Status Check Script
# ==========================
# Based on system-config.yml
# UPDATED FOR SEPARATED ARCHITECTURE: React UI (3000) + Backend (8080)

echo "🔍 View Server System Status Check (Separated Architecture)"
echo "============================================================"
echo

# Check Infrastructure Services
echo "📊 Infrastructure Services:"
echo "----------------------------"

# Redis (port 6379)
echo -n "Redis (6379): "
if docker exec viewserver-redis redis-cli ping >/dev/null 2>&1; then
    echo "✅ Running"
    REDIS_KEYS=$(docker exec viewserver-redis redis-cli DBSIZE 2>/dev/null || echo "0")
    echo "   └─ Keys in cache: $REDIS_KEYS"
else
    echo "❌ Not running"
fi

# Kafka (port 9092)
echo -n "Kafka (9092): "
if docker exec viewserver-kafka kafka-topics --bootstrap-server localhost:9092 --list >/dev/null 2>&1; then
    echo "✅ Running"
    TOPIC_COUNT=$(docker exec viewserver-kafka kafka-topics --bootstrap-server localhost:9092 --list 2>/dev/null | wc -l)
    echo "   └─ Topics available: $TOPIC_COUNT"
else
    echo "❌ Not running"
fi

echo

# Check Application Services (Separated Architecture)
echo "🚀 Application Services (Separated Architecture):"
echo "---------------------------------------------------"

# Mock Data Generator (port 8081)
echo -n "Mock Data Generator (8081): "
if curl -s http://localhost:8081/api/data-generation/status >/dev/null 2>&1; then
    echo "✅ Running"
    # Check if dynamic data is active
    DYNAMIC_STATUS=$(curl -s http://localhost:8081/api/data-generation/dynamic/status 2>/dev/null | grep -o '"running":[^,}]*' | cut -d: -f2 | tr -d ' "' || echo "unknown")
    echo "   └─ Dynamic data streams: $DYNAMIC_STATUS"
else
    echo "❌ Not running"
fi

# View Server Backend (port 8080)
echo -n "View Server Backend (8080): "
if curl -s http://localhost:8080/api/health >/dev/null 2>&1; then
    echo "✅ Running"
    echo "   └─ Backend APIs + WebSocket + CORS enabled"
    echo "   └─ Endpoint: http://localhost:8080/api/health"
else
    echo "❌ Not running"
fi

# React UI Frontend (port 3000)
echo -n "React UI Frontend (3000): "
if curl -s http://localhost:3000 >/dev/null 2>&1; then
    echo "✅ Running"
    echo "   └─ Development server with hot reload"
    echo "   └─ Frontend UI: http://localhost:3000/"
    echo "   └─ Account Overview: http://localhost:3000/account-overview"
else
    echo "❌ Not running"
    echo "   └─ Start with: cd react-ui && npm run dev"
fi

echo

# Check Port Usage
echo "🔌 Port Usage (Separated Architecture):"
echo "----------------------------------------"
for port in 3000 6379 8080 8081 9092; do
    echo -n "Port $port: "
    if lsof -i :$port >/dev/null 2>&1; then
        PROCESS=$(lsof -i :$port | tail -1 | awk '{print $1 " (PID: " $2 ")"}')
        case $port in
            3000) echo "🔴 In use by $PROCESS (React UI)" ;;
            6379) echo "🔴 In use by $PROCESS (Redis)" ;;
            8080) echo "🔴 In use by $PROCESS (View Server)" ;;
            8081) echo "🔴 In use by $PROCESS (Mock Data Generator)" ;;
            9092) echo "🔴 In use by $PROCESS (Kafka)" ;;
            *) echo "🔴 In use by $PROCESS" ;;
        esac
    else
        case $port in
            3000) echo "🟢 Available (React UI)" ;;
            6379) echo "🟢 Available (Redis)" ;;
            8080) echo "🟢 Available (View Server)" ;;
            8081) echo "🟢 Available (Mock Data Generator)" ;;
            9092) echo "🟢 Available (Kafka)" ;;
            *) echo "🟢 Available" ;;
        esac
    fi
done

echo

# Check Flink Jobs (if any are running)
echo "⚡ Flink Jobs:"
echo "--------------"

# UnifiedMarketValue job
echo -n "UnifiedMarketValue Job: "
if pgrep -f "UnifiedMarketValueJob" >/dev/null 2>&1; then
    UNIFIED_PID=$(pgrep -f "UnifiedMarketValueJob")
    echo "✅ Running (PID: $UNIFIED_PID)"
else
    echo "❌ Not running"
fi

echo

# Check Java Processes
echo "☕ Java Processes:"
echo "------------------"
JAVA_PROCS=$(ps aux | grep java | grep -v grep | wc -l)
if [ $JAVA_PROCS -gt 0 ]; then
    echo "Found $JAVA_PROCS Java processes:"
    ps aux | grep java | grep -v grep | while read line; do
        if echo "$line" | grep -q "view-server"; then
            echo "   └─ $line (VIEW SERVER)"
        elif echo "$line" | grep -q "mock-data-generator"; then
            echo "   └─ $line (MOCK DATA GENERATOR)"
        else
            echo "   └─ $line"
        fi
    done
else
    echo "No Java processes running"
fi

echo

# System Summary (Updated for Separated Architecture)
echo "📋 System Summary (Separated Architecture):"
echo "--------------------------------------------"
REDIS_OK=$(docker exec viewserver-redis redis-cli ping >/dev/null 2>&1 && echo "1" || echo "0")
KAFKA_OK=$(docker exec viewserver-kafka kafka-topics --bootstrap-server localhost:9092 --list >/dev/null 2>&1 && echo "1" || echo "0")
MOCK_OK=$(curl -s http://localhost:8081/api/data-generation/status >/dev/null 2>&1 && echo "1" || echo "0")
VIEW_OK=$(curl -s http://localhost:8080/api/health >/dev/null 2>&1 && echo "1" || echo "0")
REACT_OK=$(curl -s http://localhost:3000 >/dev/null 2>&1 && echo "1" || echo "0")
UNIFIED_FLINK_OK=$(pgrep -f "UnifiedMarketValueJob" >/dev/null 2>&1 && echo "1" || echo "0")

TOTAL_OK=$((REDIS_OK + KAFKA_OK + MOCK_OK + VIEW_OK + REACT_OK + UNIFIED_FLINK_OK))

if [ $TOTAL_OK -eq 6 ]; then
    echo "🎉 All services are running! System is fully operational."
    echo ""
    echo "🔄 Architecture Status:"
    echo "   Frontend (React): localhost:3000 ✅ → Backend (Spring): localhost:8080 ✅"
    echo "   🌐 React UI: http://localhost:3000/ (Development Server + Hot Reload)"
    echo "   📈 Account Overview: http://localhost:3000/account-overview"
    echo "   🔧 Backend APIs: http://localhost:8080/api/health"
    echo "   🔌 WebSocket: ws://localhost:8080/ws/account-overview/{viewId}"
    echo "   📊 Real-time aggregations active"
elif [ $TOTAL_OK -eq 0 ]; then
    echo "🚨 No services are running. Run './scripts/start-all.sh' to start the system."
else
    echo "⚠️  Partial system running ($TOTAL_OK/6 services). Check individual services above."
    echo ""
    echo "🔄 Architecture Status:"
    if [ $REACT_OK -eq 1 ] && [ $VIEW_OK -eq 1 ]; then
        echo "   Frontend (React): localhost:3000 ✅ → Backend (Spring): localhost:8080 ✅"
    elif [ $REACT_OK -eq 1 ] && [ $VIEW_OK -eq 0 ]; then
        echo "   Frontend (React): localhost:3000 ✅ → Backend (Spring): localhost:8080 ❌"
    elif [ $REACT_OK -eq 0 ] && [ $VIEW_OK -eq 1 ]; then
        echo "   Frontend (React): localhost:3000 ❌ → Backend (Spring): localhost:8080 ✅"
    else
        echo "   Frontend (React): localhost:3000 ❌ → Backend (Spring): localhost:8080 ❌"
    fi
    echo ""
    echo "💡 Quick fixes:"
    if [ $REDIS_OK -eq 0 ] || [ $KAFKA_OK -eq 0 ]; then
        echo "   🔧 Start infrastructure: docker-compose up -d"
    fi
    if [ $MOCK_OK -eq 0 ]; then
        echo "   📊 Start mock generator: java -Xmx2g -jar mock-data-generator/target/mock-data-generator-1.0.0-SNAPSHOT.jar &"
    fi
    if [ $VIEW_OK -eq 0 ]; then
        echo "   🔧 Start view server: java -Xmx2g -jar view-server/target/view-server-1.0.0-SNAPSHOT.jar &"
    fi
    if [ $REACT_OK -eq 0 ]; then
        echo "   🌐 Start React UI: cd react-ui && npm run dev"
    fi
    if [ $UNIFIED_FLINK_OK -eq 0 ]; then
        echo "   ⚡ Unified Flink job not running: ./scripts/start-unified-flink-job.sh"
    fi
    echo "   🚀 Or start everything: ./scripts/start-all.sh"
fi

echo
echo "📖 For detailed configuration, see: system-config.yml"
echo "🛠️  For troubleshooting, see: .cursorrules" 