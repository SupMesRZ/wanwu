import axios from 'axios'

const service = axios.create({
  baseURL: '/api/v1',
  timeout: 10000
})

service.interceptors.request.use(
  config => {
    const token = localStorage.getItem('admin_token')
    if (token) {
      config.headers.Authorization = `Bearer ${token}`
    }
    return config
  },
  error => Promise.reject(error)
)

const clearAdminSession = () => {
  localStorage.removeItem('admin_token')
  localStorage.removeItem('admin_username')
  localStorage.removeItem('admin_is_root')
  window.dispatchEvent(new Event('admin-auth-expired'))
}

service.interceptors.response.use(
  response => response.data,
  error => {
    if (error.response?.status === 401) {
      clearAdminSession()
      if (window.location.hash !== '#/login') {
        window.location.hash = '#/login'
      }
    }
    return Promise.reject(error)
  }
)

export default service
