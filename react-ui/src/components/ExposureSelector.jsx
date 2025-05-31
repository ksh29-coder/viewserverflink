import React from 'react'

const ExposureSelector = ({ selectedTypes, onSelectionChange }) => {
  
  const exposureOptions = [
    {
      type: 'SOD',
      displayName: 'Start of Day (SOD)',
      description: 'Holdings market value at start of day',
      color: '#28a745'
    },
    {
      type: 'CURRENT',
      displayName: 'Current',
      description: 'Holdings + filled orders market value',
      color: '#007bff'
    },
    {
      type: 'EXPECTED',
      displayName: 'Expected',
      description: 'Holdings + all orders (filled + pending) market value',
      color: '#ffc107'
    }
  ]

  const handleTypeToggle = (exposureType) => {
    const newSelection = selectedTypes.includes(exposureType)
      ? selectedTypes.filter(type => type !== exposureType)
      : [...selectedTypes, exposureType]
    
    // Ensure at least one exposure type is always selected
    if (newSelection.length > 0) {
      onSelectionChange(newSelection)
    }
  }

  const handleSelectAll = () => {
    onSelectionChange(['SOD', 'CURRENT', 'EXPECTED'])
  }

  const handleSelectNone = () => {
    // Keep at least one exposure type selected
    onSelectionChange(['SOD'])
  }

  return (
    <div>
      <label style={{ 
        fontWeight: 'bold', 
        color: '#495057',
        display: 'block',
        marginBottom: '8px'
      }}>
        Account Exposure Types *
      </label>
      
      <div style={{ 
        border: '1px solid #ced4da',
        borderRadius: '4px',
        backgroundColor: 'white'
      }}>
        {/* Select All/None controls */}
        <div style={{ 
          padding: '8px 12px',
          borderBottom: '1px solid #e9ecef',
          backgroundColor: '#f8f9fa',
          display: 'flex',
          gap: '10px'
        }}>
          <button
            onClick={handleSelectAll}
            style={{
              padding: '4px 8px',
              fontSize: '12px',
              backgroundColor: '#007bff',
              color: 'white',
              border: 'none',
              borderRadius: '3px',
              cursor: 'pointer'
            }}
          >
            Select All
          </button>
          <button
            onClick={handleSelectNone}
            style={{
              padding: '4px 8px',
              fontSize: '12px',
              backgroundColor: '#6c757d',
              color: 'white',
              border: 'none',
              borderRadius: '3px',
              cursor: 'pointer'
            }}
          >
            SOD Only
          </button>
        </div>

        {/* Exposure type checkboxes */}
        {exposureOptions.map(option => (
          <div
            key={option.type}
            style={{
              padding: '12px',
              borderBottom: '1px solid #f1f3f4',
              cursor: 'pointer',
              backgroundColor: selectedTypes.includes(option.type) ? '#e7f3ff' : 'white'
            }}
            onClick={() => handleTypeToggle(option.type)}
          >
            <label style={{ 
              display: 'flex', 
              alignItems: 'center', 
              cursor: 'pointer',
              margin: 0
            }}>
              <input
                type="checkbox"
                checked={selectedTypes.includes(option.type)}
                onChange={() => handleTypeToggle(option.type)}
                style={{ marginRight: '12px' }}
              />
              <div style={{ flex: 1 }}>
                <div style={{ 
                  display: 'flex', 
                  alignItems: 'center', 
                  gap: '8px',
                  marginBottom: '4px'
                }}>
                  <div
                    style={{
                      width: '12px',
                      height: '12px',
                      borderRadius: '50%',
                      backgroundColor: option.color
                    }}
                  />
                  <div style={{ 
                    fontWeight: 'bold', 
                    fontSize: '14px',
                    color: '#333'
                  }}>
                    {option.displayName}
                  </div>
                </div>
                <div style={{ 
                  fontSize: '12px', 
                  color: '#6c757d',
                  marginLeft: '20px'
                }}>
                  {option.description}
                </div>
              </div>
            </label>
          </div>
        ))}
      </div>

      {/* Selection summary */}
      <div style={{ 
        marginTop: '8px', 
        fontSize: '12px', 
        color: '#6c757d' 
      }}>
        {selectedTypes.length} of {exposureOptions.length} exposure types selected
        {selectedTypes.length === 0 && (
          <span style={{ color: '#dc3545', fontWeight: 'bold' }}>
            {' '}(At least one exposure type required)
          </span>
        )}
      </div>

      {/* Calculation explanation */}
      <div style={{ 
        marginTop: '12px',
        padding: '8px',
        backgroundColor: '#f8f9fa',
        borderRadius: '4px',
        fontSize: '11px',
        color: '#6c757d'
      }}>
        <div style={{ fontWeight: 'bold', marginBottom: '4px' }}>Calculation Logic:</div>
        <div>• <strong>SOD NAV:</strong> Sum of holdings market values</div>
        <div>• <strong>Current NAV:</strong> SOD + filled orders market values</div>
        <div>• <strong>Expected NAV:</strong> SOD + all orders market values</div>
      </div>
    </div>
  )
}

export default ExposureSelector 