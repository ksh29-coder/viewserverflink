#!/bin/bash

# Show Kafka Data
# This script displays current Kafka topics, message counts, and consumer groups

echo "📊 Kafka Data Overview"
echo "======================"
echo ""

# Check if Kafka is running
if ! docker exec viewserver-kafka kafka-topics --bootstrap-server localhost:9092 --list > /dev/null 2>&1; then
    echo "❌ Error: Kafka is not running"
    echo "Please start Kafka first: docker-compose up -d"
    exit 1
fi

echo "✅ Kafka is running"
echo ""

# Show topics and their details
echo "📋 Topics:"
echo "----------"
TOPICS=$(docker exec viewserver-kafka kafka-topics --bootstrap-server localhost:9092 --list 2>/dev/null | grep -v "^$" | sort)

if [ -z "$TOPICS" ]; then
    echo "ℹ️  No topics found"
else
    echo "$TOPICS" | while read topic; do
        if [ -n "$topic" ]; then
            # Get topic details
            DETAILS=$(docker exec viewserver-kafka kafka-topics --bootstrap-server localhost:9092 --describe --topic "$topic" 2>/dev/null)
            PARTITIONS=$(echo "$DETAILS" | grep "PartitionCount" | awk '{print $4}' || echo "?")
            REPLICATION=$(echo "$DETAILS" | grep "ReplicationFactor" | awk '{print $6}' || echo "?")
            
            # Get approximate message count
            MESSAGE_COUNT=$(docker exec viewserver-kafka kafka-run-class kafka.tools.GetOffsetShell --broker-list localhost:9092 --topic "$topic" --time -1 2>/dev/null | awk -F: '{sum += $3} END {print sum+0}' || echo "?")
            
            echo "  📄 $topic"
            echo "     └─ Partitions: $PARTITIONS, Replication: $REPLICATION, Messages: ~$MESSAGE_COUNT"
        fi
    done
fi

echo ""

# Show consumer groups
echo "👥 Consumer Groups:"
echo "-------------------"
CONSUMER_GROUPS=$(docker exec viewserver-kafka kafka-consumer-groups --bootstrap-server localhost:9092 --list 2>/dev/null | grep -v "^$" | sort)

if [ -z "$CONSUMER_GROUPS" ]; then
    echo "ℹ️  No consumer groups found"
else
    echo "$CONSUMER_GROUPS" | while read group; do
        if [ -n "$group" ]; then
            echo "  👤 $group"
            
            # Get group details
            GROUP_DETAILS=$(docker exec viewserver-kafka kafka-consumer-groups --bootstrap-server localhost:9092 --describe --group "$group" 2>/dev/null)
            
            if [ -n "$GROUP_DETAILS" ]; then
                # Show lag information
                echo "$GROUP_DETAILS" | tail -n +2 | while read line; do
                    if [ -n "$line" ]; then
                        TOPIC=$(echo "$line" | awk '{print $1}')
                        PARTITION=$(echo "$line" | awk '{print $2}')
                        CURRENT_OFFSET=$(echo "$line" | awk '{print $3}')
                        LOG_END_OFFSET=$(echo "$line" | awk '{print $4}')
                        LAG=$(echo "$line" | awk '{print $5}')
                        
                        if [ "$TOPIC" != "TOPIC" ] && [ -n "$TOPIC" ]; then
                            echo "     └─ $TOPIC[$PARTITION]: offset=$CURRENT_OFFSET, end=$LOG_END_OFFSET, lag=$LAG"
                        fi
                    fi
                done
            fi
        fi
    done
fi

echo ""

# Show disk usage
echo "💾 Storage Usage:"
echo "-----------------"
KAFKA_SIZE=$(docker exec viewserver-kafka du -sh /var/lib/kafka/data 2>/dev/null | awk '{print $1}' || echo "Unknown")
echo "  📁 Kafka data directory: $KAFKA_SIZE"

# Show volume usage
VOLUME_SIZE=$(docker system df -v 2>/dev/null | grep "viewserverflink_kafka-data" | awk '{print $3}' || echo "Unknown")
echo "  💿 Docker volume: $VOLUME_SIZE"

echo ""

# Summary
TOPIC_COUNT=$(echo "$TOPICS" | grep -v "^$" | wc -l)
GROUP_COUNT=$(echo "$CONSUMER_GROUPS" | grep -v "^$" | wc -l)
TOTAL_MESSAGES=$(echo "$TOPICS" | while read topic; do
    if [ -n "$topic" ]; then
        docker exec viewserver-kafka kafka-run-class kafka.tools.GetOffsetShell --broker-list localhost:9092 --topic "$topic" --time -1 2>/dev/null | awk -F: '{sum += $3} END {print sum+0}' || echo "0"
    fi
done | awk '{sum += $1} END {print sum+0}')

echo "📈 Summary:"
echo "-----------"
echo "  📋 Topics: $TOPIC_COUNT"
echo "  👥 Consumer Groups: $GROUP_COUNT"
echo "  📨 Total Messages: ~$TOTAL_MESSAGES"
echo "  💾 Storage: $KAFKA_SIZE"

echo ""
echo "🗑️  To purge all data:"
echo "  ./scripts/purge-kafka-data.sh      (safe with confirmation)"
echo "  ./scripts/quick-purge-kafka.sh     (fast for development)" 