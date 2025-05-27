#!/bin/bash

echo "🚀 Starting UnifiedMarketValueJob..."

# Set JAVA_HOME if needed
export JAVA_HOME=${JAVA_HOME:-/opt/homebrew/Cellar/openjdk@17/17.0.15/libexec/openjdk.jdk/Contents/Home}

# Build classpath
CLASSPATH="target/classes"
for jar in $(find ~/.m2/repository -name "*.jar" | grep -E "(flink|kafka|jackson|lombok|slf4j)" | head -50); do
    CLASSPATH="$CLASSPATH:$jar"
done

# Add project dependencies
CLASSPATH="$CLASSPATH:../data-layer/target/classes"
CLASSPATH="$CLASSPATH:../mv-calc/target/classes"

echo "Running UnifiedMarketValueJob..."
java -cp "$CLASSPATH" com.viewserver.flink.UnifiedMarketValueJob --kafka.bootstrap-servers localhost:9092 