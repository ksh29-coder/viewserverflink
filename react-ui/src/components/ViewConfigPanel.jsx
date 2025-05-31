import React, { useState } from 'react'

const ViewConfigPanel = ({ activeViews, currentViewId, onDeleteView, onRefreshViews }) => {
  const [isExpanded, setIsExpanded] = useState(false)

  const formatDateTime = (dateTimeString) => {
    if (!dateTimeString) return 'Never'
    const date = new Date(dateTimeString)
    return date.toLocaleString()
  }

  const getStatusColor = (status) => {
    const colors = {
      CREATING: '#ffc107',
      ACTIVE: '#28a745',
      IDLE: '#6c757d',
      CLEANUP_PENDING: '#fd7e14',
      DESTROYED: '#dc3545'
    }
    return colors[status] || '#6c757d'
  }

  const getStatusIcon = (status) => {
    const icons = {
      CREATING: '🔄',
      ACTIVE: '🟢',
      IDLE: '⏸️',
      CLEANUP_PENDING: '🗑️',
      DESTROYED: '❌'
    }
    return icons[status] || '❓'
  }

  return (
    <div style={{ 
      marginTop: '30px',
      border: '1px solid #dee2e6',
      borderRadius: '8px',
      backgroundColor: 'white'
    }}>
      {/* Panel Header */}
      <div 
        style={{ 
          padding: '15px 20px',
          backgroundColor: '#f8f9fa',
          borderBottom: '1px solid #dee2e6',
          borderRadius: '8px 8px 0 0',
          cursor: 'pointer',
          display: 'flex',
          justifyContent: 'space-between',
          alignItems: 'center'
        }}
        onClick={() => setIsExpanded(!isExpanded)}
      >
        <div>
          <h4 style={{ margin: 0, color: '#333' }}>
            View Management Panel
          </h4>
          <div style={{ fontSize: '12px', color: '#6c757d', marginTop: '2px' }}>
            {activeViews.length} active views • Click to {isExpanded ? 'collapse' : 'expand'}
          </div>
        </div>
        
        <div style={{ display: 'flex', alignItems: 'center', gap: '10px' }}>
          <button
            onClick={(e) => {
              e.stopPropagation()
              onRefreshViews()
            }}
            style={{
              padding: '6px 12px',
              backgroundColor: '#007bff',
              color: 'white',
              border: 'none',
              borderRadius: '4px',
              cursor: 'pointer',
              fontSize: '12px'
            }}
          >
            🔄 Refresh
          </button>
          
          <span style={{ fontSize: '18px' }}>
            {isExpanded ? '▲' : '▼'}
          </span>
        </div>
      </div>

      {/* Panel Content */}
      {isExpanded && (
        <div style={{ padding: '20px' }}>
          {activeViews.length === 0 ? (
            <div style={{ 
              textAlign: 'center', 
              padding: '40px',
              color: '#6c757d'
            }}>
              <div style={{ fontSize: '48px', marginBottom: '10px' }}>📋</div>
              <div style={{ fontSize: '16px', marginBottom: '5px' }}>No Active Views</div>
              <div style={{ fontSize: '14px' }}>
                Create a view by configuring the options above
              </div>
            </div>
          ) : (
            <div style={{ 
              display: 'grid', 
              gap: '15px'
            }}>
              {activeViews.map(view => (
                <div
                  key={view.viewId}
                  style={{
                    border: view.viewId === currentViewId ? '2px solid #007bff' : '1px solid #dee2e6',
                    borderRadius: '6px',
                    padding: '15px',
                    backgroundColor: view.viewId === currentViewId ? '#e7f3ff' : '#f8f9fa'
                  }}
                >
                  {/* View Header */}
                  <div style={{ 
                    display: 'flex', 
                    justifyContent: 'space-between', 
                    alignItems: 'flex-start',
                    marginBottom: '10px'
                  }}>
                    <div style={{ flex: 1 }}>
                      <div style={{ 
                        display: 'flex', 
                        alignItems: 'center', 
                        gap: '8px',
                        marginBottom: '5px'
                      }}>
                        <span style={{ fontSize: '16px' }}>
                          {getStatusIcon(view.status)}
                        </span>
                        <h5 style={{ 
                          margin: 0, 
                          color: '#333',
                          fontSize: '16px'
                        }}>
                          {view.viewName || `View ${view.viewId.substring(0, 8)}`}
                        </h5>
                        {view.viewId === currentViewId && (
                          <span style={{
                            padding: '2px 6px',
                            backgroundColor: '#007bff',
                            color: 'white',
                            borderRadius: '3px',
                            fontSize: '10px',
                            fontWeight: 'bold'
                          }}>
                            CURRENT
                          </span>
                        )}
                      </div>
                      
                      <div style={{ fontSize: '12px', color: '#6c757d' }}>
                        ID: {view.viewId}
                      </div>
                    </div>
                    
                    <div style={{ display: 'flex', gap: '8px' }}>
                      <span style={{
                        padding: '4px 8px',
                        backgroundColor: getStatusColor(view.status),
                        color: 'white',
                        borderRadius: '4px',
                        fontSize: '11px',
                        fontWeight: 'bold'
                      }}>
                        {view.status}
                      </span>
                      
                      <button
                        onClick={() => onDeleteView(view.viewId)}
                        style={{
                          padding: '4px 8px',
                          backgroundColor: '#dc3545',
                          color: 'white',
                          border: 'none',
                          borderRadius: '4px',
                          cursor: 'pointer',
                          fontSize: '11px'
                        }}
                      >
                        🗑️ Delete
                      </button>
                    </div>
                  </div>

                  {/* View Configuration */}
                  {view.configuration && (
                    <div style={{ 
                      display: 'grid', 
                      gridTemplateColumns: 'repeat(auto-fit, minmax(200px, 1fr))', 
                      gap: '10px',
                      marginBottom: '10px'
                    }}>
                      <div>
                        <div style={{ fontSize: '11px', fontWeight: 'bold', color: '#495057' }}>
                          Selected Accounts:
                        </div>
                        <div style={{ fontSize: '12px', color: '#6c757d' }}>
                          {Array.from(view.configuration.selectedAccounts || []).join(', ') || 'None'}
                        </div>
                      </div>
                      
                      <div>
                        <div style={{ fontSize: '11px', fontWeight: 'bold', color: '#495057' }}>
                          Group By Fields:
                        </div>
                        <div style={{ fontSize: '12px', color: '#6c757d' }}>
                          {(view.configuration.groupByFields || []).join(' → ') || 'None'}
                        </div>
                      </div>
                      
                      <div>
                        <div style={{ fontSize: '11px', fontWeight: 'bold', color: '#495057' }}>
                          Exposure Types:
                        </div>
                        <div style={{ fontSize: '12px', color: '#6c757d' }}>
                          {Array.from(view.configuration.exposureTypes || []).join(', ') || 'None'}
                        </div>
                      </div>
                    </div>
                  )}

                  {/* View Statistics */}
                  <div style={{ 
                    display: 'grid', 
                    gridTemplateColumns: 'repeat(auto-fit, minmax(120px, 1fr))', 
                    gap: '10px',
                    fontSize: '11px'
                  }}>
                    <div>
                      <span style={{ fontWeight: 'bold', color: '#495057' }}>Created:</span>
                      <div style={{ color: '#6c757d' }}>
                        {formatDateTime(view.createdAt)}
                      </div>
                    </div>
                    
                    <div>
                      <span style={{ fontWeight: 'bold', color: '#495057' }}>Last Accessed:</span>
                      <div style={{ color: '#6c757d' }}>
                        {formatDateTime(view.lastAccessedAt)}
                      </div>
                    </div>
                    
                    <div>
                      <span style={{ fontWeight: 'bold', color: '#495057' }}>Connections:</span>
                      <div style={{ color: '#6c757d' }}>
                        {view.activeConnections || 0}
                      </div>
                    </div>
                    
                    <div>
                      <span style={{ fontWeight: 'bold', color: '#495057' }}>Rows:</span>
                      <div style={{ color: '#6c757d' }}>
                        {view.rowCount || 0}
                      </div>
                    </div>
                    
                    <div>
                      <span style={{ fontWeight: 'bold', color: '#495057' }}>Last Update:</span>
                      <div style={{ color: '#6c757d' }}>
                        {formatDateTime(view.lastDataUpdate)}
                      </div>
                    </div>
                  </div>
                </div>
              ))}
            </div>
          )}
        </div>
      )}
    </div>
  )
}

export default ViewConfigPanel 