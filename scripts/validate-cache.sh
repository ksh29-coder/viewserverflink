#!/bin/bash

# Complete Cache Validation Script
# Validates all cached data types at API and Redis levels

echo "🔍 COMPLETE CACHE VALIDATION"
echo "============================"
echo

# Check if services are running
if ! curl -s http://localhost:8080/api/health > /dev/null 2>&1; then
    echo "❌ View server is not running on port 8080"
    exit 1
fi

echo "✅ View server is running"
echo

# 1. CACHE STATISTICS OVERVIEW
echo "📊 CACHE STATISTICS"
echo "-------------------"
curl -s http://localhost:8080/api/stats | jq .
echo

# 2. API-LEVEL VALIDATION  
echo "🌐 API-LEVEL DATA VALIDATION"
echo "----------------------------"

echo "Accounts (5 expected):"
ACCOUNT_COUNT=$(curl -s http://localhost:8080/api/accounts | jq '. | length')
echo "  Count: $ACCOUNT_COUNT"
curl -s http://localhost:8080/api/accounts | jq '.[0] | {accountId, accountName}' 2>/dev/null || echo "  No sample data"
echo

echo "Instruments (10 expected):"
INSTRUMENT_COUNT=$(curl -s http://localhost:8080/api/instruments | jq '. | length')
echo "  Count: $INSTRUMENT_COUNT"
curl -s http://localhost:8080/api/instruments | jq '.[0] | {instrumentId, instrumentType, currency}' 2>/dev/null || echo "  No sample data"
echo

echo "Prices (7 expected):"
PRICE_COUNT=$(curl -s http://localhost:8080/api/prices | jq '. | length')
echo "  Count: $PRICE_COUNT"
curl -s http://localhost:8080/api/prices | jq '.[0] | {instrumentId, price, date}' 2>/dev/null || echo "  No sample data"
echo

echo "Holdings for ACC001:"
HOLDING_COUNT=$(curl -s http://localhost:8080/api/holdings/ACC001 | jq '. | length')
echo "  Count: $HOLDING_COUNT"
curl -s http://localhost:8080/api/holdings/ACC001 | jq '.[0] | {instrumentId, position, accountId}' 2>/dev/null || echo "  No sample data"
echo

echo "Orders:"
ORDER_COUNT=$(curl -s http://localhost:8080/api/orders | jq '. | length')
echo "  Count: $ORDER_COUNT"
curl -s http://localhost:8080/api/orders | jq '.[0] | {orderId, instrumentId, orderStatus, quantity}' 2>/dev/null || echo "  No sample data"
echo

echo "Cash movements for ACC001:"
CASH_COUNT=$(curl -s http://localhost:8080/api/cash/ACC001 | jq '. | length')
echo "  Count: $CASH_COUNT"
curl -s http://localhost:8080/api/cash/ACC001 | jq '.[0] | {accountId, quantity, movementType, date}' 2>/dev/null || echo "  No sample data"
echo

# 3. REDIS-LEVEL VALIDATION
echo "🔧 REDIS-LEVEL KEY VALIDATION"
echo "-----------------------------"

if ! docker exec viewserver-redis redis-cli ping > /dev/null 2>&1; then
    echo "❌ Redis is not accessible"
    exit 1
fi

echo "Redis key counts:"
echo "  Accounts: $(docker exec -i viewserver-redis redis-cli KEYS "accounts:*" | wc -l | tr -d ' ')"
echo "  Instruments: $(docker exec -i viewserver-redis redis-cli KEYS "instruments:*" | wc -l | tr -d ' ')"
echo "  Prices: $(docker exec -i viewserver-redis redis-cli KEYS "prices:*" | wc -l | tr -d ' ')"
echo "  Holdings: $(docker exec -i viewserver-redis redis-cli KEYS "holdings:*" | wc -l | tr -d ' ')"
echo "  Orders: $(docker exec -i viewserver-redis redis-cli KEYS "orders:*" | wc -l | tr -d ' ')"
echo "  Cash: $(docker exec -i viewserver-redis redis-cli KEYS "cash:*" | wc -l | tr -d ' ')"
echo

# 4. DATA FRESHNESS CHECK
echo "⏰ DATA FRESHNESS CHECK"
echo "----------------------"
echo "Latest price timestamp:"
curl -s http://localhost:8080/api/prices | jq -r 'max_by(.timestamp) | .timestamp' 2>/dev/null || echo "  No price data"

echo "Latest order timestamp:"
curl -s http://localhost:8080/api/orders | jq -r 'max_by(.timestamp) | .timestamp' 2>/dev/null || echo "  No order data"
echo

# 5. SAMPLE ACCOUNT PORTFOLIO
echo "💼 SAMPLE PORTFOLIO (ACC001)"
echo "----------------------------"
echo "Holdings:"
curl -s http://localhost:8080/api/holdings/ACC001 | jq -r '.[] | "  \(.instrumentId): \(.position) shares"' 2>/dev/null || echo "  No holdings"

echo "Cash movements:"
curl -s http://localhost:8080/api/cash/ACC001 | jq -r '.[] | "  \(.movementType): \(.quantity) \(.instrumentId)"' 2>/dev/null || echo "  No cash movements"
echo

# 6. VALIDATION SUMMARY
echo "✅ VALIDATION SUMMARY"
echo "--------------------"
TOTAL_CACHED_ITEMS=$((ACCOUNT_COUNT + INSTRUMENT_COUNT + PRICE_COUNT + HOLDING_COUNT + ORDER_COUNT + CASH_COUNT))
echo "Total cached items: $TOTAL_CACHED_ITEMS"

if [ $ACCOUNT_COUNT -eq 5 ] && [ $INSTRUMENT_COUNT -eq 10 ] && [ $PRICE_COUNT -eq 7 ]; then
    echo "✅ Static data looks good"
else
    echo "⚠️  Static data counts unexpected"
fi

if [ $HOLDING_COUNT -gt 0 ] && [ $ORDER_COUNT -gt 0 ] && [ $CASH_COUNT -gt 0 ]; then
    echo "✅ Dynamic data is present"
else
    echo "⚠️  Some dynamic data may be missing"
fi

echo
echo "🎯 Cache validation complete!" 