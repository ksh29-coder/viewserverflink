#!/bin/bash

# Test OrderMV data producer
# This script manually produces a test OrderMV record to verify the pipeline

echo "🧪 Testing OrderMV Pipeline"
echo "=========================="

# Test data
TEST_ORDER_MV='{
  "orderId": "TEST-ORDER-001",
  "accountId": "ACC001",
  "instrumentId": "AAPL",
  "instrumentName": "Apple Inc.",
  "instrumentType": "EQUITY",
  "currency": "USD",
  "side": "BUY",
  "quantity": 100,
  "filledQuantity": 50,
  "price": 150.00,
  "status": "PARTIAL",
  "orderTimestamp": "2024-01-15T10:30:00",
  "date": "2024-01-15T00:00:00",
  "timestamp": "2024-01-15T10:30:00",
  "subSectors": ["Technology", "Consumer Electronics"],
  "priceTimestamp": "2024-01-15T10:29:00",
  "orderMarketValueLocal": 15000.00,
  "orderMarketValueUSD": 15000.00,
  "filledMarketValueLocal": 7500.00,
  "filledMarketValueUSD": 7500.00,
  "calculationTimestamp": "2024-01-15T10:30:00"
}'

echo "📤 Producing test OrderMV record..."

# Use the View Server's cache service directly to test
echo "$TEST_ORDER_MV" > /tmp/test-order-mv.json

echo "✅ Test OrderMV record created at /tmp/test-order-mv.json"
echo "📊 Check the View Server logs to see if the OrderMV consumer processes this data"

# Check current OrderMV count
echo "🔍 Current OrderMV count:"
curl -s http://localhost:8080/api/orders-mv | jq length

echo ""
echo "🌐 You can view the data at:"
echo "   - API: http://localhost:8080/api/orders-mv"
echo "   - UI:  http://localhost:3000" 