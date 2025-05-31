package com.viewserver.websocket.handlers;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.viewserver.computation.model.AccountOverviewResponse;
import com.viewserver.computation.model.GridUpdate;
import com.viewserver.computation.streams.AccountOverviewViewService;
import jakarta.websocket.*;
import jakarta.websocket.server.PathParam;
import jakarta.websocket.server.ServerEndpoint;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * WebSocket handler for Account Overview views.
 * Integrates with the Global State Store approach for real-time updates.
 */
@Slf4j
@Component
@ServerEndpoint("/ws/account-overview/{viewId}")
public class AccountOverviewWebSocketHandler implements AccountOverviewViewService.ViewChangeListener {
    
    private static AccountOverviewViewService viewService;
    private static final ObjectMapper objectMapper = new ObjectMapper();
    
    // Active WebSocket sessions per view
    private static final Map<String, Session> activeSessions = new ConcurrentHashMap<>();
    
    // Pending updates for batching (reduces WebSocket message spam)
    private static final Map<String, List<GridUpdate.RowChange>> pendingUpdates = new ConcurrentHashMap<>();
    
    // View ID to session mapping for change notifications
    private static final Map<String, String> sessionToViewMapping = new ConcurrentHashMap<>();
    
    // Scheduled executor for batched updates
    private static final ScheduledExecutorService updateScheduler = Executors.newScheduledThreadPool(2);
    
    static {
        // Register handler for all incoming messages
        objectMapper.findAndRegisterModules(); // For LocalDateTime serialization
        
        // Start batched update processor (every 100ms)
        updateScheduler.scheduleAtFixedRate(
            AccountOverviewWebSocketHandler::processPendingUpdates, 
            100, 100, TimeUnit.MILLISECONDS
        );
    }
    
    @Autowired
    public void setViewService(AccountOverviewViewService viewService) {
        AccountOverviewWebSocketHandler.viewService = viewService;
    }
    
    @OnOpen
    public void onOpen(Session session, @PathParam("viewId") String viewId) {
        log.info("WebSocket connection opening for view: {}", viewId);
        
        try {
            // Store session
            activeSessions.put(viewId, session);
            sessionToViewMapping.put(session.getId(), viewId);
            
            // Register this handler as a listener for real-time updates
            viewService.addConnection(viewId, this);
            
            // Send initial connection confirmation
            sendMessage(session, Map.of(
                    "type", "CONNECTION_ESTABLISHED",
                    "viewId", viewId,
                    "timestamp", LocalDateTime.now(),
                    "message", "WebSocket connection established for view " + viewId
            ));
            
            // Get and send initial view data using Redis Cache
            try {
                List<AccountOverviewResponse> initialData = viewService.getCurrentViewData(viewId);
                
                sendMessage(session, Map.of(
                        "type", "VIEW_READY",
                        "viewId", viewId,
                        "timestamp", LocalDateTime.now(),
                        "initialData", initialData,
                        "rowCount", initialData.size(),
                        "message", "Initial data loaded from Redis Cache for view " + viewId
                ));
                
                log.info("✅ Sent initial data ({} rows) from Redis Cache for view: {}", initialData.size(), viewId);
            } catch (Exception e) {
                log.error("❌ Error sending initial data for view {}: {}", viewId, e.getMessage(), e);
                sendMessage(session, Map.of(
                        "type", "ERROR",
                        "viewId", viewId,
                        "timestamp", LocalDateTime.now(),
                        "error", "Failed to load initial data: " + e.getMessage()
                ));
            }
            
            log.info("✅ WebSocket connection established for view: {}", viewId);
            
        } catch (Exception e) {
            log.error("❌ Error opening WebSocket for view {}: {}", viewId, e.getMessage(), e);
            try {
                session.close(new CloseReason(CloseReason.CloseCodes.UNEXPECTED_CONDITION, 
                                            "Server error: " + e.getMessage()));
            } catch (IOException ioException) {
                log.error("Error closing WebSocket session: {}", ioException.getMessage());
            }
        }
    }
    
    @OnClose
    public void onClose(Session session, @PathParam("viewId") String viewId, CloseReason closeReason) {
        log.info("WebSocket closed for view: {} - Reason: {}", viewId, closeReason.getReasonPhrase());
        
        try {
            // Remove session mappings
            activeSessions.remove(viewId);
            sessionToViewMapping.remove(session.getId());
            
            // Remove connection from view
            viewService.removeConnection(viewId);
            
            // Clean up pending updates for this view
            pendingUpdates.remove(viewId);
            
            log.info("✅ WebSocket cleanup completed for view: {}", viewId);
            
        } catch (Exception e) {
            log.error("❌ Error during WebSocket cleanup for view {}: {}", viewId, e.getMessage(), e);
        }
    }
    
    @OnMessage
    public void onMessage(String message, Session session, @PathParam("viewId") String viewId) {
        log.debug("WebSocket message received for view {}: {}", viewId, message);
        
        try {
            // Parse message
            Map<String, Object> messageMap = objectMapper.readValue(message, Map.class);
            String messageType = (String) messageMap.get("type");
            
            switch (messageType) {
                case "PING":
                    // Respond to ping with pong
                    sendMessage(session, Map.of(
                            "type", "PONG",
                            "viewId", viewId,
                            "timestamp", LocalDateTime.now()
                    ));
                    break;
                    
                case "REQUEST_REFRESH":
                    // Send fresh data from Global State Store
                    try {
                        List<AccountOverviewResponse> freshData = viewService.getCurrentViewData(viewId);
                        sendMessage(session, Map.of(
                                "type", "VIEW_REFRESHED",
                                "viewId", viewId,
                                "timestamp", LocalDateTime.now(),
                                "data", freshData,
                                "rowCount", freshData.size(),
                                "message", "Data refreshed from Global State Store"
                        ));
                        log.info("Sent refreshed data ({} rows) for view: {}", freshData.size(), viewId);
                    } catch (Exception e) {
                        log.error("Error refreshing data for view {}: {}", viewId, e.getMessage(), e);
                        sendMessage(session, Map.of(
                                "type", "ERROR",
                                "viewId", viewId,
                                "timestamp", LocalDateTime.now(),
                                "error", "Failed to refresh data: " + e.getMessage()
                        ));
                    }
                    break;
                    
                case "PAUSE_UPDATES":
                    // Pause updates for this view (could be implemented)
                    log.info("Pause updates requested for view: {}", viewId);
                    break;
                    
                case "RESUME_UPDATES":
                    // Resume updates for this view (could be implemented)
                    log.info("Resume updates requested for view: {}", viewId);
                    break;
                    
                default:
                    log.warn("Unknown message type received: {}", messageType);
            }
            
        } catch (Exception e) {
            log.error("Error processing WebSocket message for view {}: {}", viewId, e.getMessage(), e);
        }
    }
    
    @OnError
    public void onError(Session session, @PathParam("viewId") String viewId, Throwable throwable) {
        log.error("WebSocket error for view {}: {}", viewId, throwable.getMessage(), throwable);
        
        try {
            // Clean up on error
            activeSessions.remove(viewId);
            sessionToViewMapping.remove(session.getId());
            viewService.removeConnection(viewId);
            pendingUpdates.remove(viewId);
            
        } catch (Exception e) {
            log.error("Error during WebSocket error cleanup: {}", e.getMessage(), e);
        }
    }
    
    /**
     * ViewChangeListener implementation - called when Global State Store changes
     */
    @Override
    public void onRowChange(String viewId, GridUpdate.RowChange rowChange) {
        log.debug("Received row change for view {}: {}", viewId, rowChange.getChangeType());
        
        // Add to pending updates for batching
        pendingUpdates.computeIfAbsent(viewId, k -> new ArrayList<>()).add(rowChange);
    }
    
    /**
     * ViewChangeListener implementation - called when view is ready with initial data
     */
    @Override
    public void onViewReady(String viewId, List<AccountOverviewResponse> initialData) {
        log.info("View ready notification for {}: {} rows", viewId, initialData.size());
        
        Session session = activeSessions.get(viewId);
        if (session != null && session.isOpen()) {
            sendMessage(session, Map.of(
                    "type", "VIEW_READY",
                    "viewId", viewId,
                    "timestamp", LocalDateTime.now(),
                    "initialData", initialData,
                    "rowCount", initialData.size(),
                    "message", "View ready with initial data from Global State Store"
            ));
        }
    }
    
    /**
     * ViewChangeListener implementation - called when view encounters an error
     */
    @Override
    public void onError(String viewId, Exception error) {
        log.error("View error notification for {}: {}", viewId, error.getMessage());
        
        Session session = activeSessions.get(viewId);
        if (session != null && session.isOpen()) {
            sendMessage(session, Map.of(
                    "type", "VIEW_ERROR",
                    "viewId", viewId,
                    "timestamp", LocalDateTime.now(),
                    "error", error.getMessage(),
                    "message", "View encountered an error"
            ));
        }
    }
    
    /**
     * Process pending updates and send batched messages
     */
    private static void processPendingUpdates() {
        for (Map.Entry<String, List<GridUpdate.RowChange>> entry : pendingUpdates.entrySet()) {
            String viewId = entry.getKey();
            List<GridUpdate.RowChange> changes = entry.getValue();
            
            if (changes.isEmpty()) {
                continue;
            }
            
            Session session = activeSessions.get(viewId);
            if (session == null || !session.isOpen()) {
                // Clean up if session is gone
                pendingUpdates.remove(viewId);
                continue;
            }
            
            try {
                // Create batched grid update
                GridUpdate gridUpdate = GridUpdate.builder()
                        .type("GRID_UPDATE")
                        .viewId(viewId)
                        .changes(new ArrayList<>(changes))
                        .timestamp(LocalDateTime.now())
                        .build();
                
                // Send the update
                sendMessage(session, gridUpdate);
                
                log.debug("Sent batched update with {} changes for view: {}", changes.size(), viewId);
                
                // Clear processed changes
                changes.clear();
                
            } catch (Exception e) {
                log.error("Error sending batched update for view {}: {}", viewId, e.getMessage(), e);
            }
        }
    }
    
    /**
     * Send a message to a WebSocket session
     */
    private static void sendMessage(Session session, Object message) {
        if (session == null || !session.isOpen()) {
            return;
        }
        
        try {
            String json = objectMapper.writeValueAsString(message);
            session.getBasicRemote().sendText(json);
            
        } catch (Exception e) {
            log.error("Error sending WebSocket message: {}", e.getMessage(), e);
        }
    }
    
    /**
     * Get active session count
     */
    public static int getActiveSessionCount() {
        return activeSessions.size();
    }
    
    /**
     * Get active sessions for monitoring
     */
    public static Set<String> getActiveViewIds() {
        return new HashSet<>(activeSessions.keySet());
    }
    
    /**
     * Send a direct message to a specific view's WebSocket
     */
    public static void sendDirectMessage(String viewId, Object message) {
        Session session = activeSessions.get(viewId);
        if (session != null && session.isOpen()) {
            sendMessage(session, message);
        }
    }
    
    /**
     * Cleanup method for graceful shutdown
     */
    public static void shutdown() {
        log.info("🛑 Shutting down AccountOverviewWebSocketHandler...");
        
        // Close all active sessions
        for (Map.Entry<String, Session> entry : activeSessions.entrySet()) {
            try {
                Session session = entry.getValue();
                if (session.isOpen()) {
                    session.close(new CloseReason(CloseReason.CloseCodes.GOING_AWAY, "Server shutdown"));
                }
            } catch (Exception e) {
                log.warn("Error closing WebSocket session during shutdown: {}", e.getMessage());
            }
        }
        
        // Clear collections
        activeSessions.clear();
        sessionToViewMapping.clear();
        pendingUpdates.clear();
        
        // Shutdown scheduler
        updateScheduler.shutdown();
        
        log.info("✅ AccountOverviewWebSocketHandler shutdown completed");
    }
} 