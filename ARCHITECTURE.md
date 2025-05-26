# View Server Performance POC - Advanced Architecture

## System Overview
A horizontally scalable **4-layer materialized view system** for real-time financial data processing. The system implements dynamic, user-configurable portfolio views with sub-50ms latency using a **unified Apache Flink job** for price consistency and Kafka Streams for user view computations.

## Architecture Philosophy

### **4-Layer Materialized View Pipeline**
```
┌─────────────────────────────────────────────────────────────────┐
│                    CLIENT VIEWS LAYER (Purple)                  │
│                     React UI + WebSocket                        │
│  ┌─────────────────┐ ┌─────────────────┐ ┌─────────────────┐   │
│  │ Account Overview│ │ Cash-Only View  │ │ PID Carve-Out   │   │
│  │ (WebSocket)     │ │ (WebSocket)     │ │ (WebSocket)     │   │
│  │ Real-time Grid  │ │ Real-time Grid  │ │ Real-time Grid  │   │
│  └─────────────────┘ └─────────────────┘ └─────────────────┘   │
└─────────────────────────────────────────────────────────────────┘
                              ↑ Real-time WebSocket Updates (100ms batching)
┌─────────────────────────────────────────────────────────────────┐
│                       VIEW SERVER                               │
│                                                                 │
│  ┌───────────────────────────────────────────────────────────┐ │
│  │             COMPUTATION LAYER (Orange)                    │ │
│  │              Business Logic & View Processing             │ │
│  │  ┌─────────────────┐ ┌─────────────────┐ ┌─────────────┐ │ │
│  │  │ Account Overview│ │ Attribution     │ │ Risk & Perf │ │ │
│  │  │ Dynamic Views   │ │ Analysis        │ │ Calculations│ │ │
│  │  │ (Kafka Streams) │ │ (Kafka Streams) │ │(Kafka Strms)│ │ │
│  │  │ • SOD/Curr/Exp  │ │ • Performance   │ │ • VaR       │ │ │
│  │  │ • Dynamic Group │ │ • Attribution   │ │ • Tracking  │ │ │
│  │  │ • Real-time Δ   │ │ • Benchmarking  │ │ • Exposure  │ │ │
│  │  └─────────────────┘ └─────────────────┘ └─────────────┘ │ │
│  └───────────────────────────────────────────────────────────┘ │
│                              ↑ aggregation.unified-mv           │
│  ┌───────────────────────────────────────────────────────────┐ │
│  │             AGGREGATION LAYER (Blue)                     │ │
│  │             **UNIFIED** Stream Processing (Flink)        │ │
│  │  ┌─────────────────────────────────────────────────────┐ │ │
│  │  │              UNIFIED MARKET VALUE JOB               │ │ │
│  │  │  ┌─────────────┐ ┌─────────────┐ ┌─────────────┐   │ │ │
│  │  │  │ Holdings +  │ │ Orders +    │ │ **SHARED**  │   │ │ │
│  │  │  │ Instruments │ │ Instruments │ │ Price State │   │ │ │
│  │  │  │ (JOIN)      │ │ (JOIN)      │ │ (CRITICAL)  │   │ │ │
│  │  │  └─────────────┘ └─────────────┘ └─────────────┘   │ │ │
│  │  │  ↓ HoldingMV    ↓ OrderMV       ↓ Same Price      │ │ │
│  │  │  • Consistent Price Timestamps                     │ │ │
│  │  │  • Guaranteed Data Consistency                     │ │ │
│  │  │  • Single Source of Truth                          │ │ │
│  │  └─────────────────────────────────────────────────────┘ │ │
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
- **Purpose**: Combined service containing unified Flink job, Kafka Streams, and WebSocket endpoints
- **Components**:
  - **Aggregation Layer**: **Unified Flink job** for price-consistent stream processing
  - **Computation Layer**: Kafka Streams for business logic and dynamic view calculations  
  - **WebSocket Layer**: Real-time incremental updates to React clients
- **Input**: Consumes `base.*` topics, produces `aggregation.unified-mv`
- **Output**: Real-time WebSocket updates with 100ms batching

### **React UI**
- **Purpose**: Dynamic frontend for portfolio visualization
- **Communication**: WebSocket connection to View Server
- **Features**: Real-time grid updates with cell-level change indicators

## Technology Stack

### **Stream Processing**
- **Apache Flink**: **Unified Market Value Job** for price-consistent processing
- **Kafka Streams**: Dynamic user view processing and materialized view management
- **Apache Kafka**: Core event streaming platform
- **Spring Boot**: Application framework and WebSocket server

### **Data Storage**
- **Redis**: Materialized view caching and session state
- **Kafka Topics**: Event storage and stream processing
- **In-Memory State**: Real-time view computations with change detection

### **Frontend & Communication**
- **React 18 + Vite**: Dynamic grid visualization with AG-Grid
- **WebSocket**: Real-time incremental updates (cell-level changes)
- **REST API**: View configuration and management

## Project Structure

```
viewserverflink/
├── data-layer/                          # Base data models & Kafka producers
├── mock-data-generator/                 # Standalone data generation service
├── view-server/                         # Combined Flink + Kafka Streams + WebSocket
│   ├── aggregation/                     # Unified Flink job (Layer 2)
│   │   └── UnifiedMarketValueJob.java   # **NEW: Price-consistent processing**
│   ├── computation/                     # Kafka Streams (Layer 3) 
│   │   ├── AccountOverviewViewService   # **NEW: Dynamic view creation**
│   │   ├── ViewLifecycleManager         # **NEW: View management**
│   │   └── ChangeDetectionService       # **NEW: Incremental updates**
│   └── websocket/                       # WebSocket endpoints (Layer 4)
│       └── AccountOverviewHandler       # **NEW: Real-time grid updates**
├── react-ui/                           # React frontend
│   └── pages/AccountOverview.tsx        # **NEW: Dynamic grid page**
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

#### **Aggregation Layer Topics** (from Unified Flink Job):
- `aggregation.unified-mv` - **NEW: Single topic with both HoldingMV and OrderMV**
  - **Key**: `{type}#{instrumentId}#{accountId}` where type = "HOLDING" | "ORDER"
  - **Value**: Union type containing either HoldingMV or OrderMV
  - **Guarantee**: Same `priceTimestamp` for same `instrumentId` across both types

#### **View Layer** (Kafka Streams → WebSocket):
- In-memory materialized views updated by Kafka Streams
- Real-time WebSocket pushes with incremental changes (100ms batching)
- Cell-level change detection and visual indicators

### **Processing Pipeline**

1. **Mock Data Generator** → `base.*` topics
2. **Unified Flink Job** consumes `base.*` → produces `aggregation.unified-mv` topic  
3. **Kafka Streams** consume `aggregation.unified-mv` → update dynamic materialized views
4. **Change Detection** monitors view changes → triggers WebSocket updates
5. **WebSocket handlers** push incremental changes → React UI clients

## Critical Data Consistency Architecture

### **Unified Market Value Job (Flink)**
```java
// Single Flink job that ensures price consistency
public class UnifiedMarketValueJob {
    
    // Shared state keyed by instrumentId
    private ValueState<Price> priceState;
    private ValueState<List<SODHolding>> holdingsState;
    private ValueState<List<Order>> ordersState;
    private ValueState<Instrument> instrumentState;
    
    // When price updates arrive:
    // 1. Update shared price state
    // 2. Calculate HoldingMV for all holdings (same price)
    // 3. Calculate OrderMV for all orders (same price)
    // 4. Emit both with identical priceTimestamp
}
```

### **Price Consistency Guarantees**
- **Single Source**: One Flink job processes all price updates
- **Shared State**: Same price state used for both holdings and orders
- **Atomic Updates**: HoldingMV and OrderMV calculated in same processing cycle
- **Timestamp Validation**: Account Overview rejects mismatched price timestamps

### **Account Overview Dynamic Views**
```java
// Kafka Streams topology created dynamically per user selection
public class AccountOverviewViewService {
    
    public String createView(AccountOverviewRequest request) {
        // 1. Create unique viewId
        // 2. Build dynamic Kafka Streams topology
        // 3. Join HoldingMV + OrderMV (with price timestamp validation)
        // 4. Group by user-selected fields
        // 5. Aggregate SOD/Current/Expected NAV
        // 6. Enable change detection for WebSocket updates
    }
}
```

## Data Models

### **Unified Market Value Output**
```java
@Data
public class UnifiedMarketValue {
    private String type; // "HOLDING" | "ORDER"
    private String instrumentId;
    private String accountId;
    private LocalDateTime priceTimestamp; // **CRITICAL: Same for both types**
    private BigDecimal price;             // **CRITICAL: Same for both types**
    
    // Type-specific data (union)
    private HoldingMV holdingMV;  // null if type = "ORDER"
    private OrderMV orderMV;      // null if type = "HOLDING"
}
```

### **Account Overview Result**
```java
@Data
public class AccountOverviewResult {
    private String groupKey;                    // Dynamic grouping key
    private Map<String, String> groupFields;   // Dynamic group field values
    private BigDecimal sodNavUSD;              // sum(holdingMv.marketValueUSD)
    private BigDecimal currentNavUSD;          // sum(holdingMv + orderMv.filled)
    private BigDecimal expectedNavUSD;         // sum(holdingMv + orderMv.order)
    private LocalDateTime lastUpdated;
    private LocalDateTime priceTimestamp;      // For consistency validation
}
```

## Performance & Scalability

### **Real-Time Requirements**
- **Price Consistency**: 100% guaranteed (no temporal mismatches)
- **View Creation**: < 500ms from selection to first data
- **Update Latency**: < 100ms from data change to grid update
- **Batching**: 100ms aggregation window for rapid changes
- **Visual Feedback**: 2-second cell highlighting for changes

### **Memory Management**
- **View Lifecycle**: Auto-cleanup after 1-minute disconnection
- **State Management**: Kafka Streams state stores with TTL
- **Change Detection**: Efficient delta calculation and caching

### **Monitoring & Observability**
- **Active Views API**: Real-time view monitoring
- **Price Consistency Metrics**: Timestamp validation tracking
- **Performance Metrics**: Latency and throughput monitoring
- **Error Handling**: Graceful degradation for data inconsistencies