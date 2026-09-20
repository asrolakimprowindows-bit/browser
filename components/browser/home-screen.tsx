'use client'

import { useState, type KeyboardEvent } from 'react'
import { Crosshair, MousePointerClick, Move, Search, Settings2 } from 'lucide-react'
import { ENGINES, SHORTCUTS, type SearchEngine, type Tab } from '@/lib/browser-data'
import { LetterTile } from './primitives'

interface HomeScreenProps {
  tabs: Tab[]
  engine: SearchEngine
  onNavigate: (input: string, title?: string) => void
  onSwitchTab: (id: string) => void
  onOpenSettings: () => void
  onDirect: () => void
}

const HINTS = [
  { icon: MousePointerClick, text: 'Tap Denia to hear what she has to say.' },
  { icon: Move, text: 'Drag her anywhere when she is in the way.' },
  { icon: Crosshair, text: 'Double-tap her, then tap a spot: she runs there.' },
]

export function HomeScreen({ tabs, engine, onNavigate, onSwitchTab, onOpenSettings, onDirect }: HomeScreenProps) {
  const [query, setQuery] = useState('')
  const recent = tabs.filter((t) => t.kind === 'page').slice(-6).reverse()
  const engineInfo = ENGINES[engine]

  const submit = () => {
    const q = query.trim()
    if (!q) return
    const looksLikeUrl = !q.includes(' ') && /^[\w-]+(\.[\w-]+)+/.test(q.replace(/^https?:\/\//, ''))
    if (looksLikeUrl) onNavigate(q)
    else onNavigate(engineInfo.host, `${q} - ${engineInfo.label}`)
    setQuery('')
  }

  const onKeyDown = (e: KeyboardEvent<HTMLInputElement>) => {
    if (e.key !== 'Enter' || e.nativeEvent.isComposing || e.keyCode === 229) return
    submit()
  }

  return (
    <div className="flex flex-col gap-7 px-5 pb-36 pt-2">
      <header className="flex items-center justify-between">
        <div>
          <p className="font-display text-2xl font-semibold tracking-tight">Multex</p>
          <p className="-mt-0.5 text-xs font-semibold text-pink">with Denia</p>
        </div>
        <button
          type="button"
          onClick={onOpenSettings}
          aria-label="Open settings"
          className="glass grid size-10 place-items-center rounded-full text-ink hover:bg-ink/10"
        >
          <Settings2 className="size-5" />
        </button>
      </header>

      <div>
        <h1 className="font-display text-[34px] font-semibold leading-[1.1] text-balance">Good evening.</h1>
        <p className="mt-1 text-ink-muted">Where are we headed tonight?</p>
      </div>

      <label className="glass flex items-center gap-3 rounded-2xl px-4 py-3 ring-pink/60 transition-shadow focus-within:ring-2">
        <Search className="size-5 shrink-0 text-ink-muted" />
        <input
          value={query}
          onChange={(e) => setQuery(e.target.value)}
          onKeyDown={onKeyDown}
          placeholder={`Search ${engineInfo.label} or type a URL`}
          className="min-w-0 flex-1 bg-transparent text-[15px] text-ink outline-none placeholder:text-ink-muted"
          aria-label="Search or enter address"
          autoComplete="off"
          inputMode="url"
        />
        <span className="rounded-md bg-ink/10 px-1.5 py-0.5 text-[10px] font-bold text-ink-muted">{engineInfo.short}</span>
      </label>

      <section>
        <h2 className="mb-3 text-[11px] font-bold uppercase tracking-[0.18em] text-ink-muted">Shortcuts</h2>
        <div className="grid grid-cols-4 gap-x-3 gap-y-4">
          {SHORTCUTS.map((s) => (
            <button
              key={s.host}
              type="button"
              onClick={() => onNavigate(s.host)}
              className="flex flex-col items-center gap-2 transition-transform active:scale-95"
            >
              <LetterTile label={s.title} tint={s.tint} size="lg" />
              <span className="text-[11px] font-semibold text-ink-muted">{s.title}</span>
            </button>
          ))}
        </div>
      </section>

      {recent.length > 0 && (
        <section>
          <h2 className="mb-3 text-[11px] font-bold uppercase tracking-[0.18em] text-ink-muted">Jump back in</h2>
          <div className="no-scrollbar -mx-5 flex gap-3 overflow-x-auto px-5">
            {recent.map((t) => (
              <button
                key={t.id}
                type="button"
                onClick={() => onSwitchTab(t.id)}
                className="glass flex w-40 shrink-0 items-center gap-3 rounded-2xl p-3 text-left transition-transform active:scale-[0.98]"
              >
                <LetterTile label={t.title} tint={t.tint} />
                <div className="min-w-0">
                  <p className="truncate text-sm font-bold">{t.title}</p>
                  <p className="truncate text-[11px] text-ink-muted">{t.host}</p>
                </div>
              </button>
            ))}
          </div>
        </section>
      )}

      <section className="glass rounded-3xl p-4">
        <div className="flex items-center gap-3">
          <img src="/denia/face_happy.png" alt="" className="size-11 rounded-full object-cover ring-2 ring-pink/70" />
          <div>
            <p className="font-display text-base font-semibold">Meet Denia</p>
            <p className="text-xs text-ink-muted">Your browsing companion</p>
          </div>
        </div>
        <ul className="mt-4 space-y-2.5">
          {HINTS.map(({ icon: Icon, text }) => (
            <li key={text} className="flex items-center gap-3 text-[13px] text-ink/90">
              <span className="grid size-7 shrink-0 place-items-center rounded-lg bg-lav/20 text-lav">
                <Icon className="size-3.5" />
              </span>
              {text}
            </li>
          ))}
        </ul>
        <button
          type="button"
          onClick={onDirect}
          className="mt-4 flex w-full items-center justify-center gap-2 rounded-2xl bg-gradient-to-br from-pink to-lav py-3 text-sm font-bold text-[#2a1236] shadow-lg shadow-pink/25 transition-transform active:scale-[0.98]"
        >
          <Crosshair className="size-4" />
          Direct Denia
        </button>
      </section>
    </div>
  )
}
