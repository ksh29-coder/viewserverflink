# Unified Market Value Flink Job Implementation

## Overview

The **UnifiedMarketValueJob** is a new Flink job that combines the functionality of both `HoldingMarketValueJob` and `OrderMarketValueJob` into a single, unified processing pipeline. This ensures **price consistency** between holdings and orders by using shared price state for all market value calculations.

## Key Benefits

### 🎯 Price Consistency
- **Problem Solved**: Previously, separate jobs could process price updates at different times, leading to inconsistent market values when aggregating SOD/Current/Expected NAV
- **Solution**: Single price state shared between holdings and orders calculations
- **Result**: Identical prices and timestamps for both holdings and orders of the same instrument

### ⚡ Real-time Processing
- Immediate calculation on price updates (no windowing delays)
- KeyedState partitioned by instrumentId for parallel processing
- Fault-tolerant with checkpointed state for recovery

### 📈 Simplified Architecture
- Eliminates need for complex stream joining and validation
- Single output topic: `aggregation.unified-mv`
- Reduced operational complexity

## Architecture

```
Input Topics:
├── base.sod-holding     → Holdings data
├── base.order-events    → Order events  
├── base.instrument      → Instrument metadata
└── base.price          → Price updates (triggers calculations)

Processing:
├── KeyedState by instrumentId
├── Shared price state for consistency
└── Immediate calculation on price updates

Output Topic:
└── aggregation.unified-mv → UnifiedMarketValue records
```

## Data Model

### UnifiedMarketValue
The unified model can represent both holdings and orders:

```java
public class UnifiedMarketValue {
    // Record Type
    private String recordType;  // "HOLDING" or "ORDER"
    
    // Common Fields
    private String instrumentId;
    private String accountId;
    private LocalDateTime timestamp;
    
    // Holding-Specific Fields
    private String holdingId;
    private LocalDate date;
    private BigDecimal position;
    
    // Order-Specific Fields
    private String orderId;
    private BigDecimal orderQuantity;
    private BigDecimal filledQuantity;
    private String orderStatus;
    // ... other order fields
    
    // Unified Price Fields (CRITICAL: Same for all calculations)
    private BigDecimal price;
    private String priceCurrency;
    private String priceSource;
    private LocalDateTime priceTimestamp;
    
    // Calculated Market Value Fields
    private BigDecimal marketValueLocal;
    private BigDecimal marketValueUSD;
    private BigDecimal filledMarketValueLocal;  // Orders only
    private BigDecimal filledMarketValueUSD;    // Orders only
}
```

## Processing Logic

### State Management
The job maintains four types of state per instrumentId:

1. **holdingsState**: `List<SODHolding>` - Multiple accounts can hold same instrument
2. **ordersState**: `Map<String, Order>` - Orders keyed by orderId
3. **instrumentState**: `Instrument` - Instrument metadata
4. **lastPriceState**: `Price` - Current price for this instrument

### Processing Flow

1. **Holdings Arrive**: Store in holdingsState
2. **Orders Arrive**: Store in ordersState (keyed by orderId)
3. **Instruments Arrive**: Store in instrumentState
4. **Prices Arrive**: 
   - Update lastPriceState
   - Calculate market values for ALL holdings of this instrument
   - Calculate market values for ALL orders of this instrument
   - Emit UnifiedMarketValue records for both

### Price Consistency Guarantee

```java
// CRITICAL: Same price used for both calculations
Price sharedPrice = lastPriceState.value();

// Holdings calculation
for (SODHolding holding : holdings) {
    UnifiedMarketValue holdingMV = calculateHoldingMarketValue(holding, instrument, sharedPrice);
    out.collect(holdingMV);
}

// Orders calculation  
for (Order order : orders.values()) {
    UnifiedMarketValue orderMV = calculateOrderMarketValue(order, instrument, sharedPrice);
    out.collect(orderMV);
}
```

## Implementation Files

### Core Components

1. **`UnifiedMarketValue.java`** - Unified data model
2. **`UnifiedMarketValueProcessor.java`** - State management and processing logic
3. **`UnifiedMarketValueJob.java`** - Main Flink job
4. **`UnifiedMarketValueKafkaSerializationSchema.java`** - Kafka serialization

### Scripts

1. **`start-unified-flink-job.sh`** - Start the unified job
2. **`test-unified-flink-job.sh`** - Test and monitor the job
3. **`stop-all.sh`** - Updated to include unified job

## Usage

### Starting the Job

```bash
# Start infrastructure first
docker-compose up -d

# Start the unified job
./scripts/start-unified-flink-job.sh
```

### Monitoring

```bash
# Monitor logs
tail -f flink-unified-mv.log

# Monitor output topic
./scripts/test-unified-flink-job.sh

# Check process status
ps aux | grep UnifiedMarketValueJob
```

### Stopping the Job

```bash
# Stop just the unified job
pkill -f UnifiedMarketValueJob

# Stop all services
./scripts/stop-all.sh
```

## Output Topic Structure

The `aggregation.unified-mv` topic contains records like:

```json
{
  "recordType": "HOLDING",
  "instrumentId": "AAPL",
  "accountId": "ACC001",
  "holdingId": "HOLD_001",
  "position": 100,
  "price": 150.00,
  "priceTimestamp": "2024-01-15T10:30:00",
  "marketValueLocal": 15000.00,
  "marketValueUSD": 15000.00,
  "calculationTimestamp": "2024-01-15T10:30:05"
}
```

```json
{
  "recordType": "ORDER",
  "instrumentId": "AAPL", 
  "accountId": "ACC001",
  "orderId": "ORD_123",
  "orderQuantity": 50,
  "filledQuantity": 25,
  "orderStatus": "PARTIALLY_FILLED",
  "price": 150.00,
  "priceTimestamp": "2024-01-15T10:30:00",
  "marketValueLocal": 7500.00,
  "marketValueUSD": 7500.00,
  "filledMarketValueLocal": 3750.00,
  "filledMarketValueUSD": 3750.00,
  "calculationTimestamp": "2024-01-15T10:30:05"
}
```

## Migration Strategy

### Phase 1: Parallel Operation
- Run unified job alongside existing separate jobs
- Compare outputs for consistency
- Validate price consistency between holdings and orders

### Phase 2: Consumer Migration
- Update downstream consumers to read from `aggregation.unified-mv`
- Maintain backward compatibility during transition

### Phase 3: Deprecation
- Stop separate `HoldingMarketValueJob` and `OrderMarketValueJob`
- Remove old topics: `aggregation.holding-mv` and `aggregation.order-mv`

## Testing

### Verification Points

1. **Price Consistency**: Same instrumentId should have identical price and priceTimestamp for both holdings and orders
2. **Real-time Updates**: Market values update immediately when prices change
3. **State Recovery**: Job recovers correctly after restart with checkpointed state
4. **Performance**: Single job should perform better than two separate jobs

### Test Commands

```bash
# Start test environment
docker-compose up -d
./scripts/start-unified-flink-job.sh

# Generate test data
curl -X POST http://localhost:8081/api/data-generation/initialize
curl -X POST http://localhost:8081/api/data-generation/dynamic/start

# Monitor results
./scripts/test-unified-flink-job.sh
```

## Configuration

### Kafka Topics
- **Input**: `base.sod-holding`, `base.order-events`, `base.instrument`, `base.price`
- **Output**: `aggregation.unified-mv`

### Consumer Groups
- Default: `flink-unified-mv-keyed-state`
- Timestamped: `flink-unified-mv-$(date +%s)` for multiple instances

### Checkpointing
- Interval: 30 seconds
- State backend: Default (memory/filesystem)

## Troubleshooting

### Common Issues

1. **Job fails to start**
   - Check Java 17 compatibility
   - Verify Kafka connectivity
   - Check log: `flink-unified-mv.log`

2. **No output records**
   - Verify input topics have data
   - Check consumer group offsets
   - Ensure instruments are loaded first

3. **Inconsistent prices**
   - Should not happen with unified job
   - Check if multiple job instances are running

### Debug Commands

```bash
# Check job status
ps aux | grep UnifiedMarketValueJob

# Check Kafka topics
docker exec viewserver-kafka kafka-topics --bootstrap-server localhost:9092 --list

# Monitor input topics
docker exec viewserver-kafka kafka-console-consumer --bootstrap-server localhost:9092 --topic base.price

# Check logs
tail -f flink-unified-mv.log
```

## Performance Considerations

### Memory Usage
- State size grows with number of holdings and orders per instrument
- Consider state TTL for old orders
- Monitor memory usage in production

### Throughput
- Single job should handle combined load of both separate jobs
- Parallelism can be increased for higher throughput
- KeyedState partitioning provides natural scaling

### Latency
- Immediate processing on price updates
- No windowing delays
- Network latency to Kafka is main factor

## Future Enhancements

1. **State TTL**: Automatic cleanup of old orders
2. **Metrics**: Expose processing metrics via JMX
3. **Backpressure**: Handle high-volume scenarios
4. **Multi-currency**: Enhanced currency conversion support
5. **Watermarks**: Event-time processing for out-of-order events 