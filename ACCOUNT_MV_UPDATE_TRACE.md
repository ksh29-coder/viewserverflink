# Account Market Value Update Trace - Approach 1

## Scenario: AAPL Price Update Impact on Account ACC001

### Initial System State (Before Price Update)

#### Flink State (keyed by instrumentId = "AAPL")
```java
// AAPL partition state
priceState = Price{
    instrumentId: "AAPL",
    price: 150.00,
    date: "2024-01-15T09:55:00.000"
}

holdingsState = [
    SODHolding{accountId: "ACC001", position: 100, instrumentId: "AAPL"},
    SODHolding{accountId: "ACC002", position: 200, instrumentId: "AAPL"},
    SODHolding{accountId: "ACC003", position: 50, instrumentId: "AAPL"}
]

ordersState = [
    Order{accountId: "ACC001", orderQuantity: 20, instrumentId: "AAPL", orderStatus: "NEW"},
    Order{accountId: "ACC002", orderQuantity: -30, instrumentId: "AAPL", orderStatus: "PARTIAL"},
    Order{accountId: "ACC003", orderQuantity: 10, instrumentId: "AAPL", orderStatus: "NEW"}
]

instrumentState = Instrument{
    instrumentId: "AAPL",
    instrumentName: "Apple Inc",
    instrumentType: "EQUITY",
    currency: "USD"
}
```

#### Current Account NAV (ACC001)
```
Holdings Market Value:
- AAPL: 100 shares × $150 = $15,000
- MSFT: 50 shares × $300 = $15,000
- Total Holdings: $30,000

Orders Market Value:
- AAPL: 20 shares × $150 = $3,000 (unfilled buy order)
- MSFT: -10 shares × $300 = $3,000 (unfilled sell order)
- Total Orders: $6,000

Account Total NAV: $30,000 + $6,000 = $36,000
```

---

## Price Update Event

### Event: AAPL Price Changes
```json
{
    "instrumentId": "AAPL",
    "price": 151.50,
    "currency": "USD",
    "source": "MARKET_DATA",
    "date": "2024-01-15T10:00:01.200"
}
```

---

## Step-by-Step Processing

### Step 1: Kafka Message Routing
```
Kafka Topic: base.price
Message Key: "AAPL"
Message Value: {"instrumentId":"AAPL","price":151.50,"date":"2024-01-15T10:00:01.200"}

↓ (Kafka partitioning by instrumentId)

Flink UnifiedMarketValueJob
Operator Instance: AAPL partition (all AAPL data goes here)
```

### Step 2: Flink Processing - Price Update
```java
// UnifiedMarketValueProcessor.processElement()
String taggedData = "PRICE:{\"instrumentId\":\"AAPL\",\"price\":151.50,\"date\":\"2024-01-15T10:00:01.200\"}"

// Parse and route to processPrice()
Price newPrice = Price{
    instrumentId: "AAPL",
    price: 151.50,
    date: "2024-01-15T10:00:01.200"
}

// Update shared state
priceState.update(newPrice);
```

### Step 3: Retrieve State and Calculate Holdings
```java
// Get all AAPL holdings from state
List<SODHolding> holdings = holdingsState.value();

for (SODHolding holding : holdings) {
    if ("ACC001".equals(holding.getAccountId())) {
        
        // Calculate new market value
        HoldingMV newHoldingMV = HoldingMV{
            accountId: "ACC001",
            instrumentId: "AAPL",
            position: 100,
            price: 151.50,                    // NEW PRICE
            priceTimestamp: "2024-01-15T10:00:01.200",
            marketValueUSD: 15150.00,         // 100 × $151.50
            calculationTimestamp: "2024-01-15T10:00:01.200"
        }
        
        // Emit unified result
        UnifiedMarketValue output = UnifiedMarketValue{
            type: "HOLDING",
            instrumentId: "AAPL",
            accountId: "ACC001",
            priceTimestamp: "2024-01-15T10:00:01.200",
            price: 151.50,
            holdingMV: newHoldingMV,
            orderMV: null
        }
        
        out.collect(output);
    }
}
```

### Step 4: Calculate Orders (Same Price!)
```java
// Get all AAPL orders from state
List<Order> orders = ordersState.value();

for (Order order : orders) {
    if ("ACC001".equals(order.getAccountId())) {
        
        // Calculate new market value
        OrderMV newOrderMV = OrderMV{
            accountId: "ACC001",
            instrumentId: "AAPL",
            orderQuantity: 20,
            price: 151.50,                    // SAME PRICE AS HOLDING
            priceTimestamp: "2024-01-15T10:00:01.200", // SAME TIMESTAMP
            orderMarketValueUSD: 3030.00,     // 20 × $151.50
            calculationTimestamp: "2024-01-15T10:00:01.200"
        }
        
        // Emit unified result
        UnifiedMarketValue output = UnifiedMarketValue{
            type: "ORDER",
            instrumentId: "AAPL", 
            accountId: "ACC001",
            priceTimestamp: "2024-01-15T10:00:01.200",
            price: 151.50,
            holdingMV: null,
            orderMV: newOrderMV
        }
        
        out.collect(output);
    }
}
```

### Step 5: Kafka Output
```
Topic: aggregation.unified-mv

Message 1:
Key: "ACC001"
Value: {
    "type": "HOLDING",
    "accountId": "ACC001",
    "instrumentId": "AAPL",
    "priceTimestamp": "2024-01-15T10:00:01.200",
    "price": 151.50,
    "holdingMV": {
        "accountId": "ACC001",
        "instrumentId": "AAPL",
        "position": 100,
        "marketValueUSD": 15150.00
    }
}

Message 2:
Key: "ACC001"  
Value: {
    "type": "ORDER",
    "accountId": "ACC001", 
    "instrumentId": "AAPL",
    "priceTimestamp": "2024-01-15T10:00:01.200",
    "price": 151.50,
    "orderMV": {
        "accountId": "ACC001",
        "instrumentId": "AAPL",
        "orderQuantity": 20,
        "orderMarketValueUSD": 3030.00
    }
}
```

### Step 6: Account Overview Aggregation (Kafka Streams)
```java
// Kafka Streams processes unified-mv topic
// Filters to selected accounts and aggregates by accountId

KTable<String, AccountNAV> accountNAVTable = unifiedMVStream
    .filter((key, value) -> "ACC001".equals(value.getAccountId()))
    .groupBy((key, value) -> value.getAccountId())
    .aggregate(
        () -> new AccountNAV(),
        (accountId, unifiedMV, currentNAV) -> {
            
            if ("HOLDING".equals(unifiedMV.getType())) {
                // Update AAPL holding
                currentNAV.updateHolding("AAPL", 15150.00); // Was 15000.00
                
            } else if ("ORDER".equals(unifiedMV.getType())) {
                // Update AAPL order
                currentNAV.updateOrder("AAPL", 3030.00);    // Was 3000.00
            }
            
            return currentNAV;
        }
    );

// Result for ACC001:
AccountNAV{
    accountId: "ACC001",
    holdings: {
        "AAPL": 15150.00,  // Updated +$150
        "MSFT": 15000.00   // Unchanged
    },
    orders: {
        "AAPL": 3030.00,   // Updated +$30
        "MSFT": 3000.00    // Unchanged  
    },
    totalHoldings: 30150.00,
    totalOrders: 6030.00,
    totalNAV: 36180.00     // Was 36000.00, now +$180
}
```

### Step 7: Change Detection and WebSocket Update
```java
// Kafka Streams change detection
accountNAVTable.toStream()
    .filter((accountId, newNAV) -> hasChanged(newNAV, previousNAV))
    .foreach((accountId, updatedNAV) -> {
        
        // Create incremental update
        AccountNAVUpdate update = AccountNAVUpdate{
            accountId: "ACC001",
            timestamp: "2024-01-15T10:00:01.200",
            changes: [
                {
                    field: "holdings.AAPL",
                    oldValue: 15000.00,
                    newValue: 15150.00,
                    change: +150.00
                },
                {
                    field: "orders.AAPL", 
                    oldValue: 3000.00,
                    newValue: 3030.00,
                    change: +30.00
                },
                {
                    field: "totalNAV",
                    oldValue: 36000.00,
                    newValue: 36180.00,
                    change: +180.00
                }
            ]
        }
        
        // Send to WebSocket
        webSocketService.sendUpdate("account-overview", update);
    });
```

### Step 8: UI Update
```javascript
// WebSocket message received in React UI
const update = {
    accountId: "ACC001",
    timestamp: "2024-01-15T10:00:01.200",
    changes: [
        { field: "holdings.AAPL", newValue: 15150.00, change: +150.00 },
        { field: "orders.AAPL", newValue: 3030.00, change: +30.00 },
        { field: "totalNAV", newValue: 36180.00, change: +180.00 }
    ]
}

// AG Grid incremental update
gridApi.applyTransaction({
    update: [
        {
            accountId: "ACC001",
            holdingsAAPL: 15150.00,    // Cell flashes green
            ordersAAPL: 3030.00,       // Cell flashes green  
            totalNAV: 36180.00         // Cell flashes green
        }
    ]
});

// Visual feedback: cells flash green for 2 seconds
```

---

## Final State Comparison

### Before Price Update
```
Account ACC001:
- AAPL Holdings: 100 × $150.00 = $15,000
- AAPL Orders: 20 × $150.00 = $3,000
- Total AAPL Impact: $18,000
- Account NAV: $36,000
```

### After Price Update  
```
Account ACC001:
- AAPL Holdings: 100 × $151.50 = $15,150 (+$150)
- AAPL Orders: 20 × $151.50 = $3,030 (+$30)
- Total AAPL Impact: $18,180 (+$180)
- Account NAV: $36,180 (+$180)

✅ CONSISTENT: Both holdings and orders use price $151.50 @ 10:00:01.200
```

---

## Key Consistency Points

1. **Same Price Source**: Both HoldingMV and OrderMV calculated from identical price state
2. **Same Timestamp**: Both use `priceTimestamp: "2024-01-15T10:00:01.200"`
3. **Atomic Processing**: Calculated in same Flink operator processing cycle
4. **Validation Possible**: Account Overview can verify price consistency
5. **Real-time Updates**: UI reflects changes within milliseconds

This trace shows how Approach 1 (Unified Flink Job) ensures price consistency while maintaining real-time performance for account-level market value updates. 