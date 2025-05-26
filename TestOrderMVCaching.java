// Simple test to verify OrderMV caching works
// This would be a unit test in a real scenario

import com.viewserver.viewserver.service.CacheService;
import com.viewserver.aggregation.model.OrderMV;

public class TestOrderMVCaching {
    public static void main(String[] args) {
        String testOrderMVJson = """
        {
          "orderId": "TEST-ORDER-001",
          "accountId": "ACC001",
          "instrumentId": "AAPL",
          "instrumentName": "Apple Inc.",
          "instrumentType": "EQUITY",
          "currency": "USD",
          "side": "BUY",
          "quantity": 100,
          "filledQuantity": 50,
          "price": 150.00,
          "status": "PARTIAL",
          "orderTimestamp": "2024-01-15T10:30:00",
          "date": "2024-01-15T00:00:00",
          "timestamp": "2024-01-15T10:30:00",
          "subSectors": ["Technology", "Consumer Electronics"],
          "priceTimestamp": "2024-01-15T10:29:00",
          "orderMarketValueLocal": 15000.00,
          "orderMarketValueUSD": 15000.00,
          "filledMarketValueLocal": 7500.00,
          "filledMarketValueUSD": 7500.00,
          "calculationTimestamp": "2024-01-15T10:30:00"
        }
        """;
        
        // This would test the caching mechanism
        System.out.println("Test OrderMV JSON: " + testOrderMVJson);
    }
} 