package com.viewserver.mockdata.controller;

import com.viewserver.mockdata.generator.SODHoldingGenerator;
import com.viewserver.mockdata.generator.StaticDataGenerator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * REST controller for manually triggering data generation.
 * Separates static/SOD data (on-demand) from dynamic data (continuous).
 */
@RestController
@RequestMapping("/api/data-generation")
@RequiredArgsConstructor
@Slf4j
public class DataGenerationController {
    
    private final StaticDataGenerator staticDataGenerator;
    private final SODHoldingGenerator sodHoldingGenerator;
    
    /**
     * Trigger static data generation (accounts + instruments)
     */
    @PostMapping("/static")
    public ResponseEntity<Map<String, String>> generateStaticData() {
        log.info("Manually triggering static data generation via REST API");
        try {
            staticDataGenerator.generateStaticDataNow();
            return ResponseEntity.ok(Map.of(
                "status", "success",
                "message", "Static data generation triggered successfully"
            ));
        } catch (Exception e) {
            log.error("Failed to trigger static data generation", e);
            return ResponseEntity.internalServerError().body(Map.of(
                "status", "error",
                "message", "Failed to trigger static data generation: " + e.getMessage()
            ));
        }
    }
    
    /**
     * Trigger SOD holdings generation
     */
    @PostMapping("/sod-holdings")
    public ResponseEntity<Map<String, String>> generateSODHoldings() {
        log.info("Manually triggering SOD holdings generation via REST API");
        try {
            sodHoldingGenerator.generateSODHoldingsNow();
            return ResponseEntity.ok(Map.of(
                "status", "success",
                "message", "SOD holdings generation triggered successfully"
            ));
        } catch (Exception e) {
            log.error("Failed to trigger SOD holdings generation", e);
            return ResponseEntity.internalServerError().body(Map.of(
                "status", "error",
                "message", "Failed to trigger SOD holdings generation: " + e.getMessage()
            ));
        }
    }
    
    /**
     * Trigger both static data and SOD holdings generation together
     * This sets up the complete initial state
     */
    @PostMapping("/initialize")
    public ResponseEntity<Map<String, String>> initializeAllData() {
        log.info("Initializing complete data set (static + SOD holdings) via REST API");
        try {
            // First generate static data
            staticDataGenerator.generateStaticDataNow();
            
            // Then generate SOD holdings (which depends on static data)
            // Add a small delay to ensure static data is published first
            Thread.sleep(2000);
            sodHoldingGenerator.generateSODHoldingsNow();
            
            return ResponseEntity.ok(Map.of(
                "status", "success",
                "message", "Complete data initialization triggered successfully (static data + SOD holdings)"
            ));
        } catch (Exception e) {
            log.error("Failed to initialize complete data set", e);
            return ResponseEntity.internalServerError().body(Map.of(
                "status", "error",
                "message", "Failed to initialize data: " + e.getMessage()
            ));
        }
    }
    
    /**
     * Get status of data generation capabilities
     */
    @GetMapping("/status")
    public ResponseEntity<Map<String, Object>> getStatus() {
        return ResponseEntity.ok(Map.of(
            "static-data", Map.of(
                "endpoint", "/api/data-generation/static",
                "description", "Generate accounts and instruments"
            ),
            "sod-holdings", Map.of(
                "endpoint", "/api/data-generation/sod-holdings", 
                "description", "Generate start-of-day holdings"
            ),
            "initialize", Map.of(
                "endpoint", "/api/data-generation/initialize",
                "description", "Generate static data + SOD holdings together"
            )
        ));
    }
} 