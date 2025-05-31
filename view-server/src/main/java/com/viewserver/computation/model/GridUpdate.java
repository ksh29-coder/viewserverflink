package com.viewserver.computation.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * Model for incremental grid updates sent via WebSocket.
 * Contains only the changes, not the full dataset.
 */
@Data
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
public class GridUpdate {
    
    /**
     * Type of WebSocket message
     */
    private String type = "GRID_UPDATE";
    
    /**
     * View ID this update belongs to
     */
    private String viewId;
    
    /**
     * List of individual row changes
     */
    private List<RowChange> changes;
    
    /**
     * Timestamp when this update was generated
     */
    private LocalDateTime timestamp;
    
    /**
     * Individual row change within a grid update
     */
    @Data
    @Builder(toBuilder = true)
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RowChange {
        
        /**
         * Type of change
         */
        private ChangeType changeType;
        
        /**
         * Unique row identifier
         */
        private String rowKey;
        
        /**
         * Fields that changed in this row
         * Key = field name, Value = FieldChange with old/new values
         */
        private Map<String, FieldChange> changedFields;
        
        /**
         * Complete row data (for INSERT operations)
         */
        private AccountOverviewResponse completeRow;
        
        /**
         * Type of row change
         */
        public enum ChangeType {
            INSERT,     // New row added
            UPDATE,     // Existing row modified
            DELETE      // Row removed
        }
    }
    
    /**
     * Individual field change within a row
     */
    @Data
    @Builder(toBuilder = true)
    @NoArgsConstructor
    @AllArgsConstructor
    public static class FieldChange {
        
        /**
         * Previous value (for UPDATE operations)
         */
        private Object oldValue;
        
        /**
         * New value
         */
        private Object newValue;
        
        /**
         * Field data type for proper client-side handling
         */
        private FieldType fieldType;
        
        /**
         * Field data types
         */
        public enum FieldType {
            STRING,
            DECIMAL,
            INTEGER,
            DATETIME,
            BOOLEAN
        }
        
        /**
         * Create a field change for BigDecimal values
         */
        public static FieldChange forDecimal(BigDecimal oldValue, BigDecimal newValue) {
            return FieldChange.builder()
                    .oldValue(oldValue)
                    .newValue(newValue)
                    .fieldType(FieldType.DECIMAL)
                    .build();
        }
        
        /**
         * Create a field change for String values
         */
        public static FieldChange forString(String oldValue, String newValue) {
            return FieldChange.builder()
                    .oldValue(oldValue)
                    .newValue(newValue)
                    .fieldType(FieldType.STRING)
                    .build();
        }
        
        /**
         * Create a field change for DateTime values
         */
        public static FieldChange forDateTime(LocalDateTime oldValue, LocalDateTime newValue) {
            return FieldChange.builder()
                    .oldValue(oldValue)
                    .newValue(newValue)
                    .fieldType(FieldType.DATETIME)
                    .build();
        }
        
        /**
         * Create a field change for Integer values
         */
        public static FieldChange forInteger(Integer oldValue, Integer newValue) {
            return FieldChange.builder()
                    .oldValue(oldValue)
                    .newValue(newValue)
                    .fieldType(FieldType.INTEGER)
                    .build();
        }
    }
    
    /**
     * Create a grid update with a single row change
     */
    public static GridUpdate singleRowUpdate(String viewId, RowChange rowChange) {
        return GridUpdate.builder()
                .viewId(viewId)
                .changes(List.of(rowChange))
                .timestamp(LocalDateTime.now())
                .build();
    }
    
    /**
     * Create an INSERT row change
     */
    public static RowChange insertRow(String rowKey, AccountOverviewResponse completeRow) {
        return RowChange.builder()
                .changeType(RowChange.ChangeType.INSERT)
                .rowKey(rowKey)
                .completeRow(completeRow)
                .build();
    }
    
    /**
     * Create an UPDATE row change
     */
    public static RowChange updateRow(String rowKey, Map<String, FieldChange> changedFields) {
        return RowChange.builder()
                .changeType(RowChange.ChangeType.UPDATE)
                .rowKey(rowKey)
                .changedFields(changedFields)
                .build();
    }
    
    /**
     * Create a DELETE row change
     */
    public static RowChange deleteRow(String rowKey) {
        return RowChange.builder()
                .changeType(RowChange.ChangeType.DELETE)
                .rowKey(rowKey)
                .build();
    }
} 