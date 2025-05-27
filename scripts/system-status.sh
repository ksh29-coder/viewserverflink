#!/bin/bash

# System Status Check Script
# ==========================
# Based on system-config.yml

echo "🔍 View Server System Status Check"
echo "=================================="
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

# Check Application Services
echo "🚀 Application Services:"
echo "-------------------------"

# Mock Data Generator (port 8081)
echo -n "Mock Data Generator (8081): "
if curl -s http://localhost:8081/api/data-generation/status >/dev/null 2>&1; then
    echo "✅ Running"
    STATUS=$(curl -s http://localhost:8081/api/data-generation/status 2>/dev/null || echo "Unknown")
    echo "   └─ Status: $STATUS"
else
    echo "❌ Not running"
fi

# View Server (port 8080)
echo -n "View Server (8080): "
if curl -s http://localhost:8080/api/health >/dev/null 2>&1; then
    echo "✅ Running"
    echo "   └─ Backend API available at: http://localhost:8080/"
else
    echo "❌ Not running"
fi

# React UI (port 3000)
echo -n "React UI (3000): "
if curl -s http://localhost:3000 >/dev/null 2>&1; then
    echo "✅ Running"
    echo "   └─ Frontend UI available at: http://localhost:3000/"
else
    echo "❌ Not running"
fi

echo

# Check Port Usage
echo "🔌 Port Usage:"
echo "---------------"
for port in 3000 6379 8080 8081 9092; do
    echo -n "Port $port: "
    if lsof -i :$port >/dev/null 2>&1; then
        PROCESS=$(lsof -i :$port | tail -1 | awk '{print $1 " (PID: " $2 ")"}')
        echo "🔴 In use by $PROCESS"
    else
        echo "🟢 Available"
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
        echo "   └─ $line"
    done
else
    echo "No Java processes running"
fi

echo

# System Summary
echo "📋 System Summary:"
echo "------------------"
REDIS_OK=$(docker exec viewserver-redis redis-cli ping >/dev/null 2>&1 && echo "1" || echo "0")
KAFKA_OK=$(docker exec viewserver-kafka kafka-topics --bootstrap-server localhost:9092 --list >/dev/null 2>&1 && echo "1" || echo "0")
MOCK_OK=$(curl -s http://localhost:8081/api/data-generation/status >/dev/null 2>&1 && echo "1" || echo "0")
VIEW_OK=$(curl -s http://localhost:8080/api/health >/dev/null 2>&1 && echo "1" || echo "0")
REACT_OK=$(curl -s http://localhost:3000 >/dev/null 2>&1 && echo "1" || echo "0")
UNIFIED_FLINK_OK=$(pgrep -f "UnifiedMarketValueJob" >/dev/null 2>&1 && echo "1" || echo "0")

TOTAL_OK=$((REDIS_OK + KAFKA_OK + MOCK_OK + VIEW_OK + REACT_OK + UNIFIED_FLINK_OK))

if [ $TOTAL_OK -eq 6 ]; then
    echo "🎉 All services are running! System is fully operational."
    echo "   🌐 React UI: http://localhost:3000/ (Modern Dashboard)"
    echo "   🔧 Backend API: http://localhost:8080/ (Spring Boot)"
    echo "   📊 Real-time aggregations active"
elif [ $TOTAL_OK -eq 0 ]; then
    echo "🚨 No services are running. Run './scripts/start-all.sh' to start the system."
else
    echo "⚠️  Partial system running ($TOTAL_OK/6 services). Check individual services above."
    if [ $REDIS_OK -eq 0 ] || [ $KAFKA_OK -eq 0 ]; then
        echo "   💡 Start infrastructure first: docker-compose up -d"
    fi
    if [ $MOCK_OK -eq 0 ]; then
        echo "   💡 Start mock generator: ./scripts/start-dynamic-data.sh"
    fi
    if [ $VIEW_OK -eq 0 ]; then
        echo "   💡 Start view server: ./scripts/start-view-server.sh"
    fi
    if [ $REACT_OK -eq 0 ]; then
        echo "   💡 Start React UI: cd react-ui && npm run dev"
    fi
    if [ $UNIFIED_FLINK_OK -eq 0 ]; then
        echo "   💡 Unified Flink job not running: ./scripts/start-unified-flink-job.sh"
    fi
fi

echo
echo "📖 For detailed configuration, see: system-config.yml"
echo "🛠️  For troubleshooting, see: .cursorrules" 