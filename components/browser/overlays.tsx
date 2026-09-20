'use client'

import { BookmarkPlus, Crosshair, Hammer, Heart, Layers, MessageCircle, Plus, RotateCcw, Settings2, Sparkles, Trash2, X } from 'lucide-react'
import { cn } from '@/lib/utils'
import { ENGINES, PETS, type Session, type Settings, type Tab } from '@/lib/browser-data'
import { LetterTile, OverlaySheet, PillButton, Segmented, SettingRow, Switch } from './primitives'

export function TabsOverlay({
  tabs,
  activeId,
  onSelect,
  onCloseTab,
  onNewTab,
  onSaveSession,
  onClose,
}: {
  tabs: Tab[]
  activeId: string
  onSelect: (id: string) => void
  onCloseTab: (id: string) => void
  onNewTab: () => void
  onSaveSession: () => void
  onClose: () => void
}) {
  return (
    <OverlaySheet
      title="Tabs"
      subtitle={`${tabs.length} open`}
      onClose={onClose}
      footer={
        <div className="flex gap-3">
          <PillButton onClick={onSaveSession}>
            <BookmarkPlus className="size-4" />
            Save session
          </PillButton>
          <PillButton variant="accent" onClick={onNewTab}>
            <Plus className="size-4" />
            New tab
          </PillButton>
        </div>
      }
    >
      <div className="grid grid-cols-2 gap-3">
        {tabs.map((t) => (
          <div
            key={t.id}
            className={cn(
              'glass relative overflow-hidden rounded-2xl transition-transform active:scale-[0.98]',
              t.id === activeId && 'ring-2 ring-pink',
            )}
          >
            <button
              type="button"
              onClick={() => onSelect(t.id)}
              aria-label={`Switch to ${t.title}`}
              className="absolute inset-0 z-[1]"
            />
            <div
              className="h-24"
              style={{
                background:
                  t.kind === 'home'
                    ? 'linear-gradient(160deg, var(--lav), var(--pink))'
                    : `linear-gradient(160deg, ${t.tint}, ${t.tint}33)`,
              }}
            />
            <div className="flex items-center gap-2 p-3">
              <LetterTile label={t.kind === 'home' ? 'M' : t.title} tint={t.tint} size="sm" />
              <div className="min-w-0 flex-1">
                <p className="truncate text-sm font-bold">{t.title}</p>
                <p className="truncate text-[11px] text-ink-muted">{t.host || 'Home'}</p>
              </div>
            </div>
            <button
              type="button"
              onClick={() => onCloseTab(t.id)}
              aria-label={`Close ${t.title}`}
              className="absolute right-2 top-2 z-[2] grid size-7 place-items-center rounded-full bg-black/45 text-white hover:bg-black/65"
            >
              <X className="size-3.5" />
            </button>
          </div>
        ))}
      </div>
    </OverlaySheet>
  )
}

export function SessionsOverlay({
  sessions,
  onRestore,
  onDelete,
  onSave,
  onClose,
}: {
  sessions: Session[]
  onRestore: (s: Session) => void
  onDelete: (id: string) => void
  onSave: () => void
  onClose: () => void
}) {
  return (
    <OverlaySheet
      title="Sessions"
      subtitle="Park a set of tabs, bring it back later"
      onClose={onClose}
      footer={
        <PillButton variant="accent" onClick={onSave}>
          <BookmarkPlus className="size-4" />
          Save current tabs
        </PillButton>
      }
    >
      {sessions.length === 0 ? (
        <p className="py-10 text-center text-sm text-ink-muted">No sessions yet. Save your open tabs to start one.</p>
      ) : (
        <ul className="space-y-3">
          {sessions.map((s) => (
            <li key={s.id} className="glass rounded-2xl p-4">
              <div className="flex items-start justify-between gap-3">
                <div>
                  <p className="font-display text-base font-semibold">{s.name}</p>
                  <p className="text-xs text-ink-muted">
                    {s.tabs.length} tabs · {s.savedAt}
                  </p>
                </div>
                <button
                  type="button"
                  onClick={() => onDelete(s.id)}
                  aria-label={`Delete ${s.name}`}
                  className="grid size-8 place-items-center rounded-full text-ink-muted hover:bg-ink/10 hover:text-ink"
                >
                  <Trash2 className="size-4" />
                </button>
              </div>
              <div className="mt-3 flex items-center justify-between">
                <div className="flex -space-x-1.5">
                  {s.tabs.slice(0, 5).map((t) => (
                    <LetterTile key={t.id} label={t.title} tint={t.tint} className="ring-2 ring-[var(--glass-strong)]" />
                  ))}
                </div>
                <button
                  type="button"
                  onClick={() => onRestore(s)}
                  className="flex items-center gap-1.5 rounded-full bg-pink/20 px-3 py-1.5 text-xs font-bold text-pink hover:bg-pink/30"
                >
                  <RotateCcw className="size-3.5" />
                  Restore
                </button>
              </div>
            </li>
          ))}
        </ul>
      )}
    </OverlaySheet>
  )
}

export function SettingsOverlay({
  settings,
  onChange,
  onDirect,
  onClose,
}: {
  settings: Settings
  onChange: (patch: Partial<Settings>) => void
  onDirect: () => void
  onClose: () => void
}) {
  return (
    <OverlaySheet title="Settings" subtitle="Multex Browser" onClose={onClose}>
      <SectionTitle>Denia</SectionTitle>
      <SettingRow title="Show Denia" description="Companion on every page">
        <Switch checked={settings.companionEnabled} onChange={(v) => onChange({ companionEnabled: v })} label="Show Denia" />
      </SettingRow>
      <SettingRow title="Chatty" description="Comment on tabs, sessions, and idle time">
        <Switch checked={settings.chatty} onChange={(v) => onChange({ chatty: v })} label="Chatty" />
      </SettingRow>
      <SettingRow title="Size" stacked>
        <Segmented
          label="Companion size"
          value={settings.companionSize}
          onChange={(v) => onChange({ companionSize: v })}
          options={[
            { value: 'sm', label: 'Small' },
            { value: 'md', label: 'Medium' },
            { value: 'lg', label: 'Large' },
          ]}
        />
      </SettingRow>
      <SettingRow title="Companion pet" description="A little friend who hops beside her" stacked>
        <div className="no-scrollbar -mx-5 flex gap-2 overflow-x-auto px-5 pb-1">
          {PETS.map((p) => (
            <button
              key={p.id}
              type="button"
              onClick={() => onChange({ pet: p.id })}
              aria-pressed={settings.pet === p.id}
              className={cn(
                'flex w-[72px] shrink-0 flex-col items-center gap-1.5 rounded-2xl p-2 transition-colors',
                settings.pet === p.id ? 'bg-pink/20 ring-2 ring-pink' : 'bg-ink/5 hover:bg-ink/10',
              )}
            >
              <span className="grid h-12 w-full place-items-center">
                {p.src ? (
                  <img src={p.src} alt="" className="max-h-12 w-auto" />
                ) : (
                  <span className="text-xs text-ink-muted">–</span>
                )}
              </span>
              <span className="text-[11px] font-bold">{p.label}</span>
            </button>
          ))}
        </div>
      </SettingRow>
      <button
        type="button"
        onClick={onDirect}
        className="mt-1 flex w-full items-center justify-center gap-2 rounded-2xl bg-ink/10 py-3 text-sm font-bold hover:bg-ink/15"
      >
        <Crosshair className="size-4 text-pink" />
        Enter direct mode
      </button>

      <SectionTitle>App</SectionTitle>
      <SettingRow title="Language" description="Denia and the interface follow this" stacked>
        <Segmented
          label="App language"
          value={settings.language}
          onChange={(v) => onChange({ language: v })}
          options={[
            { value: 'en', label: 'English' },
            { value: 'id', label: 'Indonesia' },
          ]}
        />
      </SettingRow>

      <SectionTitle>Appearance</SectionTitle>
      <SettingRow title="Theme" stacked>
        <Segmented
          label="Theme"
          value={settings.theme}
          onChange={(v) => onChange({ theme: v })}
          options={[
            { value: 'midnight', label: 'Midnight' },
            { value: 'sakura', label: 'Sakura' },
          ]}
        />
      </SettingRow>

      <SectionTitle>Search</SectionTitle>
      <SettingRow title="Default engine" stacked>
        <Segmented
          label="Search engine"
          value={settings.searchEngine}
          onChange={(v) => onChange({ searchEngine: v })}
          options={(Object.keys(ENGINES) as (keyof typeof ENGINES)[]).map((k) => ({ value: k, label: ENGINES[k].label }))}
        />
      </SettingRow>

      <SectionTitle>Privacy</SectionTitle>
      <SettingRow title="Block trackers" description="Shield counter in the address bar">
        <Switch checked={settings.blockTrackers} onChange={(v) => onChange({ blockTrackers: v })} label="Block trackers" />
      </SettingRow>
      <SettingRow title="HTTPS only" description="Upgrade insecure requests">
        <Switch checked={settings.httpsOnly} onChange={(v) => onChange({ httpsOnly: v })} label="HTTPS only" />
      </SettingRow>

      <SectionTitle>About</SectionTitle>
      <CreditCard />
    </OverlaySheet>
  )
}

function CreditCard() {
  return (
    <div className="glass relative mt-1 overflow-hidden rounded-2xl p-4">
      <div aria-hidden className="pointer-events-none absolute -right-8 -top-10 size-32 rounded-full bg-pink/25 blur-2xl" />
      <div aria-hidden className="pointer-events-none absolute -bottom-12 -left-6 size-28 rounded-full bg-lav/25 blur-2xl" />
      <div className="relative flex items-center gap-3">
        <span className="grid size-12 shrink-0 place-items-center rounded-2xl bg-gradient-to-br from-pink to-lav text-[#2a1236] shadow-lg shadow-pink/25">
          <Hammer className="size-5" />
        </span>
        <div className="min-w-0 flex-1">
          <p className="text-[11px] font-bold uppercase tracking-[0.18em] text-ink-muted">Self Build</p>
          <p className="font-display text-xl font-semibold leading-tight">Shina</p>
        </div>
        <span className="rounded-full bg-ink/10 px-2.5 py-1 text-[11px] font-bold text-ink-muted">v1.0</span>
      </div>
      <div className="relative mt-4 flex items-center justify-between border-t border-glass-border pt-3 text-xs text-ink-muted">
        <span>Multex Browser × Denia</span>
        <span className="flex items-center gap-1">
          Handcrafted with
          <Heart className="size-3.5 fill-pink text-pink" aria-label="love" />
        </span>
      </div>
    </div>
  )
}

export function MenuOverlay({
  companionEnabled,
  onSessions,
  onSaveSession,
  onSettings,
  onToggleCompanion,
  onDirect,
  onChat,
  onClose,
}: {
  companionEnabled: boolean
  onSessions: () => void
  onSaveSession: () => void
  onSettings: () => void
  onToggleCompanion: () => void
  onDirect: () => void
  onChat: () => void
  onClose: () => void
}) {
  const items = [
    { icon: MessageCircle, label: 'Talk to Denia', hint: 'Ask anything or give a command', onClick: onChat },
    { icon: Layers, label: 'Sessions', hint: 'Saved tab groups', onClick: onSessions },
    { icon: BookmarkPlus, label: 'Save tabs as session', hint: 'Keep this set for later', onClick: onSaveSession },
    { icon: Crosshair, label: 'Direct Denia', hint: 'Tap a spot, she runs there', onClick: onDirect },
    { icon: Sparkles, label: companionEnabled ? 'Hide Denia' : 'Show Denia', hint: 'Toggle the companion', onClick: onToggleCompanion },
    { icon: Settings2, label: 'Settings', hint: 'Theme, search, privacy', onClick: onSettings },
  ]
  return (
    <OverlaySheet title="Menu" onClose={onClose}>
      <ul className="space-y-1.5">
        {items.map(({ icon: Icon, label, hint, onClick }) => (
          <li key={label}>
            <button
              type="button"
              onClick={onClick}
              className="flex w-full items-center gap-3 rounded-2xl px-3 py-3 text-left hover:bg-ink/10"
            >
              <span className="grid size-10 place-items-center rounded-xl bg-lav/20 text-lav">
                <Icon className="size-5" />
              </span>
              <span className="min-w-0">
                <span className="block text-sm font-bold">{label}</span>
                <span className="block text-xs text-ink-muted">{hint}</span>
              </span>
            </button>
          </li>
        ))}
      </ul>
    </OverlaySheet>
  )
}

function SectionTitle({ children }: { children: React.ReactNode }) {
  return (
    <h3 className="mb-1 mt-4 text-[11px] font-bold uppercase tracking-[0.18em] text-ink-muted first:mt-0">{children}</h3>
  )
}
