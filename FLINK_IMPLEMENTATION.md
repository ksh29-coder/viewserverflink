# Flink Implementation - Option 1: Standalone Flink Cluster

## Overview

We have successfully implemented **Option 1** from our architecture analysis: a standalone Flink cluster with shared mv-calc library. This approach provides clean separation of concerns and avoids the complex serialization issues we encountered with embedded Flink in Spring Boot.

## Architecture

```
┌─────────────────────────────────────────────────────────────────┐
│                    MOCK DATA GENERATOR                          │
│                     (Port 8081)                                 │
│  ┌─────────────────┐ ┌─────────────────┐ ┌─────────────────┐   │
│  │ SOD Holdings    │ │ Instruments     │ │ Prices          │   │
│  │ base.sod-holding│ │ base.instrument │ │ base.price      │   │
│  └─────────────────┘ └─────────────────┘ └─────────────────┘   │
└─────────────────────────────────────────────────────────────────┘
                              ↓ Kafka Topics
┌─────────────────────────────────────────────────────────────────┐
│                    FLINK JOBS MODULE                            │
│                   (Standalone JAR)                              │
│  ┌─────────────────────────────────────────────────────────────┐ │
│  │              HoldingMarketValueJob                          │ │
│  │  ┌─────────────────┐ ┌─────────────────┐ ┌─────────────┐ │ │
│  │  │ Holdings +      │ │ Instruments +   │ │ MV Calc     │ │ │
│  │  │ Instruments     │ │ Prices          │ │ (mv-calc)   │ │ │
│  │  │ (JOIN)          │ │ (JOIN)          │ │ Library     │ │ │
│  │  └─────────────────┘ └─────────────────┘ └─────────────┘ │ │
│  └─────────────────────────────────────────────────────────────┘ │
└─────────────────────────────────────────────────────────────────┘
                              ↓ aggregation.holding-mv
┌─────────────────────────────────────────────────────────────────┐
│                      VIEW SERVER                                │
│                     (Port 8080)                                 │
│  ┌─────────────────────────────────────────────────────────────┐ │
│  │              HoldingMVConsumer                              │ │
│  │  ┌─────────────────┐ ┌─────────────────┐ ┌─────────────┐ │ │
│  │  │ Kafka Consumer  │ │ Redis Cache     │ │ REST API    │ │ │
│  │  │ (aggregation.   │ │ (HoldingMV)     │ │ Endpoints   │ │ │
│  │  │ holding-mv)     │ │                 │ │             │ │ │
│  │  └─────────────────┘ └─────────────────┘ └─────────────┘ │ │
│  └─────────────────────────────────────────────────────────────┘ │
└─────────────────────────────────────────────────────────────────┘
```

## Components Implemented

### 1. MV-Calc Library (`mv-calc/`)
**Pure Java library with no framework dependencies**

- `MarketValueCalculator.java` - Main calculator with static methods
- `BondCalculator.java` - Bond-specific calculations (price/100 * face_value * quantity)
- `CurrencyConverter.java` - Multi-currency support with simplified exchange rates

**Key Features:**
- Instrument type-specific logic: EQUITY, BOND, CURRENCY, FUND
- Multi-currency support (USD, EUR, GBP, JPY, CHF, CAD, AUD)
- No Spring dependencies - can be used in any Java application
- Static methods for easy integration

### 2. Flink Jobs Module (`flink-jobs/`)
**Standalone Flink application with fat JAR**

- `HoldingMarketValueJob.java` - Main Flink job orchestrating the pipeline
- `HoldingMV.java` - Simplified data model for Flink processing
- `HoldingMarketValueEnrichmentFunction.java` - Uses mv-calc library
- `HoldingMVKafkaSerializationSchema.java` - Kafka serialization

**Data Flow:**
1. Consume from `base.sod-holding`, `base.instrument`, `base.price`
2. Join Holdings + Instruments (by instrumentId)
3. Join result + Prices (by instrumentId)
4. Calculate market values using mv-calc library
5. Produce to `aggregation.holding-mv`

**Key Features:**
- Pure Flink implementation (no Spring)
- Windowed joins with 1-minute tumbling windows
- Error handling with zero market values on calculation errors
- Configurable Kafka bootstrap servers and consumer group

### 3. View Server Integration
**Enhanced to consume aggregation topic**

- `HoldingMVConsumer.java` - Kafka consumer for aggregation.holding-mv
- `CacheService.cacheHoldingMVFromJson()` - Caches enriched holdings
- REST API endpoints already available: `/api/holdings-mv`

## Build and Deployment

### Building the Components

```bash
# Build mv-calc library
cd mv-calc && mvn clean install

# Build Flink jobs JAR
cd flink-jobs && mvn clean package

# Build view-server
cd view-server && mvn clean compile
```

### Running the System

1. **Start Infrastructure:**
   ```bash
   docker-compose up -d  # Redis + Kafka
   ```

2. **Start Mock Data Generator:**
   ```bash
   cd mock-data-generator && mvn spring-boot:run
   ```

3. **Start Flink Job:**
   ```bash
   ./scripts/run-flink-job.sh
   ```

4. **Start View Server:**
   ```bash
   cd view-server && mvn spring-boot:run
   ```

5. **Initialize Data:**
   ```bash
   curl -X POST http://localhost:8081/api/data/initialize
   ```

## Data Models

### Input Data (from Mock Data Generator)
- **SODHolding**: `{date, instrumentId, accountId, position, timestamp}`
- **Instrument**: `{instrumentId, instrumentName, instrumentType, currency, sector, ...}`
- **Price**: `{date, instrumentId, price, currency, source, timestamp}`

### Output Data (to View Server)
- **HoldingMV**: All input fields plus:
  - `marketValueLocal` - Market value in instrument currency
  - `marketValueUSD` - Market value converted to USD
  - `calculationTimestamp` - When calculation was performed

## Market Value Calculation Logic

### By Instrument Type:
- **EQUITY/FUND**: `price × quantity`
- **BOND**: `(price / 100) × face_value × quantity`
- **CURRENCY**: `quantity` (no price multiplication)

### Currency Conversion:
- Simplified exchange rates for POC
- Automatic conversion to USD for all holdings
- Supports: USD, EUR, GBP, JPY, CHF, CAD, AUD

## Benefits of This Architecture

### ✅ **Separation of Concerns**
- Pure calculation logic in mv-calc (reusable)
- Stream processing in Flink (scalable)
- API serving in Spring Boot (familiar)

### ✅ **No Serialization Issues**
- Flink runs as standalone process
- No Spring context serialization problems
- Clean dependency management

### ✅ **Scalability**
- Flink job can be deployed to cluster
- Independent scaling of components
- Horizontal scaling possible

### ✅ **Maintainability**
- Clear module boundaries
- Testable components
- Framework-specific optimizations

## Testing

### Verify Data Flow:
```bash
# Check base topics have data
kafka-console-consumer --bootstrap-server localhost:9092 --topic base.sod-holding --from-beginning

# Check aggregation topic receives data
kafka-console-consumer --bootstrap-server localhost:9092 --topic aggregation.holding-mv --from-beginning

# Check API endpoints
curl http://localhost:8080/api/holdings-mv
curl http://localhost:8080/api/holdings-mv/ACC001
```

### Monitor Cache:
```bash
# Check Redis cache
redis-cli
> KEYS holding-mv:*
> GET holding-mv:2024-01-15#INST001#ACC001
```

## Next Steps

1. **Production Deployment**: Deploy Flink job to actual Flink cluster
2. **Real-time FX Rates**: Replace simplified exchange rates with live feeds
3. **Complex Calculations**: Add accrued interest, yield calculations for bonds
4. **Monitoring**: Add metrics and alerting for the Flink job
5. **Error Handling**: Implement dead letter queues for failed calculations

## Files Created/Modified

### New Modules:
- `mv-calc/` - Pure Java calculation library
- `flink-jobs/` - Standalone Flink application

### New Files:
- `scripts/run-flink-job.sh` - Script to run Flink job
- `FLINK_IMPLEMENTATION.md` - This documentation

### Modified Files:
- `pom.xml` - Added mv-calc and flink-jobs modules
- `view-server/` - Already had HoldingMV consumer and API endpoints

This implementation successfully demonstrates the power of separating stream processing concerns from web application concerns, resulting in a clean, scalable, and maintainable architecture. 