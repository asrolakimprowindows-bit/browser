export type TabKind = 'home' | 'page'
export type Overlay = 'none' | 'tabs' | 'sessions' | 'settings' | 'menu'
export type ThemeId = 'midnight' | 'sakura'
export type SearchEngine = 'google' | 'duckduckgo' | 'brave'
export type Mood = 'happy' | 'neutral' | 'pout'
export type Lang = 'en' | 'id'
export type CompanionSize = 'sm' | 'md' | 'lg'
export type PetId = 'none' | 'bunny' | 'rabbit' | 'cat' | 'fox' | 'bear' | 'panda' | 'frog'

export interface Tab {
  id: string
  kind: TabKind
  title: string
  host: string
  tint: string
}

export interface Session {
  id: string
  name: string
  savedAt: string
  tabs: Tab[]
}

export interface Shortcut {
  title: string
  host: string
  tint: string
}

export interface Cue {
  id: number
  text: string
  mood: Mood
  force?: boolean
}

export interface Settings {
  companionEnabled: boolean
  companionSize: CompanionSize
  pet: PetId
  chatty: boolean
  theme: ThemeId
  searchEngine: SearchEngine
  language: Lang
  blockTrackers: boolean
  httpsOnly: boolean
}

export const DEFAULT_SETTINGS: Settings = {
  companionEnabled: true,
  companionSize: 'md',
  pet: 'none',
  chatty: true,
  theme: 'midnight',
  searchEngine: 'google',
  language: 'en',
  blockTrackers: true,
  httpsOnly: true,
}

export const ENGINES: Record<SearchEngine, { label: string; host: string; short: string }> = {
  google: { label: 'Google', host: 'google.com', short: 'G' },
  duckduckgo: { label: 'DuckDuckGo', host: 'duckduckgo.com', short: 'DDG' },
  brave: { label: 'Brave Search', host: 'search.brave.com', short: 'B' },
}

export const PETS: { id: PetId; label: string; src: string | null }[] = [
  { id: 'none', label: 'None', src: null },
  { id: 'bunny', label: 'Bunny', src: '/denia/bunny.png' },
  { id: 'rabbit', label: 'Rabbit', src: '/denia/animal_rabbit.png' },
  { id: 'cat', label: 'Cat', src: '/denia/animal_cat.png' },
  { id: 'fox', label: 'Fox', src: '/denia/animal_fox.png' },
  { id: 'bear', label: 'Bear', src: '/denia/animal_bear.png' },
  { id: 'panda', label: 'Panda', src: '/denia/animal_panda.png' },
  { id: 'frog', label: 'Frog', src: '/denia/animal_frog.png' },
]

export const SHORTCUTS: Shortcut[] = [
  { title: 'YouTube', host: 'youtube.com', tint: '#ff6b8a' },
  { title: 'Pixiv', host: 'pixiv.net', tint: '#5fa8ff' },
  { title: 'GitHub', host: 'github.com', tint: '#c9c4ff' },
  { title: 'Reddit', host: 'reddit.com', tint: '#ff9a6b' },
  { title: 'X', host: 'x.com', tint: '#9fd8ff' },
  { title: 'Wikipedia', host: 'wikipedia.org', tint: '#ece9ff' },
  { title: 'Spotify', host: 'spotify.com', tint: '#6cf5a8' },
  { title: 'Discord', host: 'discord.com', tint: '#a3aeff' },
]

const SITE_INFO: Record<string, { title: string; tint: string }> = Object.fromEntries(
  SHORTCUTS.map((s) => [s.host, { title: s.title, tint: s.tint }]),
)

const TINTS = ['#f79ac8', '#b9a9ff', '#86a6ff', '#7fe0c9', '#ffc178']

let seq = 0
export const uid = () => `t-${Date.now().toString(36)}-${(seq++).toString(36)}`

export function normalizeHost(input: string) {
  return input
    .trim()
    .replace(/^https?:\/\//i, '')
    .replace(/^www\./i, '')
    .split(/[/?#]/)[0]
    .toLowerCase()
}

export function makeTab(input: string, title?: string): Tab {
  const host = normalizeHost(input)
  const info = SITE_INFO[host]
  const fallbackTitle = host.split('.')[0].replace(/^\w/, (c) => c.toUpperCase())
  const hash = [...host].reduce((acc, ch) => acc + ch.charCodeAt(0), 0)
  return {
    id: uid(),
    kind: 'page',
    title: title ?? info?.title ?? fallbackTitle,
    host,
    tint: info?.tint ?? TINTS[hash % TINTS.length],
  }
}

export function makeHomeTab(): Tab {
  return { id: uid(), kind: 'home', title: 'New tab', host: '', tint: '#b9a9ff' }
}

export const INITIAL_TABS: Tab[] = [
  { id: 'home-1', kind: 'home', title: 'New tab', host: '', tint: '#b9a9ff' },
  { id: 'page-1', kind: 'page', title: 'Pixiv', host: 'pixiv.net', tint: '#5fa8ff' },
  { id: 'page-2', kind: 'page', title: 'GitHub', host: 'github.com', tint: '#c9c4ff' },
]

export const INITIAL_SESSIONS: Session[] = [
  {
    id: 'session-1',
    name: 'Art references',
    savedAt: 'Yesterday',
    tabs: [
      { id: 's1-a', kind: 'page', title: 'Pixiv', host: 'pixiv.net', tint: '#5fa8ff' },
      { id: 's1-b', kind: 'page', title: 'Wikipedia', host: 'wikipedia.org', tint: '#ece9ff' },
      { id: 's1-c', kind: 'page', title: 'X', host: 'x.com', tint: '#9fd8ff' },
    ],
  },
  {
    id: 'session-2',
    name: 'Study night',
    savedAt: 'Mon',
    tabs: [
      { id: 's2-a', kind: 'page', title: 'GitHub', host: 'github.com', tint: '#c9c4ff' },
      { id: 's2-b', kind: 'page', title: 'YouTube', host: 'youtube.com', tint: '#ff6b8a' },
    ],
  },
]

export const PAGE_COPY = [
  'This is a stand-in page so you can feel how the glass toolbar, the tab switcher, and Denia behave around real content. Scroll and she turns around to read along with you.',
  'Multex keeps the chrome light: one address pill on top, one floating dock at the bottom, and everything else stays out of the way until you ask for it.',
  'Sessions let you park a whole set of tabs under a name and bring them back later, so late-night research does not have to live in forty open tabs.',
  'Trackers on this page are blocked automatically. The shield counter in the address pill shows how many were stopped.',
  'Denia is fully directable: double-tap her to enter direct mode, then tap anywhere on the screen and she will run there. Drag her whenever she is in the way.',
]
