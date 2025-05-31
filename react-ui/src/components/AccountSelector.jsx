import React from 'react'

const AccountSelector = ({ accounts, selectedAccounts, onSelectionChange }) => {
  
  const handleAccountToggle = (accountId) => {
    const newSelection = selectedAccounts.includes(accountId)
      ? selectedAccounts.filter(id => id !== accountId)
      : [...selectedAccounts, accountId]
    
    // Ensure at least one account is always selected
    if (newSelection.length > 0) {
      onSelectionChange(newSelection)
    }
  }

  const handleSelectAll = () => {
    onSelectionChange(accounts.map(acc => acc.accountId))
  }

  const handleSelectNone = () => {
    // Keep at least one account selected
    if (accounts.length > 0) {
      onSelectionChange([accounts[0].accountId])
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
        Account Selection *
      </label>
      
      <div style={{ 
        border: '1px solid #ced4da',
        borderRadius: '4px',
        backgroundColor: 'white',
        maxHeight: '200px',
        overflowY: 'auto'
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
            Select None
          </button>
        </div>

        {/* Account checkboxes */}
        {accounts.map(account => (
          <div
            key={account.accountId}
            style={{
              padding: '8px 12px',
              borderBottom: '1px solid #f1f3f4',
              cursor: 'pointer',
              backgroundColor: selectedAccounts.includes(account.accountId) ? '#e7f3ff' : 'white'
            }}
            onClick={() => handleAccountToggle(account.accountId)}
          >
            <label style={{ 
              display: 'flex', 
              alignItems: 'center', 
              cursor: 'pointer',
              margin: 0
            }}>
              <input
                type="checkbox"
                checked={selectedAccounts.includes(account.accountId)}
                onChange={() => handleAccountToggle(account.accountId)}
                style={{ marginRight: '8px' }}
              />
              <div>
                <div style={{ fontWeight: 'bold', fontSize: '14px' }}>
                  {account.accountId}
                </div>
                <div style={{ fontSize: '12px', color: '#6c757d' }}>
                  {account.accountName}
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
        {selectedAccounts.length} of {accounts.length} accounts selected
        {selectedAccounts.length === 0 && (
          <span style={{ color: '#dc3545', fontWeight: 'bold' }}>
            {' '}(At least one account required)
          </span>
        )}
      </div>
    </div>
  )
}

export default AccountSelector 