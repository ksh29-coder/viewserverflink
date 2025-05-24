#!/bin/bash

# Initialize Complete Data Set
# This script triggers the generation of static data and SOD holdings via REST API
# The mock data generator must be running for this to work

MOCK_DATA_URL="http://localhost:8081"

echo "🔧 Initializing Complete Data Set"
echo "=================================="
echo "📡 Mock Data Generator: $MOCK_DATA_URL"
echo ""

# Check if mock data generator is running
echo "🔍 Checking mock data generator status..."
if ! curl -s "$MOCK_DATA_URL/api/data-generation/status" > /dev/null; then
    echo "❌ Mock data generator is not running!"
    echo "   Please start it first with: ./scripts/start-dynamic-data.sh"
    exit 1
fi

echo "✅ Mock data generator is running"
echo ""

# Initialize complete data set
echo "📊 Initializing static data + SOD holdings..."
response=$(curl -s -X POST "$MOCK_DATA_URL/api/data-generation/initialize" \
    -H "Content-Type: application/json")

if echo "$response" | jq -e '.status == "success"' > /dev/null 2>&1; then
    echo "✅ Data initialization successful!"
    echo "📋 Generated:"
    echo "   • 5 Accounts (fund strategies)"
    echo "   • 10 Instruments (7 equities + 3 cash)"
    echo "   • 35 SOD Holdings (5 accounts × 7 equities)"
    echo ""
    echo "🚀 System is ready! Dynamic data streams are now active."
else
    echo "❌ Data initialization failed!"
    echo "Response: $response"
    exit 1
fi

echo ""
echo "🌐 Available endpoints:"
echo "   • View Server APIs: http://localhost:8080/api/stats"
echo "   • Data Generation Control: http://localhost:8081/api/data-generation/status" 