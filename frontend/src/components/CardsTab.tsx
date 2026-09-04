import { useState, type Dispatch, type SetStateAction } from 'react'
import { closeCard, deleteCard, getCards, issueCard, purchaseCard, topUpCard } from '../api/cards'
import { ApiError, money } from '../api/client'
import { upsertCardInList } from '../lib/cardsOrder'
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
  const loan = Number(card.loanPrincipal ?? 0)
  const debt = Number(card.activeDebt ?? 0)
  return (
    <button
      type="button"
      onClick={onSelect}
      className={[
        'relative flex w-full max-w-md flex-col justify-between overflow-hidden rounded-xl bg-plastic',
        'aspect-[1.586/1] p-4 text-left text-white shadow-card transition sm:p-5',
        'hover:-translate-y-0.5',
        selected ? 'ring-2 ring-bank-teal ring-offset-2 ring-offset-bank-sand' : '',
        closed ? 'opacity-55' : '',
      ].join(' ')}
    >
      <div className="pointer-events-none absolute -right-8 -top-10 h-36 w-36 rounded-full bg-white/10" />
      <div className="pointer-events-none absolute -bottom-12 left-10 h-40 w-40 rounded-full bg-bank-teal/30 blur-2xl" />

      <div className="relative flex shrink-0 items-start justify-between gap-2">
        <span className="font-display text-base font-extrabold tracking-tight sm:text-lg">
          ZBK Bank
        </span>
        <span
          className={`shrink-0 rounded-full px-2 py-0.5 text-[10px] font-bold uppercase tracking-wider ${statusTone(card.status)}`}
        >
          {card.status}
        </span>
      </div>

      <p
        className="relative mt-3 w-full min-w-0 overflow-hidden whitespace-nowrap font-mono text-sm tracking-wide tabular-nums sm:mt-4 sm:text-base"
        title={card.cardNumberMasked}
      >
        {card.cardNumberMasked}
      </p>

      <div className="relative mt-auto grid min-w-0 grid-cols-2 gap-x-3 gap-y-2 pt-3">
        <div className="min-w-0">
          <p className="text-[9px] uppercase tracking-wider text-white/55 sm:text-[10px]">Balance</p>
          <p
            className="truncate font-display text-sm font-bold tabular-nums sm:text-base"
            title={money(card.balance, card.currency)}
          >
            {money(card.balance, card.currency)}
          </p>
        </div>
        <div className="min-w-0 text-right">
          <p className="text-[9px] uppercase tracking-wider text-white/55 sm:text-[10px]">
            Credit limit
          </p>
          <p
            className="truncate font-mono text-xs tabular-nums sm:text-sm"
            title={money(card.creditLimit, card.currency)}
          >
            {money(card.creditLimit, card.currency)}
          </p>
        </div>
        <div className="min-w-0">
          <p className="text-[9px] uppercase tracking-wider text-white/55 sm:text-[10px]">
            Taken credit
          </p>
          <p
            className="truncate font-mono text-xs tabular-nums sm:text-sm"
            title={money(loan, card.currency)}
          >
            {money(loan, card.currency)}
          </p>
        </div>
        <div className="min-w-0 text-right">
          <p className="text-[9px] uppercase tracking-wider text-white/55 sm:text-[10px]">Debt</p>
          <p
            className={[
              'truncate font-mono text-xs font-semibold tabular-nums sm:text-sm',
              debt > 0 ? 'text-amber-200' : 'text-white/90',
            ].join(' ')}
            title={money(debt, card.currency)}
          >
            {money(debt, card.currency)}
          </p>
        </div>
      </div>
    </button>
  )
}

interface Props {
  cards: CardResponse[]
  setCards: Dispatch<SetStateAction<CardResponse[]>>
  selectedCardId: string | null
  onSelectCard: (id: string | null) => void
  initialLoading: boolean
}

export function CardsTab({
  cards,
  setCards,
  selectedCardId,
  onSelectCard,
  initialLoading,
}: Props) {
  const [amount, setAmount] = useState('100')
  const [loading, setLoading] = useState(false)
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [notice, setNotice] = useState<string | null>(null)

  const selected = cards.find((c) => c.id === selectedCardId) ?? null
  const selectedActive = selected?.status.toUpperCase() === 'ACTIVE'
  const showLoading = (initialLoading && cards.length === 0) || loading
  const balanceNum = selected != null ? Number(selected.balance) : NaN
  const debtNum = selected != null ? Number(selected.activeDebt ?? 0) : NaN
  // Matches card-service: no active debt and balance is not negative.
  const selectedClosed = selected?.status.toUpperCase() === 'CLOSED'
  const canClose =
    selectedActive &&
    selected != null &&
    Number.isFinite(balanceNum) &&
    balanceNum >= 0 &&
    Number.isFinite(debtNum) &&
    debtNum <= 0
  const canDelete =
    selectedClosed &&
    selected != null &&
    Number.isFinite(balanceNum) &&
    balanceNum >= 0 &&
    Number.isFinite(debtNum) &&
    debtNum <= 0

  const lifecycleHint: { tone: 'info' | 'ok' | 'warn'; title: string; detail: string } = (() => {
    if (!selected) {
      return {
        tone: 'info',
        title: 'Select a card',
        detail: 'Active cards can be closed here. After closing, this panel switches to permanent delete.',
      }
    }
    if (selectedClosed) {
      if (Number.isFinite(debtNum) && debtNum > 0) {
        return {
          tone: 'warn',
          title: 'Cannot delete — active debt',
          detail: `Debt ${money(debtNum, selected.currency)} must be cleared first via Top-up (≥ debt). Then delete.`,
        }
      }
      if (Number.isFinite(balanceNum) && balanceNum < 0) {
        return {
          tone: 'warn',
          title: 'Cannot delete — negative balance',
          detail: `Balance is ${money(balanceNum, selected.currency)}. Top-up to a non-negative balance, then delete.`,
        }
      }
      return {
        tone: 'ok',
        title: 'Ready to delete',
        detail:
          balanceNum > 0
            ? `Card is closed. Delete removes it and its transactions from the DB. Remaining balance ${money(balanceNum, selected.currency)} will be lost.`
            : 'Card is closed. Permanent delete removes the card and its transactions. This cannot be undone.',
      }
    }
    if (!selectedActive) {
      return {
        tone: 'info',
        title: `Status: ${selected.status}`,
        detail: 'Only ACTIVE cards can be closed, and only CLOSED cards can be deleted here.',
      }
    }
    if (Number.isFinite(debtNum) && debtNum > 0) {
      return {
        tone: 'warn',
        title: 'Cannot close — active debt',
        detail: `Debt ${money(debtNum, selected.currency)} must be cleared first. Use Top-up (≥ debt); top-up pays debt before adding to balance.`,
      }
    }
    if (Number.isFinite(balanceNum) && balanceNum < 0) {
      return {
        tone: 'warn',
        title: 'Cannot close — negative balance',
        detail: `Balance is ${money(balanceNum, selected.currency)}. Top-up until the balance is zero or positive, then close.`,
      }
    }
    return {
      tone: 'ok',
      title: 'Ready to close',
      detail:
        balanceNum > 0
          ? `Balance ${money(balanceNum, selected.currency)} stays on the closed card (no refund). After close, you can delete the card here.`
          : 'No debt and non-negative balance — close the card first, then delete it permanently.',
    }
  })()

  async function refresh() {
    setLoading(true)
    setError(null)
    try {
      const data = await getCards()
      setCards(data)
      if (selectedCardId && !data.some((c) => c.id === selectedCardId)) {
        onSelectCard(data[0]?.id ?? null)
      } else if (!selectedCardId && data[0]) {
        onSelectCard(data[0].id)
      }
    } catch (e) {
      setError(e instanceof ApiError ? e.message : 'Failed to load cards')
    } finally {
      setLoading(false)
    }
  }

  async function run(action: () => Promise<CardResponse>, success: string) {
    setBusy(true)
    setError(null)
    setNotice(null)
    try {
      const updated = await action()
      setCards((prev) => upsertCardInList(prev, updated))
      onSelectCard(updated.id)
      setNotice(success)
    } catch (e) {
      setError(e instanceof ApiError ? e.message : 'Operation failed')
    } finally {
      setBusy(false)
    }
  }

  async function runDelete() {
    if (!selected) return
    const id = selected.id
    const label = selected.cardNumberMasked
    setBusy(true)
    setError(null)
    setNotice(null)
    try {
      await deleteCard(id)
      const next = cards.filter((c) => c.id !== id)
      setCards(next)
      onSelectCard(next[0]?.id ?? null)
      setNotice(`Card ${label} deleted permanently`)
    } catch (e) {
      setError(e instanceof ApiError ? e.message : 'Delete failed')
    } finally {
      setBusy(false)
    }
  }

  const parsedAmount = Number(amount)

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

        {showLoading && (
          <div className="panel h-48 animate-pulse bg-gradient-to-r from-bank-mist via-white to-bank-mist bg-[length:200%_100%] animate-shimmer" />
        )}

        {!showLoading && cards.length === 0 && (
          <div className="panel p-8 text-center text-sm text-bank-ink/55">
            No cards yet. Issue your first card.
          </div>
        )}

        <div className="grid gap-4 sm:grid-cols-2 sm:items-start">
          {cards.map((card) => (
            <PlasticCard
              key={card.id}
              card={card}
              selected={selected?.id === card.id}
              onSelect={() => onSelectCard(card.id)}
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
          <h3 className="font-display text-lg font-bold">
            {selectedClosed ? 'Delete card' : 'Close card'}
          </h3>
          <p className="mt-1 text-sm text-bank-ink/55">
            {selectedClosed ? (
              <>
                DELETE <span className="font-mono text-xs">/cards/&#123;id&#125;</span>
              </>
            ) : (
              <>
                POST <span className="font-mono text-xs">/cards/&#123;id&#125;/close</span>
                {' '}· then delete when closed
              </>
            )}
          </p>
          <div
            className={[
              'mt-3 rounded-xl px-3 py-2.5 text-sm',
              lifecycleHint.tone === 'ok' && 'bg-bank-success/10 text-bank-success',
              lifecycleHint.tone === 'warn' && 'bg-bank-warn/10 text-bank-warn',
              lifecycleHint.tone === 'info' && 'bg-bank-mist text-bank-ink/70',
            ]
              .filter(Boolean)
              .join(' ')}
          >
            <p className="font-semibold">{lifecycleHint.title}</p>
            <p className="mt-1 leading-relaxed opacity-90">{lifecycleHint.detail}</p>
          </div>
          {selectedClosed ? (
            <button
              type="button"
              className="btn-secondary mt-4 w-full border-bank-danger bg-bank-danger/10 text-bank-danger hover:border-bank-danger hover:bg-bank-danger hover:text-white"
              disabled={busy || !canDelete}
              onClick={() => void runDelete()}
            >
              Delete selected card permanently
            </button>
          ) : (
            <button
              type="button"
              className="btn-secondary mt-4 w-full border-bank-danger/40 text-bank-danger hover:border-bank-danger hover:text-bank-danger"
              disabled={busy || !canClose}
              onClick={() =>
                selected &&
                void run(() => closeCard(selected.id), 'Card closed — you can delete it now')
              }
            >
              Close selected card
            </button>
          )}
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
