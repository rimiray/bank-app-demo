import { useState, type Dispatch, type SetStateAction } from 'react'
import { closeCard, deleteCard, getCards, issueCard, purchaseCard, topUpCard } from '../api/cards'
import { ApiError, money } from '../api/client'
import { upsertCardInList } from '../lib/cardsOrder'
import { formatStatus } from '../lib/formatStatus'
import type { CardResponse } from '../types'

function statusTone(status: string): string {
  const s = status.toUpperCase()
  if (s === 'ACTIVE') return 'bg-bank-success/15 text-bank-success'
  if (s === 'CLOSED' || s === 'BLOCKED') return 'bg-bank-danger/15 text-bank-danger'
  return 'bg-bank-warn/15 text-bank-warn'
}

function panGroups(masked: string): string[] {
  const normalized = masked.trim().split(/\s+/).filter(Boolean)
  return normalized.length > 0 ? normalized : [masked]
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
  const groups = panGroups(card.cardNumberMasked)
  return (
    <button
      type="button"
      onClick={onSelect}
      className={[
        'relative flex w-full max-w-md flex-col overflow-hidden rounded-xl bg-plastic',
        'aspect-[1.586/1] p-4 text-left text-white shadow-card transition sm:p-5',
        'hover:-translate-y-0.5',
        selected ? 'ring-2 ring-bank-teal ring-offset-2 ring-offset-bank-sand' : '',
        closed ? 'opacity-55' : '',
      ].join(' ')}
    >
      <div className="pointer-events-none absolute -right-8 -top-10 h-36 w-36 rounded-full bg-white/10" />
      <div className="pointer-events-none absolute -bottom-12 left-10 h-40 w-40 rounded-full bg-bank-teal/30 blur-2xl" />

      <div className="relative flex shrink-0 items-start justify-between gap-2">
        <span className="text-base font-semibold">ZBK Bank</span>
        <span className={`status-pill ${statusTone(card.status)}`}>{formatStatus(card.status)}</span>
      </div>

      {/* Middle band — same .figure + semibold as Balance; groups edge-to-edge. */}
      <p
        className="figure relative my-auto flex w-full shrink-0 justify-between gap-1 whitespace-nowrap text-sm font-semibold sm:text-base"
        title={card.cardNumberMasked}
      >
        {groups.map((group, i) => (
          <span key={`${group}-${i}`}>{group}</span>
        ))}
      </p>

      <div className="relative grid min-w-0 shrink-0 grid-cols-2 gap-x-3 gap-y-1 pt-2 sm:gap-y-1.5 sm:pt-3">
        <div className="min-w-0">
          <p className="text-xs text-white/55">Balance</p>
          <p
            className="figure truncate text-sm font-semibold sm:text-base"
            title={money(card.balance, card.currency)}
          >
            {money(card.balance, card.currency)}
          </p>
        </div>
        <div className="min-w-0 text-right">
          <p className="text-xs text-white/55">Credit limit</p>
          <p
            className="figure truncate text-sm"
            title={money(card.creditLimit, card.currency)}
          >
            {money(card.creditLimit, card.currency)}
          </p>
        </div>
        <div className="min-w-0">
          <p className="text-xs text-white/55">Taken credit</p>
          <p className="figure truncate text-sm" title={money(loan, card.currency)}>
            {money(loan, card.currency)}
          </p>
        </div>
        <div className="min-w-0 text-right">
          <p className="text-xs text-white/55">Debt</p>
          <p
            className={[
              'figure truncate text-sm font-semibold',
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
  loadError?: string | null
  onClearLoadError?: () => void
}

export function CardsTab({
  cards,
  setCards,
  selectedCardId,
  onSelectCard,
  initialLoading,
  loadError = null,
  onClearLoadError,
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
  const displayError = error ?? loadError
  const showEmpty = !showLoading && cards.length === 0 && !displayError
  const debtNum = selected != null ? Number(selected.activeDebt ?? 0) : NaN
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
        detail:
          'Active cards can be closed here. After closing, this panel switches to permanent delete.',
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
        title: `Status: ${formatStatus(selected.status)}`,
        detail: 'Only Active cards can be closed, and only Closed cards can be deleted here.',
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
      onClearLoadError?.()
      if (selectedCardId && !data.some((c) => c.id === selectedCardId)) {
        onSelectCard(data[0]?.id ?? null)
      } else if (!selectedCardId && data[0]) {
        onSelectCard(data[0].id)
      }
    } catch (e) {
      setError(
        e instanceof ApiError
          ? e.status >= 500
            ? 'Could not load cards — please refresh'
            : e.message
          : 'Could not load cards — please refresh',
      )
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
            <h2 className="text-xl font-bold">Plastic cards</h2>
            <p className="mt-1 text-sm text-bank-ink/55">
              Live data from <span className="figure text-bank-teal-dark">localhost:8081</span>
            </p>
          </div>
          <button type="button" className="btn-secondary" onClick={() => void refresh()} disabled={loading}>
            Refresh
          </button>
        </div>

        {showLoading && (
          <div className="panel h-48 animate-pulse bg-gradient-to-r from-bank-mist via-white to-bank-mist bg-[length:200%_100%] animate-shimmer" />
        )}

        {!showLoading && displayError && cards.length === 0 && (
          <div className="panel space-y-3 p-6 text-center">
            <p className="text-sm text-bank-danger">{displayError}</p>
            <button
              type="button"
              className="btn-secondary"
              onClick={() => void refresh()}
              disabled={loading}
            >
              Retry
            </button>
          </div>
        )}

        {showEmpty && (
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
          <h3 className="text-lg font-semibold">Issue a new card</h3>
          <p className="mt-1 text-sm text-bank-ink/55">
            POST <span className="figure text-xs">/api/v1/cards</span>
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
          <h3 className="text-lg font-semibold">Balance operations</h3>
          <p className="mt-1 text-sm text-bank-ink/55">
            {selected ? (
              <>
                Selected · <span className="figure">{selected.cardNumberMasked}</span>
              </>
            ) : (
              'Select a card to top-up or purchase'
            )}
          </p>

          <label className="mt-4 block">
            <span className="label">Amount (EUR)</span>
            <input
              className="field figure"
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
          <h3 className="text-lg font-semibold">
            {selectedClosed ? 'Delete card' : 'Close card'}
          </h3>
          <p className="mt-1 text-sm text-bank-ink/55">
            {selectedClosed ? (
              <>
                DELETE <span className="figure text-xs">/cards/{'{id}'}</span>
              </>
            ) : (
              <>
                POST <span className="figure text-xs">/cards/{'{id}'}/close</span>
                {' '}
                · then delete when closed
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
        {displayError && (
          <div className="flex flex-wrap items-center gap-3 rounded-xl bg-bank-danger/10 px-3 py-2 text-sm text-bank-danger">
            <p className="flex-1">{displayError}</p>
            {cards.length > 0 && (
              <button
                type="button"
                className="btn-secondary shrink-0 border-bank-danger/30 text-bank-danger"
                onClick={() => void refresh()}
                disabled={loading}
              >
                Retry
              </button>
            )}
          </div>
        )}
      </aside>
    </div>
  )
}
