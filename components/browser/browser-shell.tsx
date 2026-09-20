'use client'

import { useCallback, useRef, useState } from 'react'
import { cn } from '@/lib/utils'
import {
  DEFAULT_SETTINGS,
  INITIAL_SESSIONS,
  INITIAL_TABS,
  makeHomeTab,
  makeTab,
  uid,
  type Cue,
  type Mood,
  type Overlay,
  type Session,
  type Settings,
  type Tab,
} from '@/lib/browser-data'
import { DeniaCompanion } from '@/components/denia/denia-companion'
import { DeniaChat } from '@/components/denia/denia-chat'
import { tx, type DeniaReply, type PendingOpen } from '@/lib/denia-commands'
import { HomeScreen } from './home-screen'
import { AddressBar, BottomBar, StatusBar, WebPage } from './chrome'
import { MenuOverlay, SessionsOverlay, SettingsOverlay, TabsOverlay } from './overlays'

export function BrowserShell() {
  const boundsRef = useRef<HTMLDivElement>(null)
  const cueSeq = useRef(0)

  const [tabs, setTabs] = useState<Tab[]>(INITIAL_TABS)
  const [activeId, setActiveId] = useState(INITIAL_TABS[0].id)
  const [sessions, setSessions] = useState<Session[]>(INITIAL_SESSIONS)
  const [settings, setSettings] = useState<Settings>(DEFAULT_SETTINGS)
  const [overlay, setOverlay] = useState<Overlay>('none')
  const [directMode, setDirectMode] = useState(false)
  const [cue, setCue] = useState<Cue | null>(null)
  const [scrollTick, setScrollTick] = useState(0)
  const [chatOpen, setChatOpen] = useState(false)

  const active = tabs.find((t) => t.id === activeId) ?? tabs[0]

  const nudge = useCallback((text: string, mood: Mood = 'happy', force = false) => {
    cueSeq.current += 1
    setCue({ id: cueSeq.current, text, mood, force })
  }, [])

  const updateSettings = (patch: Partial<Settings>) => setSettings((s) => ({ ...s, ...patch }))

  const navigate = (input: string, title?: string) => {
    const next = makeTab(input, title)
    setTabs((ts) => ts.map((t) => (t.id === active.id ? { ...next, id: t.id } : t)))
    setOverlay('none')
    nudge(`Opening ${next.host}~`)
  }

  const goHome = () => {
    setTabs((ts) => ts.map((t) => (t.id === active.id ? { ...makeHomeTab(), id: t.id } : t)))
  }

  const newTab = () => {
    const t = makeHomeTab()
    setTabs((ts) => [...ts, t])
    setActiveId(t.id)
    setOverlay('none')
    if (tabs.length + 1 >= 6) nudge('So many tabs! Want me to save them as a session?', 'pout', true)
  }

  const closeTab = (id: string) => {
    const next = tabs.filter((t) => t.id !== id)
    if (next.length === 0) {
      const home = makeHomeTab()
      setTabs([home])
      setActiveId(home.id)
      return
    }
    setTabs(next)
    if (id === activeId) setActiveId(next[next.length - 1].id)
  }

  const saveSession = () => {
    const pages = tabs.filter((t) => t.kind === 'page')
    if (pages.length === 0) {
      nudge('There is nothing to save yet~', 'pout', true)
      return
    }
    const session: Session = {
      id: uid(),
      name: `Session ${sessions.length + 1}`,
      savedAt: 'Just now',
      tabs: pages.map((t) => ({ ...t })),
    }
    setSessions((ss) => [session, ...ss])
    setOverlay('sessions')
    nudge(`Saved ${pages.length} tabs as ${session.name}!`, 'happy', true)
  }

  const restoreSession = (s: Session) => {
    const restored = s.tabs.map((t) => ({ ...t, id: uid() }))
    setTabs(restored)
    setActiveId(restored[0].id)
    setOverlay('none')
    nudge(`Restored ${s.name}~`, 'happy', true)
  }

  const openOverlay = (o: Overlay) => {
    setOverlay(o)
    if (o === 'settings') nudge('Ooh, dressing me up?')
  }

  const startDirect = () => {
    setOverlay('none')
    if (!settings.companionEnabled) updateSettings({ companionEnabled: true })
    setDirectMode(true)
    nudge('Tap anywhere and I will run there!', 'happy', true)
  }

  const openChat = () => {
    setOverlay('none')
    setChatOpen(true)
  }

  const lang = settings.language

  const openInNewTab = ({ url, label }: PendingOpen) => {
    const t = makeTab(url, label)
    setTabs((ts) => [...ts, t])
    setActiveId(t.id)
    setOverlay('none')
    setChatOpen(false)
    nudge(tx(lang, `Opening ${label} in a new tab~`, `Buka ${label} di tab baru~`), 'happy', true)
  }

  const chatContext = `${tabs.length} tabs open, active tab: ${active.kind === 'home' ? 'home screen' : active.host}, theme: ${settings.theme}, Denia visible: ${settings.companionEnabled}, ${sessions.length} saved sessions`

  const handleDeniaReply = ({ reply, mood, action }: DeniaReply) => {
    switch (action) {
      case 'hide_denia':
        nudge(reply, mood, true)
        window.setTimeout(() => {
          updateSettings({ companionEnabled: false })
          setChatOpen(false)
        }, 1400)
        return
      case 'show_denia':
        updateSettings({ companionEnabled: true })
        break
      case 'new_tab':
        newTab()
        break
      case 'open_tabs':
        setOverlay('tabs')
        setChatOpen(false)
        break
      case 'open_settings':
        setOverlay('settings')
        setChatOpen(false)
        break
      case 'open_sessions':
        setOverlay('sessions')
        setChatOpen(false)
        break
      case 'save_session':
        saveSession()
        setChatOpen(false)
        return
      case 'direct_mode':
        startDirect()
        setChatOpen(false)
        return
      case 'theme_sakura':
        updateSettings({ theme: 'sakura' })
        break
      case 'theme_midnight':
        updateSettings({ theme: 'midnight' })
        break
      case 'go_home':
        goHome()
        break
      case 'open_url':
      case 'none':
        break
    }
    if (!settings.companionEnabled && action !== 'show_denia') updateSettings({ companionEnabled: true })
    nudge(reply, mood, true)
  }

  return (
    <div
      ref={boundsRef}
      className={cn(
        'screen relative flex h-full w-full flex-col overflow-hidden font-sans text-ink',
        settings.theme === 'sakura' && 'theme-sakura',
      )}
    >
      <StatusBar />
      {active.kind === 'page' && <AddressBar tab={active} blockTrackers={settings.blockTrackers} onHome={goHome} />}

      <div className="no-scrollbar flex-1 overflow-y-auto" onScroll={() => setScrollTick((n) => n + 1)}>
        {active.kind === 'home' ? (
          <HomeScreen
            tabs={tabs}
            engine={settings.searchEngine}
            onNavigate={navigate}
            onSwitchTab={setActiveId}
            onOpenSettings={() => openOverlay('settings')}
            onDirect={startDirect}
          />
        ) : (
          <WebPage tab={active} />
        )}
      </div>

      <BottomBar
        canBack={active.kind === 'page'}
        onBack={goHome}
        tabCount={tabs.length}
        onNewTab={newTab}
        onTabs={() => openOverlay('tabs')}
        onMenu={() => openOverlay('menu')}
      />

      {overlay === 'tabs' && (
        <TabsOverlay
          tabs={tabs}
          activeId={activeId}
          onSelect={(id) => {
            setActiveId(id)
            setOverlay('none')
          }}
          onCloseTab={closeTab}
          onNewTab={newTab}
          onSaveSession={saveSession}
          onClose={() => setOverlay('none')}
        />
      )}
      {overlay === 'sessions' && (
        <SessionsOverlay
          sessions={sessions}
          onRestore={restoreSession}
          onDelete={(id) => setSessions((ss) => ss.filter((s) => s.id !== id))}
          onSave={saveSession}
          onClose={() => setOverlay('none')}
        />
      )}
      {overlay === 'settings' && (
        <SettingsOverlay
          settings={settings}
          onChange={updateSettings}
          onDirect={startDirect}
          onClose={() => setOverlay('none')}
        />
      )}
      {overlay === 'menu' && (
        <MenuOverlay
          companionEnabled={settings.companionEnabled}
          onSessions={() => openOverlay('sessions')}
          onSaveSession={saveSession}
          onSettings={() => openOverlay('settings')}
          onToggleCompanion={() => {
            updateSettings({ companionEnabled: !settings.companionEnabled })
            setOverlay('none')
          }}
          onDirect={startDirect}
          onChat={openChat}
          onClose={() => setOverlay('none')}
        />
      )}

      {chatOpen && (
        <DeniaChat
          context={chatContext}
          lang={lang}
          engine={settings.searchEngine}
          onReply={handleDeniaReply}
          onOpenUrl={openInNewTab}
          onClose={() => setChatOpen(false)}
        />
      )}

      {settings.companionEnabled && (
        <DeniaCompanion
          size={settings.companionSize}
          pet={settings.pet}
          chatty={settings.chatty}
          cue={cue}
          scrollTick={scrollTick}
          boundsRef={boundsRef}
          directMode={directMode}
          onDirectModeChange={setDirectMode}
          onTap={openChat}
        />
      )}
    </div>
  )
}
