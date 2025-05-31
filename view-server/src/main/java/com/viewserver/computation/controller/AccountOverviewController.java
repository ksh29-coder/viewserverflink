package com.viewserver.computation.controller;

import com.viewserver.computation.model.AccountOverviewRequest;
import com.viewserver.computation.model.AccountOverviewResponse;
import com.viewserver.computation.model.ViewMetadata;
import com.viewserver.computation.streams.AccountOverviewViewService;
import com.viewserver.viewserver.service.CacheService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * REST API controller for Account Overview views.
 * Provides endpoints to create, query, and manage dynamic views.
 */
@RestController
@RequestMapping("/api/account-overview")
@RequiredArgsConstructor
@Slf4j
public class AccountOverviewController {
    
    private final AccountOverviewViewService viewService;
    private final CacheService cacheService;
    
    /**
     * Create a new Account Overview view
     * POST /api/account-overview/views
     */
    @PostMapping("/views")
    public ResponseEntity<Map<String, Object>> createView(@RequestBody AccountOverviewRequest request) {
        log.info("Creating Account Overview view with request: {}", request);
        
        try {
            // Validate request
            if (!request.isValid()) {
                return ResponseEntity.badRequest().body(Map.of(
                    "status", "error",
                    "message", "Invalid request: missing required fields"
                ));
            }
            
            // Check if cache is ready (has data)
            if (cacheService.getAllUnifiedMV().isEmpty()) {
                return ResponseEntity.internalServerError().body(Map.of(
                    "status", "error", 
                    "message", "Cache is not ready yet - no unified-mv data available. Please wait a moment and try again."
                ));
            }
            
            // Create the view
            String viewId = viewService.createView(request);
            
            // Get initial data count for response
            List<AccountOverviewResponse> initialData = viewService.getCurrentViewData(viewId);
            
            return ResponseEntity.ok(Map.of(
                "status", "success",
                "viewId", viewId,
                "message", "View created successfully",
                "initialRowCount", initialData.size(),
                "cacheStats", cacheService.getCacheStats()
            ));
            
        } catch (Exception e) {
            log.error("Failed to create Account Overview view: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().body(Map.of(
                "status", "error",
                "message", "Failed to create view: " + e.getMessage()
            ));
        }
    }
    
    /**
     * Get current data for a view
     * GET /api/account-overview/views/{viewId}/data
     */
    @GetMapping("/views/{viewId}/data")
    public ResponseEntity<List<AccountOverviewResponse>> getViewData(@PathVariable("viewId") String viewId) {
        log.debug("Getting data for view: {}", viewId);
        
        try {
            List<AccountOverviewResponse> data = viewService.getCurrentViewData(viewId);
            return ResponseEntity.ok(data);
            
        } catch (Exception e) {
            log.error("Failed to get view data for {}: {}", viewId, e.getMessage(), e);
            return ResponseEntity.internalServerError().build();
        }
    }
    
    /**
     * Get metadata for a view
     * GET /api/account-overview/views/{viewId}/metadata
     */
    @GetMapping("/views/{viewId}/metadata")
    public ResponseEntity<ViewMetadata> getViewMetadata(@PathVariable("viewId") String viewId) {
        log.debug("Getting metadata for view: {}", viewId);
        
        ViewMetadata metadata = viewService.getViewMetadata(viewId);
        if (metadata == null) {
            return ResponseEntity.notFound().build();
        }
        
        return ResponseEntity.ok(metadata);
    }
    
    /**
     * Destroy a view
     * DELETE /api/account-overview/views/{viewId}
     */
    @DeleteMapping("/views/{viewId}")
    public ResponseEntity<Map<String, String>> destroyView(@PathVariable("viewId") String viewId) {
        log.info("Destroying view: {}", viewId);
        
        try {
            viewService.destroyView(viewId);
            return ResponseEntity.ok(Map.of(
                "status", "success",
                "message", "View destroyed successfully"
            ));
            
        } catch (Exception e) {
            log.error("Failed to destroy view {}: {}", viewId, e.getMessage(), e);
            return ResponseEntity.internalServerError().body(Map.of(
                "status", "error",
                "message", "Failed to destroy view: " + e.getMessage()
            ));
        }
    }
    
    /**
     * Get all active views
     * GET /api/account-overview/views
     */
    @GetMapping("/views")
    public ResponseEntity<Set<String>> getActiveViews() {
        log.debug("Getting all active views");
        
        Set<String> activeViews = viewService.getActiveViewIds();
        return ResponseEntity.ok(activeViews);
    }
    
    /**
     * Get cache statistics
     * GET /api/account-overview/cache/stats
     */
    @GetMapping("/cache/stats")
    public ResponseEntity<CacheService.CacheStats> getCacheStats() {
        log.debug("Getting cache statistics");
        
        CacheService.CacheStats stats = cacheService.getCacheStats();
        return ResponseEntity.ok(stats);
    }
    
    /**
     * Get available grouping fields for view configuration
     * GET /api/account-overview/groupby-fields
     */
    @GetMapping("/groupby-fields")
    public ResponseEntity<List<String>> getGroupByFields() {
        log.debug("Getting available grouping fields");
        
        // Return the available grouping fields that can be used in views
        List<String> fields = List.of(
            "accountName",
            "instrumentName", 
            "instrumentType",
            "currency",
            "countryOfRisk",
            "countryOfDomicile",
            "sector",
            "orderStatus",
            "orderType",
            "venue"
        );
        
        return ResponseEntity.ok(fields);
    }
    
    /**
     * Health check for the service
     * GET /api/account-overview/health
     */
    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> health() {
        boolean cacheReady = !cacheService.getAllUnifiedMV().isEmpty();
        Set<String> activeViews = viewService.getActiveViewIds();
        
        return ResponseEntity.ok(Map.of(
            "status", cacheReady ? "UP" : "DOWN",
            "cacheReady", cacheReady,
            "activeViewCount", activeViews.size(),
            "activeViews", activeViews,
            "cacheStats", cacheService.getCacheStats()
        ));
    }
} 