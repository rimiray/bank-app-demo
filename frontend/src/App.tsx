import { useState } from 'react'
import { ArchitectureTab } from './components/ArchitectureTab'
import { CardsTab } from './components/CardsTab'
import { CreditTab } from './components/CreditTab'
import { Tabs } from './components/Tabs'
import type { TabId } from './types'

export default function App() {
  const [tab, setTab] = useState<TabId>('cards')

  return (
    <div className="relative min-h-screen overflow-x-hidden">
      <div className="pointer-events-none absolute inset-x-0 top-0 h-[420px] bg-[radial-gradient(ellipse_at_top,_rgba(0,160,200,0.16),_transparent_60%)]" />

      <div className="relative mx-auto max-w-6xl px-4 pb-16 pt-8 sm:px-6 lg:px-8">
        <header className="mb-8 animate-fade-up">
          <div className="flex flex-wrap items-center justify-between gap-4">
            <div className="flex items-center gap-3">
              <div className="flex h-12 w-12 items-center justify-center rounded-2xl bg-bank-ink shadow-soft">
                <span className="font-display text-sm font-extrabold tracking-tight text-bank-teal">
                  ZBK
                </span>
              </div>
              <div>
                <p className="font-mono text-[10px] uppercase tracking-[0.2em] text-bank-teal-dark">
                  FinTech demo platform
                </p>
                <h1 className="font-display text-3xl font-extrabold tracking-tight text-bank-ink sm:text-4xl">
                  ZBK Bank Demo
                </h1>
              </div>
            </div>
            <p className="max-w-xs text-right text-sm text-bank-ink/50">
              Cards · Credit scoring · AI collateral — event-driven microservices dashboard
            </p>
          </div>
        </header>

        <Tabs active={tab} onChange={setTab} />

        <main className="mt-6">
          {tab === 'cards' && <CardsTab />}
          {tab === 'credit' && <CreditTab />}
          {tab === 'architecture' && <ArchitectureTab />}
        </main>
      </div>
    </div>
  )
}
