#!/bin/bash

# Test Unified Flink Job Script
# Tests the unified market value Flink job by monitoring its output

set -e

echo "🧪 Testing Unified Market Value Flink Job"
echo "========================================="
echo ""

# Check if infrastructure is running
echo "📋 Prerequisites Check:"
echo "-----------------------"

if ! docker exec viewserver-kafka kafka-topics --bootstrap-server localhost:9092 --list > /dev/null 2>&1; then
    echo "❌ Kafka is not running. Please start infrastructure first:"
    echo "   docker-compose up -d"
    exit 1
fi

echo "✅ Kafka is running"
echo ""

# Check if unified topic exists
echo "🔍 Checking unified topic..."
if docker exec viewserver-kafka kafka-topics --bootstrap-server localhost:9092 --list | grep -q "aggregation.unified-mv"; then
    echo "✅ Topic 'aggregation.unified-mv' exists"
else
    echo "📝 Creating topic 'aggregation.unified-mv'..."
    docker exec viewserver-kafka kafka-topics --bootstrap-server localhost:9092 --create --topic aggregation.unified-mv --partitions 1 --replication-factor 1
    echo "✅ Topic created"
fi

echo ""

# Check if unified job is running
echo "🔍 Checking if UnifiedMarketValueJob is running..."
if pgrep -f "UnifiedMarketValueJob" > /dev/null; then
    UNIFIED_PID=$(pgrep -f "UnifiedMarketValueJob")
    echo "✅ UnifiedMarketValueJob is running (PID: $UNIFIED_PID)"
else
    echo "❌ UnifiedMarketValueJob is not running"
    echo "💡 Start it with: ./scripts/start-unified-flink-job.sh"
    exit 1
fi

echo ""

# Monitor the unified topic for 30 seconds
echo "📊 Monitoring unified topic for 30 seconds..."
echo "   Topic: aggregation.unified-mv"
echo "   Looking for both HOLDING and ORDER records with consistent prices"
echo ""
echo "🔄 Press Ctrl+C to stop monitoring early"
echo ""

timeout 30s docker exec viewserver-kafka kafka-console-consumer \
    --bootstrap-server localhost:9092 \
    --topic aggregation.unified-mv \
    --from-beginning \
    --property print.timestamp=true \
    --property print.key=true \
    --property key.separator=" | " 2>/dev/null || true

echo ""
echo "📊 Test completed!"
echo ""

# Check recent log entries
echo "📄 Recent UnifiedMarketValueJob log entries:"
echo "--------------------------------------------"
if [ -f "flink-unified-mv.log" ]; then
    echo "Last 10 lines from flink-unified-mv.log:"
    tail -10 flink-unified-mv.log
else
    echo "❌ Log file not found: flink-unified-mv.log"
fi

echo ""
echo "🎯 What to look for:"
echo "  ✅ Records with recordType: 'HOLDING' and 'ORDER'"
echo "  ✅ Same instrumentId should have identical price and priceTimestamp"
echo "  ✅ Both holdings and orders calculated with same price"
echo "  ✅ Log entries showing 'HOLDING MV:' and 'ORDER MV:' calculations"
echo ""

echo "📈 Next steps:"
echo "  🔍 Monitor logs: tail -f flink-unified-mv.log"
echo "  📊 View all data: ./scripts/show-kafka-data.sh"
echo "  🛑 Stop job: pkill -f UnifiedMarketValueJob" 