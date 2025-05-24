# View Server Performance POC - Advanced Architecture

## System Overview
A horizontally scalable **4-layer materialized view system** for real-time financial data processing. The system implements dynamic, user-configurable portfolio views with sub-50ms latency using Apache Flink for complex stream processing and Kafka Streams for user view computations.

## Architecture Philosophy

### **4-Layer Materialized View Pipeline**
```
┌─────────────────────────────────────────────────────────────────┐
│                    CLIENT VIEWS LAYER (Purple)                  │
│                     React UI + WebSocket                        │
│  ┌─────────────────┐ ┌─────────────────┐ ┌─────────────────┐   │
│  │ Portfolio View  │ │ Cash-Only View  │ │ PID Carve-Out   │   │
│  │ (WebSocket)     │ │ (WebSocket)     │ │ (WebSocket)     │   │
│  └─────────────────┘ └─────────────────┘ └─────────────────┘   │
└─────────────────────────────────────────────────────────────────┘
                              ↑ Real-time WebSocket Updates
┌─────────────────────────────────────────────────────────────────┐
│                       VIEW SERVER                               │
│                                                                 │
│  ┌───────────────────────────────────────────────────────────┐ │
│  │             COMPUTATION LAYER (Orange)                    │ │
│  │              Business Logic & View Processing             │ │
│  │  ┌─────────────────┐ ┌─────────────────┐ ┌─────────────┐ │ │
│  │  │ Portfolio       │ │ Attribution     │ │ Risk & Perf │ │ │
│  │  │ Aggregations    │ │ Analysis        │ │ Calculations│ │ │
│  │  │ (Kafka Streams) │ │ (Kafka Streams) │ │(Kafka Strms)│ │ │
│  │  └─────────────────┘ └─────────────────┘ └─────────────┘ │ │
│  └───────────────────────────────────────────────────────────┘ │
│                              ↑ aggregation.* topics             │
│  ┌───────────────────────────────────────────────────────────┐ │
│  │             AGGREGATION LAYER (Blue)                     │ │
│  │             Complex Stream Processing (Flink)            │ │
│  │  ┌─────────────────┐ ┌─────────────────┐ ┌─────────────┐ │ │
│  │  │ Holdings +      │ │ Orders + ETF +  │ │ Account +   │ │ │
│  │  │ Instruments +   │ │ Market Values   │ │ Cash Rollup │ │ │
│  │  │ Prices (JOIN)   │ │ (COMPLEX JOIN)  │ │(TIME WINDOW)│ │ │
│  │  └─────────────────┘ └─────────────────┘ └─────────────┘ │ │
│  └───────────────────────────────────────────────────────────┘ │
└─────────────────────────────────────────────────────────────────┘
                              ↑ base.* topics
┌─────────────────────────────────────────────────────────────────┐
│                     BASE DATA LAYER (Green)                     │
│                        Raw Financial Data                       │
│  ┌────────┐ ┌────────┐ ┌────────┐ ┌────────┐ ┌────────┐ ┌─────┐ │
│  │Holding │ │ Instr  │ │Account │ │ Cash   │ │ Order  │ │Price│ │
│  │        │ │        │ │        │ │        │ │        │ │     │ │
│  └────────┘ └────────┘ └────────┘ └────────┘ └────────┘ └─────┘ │
└─────────────────────────────────────────────────────────────────┘
                              ↑ Mock Data Generator
```

## Service Architecture

### **Mock Data Generator Service**
- **Purpose**: Standalone service that generates realistic financial data
- **Dependencies**: Uses `data-layer` models
- **Output**: Publishes to `base.*` Kafka topics
- **Scheduling**: SOD events (daily) + Intraday events (continuous)

### **View Server Service** 
- **Purpose**: Combined service containing Flink jobs, Kafka Streams, and WebSocket endpoints
- **Components**:
  - **Aggregation Layer**: Flink jobs for complex stream processing
  - **Computation Layer**: Kafka Streams for business logic and view calculations  
  - **WebSocket Layer**: Real-time updates to React clients
- **Input**: Consumes `base.*` and `aggregation.*` topics
- **Output**: Real-time WebSocket updates to React UI

### **React UI**
- **Purpose**: Dynamic frontend for portfolio visualization
- **Communication**: WebSocket connection to View Server
- **Features**: Real-time grid updates, view configuration

## Technology Stack

### **Stream Processing**
- **Apache Flink**: Complex multi-stream joins and temporal processing (in View Server)
- **Kafka Streams**: User view processing and materialized view management (in View Server)
- **Apache Kafka**: Core event streaming platform
- **Spring Boot**: Application framework and WebSocket server (View Server)

### **Data Storage**
- **Redis**: Materialized view caching and session state
- **Kafka Topics**: Event storage and stream processing
- **In-Memory State**: Real-time view computations

### **Frontend & Communication**
- **React 18 + Vite**: Dynamic grid visualization  
- **WebSocket**: Real-time view updates
- **REST API**: View configuration and management

## Project Structure

```
viewserverflink/
├── data-layer/                          # Base data models & Kafka producers
├── mock-data-generator/                 # Standalone data generation service
├── view-server/                         # Combined Flink + Kafka Streams + WebSocket
│   ├── aggregation/                     # Flink jobs (Layer 2)
│   ├── computation/                     # Kafka Streams (Layer 3) 
│   └── websocket/                       # WebSocket endpoints (Layer 4)
├── react-ui/                           # React frontend
├── shared-common/                       # Shared utilities
└── integration-tests/                   # End-to-end testing
```

## Data Flow

### **Kafka Topics Flow**

#### **Base Layer Topics** (from Mock Data Generator):
- `base.account` - Key: `accountId`
- `base.instrument` - Key: `instrumentId` (numeric)
- `base.sod-holding` - Key: `{date}#{instrumentId}#{accountId}`
- `base.price` - Key: `{instrumentId}#{date}`
- `base.intraday-cash` - Key: `{date}#{instrumentId}#{accountId}`
- `base.order-events` - Key: `orderId`

#### **Aggregation Layer Topics** (from Flink Jobs):
- `aggregation.enriched-holdings` - Holdings + Instruments + Prices joined
- `aggregation.market-values` - Orders + ETF + Market Values complex joins
- `aggregation.account-cash-summary` - Account + Cash rollups with time windows

#### **View Layer** (Kafka Streams → WebSocket):
- In-memory materialized views updated by Kafka Streams
- Real-time WebSocket pushes to React UI clients

### **Processing Pipeline**

1. **Mock Data Generator** → `base.*` topics
2. **Flink Jobs** consume `base.*` → produce `aggregation.*` topics  
3. **Kafka Streams** consume `aggregation.*` → update materialized views
4. **WebSocket handlers** push view updates → React UI clients

## Data Models
