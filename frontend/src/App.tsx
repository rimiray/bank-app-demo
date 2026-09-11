import { useEffect, useState } from 'react'
import { ArchitectureTab } from './components/ArchitectureTab'
import { CardsTab } from './components/CardsTab'
import { CreditTab } from './components/CreditTab'
import { Tabs } from './components/Tabs'
import { getCards } from './api/cards'
import type { CardResponse, TabId } from './types'

export default function App() {
  const [tab, setTab] = useState<TabId>('cards')
  const [cards, setCards] = useState<CardResponse[]>([])
  const [selectedCardId, setSelectedCardId] = useState<string | null>(null)
  const [cardsLoading, setCardsLoading] = useState(true)
  const [cardsLoadError, setCardsLoadError] = useState<string | null>(null)

  useEffect(() => {
    void getCards()
      .then((data) => {
        setCards(data)
        setCardsLoadError(null)
        setSelectedCardId((current) => {
          if (current && data.some((c) => c.id === current)) return current
          return data[0]?.id ?? null
        })
      })
      .catch(() => {
        setCards([])
        setCardsLoadError('Could not load cards — please refresh')
      })
      .finally(() => setCardsLoading(false))
  }, [])

  return (
    <div className="relative min-h-screen overflow-x-hidden">
      <div className="pointer-events-none absolute inset-x-0 top-0 h-[420px] bg-[radial-gradient(ellipse_at_top,_rgba(0,160,200,0.16),_transparent_60%)]" />

      <div className="relative mx-auto max-w-6xl px-4 pb-16 pt-8 sm:px-6 lg:px-8">
        <header className="mb-8 animate-fade-up">
          <div className="flex flex-wrap items-center justify-between gap-4">
            <div className="flex items-center gap-3">
              <div className="flex h-12 w-12 items-center justify-center rounded-2xl bg-bank-ink shadow-soft">
                <span className="text-sm font-bold text-bank-teal">ZBK</span>
              </div>
              <div>
                <p className="text-xs font-medium text-bank-teal-dark">FinTech demo platform</p>
                <h1 className="text-2xl font-bold text-bank-ink">ZBK Bank Demo</h1>
              </div>
            </div>
            <p className="max-w-xs text-right text-sm text-bank-ink/50">
              Cards · Credit scoring · AI collateral — event-driven microservices dashboard
            </p>
          </div>
        </header>

        <Tabs active={tab} onChange={setTab} />

        <main className="mt-6">
          <div className={tab === 'cards' ? undefined : 'hidden'}>
            <CardsTab
              cards={cards}
              setCards={setCards}
              selectedCardId={selectedCardId}
              onSelectCard={setSelectedCardId}
              initialLoading={cardsLoading}
              loadError={cardsLoadError}
              onClearLoadError={() => setCardsLoadError(null)}
            />
          </div>
          <div className={tab === 'credit' ? undefined : 'hidden'}>
            <CreditTab
              cards={cards}
              setCards={setCards}
              selectedCardId={selectedCardId}
              onSelectCard={setSelectedCardId}
            />
          </div>
          <div className={tab === 'architecture' ? undefined : 'hidden'}>
            <ArchitectureTab />
          </div>
        </main>
      </div>
    </div>
  )
}
