#!/bin/bash

# Test Flink Implementation End-to-End
# This script tests the complete data flow from Mock Data Generator through Flink to View Server

set -e

echo "🧪 Testing Flink Implementation End-to-End"
echo "=========================================="

# Configuration
MOCK_DATA_URL="http://localhost:8081"
VIEW_SERVER_URL="http://localhost:8080"
KAFKA_BOOTSTRAP="localhost:9092"

echo ""
echo "📋 Pre-flight Checks"
echo "--------------------"

# Check if services are running
echo "Checking Mock Data Generator..."
if curl -s "$MOCK_DATA_URL/actuator/health" > /dev/null; then
    echo "✅ Mock Data Generator is running"
else
    echo "❌ Mock Data Generator is not running on port 8081"
    exit 1
fi

echo "Checking View Server..."
if curl -s "$VIEW_SERVER_URL/actuator/health" > /dev/null; then
    echo "✅ View Server is running"
else
    echo "❌ View Server is not running on port 8080"
    exit 1
fi

echo "Checking Kafka..."
if nc -z localhost 9092; then
    echo "✅ Kafka is running"
else
    echo "❌ Kafka is not running on port 9092"
    exit 1
fi

echo ""
echo "🔄 Testing Data Flow"
echo "--------------------"

# Initialize data
echo "Initializing static data..."
curl -s -X POST "$MOCK_DATA_URL/api/data/initialize" > /dev/null
echo "✅ Static data initialized"

# Wait for data to flow
echo "Waiting for data to flow through system..."
sleep 5

# Check base topics have data
echo "Checking base topics..."
HOLDING_COUNT=$(timeout 5 kafka-console-consumer --bootstrap-server $KAFKA_BOOTSTRAP --topic base.sod-holding --from-beginning --max-messages 1 2>/dev/null | wc -l || echo "0")
INSTRUMENT_COUNT=$(timeout 5 kafka-console-consumer --bootstrap-server $KAFKA_BOOTSTRAP --topic base.instrument --from-beginning --max-messages 1 2>/dev/null | wc -l || echo "0")
PRICE_COUNT=$(timeout 5 kafka-console-consumer --bootstrap-server $KAFKA_BOOTSTRAP --topic base.price --from-beginning --max-messages 1 2>/dev/null | wc -l || echo "0")

if [ "$HOLDING_COUNT" -gt 0 ]; then
    echo "✅ Holdings data flowing"
else
    echo "❌ No holdings data found"
fi

if [ "$INSTRUMENT_COUNT" -gt 0 ]; then
    echo "✅ Instruments data flowing"
else
    echo "❌ No instruments data found"
fi

if [ "$PRICE_COUNT" -gt 0 ]; then
    echo "✅ Prices data flowing"
else
    echo "❌ No prices data found"
fi

echo ""
echo "🚀 Running Flink Job (Background)"
echo "--------------------------------"

# Start Flink job in background
echo "Starting Flink job..."
./scripts/run-flink-job.sh &
FLINK_PID=$!
echo "✅ Flink job started (PID: $FLINK_PID)"

# Wait for Flink job to process data
echo "Waiting for Flink job to process data..."
sleep 15

echo ""
echo "🔍 Checking Results"
echo "------------------"

# Check aggregation topic
echo "Checking aggregation topic..."
AGG_COUNT=$(timeout 10 kafka-console-consumer --bootstrap-server $KAFKA_BOOTSTRAP --topic aggregation.holding-mv --from-beginning --max-messages 1 2>/dev/null | wc -l || echo "0")

if [ "$AGG_COUNT" -gt 0 ]; then
    echo "✅ Aggregation topic has data"
    
    # Sample aggregation data
    echo "Sample aggregation data:"
    timeout 5 kafka-console-consumer --bootstrap-server $KAFKA_BOOTSTRAP --topic aggregation.holding-mv --from-beginning --max-messages 1 2>/dev/null || echo "Could not fetch sample"
else
    echo "❌ No data in aggregation topic"
fi

# Check View Server API
echo ""
echo "Checking View Server API..."
HOLDINGS_MV_RESPONSE=$(curl -s "$VIEW_SERVER_URL/api/holdings-mv" || echo "[]")
HOLDINGS_MV_COUNT=$(echo "$HOLDINGS_MV_RESPONSE" | jq length 2>/dev/null || echo "0")

if [ "$HOLDINGS_MV_COUNT" -gt 0 ]; then
    echo "✅ View Server API returning $HOLDINGS_MV_COUNT holdings-mv records"
    
    # Show sample data
    echo "Sample HoldingMV record:"
    echo "$HOLDINGS_MV_RESPONSE" | jq '.[0]' 2>/dev/null || echo "Could not parse JSON"
else
    echo "❌ View Server API returning no holdings-mv data"
fi

# Check Redis cache
echo ""
echo "Checking Redis cache..."
REDIS_KEYS=$(redis-cli KEYS "holding-mv:*" 2>/dev/null | wc -l || echo "0")

if [ "$REDIS_KEYS" -gt 0 ]; then
    echo "✅ Redis cache has $REDIS_KEYS holding-mv keys"
else
    echo "❌ No holding-mv keys in Redis cache"
fi

echo ""
echo "🧹 Cleanup"
echo "----------"

# Stop Flink job
if kill -0 $FLINK_PID 2>/dev/null; then
    echo "Stopping Flink job..."
    kill $FLINK_PID
    echo "✅ Flink job stopped"
fi

echo ""
echo "📊 Test Summary"
echo "==============="

if [ "$HOLDING_COUNT" -gt 0 ] && [ "$INSTRUMENT_COUNT" -gt 0 ] && [ "$PRICE_COUNT" -gt 0 ] && [ "$AGG_COUNT" -gt 0 ] && [ "$HOLDINGS_MV_COUNT" -gt 0 ]; then
    echo "🎉 SUCCESS: End-to-end test passed!"
    echo "   - Base data flowing: ✅"
    echo "   - Flink processing: ✅"
    echo "   - Aggregation output: ✅"
    echo "   - View Server API: ✅"
    exit 0
else
    echo "❌ FAILURE: End-to-end test failed!"
    echo "   - Base data flowing: $([ "$HOLDING_COUNT" -gt 0 ] && [ "$INSTRUMENT_COUNT" -gt 0 ] && [ "$PRICE_COUNT" -gt 0 ] && echo "✅" || echo "❌")"
    echo "   - Flink processing: $([ "$AGG_COUNT" -gt 0 ] && echo "✅" || echo "❌")"
    echo "   - View Server API: $([ "$HOLDINGS_MV_COUNT" -gt 0 ] && echo "✅" || echo "❌")"
    exit 1
fi 