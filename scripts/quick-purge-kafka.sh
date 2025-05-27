#!/bin/bash

# Quick Kafka Purge (Development)
# Fast purge for development - no confirmations, minimal output

set -e

echo "🗑️  Quick Kafka Purge (Development Mode)"
echo "========================================"

# Check if Kafka is running
if ! docker exec viewserver-kafka kafka-topics --bootstrap-server localhost:9092 --list > /dev/null 2>&1; then
    echo "❌ Error: Kafka is not running"
    echo "Please start Kafka first: docker-compose up -d"
    exit 1
fi

# Stop consumers quickly
echo "🛑 Stopping consumers..."
pkill -f "ViewServerApplication\|mock-data-generator\|UnifiedMarketValueJob" 2>/dev/null || true
sleep 2

# Get topics
TOPICS=$(docker exec viewserver-kafka kafka-topics --bootstrap-server localhost:9092 --list 2>/dev/null | grep -v "^$")

if [ -z "$TOPICS" ]; then
    echo "ℹ️  No topics to purge"
    exit 0
fi

echo "🗑️  Purging topics..."

# Quick method: Set retention to 1ms, wait, then reset
echo "$TOPICS" | while read topic; do
    if [ -n "$topic" ]; then
        echo "  🔄 $topic"
        # Set very short retention
        docker exec viewserver-kafka kafka-configs --bootstrap-server localhost:9092 --entity-type topics --entity-name "$topic" --alter --add-config retention.ms=1 > /dev/null 2>&1 || true
    fi
done

# Wait for cleanup
echo "⏳ Waiting for cleanup..."
sleep 5

# Reset retention to default
echo "$TOPICS" | while read topic; do
    if [ -n "$topic" ]; then
        # Reset to default retention (7 days)
        docker exec viewserver-kafka kafka-configs --bootstrap-server localhost:9092 --entity-type topics --entity-name "$topic" --alter --add-config retention.ms=604800000 > /dev/null 2>&1 || true
    fi
done

# Reset consumer offsets
echo "🔄 Resetting consumer offsets..."
CONSUMER_GROUPS=$(docker exec viewserver-kafka kafka-consumer-groups --bootstrap-server localhost:9092 --list 2>/dev/null | grep -v "^$")

if [ -n "$CONSUMER_GROUPS" ]; then
    echo "$CONSUMER_GROUPS" | while read group; do
        if [ -n "$group" ]; then
            docker exec viewserver-kafka kafka-consumer-groups --bootstrap-server localhost:9092 --group "$group" --reset-offsets --to-earliest --all-topics --execute > /dev/null 2>&1 || true
        fi
    done
fi

echo "✅ Quick purge complete!"
echo ""
echo "📝 Ready to restart services:"
echo "  ./scripts/start-all.sh" 