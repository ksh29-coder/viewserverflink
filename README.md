# View Server Performance POC

A horizontally scalable **4-layer materialized view system** for real-time financial data processing. The system implements dynamic, user-configurable portfolio views with sub-50ms latency using Apache Flink for complex stream processing and Kafka Streams for user view computations.

## Architecture Overview

```
Mock Data Generator → Kafka Topics → View Server (Flink + Kafka Streams + WebSocket) → React UI
```

### 4-Layer Architecture:
1. **Base Data Layer**: Raw financial data models and Kafka producers
2. **Aggregation Layer**: Complex stream processing using Apache Flink 
3. **Computation Layer**: Business logic and view processing using Kafka Streams
4. **Client Views Layer**: WebSocket-enabled React UI for real-time updates

## Project Structure

```
viewserverflink/
├── shared-common/           # Common utilities and configurations
├── data-layer/             # Base data models & Kafka producers
├── mock-data-generator/    # Standalone data generation service
├── view-server/            # Combined Flink + Kafka Streams + WebSocket service
├── react-ui/              # React frontend
├── integration-tests/      # End-to-end testing
├── docker-compose.yml     # Infrastructure (Kafka, Redis)
└── pom.xml               # Root Maven configuration
```

## Prerequisites

- **Java 17+**
- **Maven 3.8+**
- **Docker & Docker Compose**
- **Node.js 18+** (for React UI)

## Quick Start

### 1. Start Infrastructure
```bash
# Start Kafka, Redis, and monitoring tools
docker-compose up -d

# Optional: Start with monitoring
docker-compose --profile monitoring up -d
```

### 2. Build Project
```bash
# Build all Maven modules
mvn clean compile

# Package applications
mvn clean package
```

### 3. Start Services

#### Mock Data Generator
```bash
cd mock-data-generator
mvn spring-boot:run
```

#### View Server
```bash
cd view-server  
mvn spring-boot:run
```

#### React UI
```bash
cd react-ui
npm install
npm run dev
```

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

## Next Steps

1. Implement base data models in `data-layer`
2. Create mock data generators
3. Set up Flink aggregation jobs
4. Build Kafka Streams computation layer
5. Develop React UI components

## Contributing

This is a performance POC for financial data processing. Follow the bottom-up development approach:
`shared-common` → `data-layer` → `mock-data-generator` → `view-server` → `react-ui` 