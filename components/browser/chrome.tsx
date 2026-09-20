'use client'

import { BatteryFull, ChevronLeft, ChevronRight, Ellipsis, Home, Lock, Plus, RotateCw, Shield, Signal, Wifi } from 'lucide-react'
import { cn } from '@/lib/utils'
import { PAGE_COPY, type Tab } from '@/lib/browser-data'
import { LetterTile } from './primitives'

export function StatusBar() {
  return (
    <div className="flex items-center justify-between px-6 pb-1 pt-3 text-[13px] font-bold">
      <span>11:47</span>
      <div className="flex items-center gap-1.5">
        <Signal className="size-3.5" />
        <Wifi className="size-3.5" />
        <BatteryFull className="size-4" />
      </div>
    </div>
  )
}

export function AddressBar({ tab, blockTrackers, onHome }: { tab: Tab; blockTrackers: boolean; onHome: () => void }) {
  return (
    <div className="px-3 pb-2 pt-1">
      <div className="glass flex items-center gap-2 rounded-full py-1.5 pl-1.5 pr-1.5">
        <button
          type="button"
          onClick={onHome}
          aria-label="Go home"
          className="grid size-8 place-items-center rounded-full text-ink-muted hover:bg-ink/10 hover:text-ink"
        >
          <Home className="size-4" />
        </button>
        <Lock className="size-3.5 shrink-0 text-ink-muted" aria-label="Secure connection" />
        <span className="flex-1 truncate text-sm font-semibold">{tab.host}</span>
        {blockTrackers && (
          <span className="flex items-center gap-1 rounded-full bg-sky/20 px-2 py-0.5 text-[11px] font-bold text-sky">
            <Shield className="size-3" />3
          </span>
        )}
        <button
          type="button"
          aria-label="Reload"
          className="grid size-8 place-items-center rounded-full text-ink-muted hover:bg-ink/10 hover:text-ink"
        >
          <RotateCw className="size-4" />
        </button>
      </div>
    </div>
  )
}

export function WebPage({ tab }: { tab: Tab }) {
  return (
    <article className="px-4 pb-36 pt-1">
      <div
        className="rounded-3xl p-5"
        style={{ background: `linear-gradient(160deg, ${tab.tint}40, transparent 75%)` }}
      >
        <div className="flex items-center gap-3">
          <LetterTile label={tab.title} tint={tab.tint} />
          <div className="min-w-0">
            <p className="truncate text-xs text-ink-muted">{tab.host}</p>
            <h1 className="truncate font-display text-xl font-semibold">{tab.title}</h1>
          </div>
        </div>
      </div>
      <div className="mt-5 space-y-4 text-[15px] leading-7 text-ink/85">
        <p>{PAGE_COPY[0]}</p>
        <p>{PAGE_COPY[1]}</p>
        <div className="grid grid-cols-2 gap-3 py-1">
          {[0, 1, 2, 3].map((i) => (
            <div key={i} className="glass rounded-2xl p-3">
              <div className="mb-2 h-16 rounded-xl" style={{ background: `${tab.tint}${i % 2 ? '55' : '33'}` }} />
              <div className="h-2 w-3/4 rounded-full bg-ink/25" />
              <div className="mt-1.5 h-2 w-1/2 rounded-full bg-ink/15" />
            </div>
          ))}
        </div>
        <p>{PAGE_COPY[2]}</p>
        <p>{PAGE_COPY[3]}</p>
        <p>{PAGE_COPY[4]}</p>
      </div>
    </article>
  )
}

function DockButton({
  label,
  onClick,
  disabled,
  children,
}: {
  label: string
  onClick?: () => void
  disabled?: boolean
  children: React.ReactNode
}) {
  return (
    <button
      type="button"
      aria-label={label}
      onClick={onClick}
      disabled={disabled}
      className={cn(
        'grid size-11 place-items-center rounded-full transition-colors',
        disabled ? 'text-ink/30' : 'text-ink hover:bg-ink/10 active:bg-ink/15',
      )}
    >
      {children}
    </button>
  )
}

export function BottomBar({
  canBack,
  onBack,
  tabCount,
  onNewTab,
  onTabs,
  onMenu,
}: {
  canBack: boolean
  onBack: () => void
  tabCount: number
  onNewTab: () => void
  onTabs: () => void
  onMenu: () => void
}) {
  return (
    <nav aria-label="Browser controls" className="absolute inset-x-0 bottom-0 z-20 px-4 pb-4">
      <div className="glass-strong flex items-center justify-between rounded-[28px] px-2 py-2 shadow-[0_12px_40px_rgba(0,0,0,0.35)]">
        <DockButton label="Back" onClick={onBack} disabled={!canBack}>
          <ChevronLeft className="size-6" />
        </DockButton>
        <DockButton label="Forward" disabled>
          <ChevronRight className="size-6" />
        </DockButton>
        <button
          type="button"
          onClick={onNewTab}
          aria-label="New tab"
          className="grid size-12 place-items-center rounded-full bg-gradient-to-br from-pink to-lav text-[#2a1236] shadow-lg shadow-pink/30 transition-transform active:scale-95"
        >
          <Plus className="size-6" strokeWidth={2.5} />
        </button>
        <DockButton label={`${tabCount} open tabs`} onClick={onTabs}>
          <span className="grid size-6 place-items-center rounded-[7px] border-2 border-current text-[11px] font-bold">
            {tabCount}
          </span>
        </DockButton>
        <DockButton label="Menu" onClick={onMenu}>
          <Ellipsis className="size-6" />
        </DockButton>
      </div>
    </nav>
  )
}
