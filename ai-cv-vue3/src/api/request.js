import { fetchEventSource } from '@microsoft/fetch-event-source'

/**
 * 基础 API 配置
 */
const BASE_URL = '' // 开发环境使用 Vite 代理
const TIMEOUT = 30000 // 30 秒超时

/**
 * 通用请求封装
 */
class Request {
  constructor(baseURL = BASE_URL, timeout = TIMEOUT) {
    this.baseURL = baseURL
    this.timeout = timeout
  }

  /**
   * 处理响应
   */
  handleResponse(response) {
    if (!response.ok) {
      throw new Error(`HTTP error! status: ${response.status}`)
    }
    return response
  }

  /**
   * GET 请求
   */
  async get(url, params = {}) {
    const queryString = new URLSearchParams(params).toString()
    const fullUrl = queryString ? `${this.baseURL}${url}?${queryString}` : `${this.baseURL}${url}`
    
    const controller = new AbortController()
    const timeoutId = setTimeout(() => controller.abort(), this.timeout)

    try {
      const response = await fetch(fullUrl, {
        method: 'GET',
        signal: controller.signal,
        headers: {
          'Content-Type': 'application/json'
        }
      })
      
      clearTimeout(timeoutId)
      await this.handleResponse(response)
      return await response.json()
    } catch (error) {
      clearTimeout(timeoutId)
      if (error.name === 'AbortError') {
        throw new Error('请求超时')
      }
      throw error
    }
  }

  /**
   * POST 请求
   */
  async post(url, data = {}) {
    const controller = new AbortController()
    const timeoutId = setTimeout(() => controller.abort(), this.timeout)

    try {
      const response = await fetch(`${this.baseURL}${url}`, {
        method: 'POST',
        signal: controller.signal,
        headers: {
          'Content-Type': 'application/json'
        },
        body: JSON.stringify(data)
      })
      
      clearTimeout(timeoutId)
      await this.handleResponse(response)
      return await response.json()
    } catch (error) {
      clearTimeout(timeoutId)
      if (error.name === 'AbortError') {
        throw new Error('请求超时')
      }
      throw error
    }
  }

  /**
   * 文件上传
   */
  async upload(url, file) {
    const formData = new FormData()
    formData.append('file', file)

    const controller = new AbortController()
    const timeoutId = setTimeout(() => controller.abort(), this.timeout)

    try {
      const response = await fetch(`${this.baseURL}${url}`, {
        method: 'POST',
        signal: controller.signal,
        body: formData
      })
      
      clearTimeout(timeoutId)
      await this.handleResponse(response)
      
      const contentType = response.headers.get('content-type') || ''
      if (contentType.includes('application/json')) {
        return await response.json()
      } else {
        const text = await response.text()
        throw new Error(`服务器响应格式错误：${text}`)
      }
    } catch (error) {
      clearTimeout(timeoutId)
      if (error.name === 'AbortError') {
        throw new Error('上传超时')
      }
      throw error
    }
  }

  /**
   * SSE 流式请求
   */
  async stream(url, data = {}, options = {}) {
    const {
      onmessage,
      onerror,
      onopen,
      onclose,
      openWhenHidden,
      signal
    } = options

    return fetchEventSource(`${this.baseURL}${url}`, {
      method: 'POST',
      signal,
      // 避免页面切到后台触发自动重连，导致重复请求/重复输出
      openWhenHidden: openWhenHidden ?? true,
      headers: {
        'Content-Type': 'application/json',
        // 显式声明期望 SSE，避免部分代理/中间件按普通请求缓冲
        'Accept': 'text/event-stream'
      },
      body: JSON.stringify(data),
      onmessage,
      onerror,
      onopen,
      onclose
    })
  }
}

// 创建实例
const request = new Request()

export default request
