import { useCallback, useEffect, useState } from 'react'
import { fetchHealth } from '../api/health'
import type { HealthResponse, ServiceKey } from '../types'

const LAYOUT: {
  key: ServiceKey
  x: number
  y: number
  w: number
  h: number
  accent: string
}[] = [
  { key: 'cards', x: 40, y: 40, w: 160, h: 72, accent: '#00A0C8' },
  { key: 'credit', x: 260, y: 40, w: 160, h: 72, accent: '#007A9A' },
  { key: 'collateral', x: 480, y: 40, w: 160, h: 72, accent: '#1B8A5A' },
  { key: 'redis', x: 40, y: 200, w: 140, h: 64, accent: '#C0392B' },
  { key: 'postgres', x: 250, y: 200, w: 160, h: 64, accent: '#336791' },
  { key: 'rabbitmq', x: 480, y: 200, w: 160, h: 64, accent: '#F60' },
]

const EDGES: [ServiceKey, ServiceKey][] = [
  ['cards', 'redis'],
  ['cards', 'postgres'],
  ['credit', 'postgres'],
  ['credit', 'rabbitmq'],
  ['collateral', 'credit'],
]

function nodeCenter(key: ServiceKey) {
  const n = LAYOUT.find((l) => l.key === key)!
  return { x: n.x + n.w / 2, y: n.y + n.h / 2 }
}

export function ArchitectureTab() {
  const [health, setHealth] = useState<HealthResponse | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [loading, setLoading] = useState(true)

  const refresh = useCallback(async () => {
    setLoading(true)
    setError(null)
    try {
      setHealth(await fetchHealth())
    } catch {
      setError('Health probe failed — is the Vite dev server running?')
    } finally {
      setLoading(false)
    }
  }, [])

  useEffect(() => {
    void refresh()
    const id = window.setInterval(() => void refresh(), 8000)
    return () => window.clearInterval(id)
  }, [refresh])

  const services = health?.services

  return (
    <div className="animate-fade-up space-y-6">
      <div className="flex flex-wrap items-end justify-between gap-3">
        <div>
          <h2 className="font-display text-2xl font-bold tracking-tight">System Architecture</h2>
          <p className="mt-1 text-sm text-bank-ink/55">
            Live probes against microservices and infra ports
            {health && (
              <>
                {' '}
                · last check{' '}
                <span className="font-mono text-xs">
                  {new Date(health.checkedAt).toLocaleTimeString()}
                </span>
              </>
            )}
          </p>
        </div>
        <button type="button" className="btn-secondary" onClick={() => void refresh()} disabled={loading}>
          {loading ? 'Checking…' : 'Re-check'}
        </button>
      </div>

      <div className="panel overflow-hidden p-4 sm:p-6">
        <svg viewBox="0 0 680 300" className="h-auto w-full" role="img" aria-label="Service topology">
          <defs>
            <marker id="arrow" markerWidth="8" markerHeight="8" refX="6" refY="3" orient="auto">
              <path d="M0,0 L6,3 L0,6 Z" fill="#C5D5DC" />
            </marker>
          </defs>

          {EDGES.map(([from, to]) => {
            const a = nodeCenter(from)
            const b = nodeCenter(to)
            const up = services?.[from]?.up && services?.[to]?.up
            return (
              <line
                key={`${from}-${to}`}
                x1={a.x}
                y1={a.y}
                x2={b.x}
                y2={b.y}
                stroke={up ? '#00A0C8' : '#C5D5DC'}
                strokeWidth={up ? 2.5 : 1.5}
                strokeDasharray={up ? undefined : '6 6'}
                markerEnd="url(#arrow)"
                opacity={0.85}
              />
            )
          })}

          {LAYOUT.map((node) => {
            const s = services?.[node.key]
            const up = s?.up ?? false
            return (
              <g key={node.key} transform={`translate(${node.x}, ${node.y})`}>
                <rect
                  width={node.w}
                  height={node.h}
                  rx={14}
                  fill="#fff"
                  stroke={up ? node.accent : '#C5D5DC'}
                  strokeWidth={up ? 2.5 : 1.5}
                />
                <circle cx={18} cy={22} r={6} fill={up ? '#1B8A5A' : '#C0392B'}>
                  {up && (
                    <animate
                      attributeName="opacity"
                      values="1;0.45;1"
                      dur="1.6s"
                      repeatCount="indefinite"
                    />
                  )}
                </circle>
                <text x={32} y={26} className="fill-bank-ink" style={{ fontSize: 13, fontWeight: 700 }}>
                  {s?.name ?? node.key}
                </text>
                <text x={18} y={48} style={{ fontSize: 11, fill: '#6b8490', fontFamily: 'IBM Plex Mono, monospace' }}>
                  :{s?.port ?? node.key} · {up ? 'UP' : 'DOWN'}
                </text>
              </g>
            )
          })}
        </svg>
      </div>

      <div className="grid gap-3 sm:grid-cols-2 lg:grid-cols-3">
        {LAYOUT.map((node) => {
          const s = services?.[node.key]
          const up = s?.up ?? false
          const name = s?.name ?? node.key
          const port = s?.port ?? node.key
          if (loading && !services) {
            return <div key={node.key} className="panel h-16 animate-pulse bg-bank-mist/80" />
          }
          return (
            <div key={node.key} className="panel flex items-center gap-3 p-4">
              <span
                className={[
                  'h-3 w-3 rounded-full',
                  up ? 'animate-pulse-dot bg-bank-success' : 'bg-bank-danger',
                ].join(' ')}
              />
              <div className="min-w-0 flex-1">
                <p className="truncate font-display text-sm font-bold">{name}</p>
                <p className="font-mono text-[11px] text-bank-ink/45">port {port}</p>
              </div>
              <span
                className={[
                  'rounded-full px-2.5 py-0.5 text-[10px] font-bold uppercase tracking-wider',
                  up ? 'bg-bank-success/15 text-bank-success' : 'bg-bank-danger/15 text-bank-danger',
                ].join(' ')}
              >
                {up ? 'healthy' : 'offline'}
              </span>
            </div>
          )
        })}
      </div>

      {error && (
        <p className="rounded-xl bg-bank-danger/10 px-3 py-2 text-sm text-bank-danger">{error}</p>
      )}
    </div>
  )
}
