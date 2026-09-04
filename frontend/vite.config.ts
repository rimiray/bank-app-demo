import { defineConfig, type Plugin } from 'vite'
import react from '@vitejs/plugin-react'
import net from 'node:net'
import http from 'node:http'

function tcpProbe(host: string, port: number, timeoutMs = 1200): Promise<boolean> {
  return new Promise((resolve) => {
    const socket = net.connect({ host, port })
    const done = (ok: boolean) => {
      socket.destroy()
      resolve(ok)
    }
    socket.setTimeout(timeoutMs)
    socket.on('connect', () => done(true))
    socket.on('timeout', () => done(false))
    socket.on('error', () => done(false))
  })
}

function httpProbe(
  url: string,
  options: { timeoutMs?: number; auth?: string } = {},
): Promise<boolean> {
  const { timeoutMs = 1500, auth } = options
  return new Promise((resolve) => {
    const req = http.get(
      url,
      {
        timeout: timeoutMs,
        headers: auth ? { Authorization: `Basic ${auth}` } : undefined,
      },
      (res) => {
        res.resume()
        resolve((res.statusCode ?? 500) < 500)
      },
    )
    req.on('timeout', () => {
      req.destroy()
      resolve(false)
    })
    req.on('error', () => resolve(false))
  })
}

function healthPlugin(): Plugin {
  return {
    name: 'infra-health',
    configureServer(server) {
      server.middlewares.use('/api/health', async (_req, res) => {
        const rabbitAuth = Buffer.from('bank_guest:bank_guest').toString('base64')
        const [cards, credit, collateral, redis, rabbitmq, postgres] = await Promise.all([
          // App services: TCP — GET on POST-only routes can return 500 and look "down"
          tcpProbe('127.0.0.1', 8081),
          tcpProbe('127.0.0.1', 8082),
          tcpProbe('127.0.0.1', 8083),
          tcpProbe('127.0.0.1', 6379),
          httpProbe('http://127.0.0.1:15672/api/overview', { auth: rabbitAuth }),
          tcpProbe('127.0.0.1', 5432),
        ])

        res.setHeader('Content-Type', 'application/json')
        res.end(
          JSON.stringify({
            checkedAt: new Date().toISOString(),
            services: {
              cards: { name: 'Card Service', port: 8081, up: cards },
              credit: { name: 'Credit Service', port: 8082, up: credit },
              collateral: { name: 'AI Collateral', port: 8083, up: collateral },
              redis: { name: 'Redis', port: 6379, up: redis },
              rabbitmq: { name: 'RabbitMQ', port: 5672, up: rabbitmq },
              postgres: { name: 'PostgreSQL', port: 5432, up: postgres },
            },
          }),
        )
      })
    },
  }
}

export default defineConfig({
  plugins: [react(), healthPlugin()],
  server: {
    port: 5173,
    proxy: {
      '/api/v1/cards': {
        target: 'http://localhost:8081',
        changeOrigin: true,
      },
      '/api/v1/credits': {
        target: 'http://localhost:8082',
        changeOrigin: true,
      },
      '/api/v1/collateral': {
        target: 'http://localhost:8083',
        changeOrigin: true,
      },
    },
  },
})
