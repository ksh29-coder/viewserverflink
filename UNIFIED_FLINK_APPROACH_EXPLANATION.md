# Approach 1: Unified Flink Job - Detailed Explanation

## Architecture Overview

### Current Problem (Separate Jobs)
```
Price Update: AAPL $150 → $151
    ↓
┌─────────────────┐    ┌─────────────────┐
│ HoldingMV Job   │    │ OrderMV Job     │
│ Processes @     │    │ Processes @     │
│ 10:00:01.123    │    │ 10:00:01.456    │
│ Price: $150     │    │ Price: $151     │
└─────────────────┘    └─────────────────┘
    ↓                      ↓
HoldingMV with $150    OrderMV with $151
    ↓                      ↓
Account NAV = Holdings($150) + Orders($151) = INCONSISTENT!
```

### Solution: Unified Job
```
Price Update: AAPL $150 → $151
    ↓
┌─────────────────────────────────┐
│ UnifiedMarketValueJob           │
│ Single processing @ 10:00:01.200│
│ Shared Price State: $151        │
│                                 │
│ ┌─────────────┐ ┌─────────────┐ │
│ │ HoldingMV   │ │ OrderMV     │ │
│ │ Calc        │ │ Calc        │ │
│ │ Price: $151 │ │ Price: $151 │ │
│ └─────────────┘ └─────────────┘ │
└─────────────────────────────────┘
    ↓                      ↓
HoldingMV with $151    OrderMV with $151
    ↓                      ↓
Account NAV = Holdings($151) + Orders($151) = CONSISTENT!
```

## Detailed Implementation

### 1. Unified State Management

The job maintains **shared state per instrumentId**:

```java
public class UnifiedMarketValueProcessor extends KeyedProcessFunction<String, String, UnifiedMarketValue> {
    
    // Shared state keyed by instrumentId
    private ValueState<Price> priceState;              // Latest price
    private ValueState<List<SODHolding>> holdingsState; // All holdings for this instrument
    private ValueState<List<Order>> ordersState;        // All orders for this instrument  
    private ValueState<Instrument> instrumentState;     // Instrument details
    
    @Override
    public void processElement(String taggedData, Context ctx, Collector<UnifiedMarketValue> out) {
        String[] parts = taggedData.split(":", 2);
        String type = parts[0];  // "HOLDING", "ORDER", "INSTRUMENT", "PRICE"
        String json = parts[1];
        
        switch (type) {
            case "HOLDING":
                processHolding(json);
                break;
            case "ORDER":
                processOrder(json);
                break;
            case "INSTRUMENT":
                processInstrument(json);
                break;
            case "PRICE":
                processPrice(json, out); // This triggers calculations
                break;
        }
    }
}
```

### 2. Price Update Processing Flow

When a price update arrives, here's exactly what happens:

```java
private void processPrice(String json, Collector<UnifiedMarketValue> out) throws Exception {
    // 1. Parse new price
    Price newPrice = objectMapper.readValue(json, Price.class);
    String instrumentId = newPrice.getInstrumentId(); // e.g., "AAPL"
    
    // 2. Update shared price state
    priceState.update(newPrice);
    
    // 3. Get all data from state
    List<SODHolding> holdings = holdingsState.value();
    List<Order> orders = ordersState.value();
    Instrument instrument = instrumentState.value();
    
    // 4. Calculate HoldingMV for ALL holdings of this instrument
    if (holdings != null && instrument != null) {
        for (SODHolding holding : holdings) {
            HoldingMV holdingMV = calculateHoldingMV(holding, instrument, newPrice);
            
            // Emit unified result
            UnifiedMarketValue result = UnifiedMarketValue.builder()
                .type("HOLDING")
                .instrumentId(instrumentId)
                .accountId(holding.getAccountId())
                .priceTimestamp(newPrice.getDate())  // SAME timestamp
                .price(newPrice.getPrice())          // SAME price
                .holdingMV(holdingMV)
                .orderMV(null)
                .build();
            
            out.collect(result);
        }
    }
    
    // 5. Calculate OrderMV for ALL orders of this instrument
    if (orders != null && instrument != null) {
        for (Order order : orders) {
            OrderMV orderMV = calculateOrderMV(order, instrument, newPrice);
            
            // Emit unified result
            UnifiedMarketValue result = UnifiedMarketValue.builder()
                .type("ORDER")
                .instrumentId(instrumentId)
                .accountId(order.getAccountId())
                .priceTimestamp(newPrice.getDate())  // SAME timestamp
                .price(newPrice.getPrice())          // SAME price
                .holdingMV(null)
                .orderMV(orderMV)
                .build();
            
            out.collect(result);
        }
    }
}
```

## Account-Level Market Value Update Example

Let's trace how an account's market value gets updated when AAPL price changes:

### Initial State
```
Account: ACC001
Holdings:
- AAPL: 100 shares @ $150 = $15,000
- MSFT: 50 shares @ $300 = $15,000
Orders:
- AAPL: Buy 20 shares @ $150 = $3,000 (unfilled)
- MSFT: Sell 10 shares @ $300 = $3,000 (unfilled)

Total Account NAV = $15,000 + $15,000 + $3,000 + $3,000 = $36,000
```

### Price Update Event
```
AAPL price update: $150 → $151 (at 10:00:01.200)
```

### Step-by-Step Processing

#### Step 1: Unified Job Receives Price Update
```java
// Price message arrives at UnifiedMarketValueJob
Price newPrice = {
    instrumentId: "AAPL",
    price: 151.00,
    date: "2024-01-15T10:00:01.200"
}

// Job is keyed by instrumentId, so all AAPL data goes to same operator
```

#### Step 2: Update Shared State
```java
// Update price state for AAPL
priceState.update(newPrice); // Now AAPL state has $151

// Retrieve all AAPL-related data from state
List<SODHolding> aaplHoldings = holdingsState.value();
// Returns: [ACC001: 100 shares, ACC002: 200 shares, ...]

List<Order> aaplOrders = ordersState.value();  
// Returns: [ACC001: Buy 20, ACC003: Sell 50, ...]

Instrument aaplInstrument = instrumentState.value();
// Returns: {instrumentId: "AAPL", name: "Apple Inc", ...}
```

#### Step 3: Calculate New HoldingMV (Same Price)
```java
// For ACC001 AAPL holding
SODHolding holding = {accountId: "ACC001", position: 100, instrumentId: "AAPL"}

HoldingMV newHoldingMV = calculateHoldingMV(holding, aaplInstrument, newPrice);
// Result:
{
    accountId: "ACC001",
    instrumentId: "AAPL", 
    position: 100,
    price: 151.00,                    // NEW PRICE
    priceTimestamp: "10:00:01.200",   // CONSISTENT TIMESTAMP
    marketValueUSD: 15100.00,         // 100 * $151 = $15,100
    calculationTimestamp: "10:00:01.200"
}

// Emit to Kafka topic: aggregation.unified-mv
UnifiedMarketValue output1 = {
    type: "HOLDING",
    accountId: "ACC001",
    priceTimestamp: "10:00:01.200",
    price: 151.00,
    holdingMV: newHoldingMV
}
```

#### Step 4: Calculate New OrderMV (Same Price)
```java
// For ACC001 AAPL order
Order order = {accountId: "ACC001", orderQuantity: 20, instrumentId: "AAPL"}

OrderMV newOrderMV = calculateOrderMV(order, aaplInstrument, newPrice);
// Result:
{
    accountId: "ACC001",
    instrumentId: "AAPL",
    orderQuantity: 20,
    price: 151.00,                    // SAME PRICE AS HOLDING
    priceTimestamp: "10:00:01.200",   // SAME TIMESTAMP AS HOLDING
    orderMarketValueUSD: 3020.00,     // 20 * $151 = $3,020
    calculationTimestamp: "10:00:01.200"
}

// Emit to Kafka topic: aggregation.unified-mv
UnifiedMarketValue output2 = {
    type: "ORDER", 
    accountId: "ACC001",
    priceTimestamp: "10:00:01.200",
    price: 151.00,
    orderMV: newOrderMV
}
```

#### Step 5: Account Overview Aggregation
```java
// Kafka Streams processes the unified-mv topic
// Groups by accountId and aggregates

KTable<String, AccountNAV> accountNAV = unifiedMVStream
    .filter((key, value) -> selectedAccounts.contains(value.getAccountId()))
    .groupBy((key, value) -> value.getAccountId())
    .aggregate(
        () -> new AccountNAV(),
        (accountId, unifiedMV, accountNAV) -> {
            if ("HOLDING".equals(unifiedMV.getType())) {
                accountNAV.addHolding(unifiedMV.getHoldingMV());
            } else if ("ORDER".equals(unifiedMV.getType())) {
                accountNAV.addOrder(unifiedMV.getOrderMV());
            }
            return accountNAV;
        }
    );
```

### Final Result
```
Account: ACC001 (Updated)
Holdings:
- AAPL: 100 shares @ $151 = $15,100 (+$100)
- MSFT: 50 shares @ $300 = $15,000 (unchanged)
Orders:
- AAPL: Buy 20 shares @ $151 = $3,020 (+$20)
- MSFT: Sell 10 shares @ $300 = $3,000 (unchanged)

Total Account NAV = $15,100 + $15,000 + $3,020 + $3,000 = $36,120 (+$120)

✅ CONSISTENT: Both AAPL holding and order use price $151 @ 10:00:01.200
```

## Key Benefits

### 1. **Price Consistency Guarantee**
- Single source of truth for prices
- Atomic calculation ensures same price for holdings and orders
- Identical `priceTimestamp` enables validation

### 2. **Real-time Updates**
- Account NAV updates immediately when prices change
- WebSocket can push incremental changes to UI
- Users see consistent totals across all views

### 3. **Scalability**
- State is partitioned by instrumentId (7 instruments = 7 partitions)
- Each price update only affects one partition
- Parallel processing across instruments

### 4. **Fault Tolerance**
- Flink checkpointing preserves state
- Kafka provides message durability
- System can recover from failures without data loss

## Data Flow Summary

```
1. Price Update (AAPL: $150 → $151)
   ↓
2. UnifiedMarketValueJob (keyed by instrumentId)
   ↓
3. Update shared price state
   ↓
4. Calculate HoldingMV for all AAPL holdings (same price)
   ↓
5. Calculate OrderMV for all AAPL orders (same price)
   ↓
6. Emit to aggregation.unified-mv topic
   ↓
7. Kafka Streams aggregates by accountId
   ↓
8. Account Overview shows updated NAV
   ↓
9. WebSocket pushes changes to UI
   ↓
10. User sees real-time consistent updates
```

This approach ensures that when you aggregate SOD Holdings + Current Orders + Expected Fills for any account, all components use the same price and timestamp, eliminating the consistency issues that motivated this architectural change. 