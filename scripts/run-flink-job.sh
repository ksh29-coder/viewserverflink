#!/bin/bash

# Run Flink HoldingMarketValue Job with KeyedState
# This script runs the standalone Flink job that processes holding market values using KeyedState

set -e

echo "🚀 Starting Flink HoldingMarketValue Job with KeyedState..."

# Configuration
KAFKA_BOOTSTRAP_SERVERS="localhost:9092"
CONSUMER_GROUP_ID="flink-holding-mv-keyed-state"
FLINK_JAR="flink-jobs/target/flink-jobs-1.0.0-SNAPSHOT.jar"

# Check if JAR exists
if [ ! -f "$FLINK_JAR" ]; then
    echo "❌ Error: Flink JAR not found at $FLINK_JAR"
    echo "Please build the project first: cd flink-jobs && mvn clean package"
    exit 1
fi

# Check if Kafka is running
echo "🔍 Checking Kafka connectivity..."
if ! nc -z localhost 9092; then
    echo "❌ Error: Kafka is not running on localhost:9092"
    echo "Please start Kafka first using: docker-compose up -d"
    exit 1
fi

echo "✅ Kafka is running"

echo "🔧 Running Flink job with:"
echo "  JAR: $FLINK_JAR"
echo "  Kafka: $KAFKA_BOOTSTRAP_SERVERS"
echo "  Consumer Group: $CONSUMER_GROUP_ID"
echo ""

echo "📊 Architecture: KeyedState-based processing"
echo "  - Holdings and Instruments stored in Flink state"
echo "  - Price updates trigger immediate market value calculations"
echo "  - No windowed joins - real-time processing!"
echo ""

# Run the Flink job
echo "🚀 Starting Flink job..."
java -cp "$FLINK_JAR" com.viewserver.flink.HoldingMarketValueJob \
    --kafka.bootstrap-servers "$KAFKA_BOOTSTRAP_SERVERS" \
    --consumer.group-id "$CONSUMER_GROUP_ID"

echo "✅ Flink job completed." 