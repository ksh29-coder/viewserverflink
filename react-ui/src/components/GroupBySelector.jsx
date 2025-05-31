import React, { useState } from 'react'

const GroupBySelector = ({ options, selectedFields, onSelectionChange }) => {
  const [isOpen, setIsOpen] = useState(false)

  const handleFieldToggle = (fieldName) => {
    if (fieldName === 'accountName') {
      // accountName is always required and should be first
      return
    }

    const newSelection = selectedFields.includes(fieldName)
      ? selectedFields.filter(field => field !== fieldName)
      : [...selectedFields, fieldName]
    
    // Ensure accountName is always first
    const finalSelection = ['accountName', ...newSelection.filter(field => field !== 'accountName')]
    
    onSelectionChange(finalSelection)
  }

  const handleMoveUp = (fieldName) => {
    if (fieldName === 'accountName') return // Can't move accountName
    
    const currentIndex = selectedFields.indexOf(fieldName)
    if (currentIndex <= 1) return // Already at top (after accountName)
    
    const newSelection = [...selectedFields]
    newSelection.splice(currentIndex, 1)
    newSelection.splice(currentIndex - 1, 0, fieldName)
    
    onSelectionChange(newSelection)
  }

  const handleMoveDown = (fieldName) => {
    if (fieldName === 'accountName') return // Can't move accountName
    
    const currentIndex = selectedFields.indexOf(fieldName)
    if (currentIndex >= selectedFields.length - 1) return // Already at bottom
    
    const newSelection = [...selectedFields]
    newSelection.splice(currentIndex, 1)
    newSelection.splice(currentIndex + 1, 0, fieldName)
    
    onSelectionChange(newSelection)
  }

  const getFieldOption = (fieldName) => {
    return options.find(opt => opt.fieldName === fieldName) || {
      fieldName,
      displayName: fieldName,
      description: '',
      required: false
    }
  }

  return (
    <div>
      <label style={{ 
        fontWeight: 'bold', 
        color: '#495057',
        display: 'block',
        marginBottom: '8px'
      }}>
        Group By Fields *
      </label>

      {/* Selected fields with ordering */}
      <div style={{ 
        border: '1px solid #ced4da',
        borderRadius: '4px',
        backgroundColor: 'white',
        marginBottom: '10px'
      }}>
        <div style={{ 
          padding: '8px 12px',
          backgroundColor: '#f8f9fa',
          borderBottom: '1px solid #e9ecef',
          fontSize: '12px',
          fontWeight: 'bold'
        }}>
          Selected Fields (in order):
        </div>
        
        {selectedFields.map((fieldName, index) => {
          const option = getFieldOption(fieldName)
          const isFirst = index === 0
          const isLast = index === selectedFields.length - 1
          const isRequired = option.required || fieldName === 'accountName'
          
          return (
            <div
              key={fieldName}
              style={{
                padding: '8px 12px',
                borderBottom: index < selectedFields.length - 1 ? '1px solid #f1f3f4' : 'none',
                display: 'flex',
                alignItems: 'center',
                justifyContent: 'space-between'
              }}
            >
              <div style={{ flex: 1 }}>
                <div style={{ 
                  fontWeight: 'bold', 
                  fontSize: '14px',
                  color: isRequired ? '#007bff' : '#333'
                }}>
                  {index + 1}. {option.displayName}
                  {isRequired && <span style={{ color: '#dc3545' }}> *</span>}
                </div>
                <div style={{ fontSize: '12px', color: '#6c757d' }}>
                  {option.description}
                </div>
              </div>
              
              {!isRequired && (
                <div style={{ display: 'flex', gap: '5px', marginLeft: '10px' }}>
                  <button
                    onClick={() => handleMoveUp(fieldName)}
                    disabled={isFirst || index === 1} // Can't move above accountName
                    style={{
                      padding: '2px 6px',
                      fontSize: '12px',
                      backgroundColor: '#6c757d',
                      color: 'white',
                      border: 'none',
                      borderRadius: '3px',
                      cursor: 'pointer',
                      opacity: (isFirst || index === 1) ? 0.5 : 1
                    }}
                  >
                    ↑
                  </button>
                  <button
                    onClick={() => handleMoveDown(fieldName)}
                    disabled={isLast}
                    style={{
                      padding: '2px 6px',
                      fontSize: '12px',
                      backgroundColor: '#6c757d',
                      color: 'white',
                      border: 'none',
                      borderRadius: '3px',
                      cursor: 'pointer',
                      opacity: isLast ? 0.5 : 1
                    }}
                  >
                    ↓
                  </button>
                  <button
                    onClick={() => handleFieldToggle(fieldName)}
                    style={{
                      padding: '2px 6px',
                      fontSize: '12px',
                      backgroundColor: '#dc3545',
                      color: 'white',
                      border: 'none',
                      borderRadius: '3px',
                      cursor: 'pointer'
                    }}
                  >
                    ✕
                  </button>
                </div>
              )}
            </div>
          )
        })}
      </div>

      {/* Available fields dropdown */}
      <div style={{ position: 'relative' }}>
        <button
          onClick={() => setIsOpen(!isOpen)}
          style={{
            width: '100%',
            padding: '8px 12px',
            backgroundColor: '#007bff',
            color: 'white',
            border: 'none',
            borderRadius: '4px',
            cursor: 'pointer',
            fontSize: '14px',
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'space-between'
          }}
        >
          Add Field
          <span>{isOpen ? '▲' : '▼'}</span>
        </button>

        {isOpen && (
          <div style={{
            position: 'absolute',
            top: '100%',
            left: 0,
            right: 0,
            backgroundColor: 'white',
            border: '1px solid #ced4da',
            borderRadius: '4px',
            boxShadow: '0 2px 4px rgba(0,0,0,0.1)',
            zIndex: 1000,
            maxHeight: '200px',
            overflowY: 'auto'
          }}>
            {options
              .filter(option => !selectedFields.includes(option.fieldName))
              .map(option => (
                <div
                  key={option.fieldName}
                  onClick={() => {
                    handleFieldToggle(option.fieldName)
                    setIsOpen(false)
                  }}
                  style={{
                    padding: '8px 12px',
                    cursor: 'pointer',
                    borderBottom: '1px solid #f1f3f4',
                    backgroundColor: 'white'
                  }}
                  onMouseEnter={(e) => e.target.style.backgroundColor = '#f8f9fa'}
                  onMouseLeave={(e) => e.target.style.backgroundColor = 'white'}
                >
                  <div style={{ fontWeight: 'bold', fontSize: '14px' }}>
                    {option.displayName}
                  </div>
                  <div style={{ fontSize: '12px', color: '#6c757d' }}>
                    {option.description}
                  </div>
                </div>
              ))}
            
            {options.filter(option => !selectedFields.includes(option.fieldName)).length === 0 && (
              <div style={{ 
                padding: '8px 12px', 
                fontSize: '12px', 
                color: '#6c757d',
                fontStyle: 'italic'
              }}>
                All available fields are selected
              </div>
            )}
          </div>
        )}
      </div>

      {/* Help text */}
      <div style={{ 
        marginTop: '8px', 
        fontSize: '12px', 
        color: '#6c757d' 
      }}>
        Fields determine how data is grouped in the grid. Account Name is always first.
      </div>
    </div>
  )
}

export default GroupBySelector 