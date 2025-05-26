import axios from 'axios'

// Base URL for the View Server API
const BASE_URL = 'http://localhost:8080/api'

// Create axios instance with default config
const api = axios.create({
  baseURL: BASE_URL,
  timeout: 10000,
  headers: {
    'Content-Type': 'application/json',
  },
})

// Add response interceptor for error handling
api.interceptors.response.use(
  (response) => response,
  (error) => {
    console.error('API Error:', error)
    if (error.response) {
      // Server responded with error status
      throw new Error(`Server Error: ${error.response.status} - ${error.response.data?.message || error.response.statusText}`)
    } else if (error.request) {
      // Request was made but no response received
      throw new Error('Network Error: Unable to connect to the server. Please check if the View Server is running on port 8080.')
    } else {
      // Something else happened
      throw new Error(`Request Error: ${error.message}`)
    }
  }
)

export const apiService = {
  // Health check
  async getHealth() {
    const response = await api.get('/health')
    return response.data
  },

  // Base Data Layer APIs
  async getAccounts() {
    const response = await api.get('/accounts')
    return response.data
  },

  async getInstruments() {
    const response = await api.get('/instruments')
    return response.data
  },

  async getHoldings(accountId) {
    const response = await api.get(`/holdings/${accountId}`)
    return response.data
  },

  async getPrices() {
    const response = await api.get('/prices')
    return response.data
  },

  async getPrice(instrumentId) {
    const response = await api.get(`/prices/${instrumentId}`)
    return response.data
  },

  async getOrders() {
    const response = await api.get('/orders')
    return response.data
  },

  async getCashMovements(accountId) {
    const response = await api.get(`/cash/${accountId}`)
    return response.data
  },

  // Aggregation Layer APIs
  async getHoldingsMV(accountId) {
    const response = await api.get(`/holdings-mv/${accountId}`)
    return response.data
  },

  async getAllHoldingsMV() {
    const response = await api.get('/holdings-mv')
    return response.data
  },

  async getOrdersMV(accountId) {
    const response = await api.get(`/orders-mv/${accountId}`)
    return response.data
  },

  async getAllOrdersMV() {
    const response = await api.get('/orders-mv')
    return response.data
  },

  // Cache statistics
  async getCacheStats() {
    const response = await api.get('/stats')
    return response.data
  },

  // Data generation control (Mock Data Generator)
  async getDataGenerationStatus() {
    const response = await axios.get('http://localhost:8081/api/data-generation/status')
    return response.data
  },

  async initializeData() {
    const response = await axios.post('http://localhost:8081/api/data-generation/initialize')
    return response.data
  },

  async startDynamicData() {
    const response = await axios.post('http://localhost:8081/api/data-generation/dynamic/start')
    return response.data
  },

  async stopDynamicData() {
    const response = await axios.post('http://localhost:8081/api/data-generation/dynamic/stop')
    return response.data
  },

  async getDynamicDataStatus() {
    const response = await axios.get('http://localhost:8081/api/data-generation/dynamic/status')
    return response.data
  }
} 