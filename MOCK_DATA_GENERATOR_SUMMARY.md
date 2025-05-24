# Mock Data Generator Implementation Summary

## Overview

The Mock Data Generator is a standalone Spring Boot service that generates realistic financial data and publishes it to Kafka topics. It serves as the data source for testing and demonstrating the 4-layer materialized view system.

## Architecture

### Service Structure
```
mock-data-generator/
├── src/main/java/com/viewserver/mockdata/
│   ├── MockDataGeneratorApplication.java     # Main Spring Boot application
│   └── generator/
│       ├── StaticDataGenerator.java          # Static reference data (accounts, instruments)
│       ├── SODHoldingGenerator.java          # Start-of-day holdings
│       ├── PriceGenerator.java               # Real-time price updates
│       ├── OrderGenerator.java               # Trading orders and lifecycle
│       └── IntradayCashGenerator.java        # Cash movements and dividends
├── src/main/resources/
│   └── application.yml                       # Configuration
└── pom.xml                                   # Maven dependencies
```

### Dependencies
- **data-layer**: Uses base data models and Kafka publishers
- **Spring Boot**: Application framework with scheduling
- **Spring Kafka**: Kafka integration for publishing
- **Lombok**: Code generation for boilerplate reduction

## Data Generation Strategy

### 1. Static Data Generator (`StaticDataGenerator`)
**Purpose**: Creates reference data once at application startup

**Data Generated**:
- **5 Accounts**: Fund strategies (Global Growth, US Value, etc.)
- **10 Instruments**: 
  - 7 Equity instruments (AAPL, MSFT, GOOGL, JPM, BAC, NESN, ASML)
  - 3 Cash instruments (USD, EUR, GBP)

**Timing**: Triggered on `ApplicationReadyEvent`

**Key Features**:
- Provides static helper methods for other generators
- Realistic financial instrument data with sectors and sub-sectors
- Publishes to `base.account` and `base.instrument` topics

### 2. SOD Holdings Generator (`SODHoldingGenerator`)
**Purpose**: Generates start-of-day portfolio positions

**Schedule**: Daily at 6:00 AM (`@Scheduled(cron = "0 0 6 * * *")`)

**Data Pattern**:
- Creates holdings for each account-instrument combination
- Position sizes: 20% zero positions, 60% small (1-1000 shares), 20% large (1000-10000 shares)
- Generates unique holding IDs with date, account, and instrument

**Kafka Topic**: `base.sod-holding`

### 3. Price Generator (`PriceGenerator`)
**Purpose**: Simulates real-time market price movements

**Schedule**: Every 5 seconds (`@Scheduled(fixedRate = 5000)`)

**Algorithm**:
- **Random Walk Model**: Uses Gaussian distribution for price changes
- **Base Prices**: Realistic starting prices for each instrument
- **Price Bounds**: Prevents negative prices, rounds to 2 decimal places
- **Volatility**: 95% of moves within ±1%, 5% larger moves (±1% to ±3%)

**Kafka Topic**: `base.price`

### 4. Order Generator (`OrderGenerator`)
**Purpose**: Simulates trading activity and order lifecycle

**Schedules**:
- **New Orders**: Every 30 seconds (1-3 orders per cycle)
- **Order Updates**: Every 10 seconds (0-2 updates per cycle)

**Order Types**: MARKET, LIMIT, STOP
**Venues**: NYSE, NASDAQ, LSE, EURONEXT

**Order Lifecycle**:
- **Creation**: Orders start with `CREATED` status
- **Updates**: Simulates fills, partial fills, cancellations
- **Fill Ratios**: 30% no fill, 40% partial fill, 30% complete fill

**Kafka Topic**: `base.order-events`

### 5. Intraday Cash Generator (`IntradayCashGenerator`)
**Purpose**: Generates cash movements throughout the trading day

**Schedule**: Every 2 minutes (`@Scheduled(fixedRate = 120000)`)

**Movement Types**:
- **DIVIDEND**: Small positive amounts ($50-$550)
- **TRADE_SETTLEMENT**: Large positive/negative amounts (±$1K-$51K)
- **SUBSCRIPTION**: Large positive inflows ($10K-$110K)
- **REDEMPTION**: Large negative outflows (-$10K to -$110K)
- **FEE**: Small negative amounts (-$10 to -$210)
- **INTEREST**: Small positive amounts ($5-$105)

**Special Features**:
- `generateDividendPayments()`: Quarterly dividend simulation
- Reference ID generation based on movement type

**Kafka Topic**: `base.intraday-cash`

## Configuration

### Kafka Configuration (`application.yml`)
```yaml
spring:
  kafka:
    bootstrap-servers: localhost:9092
    producer:
      key-serializer: org.apache.kafka.common.serialization.StringSerializer
      value-serializer: org.springframework.kafka.support.serializer.JsonSerializer
      retries: 3
      batch-size: 16384
      linger-ms: 5
      acks: all
      enable-idempotence: true
      compression-type: snappy
```

### Generation Control
```yaml
mockdata:
  generation:
    sod-holdings:
      enabled: true
      cron: "0 0 6 * * *"
    prices:
      enabled: true
      interval-seconds: 5
    orders:
      enabled: true
      new-orders-interval-seconds: 30
      order-updates-interval-seconds: 10
    cash-movements:
      enabled: true
      interval-seconds: 120
```

## Data Volume and Characteristics

### Expected Data Rates
- **Static Data**: 15 records once at startup
- **SOD Holdings**: 35 records daily (5 accounts × 7 instruments)
- **Prices**: 7 records every 5 seconds (7 equity instruments)
- **Orders**: 1-3 new orders every 30 seconds + 0-2 updates every 10 seconds
- **Cash Movements**: 0-3 movements every 2 minutes

### Daily Volume Estimates
- **Prices**: ~120,000 records/day (7 instruments × 17,280 intervals)
- **Orders**: ~5,000-8,000 records/day
- **Cash Movements**: ~2,000 records/day
- **SOD Holdings**: 35 records/day

## Key Design Decisions

### 1. Realistic Data Patterns
- **Financial Accuracy**: Uses realistic instrument names, sectors, and price ranges
- **Market Behavior**: Price movements follow normal distribution with occasional volatility
- **Portfolio Diversity**: Mix of position sizes and zero holdings

### 2. Kafka Key Strategy
- **Composite Keys**: Uses date, account, and instrument combinations for optimal partitioning
- **Co-location**: Related data lands on same partitions for efficient processing

### 3. Asynchronous Publishing
- **CompletableFuture**: All Kafka publishing is asynchronous
- **Error Handling**: Comprehensive logging for success/failure scenarios
- **Batch Operations**: Efficient bulk publishing for static data

### 4. Configurable Generation
- **Spring Scheduling**: Uses `@Scheduled` annotations for timing control
- **Manual Triggers**: Test methods for immediate data generation
- **Environment Specific**: Configuration allows rate adjustment per environment

## Testing and Validation

### Manual Testing Methods
Each generator provides manual trigger methods:
- `StaticDataGenerator.generateStaticData()`
- `SODHoldingGenerator.generateSODHoldingsNow()`
- `PriceGenerator.generatePricesNow()`
- `OrderGenerator.generateOrdersNow()`
- `IntradayCashGenerator.generateCashMovementsNow()`

### Build Status
✅ **Compilation**: Successfully compiles with Maven
✅ **Dependencies**: All dependencies resolved
✅ **Lombok Integration**: Annotation processing configured
✅ **Spring Boot**: Application starts and schedules tasks

## Integration Points

### With Data Layer
- **Direct Dependency**: Uses `data-layer` module for models and publishers
- **Kafka Topics**: Publishes to all base layer topics
- **Key Generation**: Uses shared key building utilities

### With View Server
- **Data Source**: Provides input data for Flink aggregation jobs
- **Real-time Stream**: Continuous data flow for stream processing
- **Event Ordering**: Proper timestamp and key ordering for processing

## Next Steps

1. **Infrastructure Setup**: Start Kafka cluster for data publishing
2. **View Server Integration**: Connect Flink jobs to consume generated data
3. **Monitoring**: Add metrics for data generation rates and Kafka publishing
4. **Data Quality**: Implement data validation and consistency checks
5. **Performance Tuning**: Optimize generation rates based on system capacity

## File Structure Summary

```
mock-data-generator/
├── pom.xml                                   # Maven configuration with Lombok
├── src/main/java/com/viewserver/mockdata/
│   ├── MockDataGeneratorApplication.java    # 18 lines - Main application
│   └── generator/
│       ├── StaticDataGenerator.java         # 213 lines - Reference data
│       ├── SODHoldingGenerator.java         # 115 lines - Daily holdings
│       ├── PriceGenerator.java              # 164 lines - Price updates
│       ├── OrderGenerator.java              # 222 lines - Trading orders
│       └── IntradayCashGenerator.java       # 203 lines - Cash movements
└── src/main/resources/
    └── application.yml                       # 47 lines - Configuration
```

**Total Implementation**: ~982 lines of code across 7 files

The Mock Data Generator is now complete and ready to provide realistic financial data for testing the materialized view system. It demonstrates proper Spring Boot architecture, Kafka integration, and realistic financial data patterns. 