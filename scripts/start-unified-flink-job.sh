#!/bin/bash

# Start Unified Flink Job Script
# Starts the unified market value Flink job with Java 17 support using fat JAR approach

set -e

echo "🚀 Starting Unified Market Value Flink Job with Java 17 (Fat JAR Approach)"
echo "=========================================================================="
echo ""

# Check prerequisites
echo "📋 Prerequisites Check:"
echo "-----------------------"

# Check Java version
echo "🔍 Java version:"
java -version
echo ""

# Check if infrastructure is running
echo "🔍 Checking infrastructure services..."
if ! docker exec viewserver-redis redis-cli ping > /dev/null 2>&1; then
    echo "❌ Redis is not running. Please start infrastructure first:"
    echo "   docker-compose up -d"
    exit 1
fi

if ! docker exec viewserver-kafka kafka-topics --bootstrap-server localhost:9092 --list > /dev/null 2>&1; then
    echo "❌ Kafka is not running. Please start infrastructure first:"
    echo "   docker-compose up -d"
    exit 1
fi

echo "✅ Infrastructure services are running"
echo ""

# Java 17 Module System Arguments for Flink
JAVA_17_ARGS="--add-opens java.base/java.util=ALL-UNNAMED \
--add-opens java.base/java.lang=ALL-UNNAMED \
--add-opens java.base/java.lang.invoke=ALL-UNNAMED \
--add-opens java.base/java.lang.reflect=ALL-UNNAMED \
--add-opens java.base/java.io=ALL-UNNAMED \
--add-opens java.base/java.net=ALL-UNNAMED \
--add-opens java.base/java.nio=ALL-UNNAMED \
--add-opens java.base/java.time=ALL-UNNAMED \
--add-opens java.base/java.util.concurrent=ALL-UNNAMED \
--add-opens java.base/java.util.concurrent.atomic=ALL-UNNAMED \
--add-opens java.base/java.util.concurrent.locks=ALL-UNNAMED \
--add-opens java.base/sun.nio.ch=ALL-UNNAMED \
--add-opens java.base/sun.nio.cs=ALL-UNNAMED \
--add-opens java.base/sun.security.action=ALL-UNNAMED \
--add-opens java.base/sun.util.calendar=ALL-UNNAMED \
--add-opens java.security.jgss/sun.security.krb5=ALL-UNNAMED \
--add-opens java.rmi/sun.rmi.registry=ALL-UNNAMED \
--add-opens java.rmi/sun.rmi.server=ALL-UNNAMED \
--add-opens java.sql/java.sql=ALL-UNNAMED"

# Build the fat JAR
echo "📋 Building Flink Jobs Fat JAR:"
echo "-------------------------------"
echo "🔧 Building flink-jobs fat JAR with all dependencies..."
mvn clean package -pl flink-jobs -q

if [ $? -ne 0 ]; then
    echo "❌ Build failed! Please check compilation errors."
    exit 1
fi

# Verify fat JAR exists
if [ ! -f "flink-jobs/target/flink-jobs-1.0.0-SNAPSHOT.jar" ]; then
    echo "❌ Fat JAR not found! Build may have failed."
    exit 1
fi

echo "✅ Fat JAR built successfully ($(du -h flink-jobs/target/flink-jobs-1.0.0-SNAPSHOT.jar | cut -f1))"
echo ""

# Stop any existing Flink jobs
echo "📋 Cleanup:"
echo "-----------"
if pgrep -f "UnifiedMarketValueJob\|HoldingMarketValueJob\|OrderMarketValueJob" > /dev/null; then
    echo "🔄 Stopping existing Flink jobs..."
    pkill -f "UnifiedMarketValueJob" 2>/dev/null || true
    pkill -f "HoldingMarketValueJob" 2>/dev/null || true
    pkill -f "OrderMarketValueJob" 2>/dev/null || true
    sleep 3
fi

echo "✅ Ready to start Unified Flink job"
echo ""

# Start UnifiedMarketValueJob using fat JAR
echo "📋 Starting UnifiedMarketValueJob:"
echo "----------------------------------"
echo "🔧 Starting UnifiedMarketValueJob in background using fat JAR..."
echo "📊 This job ensures PRICE CONSISTENCY between holdings and orders"
echo "📈 Output topic: aggregation.unified-mv"
echo ""

nohup java $JAVA_17_ARGS \
    -cp flink-jobs/target/flink-jobs-1.0.0-SNAPSHOT.jar \
    com.viewserver.flink.UnifiedMarketValueJob \
    --kafka.bootstrap-servers localhost:9092 \
    --consumer.group-id flink-unified-mv-$(date +%s) \
    > flink-unified-mv.log 2>&1 &

UNIFIED_PID=$!
echo "🔄 UnifiedMarketValueJob started (PID: $UNIFIED_PID)"

# Wait a moment and check if it's still running
sleep 5
if kill -0 $UNIFIED_PID 2>/dev/null; then
    echo "✅ UnifiedMarketValueJob is running"
else
    echo "❌ UnifiedMarketValueJob failed to start. Check logs: flink-unified-mv.log"
    echo "📄 Last 10 lines of log:"
    tail -10 flink-unified-mv.log 2>/dev/null || echo "No log file found"
    exit 1
fi

echo ""

# Final status
echo "📋 Unified Flink Job Status:"
echo "----------------------------"
echo "✅ UnifiedMarketValueJob: Running (PID: $UNIFIED_PID)"
echo ""

echo "📊 Key Benefits:"
echo "  🎯 Price Consistency: Holdings and orders use identical prices"
echo "  ⚡ Real-time: Immediate calculation on price updates"
echo "  📈 Scalable: State partitioned by instrumentId"
echo "  🔄 Fault Tolerant: Checkpointed state for recovery"
echo ""

echo "📊 Monitoring:"
echo "  📄 Unified logs: tail -f flink-unified-mv.log"
echo "  🔍 Process status: ps aux | grep UnifiedMarketValueJob"
echo "  📈 Kafka topic: kafka-console-consumer --bootstrap-server localhost:9092 --topic aggregation.unified-mv"
echo ""

echo "🛑 To stop job:"
echo "  pkill -f UnifiedMarketValueJob"
echo "  or use: ./scripts/stop-all.sh"
echo ""

echo "🎉 Unified Market Value Flink job started successfully!"
echo "   Job will process holdings and orders with shared price state for consistency" 