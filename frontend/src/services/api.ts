import axios from 'axios'
import { useAuthStore } from '../store/authStore'

const api = axios.create({
  baseURL: '/api',
})

api.interceptors.request.use((config) => {
  const token = useAuthStore.getState().token
  if (token) {
    config.headers.Authorization = `Bearer ${token}`
  }
  return config
})

export const authApi = {
  login: async () => {
    const response = await api.post('/auth/login', { username: 'demo', password: 'demo' })
    return response.data
  },
  register: async (username: string) => {
    const response = await api.post('/auth/register', { username })
    return response.data
  },
}

export const documentApi = {
  list: async () => {
    const response = await api.get('/documents')
    return response.data
  },

  get: async (id: string) => {
    const response = await api.get(`/documents/${id}`)
    return response.data
  },

  create: async (title: string) => {
    const response = await api.post('/documents', { title })
    return response.data
  },

  delete: async (id: string) => {
    await api.delete(`/documents/${id}`)
  },

  getSnapshot: async (id: string, version?: number) => {
    const url = version
      ? `/documents/${id}/snapshot?version=${version}`
      : `/documents/${id}/snapshot`
    const response = await api.get(url, { responseType: 'arraybuffer' })
    return new Uint8Array(response.data)
  },
}

export default api
