import type { TabId } from '../types'

const TABS: { id: TabId; label: string; hint: string }[] = [
  { id: 'cards', label: 'My Cards', hint: 'card-service · 8081' },
  { id: 'credit', label: 'Credit & AI Collateral', hint: '8082 + 8083' },
  { id: 'architecture', label: 'System Architecture', hint: 'health check' },
]

interface Props {
  active: TabId
  onChange: (tab: TabId) => void
}

export function Tabs({ active, onChange }: Props) {
  return (
    <div className="panel flex flex-col gap-1 p-1.5 sm:flex-row" role="tablist">
      {TABS.map((tab) => {
        const selected = tab.id === active
        return (
          <button
            key={tab.id}
            type="button"
            role="tab"
            aria-selected={selected}
            onClick={() => onChange(tab.id)}
            className={[
              'flex flex-1 flex-col items-start rounded-xl px-4 py-3 text-left transition',
              selected
                ? 'bg-bank-ink text-white shadow-soft'
                : 'text-bank-ink/70 hover:bg-bank-mist hover:text-bank-ink',
            ].join(' ')}
          >
            <span className="font-display text-sm font-bold tracking-tight sm:text-base">
              {tab.label}
            </span>
            <span
              className={[
                'mt-0.5 font-mono text-[10px] uppercase tracking-wider',
                selected ? 'text-bank-teal' : 'text-bank-ink/40',
              ].join(' ')}
            >
              {tab.hint}
            </span>
          </button>
        )
      })}
    </div>
  )
}
