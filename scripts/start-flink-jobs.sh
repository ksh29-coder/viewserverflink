#!/bin/bash

# Start Flink Jobs Script
# Starts Flink stream processing jobs with Java 17 support using fat JAR approach

set -e

echo "🚀 Starting Flink Jobs with Java 17 (Fat JAR Approach)"
echo "======================================================"
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
if pgrep -f "HoldingMarketValueJob\|OrderMarketValueJob" > /dev/null; then
    echo "🔄 Stopping existing Flink jobs..."
    pkill -f "HoldingMarketValueJob" 2>/dev/null || true
    pkill -f "OrderMarketValueJob" 2>/dev/null || true
    sleep 3
fi

echo "✅ Ready to start Flink jobs"
echo ""

# Start HoldingMarketValueJob using fat JAR
echo "📋 Starting HoldingMarketValueJob:"
echo "----------------------------------"
echo "🔧 Starting HoldingMarketValueJob in background using fat JAR..."

nohup java $JAVA_17_ARGS \
    -cp flink-jobs/target/flink-jobs-1.0.0-SNAPSHOT.jar \
    com.viewserver.flink.HoldingMarketValueJob \
    --kafka.bootstrap-servers localhost:9092 \
    --consumer.group-id flink-holding-mv-$(date +%s) \
    > flink-holding-mv.log 2>&1 &

HOLDING_PID=$!
echo "🔄 HoldingMarketValueJob started (PID: $HOLDING_PID)"

# Wait a moment and check if it's still running
sleep 5
if kill -0 $HOLDING_PID 2>/dev/null; then
    echo "✅ HoldingMarketValueJob is running"
else
    echo "❌ HoldingMarketValueJob failed to start. Check logs: flink-holding-mv.log"
    echo "📄 Last 10 lines of log:"
    tail -10 flink-holding-mv.log 2>/dev/null || echo "No log file found"
    exit 1
fi

echo ""

# Start OrderMarketValueJob using fat JAR
echo "📋 Starting OrderMarketValueJob:"
echo "--------------------------------"
echo "🔧 Starting OrderMarketValueJob in background using fat JAR..."

nohup java $JAVA_17_ARGS \
    -cp flink-jobs/target/flink-jobs-1.0.0-SNAPSHOT.jar \
    com.viewserver.flink.OrderMarketValueJob \
    --kafka.bootstrap-servers localhost:9092 \
    --consumer.group-id flink-order-mv-$(date +%s) \
    > flink-order-mv.log 2>&1 &

ORDER_PID=$!
echo "🔄 OrderMarketValueJob started (PID: $ORDER_PID)"

# Wait a moment and check if it's still running
sleep 5
if kill -0 $ORDER_PID 2>/dev/null; then
    echo "✅ OrderMarketValueJob is running"
else
    echo "❌ OrderMarketValueJob failed to start. Check logs: flink-order-mv.log"
    echo "📄 Last 10 lines of log:"
    tail -10 flink-order-mv.log 2>/dev/null || echo "No log file found"
    exit 1
fi

echo ""

# Final status
echo "📋 Flink Jobs Status:"
echo "---------------------"
echo "✅ HoldingMarketValueJob: Running (PID: $HOLDING_PID)"
echo "✅ OrderMarketValueJob: Running (PID: $ORDER_PID)"
echo ""

echo "📊 Monitoring:"
echo "  📄 HoldingMV logs: tail -f flink-holding-mv.log"
echo "  📄 OrderMV logs: tail -f flink-order-mv.log"
echo "  🔍 Process status: ps aux | grep -E 'HoldingMarketValueJob|OrderMarketValueJob'"
echo ""

echo "🛑 To stop jobs:"
echo "  pkill -f HoldingMarketValueJob"
echo "  pkill -f OrderMarketValueJob"
echo "  or use: ./scripts/stop-all.sh"
echo ""

echo "🎉 Flink jobs started successfully using fat JAR approach!"
echo "   Jobs will process data from Kafka topics and output to aggregation topics" 