#!/bin/bash

# Simple Flink Test Script
# Tests if Flink jobs can run with Java 17 and proper JVM module arguments

set -e

echo "🧪 Testing Flink Job Startup with Java 17"
echo "=========================================="
echo ""

# Check Java version
echo "📋 Java Version Check:"
echo "----------------------"
java -version
echo ""

# Java 17 Module System Arguments for Flink
JAVA_17_ARGS="--add-opens java.base/java.util=ALL-UNNAMED \
--add-opens java.base/java.lang=ALL-UNNAMED \
--add-opens java.base/java.lang.invoke=ALL-UNNAMED \
--add-opens java.base/java.lang.reflect=ALL-UNNAMED \
--add-opens java.base/java.io=ALL-UNNAMED \
--add-opens java.base/java.net=ALL-UNNAMED \
--add-opens java.base/java.nio=ALL-UNNAMED \
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

echo "📋 Compilation Check:"
echo "---------------------"
echo "🔧 Building flink-jobs module..."
mvn clean compile -pl flink-jobs -q

if [ $? -ne 0 ]; then
    echo "❌ Compilation failed! Please check compilation errors."
    exit 1
fi

echo "✅ Compilation successful"
echo ""

echo "📋 Flink Job Test:"
echo "------------------"
echo "🔧 Testing Flink job startup (will run for 10 seconds)..."
echo "   This will test if Flink can start without Java 17 module errors"
echo ""

# Set JVM arguments
export MAVEN_OPTS="$JAVA_17_ARGS"

# Start the job in background
mvn exec:java -pl flink-jobs \
    -Dexec.mainClass="com.viewserver.flink.UnifiedMarketValueJob" \
    -Dexec.args="--kafka.bootstrap-servers localhost:9092" \
    > flink-test.log 2>&1 &

FLINK_PID=$!

echo "🔄 Flink job started (PID: $FLINK_PID)"
echo "⏳ Waiting 10 seconds to check for startup errors..."

# Wait for 10 seconds
sleep 10

# Check if the process is still running (good sign)
if kill -0 $FLINK_PID 2>/dev/null; then
    echo "✅ Flink job is running successfully!"
    echo "🛑 Stopping test job..."
    kill $FLINK_PID 2>/dev/null || true
    sleep 2
    # Force kill if still running
    kill -9 $FLINK_PID 2>/dev/null || true
else
    echo "⚠️  Flink job stopped. Checking logs..."
fi

echo ""
echo "📋 Log Analysis:"
echo "----------------"

# Wait a moment for log to be written
sleep 1

# Check for common Java 17 module errors
if [ -f flink-test.log ]; then
    if grep -q "InaccessibleObjectException" flink-test.log; then
        echo "❌ Java 17 module access error found!"
        echo "   Need to add more --add-opens arguments"
        grep "InaccessibleObjectException" flink-test.log | head -3
    elif grep -q "Exception" flink-test.log; then
        echo "⚠️  Other exceptions found in logs:"
        grep "Exception" flink-test.log | head -5
    elif grep -q "Starting Flink\|StreamExecutionEnvironment" flink-test.log; then
        echo "✅ Flink started successfully!"
        echo "✅ No Java 17 module system errors detected"
    else
        echo "ℹ️  Job may have started but no clear success indicators"
    fi
else
    echo "⚠️  No log file found"
fi

echo ""
echo "📄 Last 15 lines of log:"
echo "------------------------"
if [ -f flink-test.log ]; then
    tail -15 flink-test.log
else
    echo "No log file available"
fi

echo ""
echo "📋 Test Summary:"
echo "----------------"
if [ -f flink-test.log ] && ! grep -q "InaccessibleObjectException" flink-test.log; then
    echo "✅ Java 17 compatibility test PASSED"
    echo "   Flink jobs should work with current configuration"
else
    echo "❌ Java 17 compatibility test FAILED"
    echo "   Need to adjust JVM arguments or consider Java 11"
fi

echo ""
echo "📝 Full log saved to: flink-test.log"
echo "🔧 To run manually: MAVEN_OPTS=\"$JAVA_17_ARGS\" mvn exec:java -pl flink-jobs -Dexec.mainClass=\"com.viewserver.flink.UnifiedMarketValueJob\"" 