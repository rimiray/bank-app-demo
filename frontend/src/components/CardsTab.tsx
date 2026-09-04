import { useEffect, useState } from 'react'
import { closeCard, getCards, issueCard, purchaseCard, topUpCard } from '../api/cards'
import { ApiError, money } from '../api/client'
import type { CardResponse } from '../types'

function statusTone(status: string): string {
  const s = status.toUpperCase()
  if (s === 'ACTIVE') return 'bg-bank-success/15 text-bank-success'
  if (s === 'CLOSED' || s === 'BLOCKED') return 'bg-bank-danger/15 text-bank-danger'
  return 'bg-bank-warn/15 text-bank-warn'
}

function PlasticCard({
  card,
  selected,
  onSelect,
}: {
  card: CardResponse
  selected: boolean
  onSelect: () => void
}) {
  const closed = card.status.toUpperCase() === 'CLOSED'
  return (
    <button
      type="button"
      onClick={onSelect}
      className={[
        'relative w-full overflow-hidden rounded-2xl bg-plastic p-5 text-left text-white shadow-card transition',
        'hover:-translate-y-0.5',
        selected ? 'ring-2 ring-bank-teal ring-offset-2 ring-offset-bank-sand' : '',
        closed ? 'opacity-55' : '',
      ].join(' ')}
    >
      <div className="pointer-events-none absolute -right-8 -top-10 h-36 w-36 rounded-full bg-white/10" />
      <div className="pointer-events-none absolute -bottom-12 left-10 h-40 w-40 rounded-full bg-bank-teal/30 blur-2xl" />

      <div className="relative flex items-start justify-between">
        <span className="font-display text-lg font-extrabold tracking-tight">ZBK Bank</span>
        <span
          className={`rounded-full px-2.5 py-0.5 text-[10px] font-bold uppercase tracking-wider ${statusTone(card.status)}`}
        >
          {card.status}
        </span>
      </div>

      <p className="relative mt-8 font-mono text-lg tracking-[0.18em] sm:text-xl">
        {card.cardNumberMasked}
      </p>

      <div className="relative mt-6 flex items-end justify-between gap-3">
        <div>
          <p className="text-[10px] uppercase tracking-wider text-white/55">Balance</p>
          <p className="font-display text-2xl font-bold">{money(card.balance, card.currency)}</p>
        </div>
        <div className="text-right">
          <p className="text-[10px] uppercase tracking-wider text-white/55">Limit</p>
          <p className="font-mono text-sm">{money(card.creditLimit, card.currency)}</p>
        </div>
      </div>
    </button>
  )
}

export function CardsTab() {
  const [cards, setCards] = useState<CardResponse[]>([])
  const [selectedId, setSelectedId] = useState<string | null>(null)
  const [amount, setAmount] = useState('100')
  const [loading, setLoading] = useState(true)
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [notice, setNotice] = useState<string | null>(null)

  const selected = cards.find((c) => c.id === selectedId) ?? cards[0] ?? null
  const selectedActive = selected?.status.toUpperCase() === 'ACTIVE'

  async function refresh(preferredId?: string | null) {
    setLoading(true)
    setError(null)
    try {
      const data = await getCards()
      setCards(data)
      setSelectedId((current) => {
        const prefer = preferredId ?? current
        if (prefer && data.some((c) => c.id === prefer)) return prefer
        const active = data.find((c) => c.status.toUpperCase() === 'ACTIVE')
        return active?.id ?? data[0]?.id ?? null
      })
    } catch (e) {
      setError(e instanceof ApiError ? e.message : 'Failed to load cards')
    } finally {
      setLoading(false)
    }
  }

  useEffect(() => {
    void refresh()
  }, [])

  async function run(action: () => Promise<CardResponse>, success: string) {
    setBusy(true)
    setError(null)
    setNotice(null)
    try {
      const updated = await action()
      setCards((prev) => {
        const idx = prev.findIndex((c) => c.id === updated.id)
        if (idx === -1) return [updated, ...prev]
        const next = [...prev]
        next[idx] = updated
        return next
      })
      setSelectedId(updated.id)
      setNotice(success)
    } catch (e) {
      setError(e instanceof ApiError ? e.message : 'Operation failed')
    } finally {
      setBusy(false)
    }
  }

  const parsedAmount = Number(amount)
  const canClose =
    selectedActive &&
    selected != null &&
    Number(selected.balance) === 0

  return (
    <div className="animate-fade-up grid gap-6 lg:grid-cols-[1.1fr_0.9fr]">
      <section className="space-y-4">
        <div className="flex items-end justify-between gap-3">
          <div>
            <h2 className="font-display text-2xl font-bold tracking-tight">Plastic cards</h2>
            <p className="mt-1 text-sm text-bank-ink/55">
              Live data from <span className="font-mono text-bank-teal-dark">localhost:8081</span>
            </p>
          </div>
          <button type="button" className="btn-secondary" onClick={() => void refresh()} disabled={loading}>
            Refresh
          </button>
        </div>

        {loading && (
          <div className="panel h-48 animate-pulse bg-gradient-to-r from-bank-mist via-white to-bank-mist bg-[length:200%_100%] animate-shimmer" />
        )}

        {!loading && cards.length === 0 && (
          <div className="panel p-8 text-center text-sm text-bank-ink/55">
            No cards yet. Issue your first card.
          </div>
        )}

        <div className="grid gap-4 sm:grid-cols-2">
          {cards.map((card) => (
            <PlasticCard
              key={card.id}
              card={card}
              selected={selected?.id === card.id}
              onSelect={() => setSelectedId(card.id)}
            />
          ))}
        </div>
      </section>

      <aside className="panel space-y-6 p-5 sm:p-6">
        <div>
          <h3 className="font-display text-lg font-bold">Issue a new card</h3>
          <p className="mt-1 text-sm text-bank-ink/55">
            POST <span className="font-mono text-xs">/api/v1/cards</span>
          </p>
          <button
            type="button"
            className="btn-primary mt-4 w-full"
            disabled={busy}
            onClick={() => void run(() => issueCard(), 'New card issued')}
          >
            Issue card
          </button>
        </div>

        <div className="h-px bg-bank-line/80" />

        <div>
          <h3 className="font-display text-lg font-bold">Balance operations</h3>
          <p className="mt-1 text-sm text-bank-ink/55">
            {selected
              ? `Selected · ${selected.cardNumberMasked}`
              : 'Select a card to top-up or purchase'}
          </p>

          <label className="mt-4 block">
            <span className="label">Amount (EUR)</span>
            <input
              className="field"
              type="number"
              min="0.01"
              step="0.01"
              value={amount}
              onChange={(e) => setAmount(e.target.value)}
            />
          </label>

          <div className="mt-4 grid grid-cols-2 gap-3">
            <button
              type="button"
              className="btn-primary"
              disabled={busy || !selectedActive || !(parsedAmount > 0)}
              onClick={() =>
                selected &&
                void run(
                  () => topUpCard(selected.id, parsedAmount),
                  `Topped up ${money(parsedAmount)}`,
                )
              }
            >
              Top-up
            </button>
            <button
              type="button"
              className="btn-secondary"
              disabled={busy || !selectedActive || !(parsedAmount > 0)}
              onClick={() =>
                selected &&
                void run(
                  () => purchaseCard(selected.id, parsedAmount),
                  `Purchase ${money(parsedAmount)}`,
                )
              }
            >
              Purchase
            </button>
          </div>
        </div>

        <div className="h-px bg-bank-line/80" />

        <div>
          <h3 className="font-display text-lg font-bold">Close card</h3>
          <p className="mt-1 text-sm text-bank-ink/55">
            POST <span className="font-mono text-xs">/cards/&#123;id&#125;/close</span>
            {' '}· balance must be 0 and no active debt
          </p>
          <button
            type="button"
            className="btn-secondary mt-4 w-full border-bank-danger/40 text-bank-danger hover:border-bank-danger hover:text-bank-danger"
            disabled={busy || !canClose}
            onClick={() =>
              selected &&
              void run(() => closeCard(selected.id), 'Card closed')
            }
          >
            Close selected card
          </button>
        </div>

        {notice && (
          <p className="rounded-xl bg-bank-success/10 px-3 py-2 text-sm text-bank-success">{notice}</p>
        )}
        {error && (
          <p className="rounded-xl bg-bank-danger/10 px-3 py-2 text-sm text-bank-danger">{error}</p>
        )}
      </aside>
    </div>
  )
}
