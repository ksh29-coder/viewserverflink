#!/bin/bash

# Purge Kafka Data
# This script removes all historical messages from Kafka topics and resets consumer offsets
# WARNING: This is destructive and cannot be undone!

set -e

echo "🗑️  Kafka Data Purge Script"
echo "=========================="
echo ""
echo "⚠️  WARNING: This will permanently delete ALL Kafka messages!"
echo "   - All topic data will be lost"
echo "   - Consumer offsets will be reset"
echo "   - This action cannot be undone"
echo ""

# Safety confirmation
read -p "Are you sure you want to purge all Kafka data? (type 'YES' to confirm): " confirmation
if [ "$confirmation" != "YES" ]; then
    echo "❌ Operation cancelled"
    exit 0
fi

echo ""
echo "🔍 Checking prerequisites..."

# Check if Kafka is running
if ! docker exec viewserver-kafka kafka-topics --bootstrap-server localhost:9092 --list > /dev/null 2>&1; then
    echo "❌ Error: Kafka is not running"
    echo "Please start Kafka first: docker-compose up -d"
    exit 1
fi

echo "✅ Kafka is running"
echo ""

# Stop all consumers first to prevent conflicts
echo "🛑 Step 1: Stopping all consumers..."
./scripts/stop-all.sh > /dev/null 2>&1 || true
sleep 5

echo "✅ All consumers stopped"
echo ""

# Get list of topics
echo "🔍 Step 2: Discovering Kafka topics..."
TOPICS=$(docker exec viewserver-kafka kafka-topics --bootstrap-server localhost:9092 --list 2>/dev/null | grep -v "^$" | sort)

if [ -z "$TOPICS" ]; then
    echo "ℹ️  No topics found to purge"
    exit 0
fi

echo "📋 Found topics to purge:"
echo "$TOPICS" | while read topic; do
    echo "  - $topic"
done
echo ""

# Method 1: Delete and recreate topics (fastest and most complete)
echo "🗑️  Step 3: Deleting and recreating topics..."

echo "$TOPICS" | while read topic; do
    if [ -n "$topic" ]; then
        echo "  🔄 Processing topic: $topic"
        
        # Get topic configuration before deletion
        PARTITIONS=$(docker exec viewserver-kafka kafka-topics --bootstrap-server localhost:9092 --describe --topic "$topic" 2>/dev/null | grep "PartitionCount" | awk '{print $4}' || echo "12")
        REPLICATION=$(docker exec viewserver-kafka kafka-topics --bootstrap-server localhost:9092 --describe --topic "$topic" 2>/dev/null | grep "ReplicationFactor" | awk '{print $6}' || echo "1")
        
        # Delete topic
        docker exec viewserver-kafka kafka-topics --bootstrap-server localhost:9092 --delete --topic "$topic" > /dev/null 2>&1 || true
        
        # Wait a moment for deletion to complete
        sleep 2
        
        # Recreate topic with same configuration
        docker exec viewserver-kafka kafka-topics --bootstrap-server localhost:9092 --create --topic "$topic" --partitions "$PARTITIONS" --replication-factor "$REPLICATION" > /dev/null 2>&1 || true
        
        echo "    ✅ Recreated: $topic (partitions: $PARTITIONS, replication: $REPLICATION)"
    fi
done

echo ""

# Method 2: Reset consumer group offsets (backup method)
echo "🔄 Step 4: Resetting consumer group offsets..."

# Get list of consumer groups
CONSUMER_GROUPS=$(docker exec viewserver-kafka kafka-consumer-groups --bootstrap-server localhost:9092 --list 2>/dev/null | grep -v "^$" | sort)

if [ -n "$CONSUMER_GROUPS" ]; then
    echo "$CONSUMER_GROUPS" | while read group; do
        if [ -n "$group" ]; then
            echo "  🔄 Resetting consumer group: $group"
            docker exec viewserver-kafka kafka-consumer-groups --bootstrap-server localhost:9092 --group "$group" --reset-offsets --to-earliest --all-topics --execute > /dev/null 2>&1 || true
            echo "    ✅ Reset: $group"
        fi
    done
else
    echo "ℹ️  No consumer groups found"
fi

echo ""

# Verification
echo "🔍 Step 5: Verification..."

# Check topics exist and are empty
echo "📊 Topic status after purge:"
echo "$TOPICS" | while read topic; do
    if [ -n "$topic" ]; then
        # Check if topic exists
        if docker exec viewserver-kafka kafka-topics --bootstrap-server localhost:9092 --describe --topic "$topic" > /dev/null 2>&1; then
            # Get message count (this is approximate)
            MESSAGE_COUNT=$(docker exec viewserver-kafka kafka-run-class kafka.tools.GetOffsetShell --broker-list localhost:9092 --topic "$topic" --time -1 2>/dev/null | awk -F: '{sum += $3} END {print sum+0}' || echo "0")
            echo "  ✅ $topic: $MESSAGE_COUNT messages"
        else
            echo "  ❌ $topic: Topic not found"
        fi
    fi
done

echo ""

# Optional: Clean up log segments (more thorough)
echo "🧹 Step 6: Cleaning up log segments..."
docker exec viewserver-kafka find /var/lib/kafka/data -name "*.log" -delete 2>/dev/null || true
docker exec viewserver-kafka find /var/lib/kafka/data -name "*.index" -delete 2>/dev/null || true
docker exec viewserver-kafka find /var/lib/kafka/data -name "*.timeindex" -delete 2>/dev/null || true

echo "✅ Log segments cleaned"
echo ""

# Restart Kafka to ensure clean state
echo "🔄 Step 7: Restarting Kafka for clean state..."
docker-compose restart kafka > /dev/null 2>&1

# Wait for Kafka to be ready
echo "⏳ Waiting for Kafka to restart..."
sleep 15

# Verify Kafka is responsive
while ! docker exec viewserver-kafka kafka-topics --bootstrap-server localhost:9092 --list > /dev/null 2>&1; do
    echo "  ⏳ Waiting for Kafka to be ready..."
    sleep 5
done

echo "✅ Kafka restarted successfully"
echo ""

# Final verification
echo "🎉 Purge Complete!"
echo "=================="
echo ""
echo "📊 Final Status:"

# Show topics
FINAL_TOPICS=$(docker exec viewserver-kafka kafka-topics --bootstrap-server localhost:9092 --list 2>/dev/null | grep -v "^$" | wc -l)
echo "  📋 Topics available: $FINAL_TOPICS"

# Show consumer groups
FINAL_GROUPS=$(docker exec viewserver-kafka kafka-consumer-groups --bootstrap-server localhost:9092 --list 2>/dev/null | grep -v "^$" | wc -l)
echo "  👥 Consumer groups: $FINAL_GROUPS"

echo ""
echo "✅ All Kafka data has been purged successfully!"
echo ""
echo "📝 Next steps:"
echo "  1. Start services: ./scripts/start-all.sh"
echo "  2. Initialize data: ./scripts/initialize-data.sh"
echo "  3. Check status: ./scripts/system-status.sh"
echo ""
echo "⚠️  Note: You'll need to restart your applications to begin producing/consuming data again." 