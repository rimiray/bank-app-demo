import { useMemo, useState, type Dispatch, type SetStateAction } from 'react'
import { applyCreditToCard } from '../api/cards'
import { evaluateCollateral } from '../api/collateral'
import { calculateCredit } from '../api/credit'
import { ApiError, money } from '../api/client'
import { upsertCardInList } from '../lib/cardsOrder'
import type {
  CardResponse,
  CollateralEvaluationResponse,
  CreditCalculationResponse,
} from '../types'

type Step = 1 | 2

interface Props {
  cards: CardResponse[]
  setCards: Dispatch<SetStateAction<CardResponse[]>>
  selectedCardId: string | null
  onSelectCard: (id: string | null) => void
}

export function CreditTab({ cards, setCards, selectedCardId, onSelectCard }: Props) {
  const [step, setStep] = useState<Step>(1)
  const [file, setFile] = useState<File | null>(null)
  const [preview, setPreview] = useState<string | null>(null)
  const [dragOver, setDragOver] = useState(false)
  const [collateral, setCollateral] = useState<CollateralEvaluationResponse | null>(null)
  const [requestedAmount, setRequestedAmount] = useState('10000')
  const [monthlyIncome, setMonthlyIncome] = useState('3500')
  const [termMonths, setTermMonths] = useState('24')
  const [result, setResult] = useState<CreditCalculationResponse | null>(null)
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [notice, setNotice] = useState<string | null>(null)

  const activeCards = useMemo(
    () => cards.filter((c) => c.status.toUpperCase() === 'ACTIVE'),
    [cards],
  )

  const targetCardId =
    selectedCardId && activeCards.some((c) => c.id === selectedCardId)
      ? selectedCardId
      : (activeCards[0]?.id ?? '')

  const collateralValue = useMemo(() => {
    if (!collateral) return ''
    return String(collateral.estimatedValueEur)
  }, [collateral])

  function pickFile(next: File | null) {
    if (preview) URL.revokeObjectURL(preview)
    setFile(next)
    setPreview(next ? URL.createObjectURL(next) : null)
    setCollateral(null)
    setResult(null)
    setNotice(null)
    setError(null)
  }

  async function onEvaluate() {
    if (!file) return
    setBusy(true)
    setError(null)
    setNotice(null)
    try {
      const data = await evaluateCollateral(file)
      setCollateral(data)
      setStep(2)
    } catch (e) {
      setError(e instanceof ApiError ? e.message : 'Collateral evaluation failed')
    } finally {
      setBusy(false)
    }
  }

  async function onCalculate() {
    setBusy(true)
    setError(null)
    setNotice(null)
    try {
      const data = await calculateCredit({
        requestedAmount: Number(requestedAmount),
        monthlyIncome: Number(monthlyIncome),
        termMonths: Number(termMonths),
        aiCollateralValueEur: collateral ? Number(collateral.estimatedValueEur) : undefined,
      })
      setResult(data)
    } catch (e) {
      setError(e instanceof ApiError ? e.message : 'Credit calculation failed')
    } finally {
      setBusy(false)
    }
  }

  async function onApplyToCard() {
    if (!result || result.status !== 'APPROVED' || !targetCardId) return
    setBusy(true)
    setError(null)
    setNotice(null)
    try {
      const updated = await applyCreditToCard(
        targetCardId,
        Number(requestedAmount),
        Number(result.approvedLimit),
      )
      setCards((prev) => upsertCardInList(prev, updated))
      onSelectCard(updated.id)
      setNotice(
        `Credit applied to ${updated.cardNumberMasked}: +${money(Number(requestedAmount))} balance & debt, loan ${money(Number(updated.loanPrincipal ?? 0))}, limit ${money(updated.creditLimit)}`,
      )
    } catch (e) {
      setError(e instanceof ApiError ? e.message : 'Failed to apply credit to card')
    } finally {
      setBusy(false)
    }
  }

  return (
    <div className="animate-fade-up space-y-6">
      <div className="flex flex-wrap items-end justify-between gap-3">
        <div>
          <h2 className="font-display text-2xl font-bold tracking-tight">Credit & AI Collateral</h2>
          <p className="mt-1 text-sm text-bank-ink/55">
            Step-by-step flow · AI appraisal then annuity scoring
          </p>
        </div>
        <ol className="flex items-center gap-2">
          {[1, 2].map((n) => (
            <li key={n} className="flex items-center gap-2">
              <button
                type="button"
                onClick={() => setStep(n as Step)}
                className={[
                  'flex h-9 w-9 items-center justify-center rounded-full font-display text-sm font-bold transition',
                  step === n
                    ? 'bg-bank-teal text-white'
                    : 'bg-white text-bank-ink/50 ring-1 ring-bank-line',
                ].join(' ')}
              >
                {n}
              </button>
              {n === 1 && <span className="hidden text-xs text-bank-ink/40 sm:inline">Collateral</span>}
              {n === 2 && <span className="hidden text-xs text-bank-ink/40 sm:inline">Credit</span>}
            </li>
          ))}
        </ol>
      </div>

      {step === 1 && (
        <section className="panel grid gap-6 p-5 sm:p-6 lg:grid-cols-[1.1fr_0.9fr]">
          <div>
            <h3 className="font-display text-lg font-bold">Step 1 · Upload collateral photo</h3>
            <p className="mt-1 text-sm text-bank-ink/55">
              POST <span className="font-mono text-xs">localhost:8083/api/v1/collateral/evaluate</span>
            </p>

            <label
              onDragOver={(e) => {
                e.preventDefault()
                setDragOver(true)
              }}
              onDragLeave={() => setDragOver(false)}
              onDrop={(e) => {
                e.preventDefault()
                setDragOver(false)
                const dropped = e.dataTransfer.files?.[0]
                if (dropped) pickFile(dropped)
              }}
              className={[
                'mt-5 flex min-h-56 cursor-pointer flex-col items-center justify-center rounded-2xl border-2 border-dashed px-6 text-center transition',
                dragOver
                  ? 'border-bank-teal bg-bank-mist'
                  : 'border-bank-line bg-bank-sand/60 hover:border-bank-teal/60',
              ].join(' ')}
            >
              <input
                type="file"
                accept="image/*"
                className="hidden"
                onChange={(e) => pickFile(e.target.files?.[0] ?? null)}
              />
              {preview ? (
                <img
                  src={preview}
                  alt="Collateral preview"
                  className="max-h-48 rounded-xl object-contain shadow-soft"
                />
              ) : (
                <>
                  <span className="font-display text-base font-bold">Drop photo here</span>
                  <span className="mt-1 text-sm text-bank-ink/45">or click to browse · JPG / PNG</span>
                </>
              )}
            </label>

            <button
              type="button"
              className="btn-primary mt-4 w-full sm:w-auto"
              disabled={!file || busy}
              onClick={() => void onEvaluate()}
            >
              {busy ? 'Evaluating…' : 'Evaluate with AI'}
            </button>
          </div>

          <div className="rounded-2xl bg-bank-ink p-5 text-white">
            <p className="font-mono text-[10px] uppercase tracking-wider text-bank-teal">AI result</p>
            {collateral ? (
              <dl className="mt-4 space-y-4">
                <div>
                  <dt className="text-xs text-white/50">Object</dt>
                  <dd className="font-display text-xl font-bold">{collateral.objectDetected}</dd>
                </div>
                <div>
                  <dt className="text-xs text-white/50">Condition</dt>
                  <dd className="text-sm">{collateral.condition}</dd>
                </div>
                <div>
                  <dt className="text-xs text-white/50">Estimated value</dt>
                  <dd className="font-display text-2xl font-bold text-bank-teal">
                    {money(collateral.estimatedValueEur)}
                  </dd>
                </div>
              </dl>
            ) : (
              <p className="mt-6 text-sm text-white/45">
                Upload a photo of collateral (watch, laptop, bike…) to get a Gemini-backed valuation.
              </p>
            )}
          </div>
        </section>
      )}

      {step === 2 && (
        <section className="panel grid gap-6 p-5 sm:p-6 lg:grid-cols-2">
          <div>
            <h3 className="font-display text-lg font-bold">Step 2 · Credit calculator</h3>
            <p className="mt-1 text-sm text-bank-ink/55">
              POST <span className="font-mono text-xs">localhost:8082/api/v1/credits/calculate</span>
            </p>

            <div className="mt-5 space-y-4">
              <label className="block">
                <span className="label">Target card</span>
                <select
                  className="field"
                  value={targetCardId}
                  onChange={(e) => onSelectCard(e.target.value || null)}
                >
                  {activeCards.length === 0 && (
                    <option value="">No active cards — issue one first</option>
                  )}
                  {activeCards.map((c) => (
                    <option key={c.id} value={c.id}>
                      {c.cardNumberMasked} · bal {money(c.balance, c.currency)} · loan{' '}
                      {money(Number(c.loanPrincipal ?? 0), c.currency)} · debt{' '}
                      {money(Number(c.activeDebt ?? 0), c.currency)}
                    </option>
                  ))}
                </select>
              </label>
              <label className="block">
                <span className="label">Requested amount (EUR)</span>
                <input
                  className="field"
                  type="number"
                  min="0.01"
                  step="0.01"
                  value={requestedAmount}
                  onChange={(e) => setRequestedAmount(e.target.value)}
                />
              </label>
              <label className="block">
                <span className="label">Monthly income (EUR)</span>
                <input
                  className="field"
                  type="number"
                  min="0.01"
                  step="0.01"
                  value={monthlyIncome}
                  onChange={(e) => setMonthlyIncome(e.target.value)}
                />
              </label>
              <label className="block">
                <span className="label">Term (months)</span>
                <input
                  className="field"
                  type="number"
                  min="1"
                  max="120"
                  value={termMonths}
                  onChange={(e) => setTermMonths(e.target.value)}
                />
              </label>
              <label className="block">
                <span className="label">AI collateral value (auto)</span>
                <input
                  className="field bg-bank-mist/70"
                  type="number"
                  readOnly
                  value={collateralValue}
                  placeholder="Complete step 1 first"
                />
              </label>
            </div>

            <div className="mt-5 flex flex-wrap gap-3">
              <button type="button" className="btn-secondary" onClick={() => setStep(1)}>
                Back
              </button>
              <button
                type="button"
                className="btn-primary"
                disabled={busy}
                onClick={() => void onCalculate()}
              >
                {busy ? 'Calculating…' : 'Calculate credit'}
              </button>
            </div>
          </div>

          <div className="rounded-2xl border border-bank-line bg-bank-sand/50 p-5">
            <p className="font-mono text-[10px] uppercase tracking-wider text-bank-ink/45">Verdict</p>
            {result ? (
              <div className="mt-4 space-y-5">
                <div
                  className={[
                    'inline-flex rounded-full px-3 py-1 font-display text-sm font-bold uppercase tracking-wide',
                    result.status === 'APPROVED'
                      ? 'bg-bank-success/15 text-bank-success'
                      : 'bg-bank-danger/15 text-bank-danger',
                  ].join(' ')}
                >
                  {result.status}
                </div>
                <dl className="grid gap-4 sm:grid-cols-2">
                  <div>
                    <dt className="text-xs text-bank-ink/45">Monthly payment</dt>
                    <dd className="font-display text-2xl font-bold">
                      {money(result.monthlyPayment)}
                    </dd>
                  </div>
                  <div>
                    <dt className="text-xs text-bank-ink/45">Approved limit</dt>
                    <dd className="font-display text-2xl font-bold">
                      {money(result.approvedLimit)}
                    </dd>
                  </div>
                  <div>
                    <dt className="text-xs text-bank-ink/45">Interest rate</dt>
                    <dd className="font-mono text-lg">{result.interestRate}%</dd>
                  </div>
                  {collateral && (
                    <div>
                      <dt className="text-xs text-bank-ink/45">Collateral boost</dt>
                      <dd className="font-mono text-lg">{money(collateral.estimatedValueEur)}</dd>
                    </div>
                  )}
                </dl>

                {result.status === 'APPROVED' && (
                  <div className="space-y-2 border-t border-bank-line/70 pt-4">
                    <p className="text-sm text-bank-ink/55">
                      Disburse <strong>{money(Number(requestedAmount))}</strong> to card balance, record the
                      same amount as taken credit / debt, and raise credit limit to at least{' '}
                      <strong>{money(result.approvedLimit)}</strong>.
                    </p>
                    <button
                      type="button"
                      className="btn-primary w-full"
                      disabled={busy || !targetCardId}
                      onClick={() => void onApplyToCard()}
                    >
                      {busy ? 'Applying…' : 'Apply credit to selected card'}
                    </button>
                  </div>
                )}
              </div>
            ) : (
              <p className="mt-6 text-sm text-bank-ink/45">
                Fill in amount, income and term. Collateral value from AI is injected automatically.
              </p>
            )}
          </div>
        </section>
      )}

      {notice && (
        <p className="rounded-xl bg-bank-success/10 px-3 py-2 text-sm text-bank-success">{notice}</p>
      )}
      {error && (
        <p className="rounded-xl bg-bank-danger/10 px-3 py-2 text-sm text-bank-danger">{error}</p>
      )}
    </div>
  )
}
