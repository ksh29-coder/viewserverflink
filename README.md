# View Server Flink - Performance POC

## Architecture Overview

4-layer materialized view system for real-time financial data processing:

1. **Raw Data Layer** - Mock data generator producing realistic financial events
2. **View Server Layer** - Real-time caching and API layer (Redis + Kafka consumers)
3. **Aggregation Layer** - Flink stream processing for real-time analytics
4. **Materialized Views** - Kafka Streams for complex event processing

## Data Generation Strategy

### Static/Initial Data (Manual Trigger)
- **Accounts & Instruments**: Reference data foundation
- **SOD Holdings**: Start-of-day positions
- **Trigger Method**: REST API endpoints (on-demand)

### Dynamic Data (Continuous Streams)  
- **Prices**: Real-time market data (every 5s)
- **Orders**: New orders every 30s, updates every 10s
- **Cash movements**: Every 2 minutes

## Quick Start

### 1. Start Infrastructure
```bash
# Start Docker if not running
open -a Docker

# Start Kafka, Zookeeper, and Redis
docker-compose up -d
```

### 2. Start Mock Data Generator (Dynamic Data)
```bash
./scripts/start-dynamic-data.sh
```

### 3. Initialize Static Data & SOD Holdings
```bash
# Initialize everything at once
curl -X POST http://localhost:8081/api/data-generation/initialize

# Or separately:
curl -X POST http://localhost:8081/api/data-generation/static
curl -X POST http://localhost:8081/api/data-generation/sod-holdings
```

### 4. Start View Server
```bash
./scripts/start-view-server.sh
```

### 5. Test APIs
```bash
# Cache statistics
curl http://localhost:8080/api/stats | jq .

# Data endpoints
curl http://localhost:8080/api/accounts | jq .
curl http://localhost:8080/api/instruments | jq .
curl http://localhost:8080/api/prices | jq .
curl http://localhost:8080/api/orders | jq .
```

## Current Status

✅ **Working Components**:
- Mock Data Generator with manual triggers
- Static data generation (accounts, instruments)
- SOD holdings generation
- Dynamic data streams (prices, orders, cash movements)
- View Server with Redis caching
- REST APIs for data inspection

✅ **Working APIs**:
- `GET /api/stats` - Cache statistics
- `GET /api/accounts` - All accounts (5 records)
- `GET /api/instruments` - All instruments (10 records)
- `GET /api/prices` - All prices (7 records, real-time updates)
- `GET /api/orders` - All orders (700+ records)

⚠️ **Known Issues**:
- `GET /api/holdings/{accountId}` - Returns 500 error
- `GET /api/cash/{accountId}` - Returns 500 error

## Data Generation Control

### Mock Data Generator Endpoints (Port 8081)
```bash
# Check available endpoints
curl http://localhost:8081/api/data-generation/status

# Generate static data + SOD holdings
curl -X POST http://localhost:8081/api/data-generation/initialize

# Generate only static data (accounts + instruments)
curl -X POST http://localhost:8081/api/data-generation/static

# Generate only SOD holdings
curl -X POST http://localhost:8081/api/data-generation/sod-holdings
```

## Management Scripts

```bash
# Start dynamic data generation only
./scripts/start-dynamic-data.sh

# Start view server
./scripts/start-view-server.sh

# Stop all services
./scripts/stop-all.sh

# Initialize complete data set (requires running mock generator)
./scripts/initialize-data.sh
```

## Infrastructure

- **Kafka**: localhost:9092
- **Redis**: localhost:6379
- **Mock Data Generator**: localhost:8081
- **View Server**: localhost:8080

## Next Steps

1. Fix holdings and cash movements API endpoints
2. Add Flink stream processing layer
3. Implement Kafka Streams for complex event processing
4. Add WebSocket endpoints for real-time data streaming
5. Performance testing and optimization

## Project Structure

- `base-data/` - Shared data models and Kafka utilities
- `mock-data-generator/` - Financial data simulation with REST control
- `view-server/` - Redis caching + REST APIs  
- `scripts/` - Startup and control scripts

## Benefits of This Approach

1. **Flexible Testing**: Initialize static data when needed
2. **Realistic Simulation**: Continuous dynamic data streams
3. **Clean Separation**: Static vs dynamic data generation
4. **Easy Control**: REST APIs for data management
5. **Production-Ready**: Scalable architecture for real systems

## Prerequisites

- **Java 17+**
- **Maven 3.8+**
- **Docker & Docker Compose**
- **Node.js 18+** (for React UI)

## Service Ports

- **Kafka**: 9092
- **Kafka UI**: 8090 (with monitoring profile)
- **Redis**: 6379
- **Mock Data Generator**: 8081
- **View Server**: 8080
- **React UI**: 3000

## Kafka Topics

### Base Layer Topics (from Mock Data Generator):
- `base.account` - Key: `accountId`
- `base.instrument` - Key: `instrumentId` (numeric)
- `base.sod-holding` - Key: `{date}#{instrumentId}#{accountId}`
- `base.price` - Key: `{instrumentId}#{date}`
- `base.intraday-cash` - Key: `{date}#{instrumentId}#{accountId}`
- `base.order-events` - Key: `orderId`

### Aggregation Layer Topics (from Flink Jobs):
- `aggregation.enriched-holdings`
- `aggregation.market-values`  
- `aggregation.account-cash-summary`

## Data Models

### Base Data Models:
- **Account**: Account ID, name (equity strategy names)
- **Instrument**: Numeric ID, name, country of risk/domicile, sector, sub-sectors
- **SOD Holding**: Holding ID, date, instrument ID, position
- **Price**: Date, instrument ID, price
- **Intraday Cash**: Date, instrument ID (currency), quantity
- **Order**: Order ID, instrument ID, account ID, date, quantities, status

## Development Phases

### Phase 1: Foundation ✅
- [x] Project structure and Maven configuration
- [x] Docker Compose infrastructure setup
- [ ] Base data models implementation
- [ ] Shared utilities and key builders

### Phase 2: Data Generation
- [ ] Mock data generators for all data types
- [ ] Kafka producers for base layer topics
- [ ] Scheduling for SOD and intraday events

### Phase 3: Stream Processing  
- [ ] Flink jobs for complex aggregations
- [ ] Kafka Streams for business logic
- [ ] Materialized view management

### Phase 4: User Interface
- [ ] WebSocket handlers for real-time updates
- [ ] React components for portfolio visualization
- [ ] Dynamic view configuration

### Phase 5: Integration & Testing
- [ ] End-to-end integration tests
- [ ] Performance testing and optimization
- [ ] Documentation and deployment guides

## Technology Stack

- **Backend**: Spring Boot, Apache Flink, Kafka Streams
- **Messaging**: Apache Kafka
- **Caching**: Redis
- **Frontend**: React 18, Vite, AG Grid
- **Communication**: WebSocket
- **Build**: Maven (Java), npm (React)
- **Infrastructure**: Docker Compose

## Monitoring

Access monitoring tools:
- **Kafka UI**: http://localhost:8090 (lightweight option)
- **Control Center**: http://localhost:9021 (full Confluent platform)

## Contributing

This is a performance POC for financial data processing. Follow the bottom-up development approach:
`shared-common` → `data-layer` → `mock-data-generator` → `view-server` → `react-ui` 