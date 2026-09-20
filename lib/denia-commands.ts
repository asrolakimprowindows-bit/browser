import type { Lang, Mood, SearchEngine } from './browser-data'
import { ENGINES } from './browser-data'

export const DENIA_ACTIONS = [
  'none',
  'hide_denia',
  'show_denia',
  'new_tab',
  'open_tabs',
  'open_settings',
  'open_sessions',
  'save_session',
  'direct_mode',
  'theme_sakura',
  'theme_midnight',
  'go_home',
  'open_url',
] as const

export type DeniaAction = (typeof DENIA_ACTIONS)[number]

export interface PendingOpen {
  url: string
  label: string
}

export interface DeniaReply {
  reply: string
  mood: Mood
  action: DeniaAction
  /** Set when Denia wants to open a site but is waiting for the user's Yes / No. */
  pending?: PendingOpen
}

export const tx = (lang: Lang, en: string, id: string) => (lang === 'id' ? id : en)

// Matched locally first so common commands work instantly and without an AI call.
const LOCAL_RULES: Array<{ test: RegExp; reply: (lang: Lang) => DeniaReply }> = [
  {
    test: /\b(hide|sembunyi\w*|matiin|matikan|hilang\w*|umpetin|pergi)\b/i,
    reply: (l) => ({ reply: tx(l, 'Okay~ I will hide for now. Call me when you need me!', 'Oke~ Denia sembunyi dulu. Panggil kalau butuh ya!'), mood: 'pout', action: 'hide_denia' }),
  },
  {
    test: /\b(show|muncul\w*|nyalain|nyalakan|tampil\w*|balik|kembali|come back)\b/i,
    reply: (l) => ({ reply: tx(l, 'Tadaa! Denia is back~', 'Tadaa! Denia balik lagi~'), mood: 'happy', action: 'show_denia' }),
  },
  {
    test: /\b(new tab|tab baru|buka tab|bikin tab)\b/i,
    reply: (l) => ({ reply: tx(l, 'One fresh tab, coming right up!', 'Satu tab baru, siap~'), mood: 'happy', action: 'new_tab' }),
  },
  {
    test: /\b(setting\w*|pengaturan|setelan)\b/i,
    reply: (l) => ({ reply: tx(l, 'Opening settings~ Dress me up nicely, okay?', 'Buka pengaturan~ Dandanin aku yang cantik ya?'), mood: 'happy', action: 'open_settings' }),
  },
  {
    test: /\b(save|simpan)\b.*\b(session|sesi|tabs?)\b|\b(session|sesi)\b.*\b(save|simpan)\b/i,
    reply: (l) => ({ reply: tx(l, 'Saving your tabs as a session!', 'Simpan tab-mu jadi sesi!'), mood: 'happy', action: 'save_session' }),
  },
  {
    test: /\b(sessions?|sesi)\b/i,
    reply: (l) => ({ reply: tx(l, 'Here are your saved sessions~', 'Ini sesi yang tersimpan~'), mood: 'neutral', action: 'open_sessions' }),
  },
  {
    test: /\b(tabs|semua tab|list tab|daftar tab)\b/i,
    reply: (l) => ({ reply: tx(l, 'Here are all your tabs!', 'Ini semua tab-mu!'), mood: 'neutral', action: 'open_tabs' }),
  },
  {
    test: /\b(direct|sini|kesini|ke sini|follow|ikut)\b/i,
    reply: (l) => ({ reply: tx(l, 'Tap anywhere and I will run there!', 'Ketuk di mana saja, aku lari ke sana!'), mood: 'happy', action: 'direct_mode' }),
  },
  {
    test: /\bsakura\b|\b(pink|light|terang)\b.*\b(theme|tema|mode)\b|\b(theme|tema|mode)\b.*\b(pink|light|terang)\b/i,
    reply: (l) => ({ reply: tx(l, 'Sakura mode on~ So pretty!', 'Mode sakura nyala~ Cantik banget!'), mood: 'happy', action: 'theme_sakura' }),
  },
  {
    test: /\bmidnight\b|\b(dark|gelap|malam)\b.*\b(theme|tema|mode)\b|\b(theme|tema|mode)\b.*\b(dark|gelap|malam)\b/i,
    reply: (l) => ({ reply: tx(l, 'Midnight mode. Cozy and dark~', 'Mode midnight. Gelap dan nyaman~'), mood: 'neutral', action: 'theme_midnight' }),
  },
  {
    test: /\b(home|beranda|balik ke awal)\b/i,
    reply: (l) => ({ reply: tx(l, 'Taking you home~', 'Antar pulang ke beranda~'), mood: 'happy', action: 'go_home' }),
  },
]

export function matchLocalCommand(text: string, lang: Lang = 'en'): DeniaReply | null {
  const t = text.trim()
  if (t.length > 60 || /\?/.test(t)) return null
  for (const rule of LOCAL_RULES) if (rule.test.test(t)) return rule.reply(lang)
  return null
}

/** Known official sites, keyed by the aliases people actually type. */
const SITES: Array<[aliases: string[], label: string, url: string]> = [
  [['github', 'git hub'], 'GitHub', 'https://github.com'],
  [['youtube', 'yt'], 'YouTube', 'https://www.youtube.com'],
  [['google'], 'Google', 'https://www.google.com'],
  [['gmail'], 'Gmail', 'https://mail.google.com'],
  [['facebook', 'fb'], 'Facebook', 'https://www.facebook.com'],
  [['instagram', 'ig'], 'Instagram', 'https://www.instagram.com'],
  [['twitter', 'x.com'], 'X (Twitter)', 'https://x.com'],
  [['tiktok'], 'TikTok', 'https://www.tiktok.com'],
  [['reddit'], 'Reddit', 'https://www.reddit.com'],
  [['wikipedia', 'wiki'], 'Wikipedia', 'https://www.wikipedia.org'],
  [['pixiv'], 'Pixiv', 'https://www.pixiv.net'],
  [['discord'], 'Discord', 'https://discord.com'],
  [['spotify'], 'Spotify', 'https://open.spotify.com'],
  [['netflix'], 'Netflix', 'https://www.netflix.com'],
  [['amazon'], 'Amazon', 'https://www.amazon.com'],
  [['shopee'], 'Shopee', 'https://shopee.co.id'],
  [['tokopedia', 'tokped'], 'Tokopedia', 'https://www.tokopedia.com'],
  [['lazada'], 'Lazada', 'https://www.lazada.co.id'],
  [['bukalapak'], 'Bukalapak', 'https://www.bukalapak.com'],
  [['whatsapp', 'wa'], 'WhatsApp', 'https://web.whatsapp.com'],
  [['telegram'], 'Telegram', 'https://web.telegram.org'],
  [['twitch'], 'Twitch', 'https://www.twitch.tv'],
  [['steam'], 'Steam', 'https://store.steampowered.com'],
  [['epic games', 'epic'], 'Epic Games', 'https://store.epicgames.com'],
  [['roblox'], 'Roblox', 'https://www.roblox.com'],
  [['minecraft'], 'Minecraft', 'https://www.minecraft.net'],
  [['stack overflow', 'stackoverflow'], 'Stack Overflow', 'https://stackoverflow.com'],
  [['chatgpt', 'openai'], 'ChatGPT', 'https://chatgpt.com'],
  [['gemini'], 'Gemini', 'https://gemini.google.com'],
  [['claude', 'anthropic'], 'Claude', 'https://claude.ai'],
  [['vercel'], 'Vercel', 'https://vercel.com'],
  [['v0'], 'v0', 'https://v0.app'],
  [['nextjs', 'next.js', 'next js'], 'Next.js', 'https://nextjs.org'],
  [['react'], 'React', 'https://react.dev'],
  [['kotlin'], 'Kotlin', 'https://kotlinlang.org'],
  [['android developer', 'android dev', 'android studio'], 'Android Developers', 'https://developer.android.com'],
  [['apple'], 'Apple', 'https://www.apple.com'],
  [['microsoft'], 'Microsoft', 'https://www.microsoft.com'],
  [['linkedin'], 'LinkedIn', 'https://www.linkedin.com'],
  [['pinterest'], 'Pinterest', 'https://www.pinterest.com'],
  [['tumblr'], 'Tumblr', 'https://www.tumblr.com'],
  [['quora'], 'Quora', 'https://www.quora.com'],
  [['medium'], 'Medium', 'https://medium.com'],
  [['notion'], 'Notion', 'https://www.notion.so'],
  [['figma'], 'Figma', 'https://www.figma.com'],
  [['canva'], 'Canva', 'https://www.canva.com'],
  [['bing'], 'Bing', 'https://www.bing.com'],
  [['duckduckgo', 'ddg'], 'DuckDuckGo', 'https://duckduckgo.com'],
  [['brave'], 'Brave', 'https://brave.com'],
  [['firefox', 'mozilla'], 'Firefox', 'https://www.mozilla.org/firefox'],
  [['chrome'], 'Google Chrome', 'https://www.google.com/chrome'],
  [['bilibili'], 'Bilibili', 'https://www.bilibili.com'],
  [['crunchyroll'], 'Crunchyroll', 'https://www.crunchyroll.com'],
  [['myanimelist', 'mal'], 'MyAnimeList', 'https://myanimelist.net'],
  [['anilist'], 'AniList', 'https://anilist.co'],
  [['mangadex'], 'MangaDex', 'https://mangadex.org'],
  [['deviantart'], 'DeviantArt', 'https://www.deviantart.com'],
  [['artstation'], 'ArtStation', 'https://www.artstation.com'],
  [['behance'], 'Behance', 'https://www.behance.net'],
  [['dribbble'], 'Dribbble', 'https://dribbble.com'],
  [['unsplash'], 'Unsplash', 'https://unsplash.com'],
  [['soundcloud'], 'SoundCloud', 'https://soundcloud.com'],
  [['kaskus'], 'Kaskus', 'https://www.kaskus.co.id'],
  [['detik'], 'Detik', 'https://www.detik.com'],
  [['kompas'], 'Kompas', 'https://www.kompas.com'],
  [['tribun'], 'Tribunnews', 'https://www.tribunnews.com'],
  [['cnn'], 'CNN', 'https://edition.cnn.com'],
  [['bbc'], 'BBC', 'https://www.bbc.com'],
  [['gojek'], 'Gojek', 'https://www.gojek.com'],
  [['grab'], 'Grab', 'https://www.grab.com'],
  [['traveloka'], 'Traveloka', 'https://www.traveloka.com'],
  [['tiket.com', 'tiket'], 'tiket.com', 'https://www.tiket.com'],
  [['zoom'], 'Zoom', 'https://zoom.us'],
  [['dropbox'], 'Dropbox', 'https://www.dropbox.com'],
  [['google drive', 'gdrive', 'drive'], 'Google Drive', 'https://drive.google.com'],
  [['google maps', 'maps'], 'Google Maps', 'https://maps.google.com'],
  [['google translate', 'translate'], 'Google Translate', 'https://translate.google.com'],
  [['playstore', 'play store'], 'Google Play', 'https://play.google.com'],
  [['app store', 'appstore'], 'App Store', 'https://apps.apple.com'],
  [['hoyoverse', 'genshin', 'genshin impact'], 'Genshin Impact', 'https://genshin.hoyoverse.com'],
  [['honkai', 'star rail'], 'Honkai: Star Rail', 'https://hsr.hoyoverse.com'],
  [['mobile legends', 'mlbb'], 'Mobile Legends', 'https://m.mobilelegends.com'],
  [['valorant'], 'Valorant', 'https://playvalorant.com'],
]

// Question shapes like "denia website github official yang mana?" / "situs resmi shopee?" / "where is the github site".
const SITE_QUESTION = /\b(website|web|situs|site|link|url|halaman|homepage|official|resmi|buka|open|cari|search)\b/i
const FILLER =
  /\b(denia|website|web|situs|site|link|url|halaman|homepage|official|resmi|officialnya|resminya|yang|mana|dimana|di mana|itu|apa|apaan|dong|sih|nya|tolong|please|coba|the|is|what|which|where|of|for|buka|open|cari|search|carikan|find|me|kan|ya|deh|aja)\b/gi

const hostOf = (url: string) => url.replace(/^https?:\/\//, '').replace(/^www\./, '')

export function lookupSite(text: string, lang: Lang, engine: SearchEngine): DeniaReply | null {
  if (!SITE_QUESTION.test(text)) return null
  const name = text
    .replace(/[?!.,:;"']/g, ' ')
    .replace(FILLER, ' ')
    .replace(/\s+/g, ' ')
    .trim()
  if (!name) return null
  const needle = name.toLowerCase()

  const hit = SITES.find(([aliases]) => aliases.some((a) => needle === a || needle.includes(a)))
  if (hit) {
    const [, label, url] = hit
    return {
      reply: tx(
        lang,
        `Found it! The official ${label} site is ${hostOf(url)}. Open it in a new tab?`,
        `Ketemu! Situs resmi ${label} itu ${hostOf(url)}. Buka di tab baru?`,
      ),
      mood: 'happy',
      action: 'open_url',
      pending: { url, label },
    }
  }

  const searchUrl = `https://${ENGINES[engine].host}/search?q=${encodeURIComponent(`${name} official site`)}`
  return {
    reply: tx(
      lang,
      `Hmm, ${name} is not in my notes. Want me to search for the official site in a new tab?`,
      `Hmm, ${name} nggak ada di catatanku. Mau aku cari situs resminya di tab baru?`,
    ),
    mood: 'neutral',
    action: 'open_url',
    pending: { url: searchUrl, label: name },
  }
}

export const deniaSuggestions = (lang: Lang) =>
  lang === 'id'
    ? ['Sembunyikan Denia', 'Tab baru', 'Tema sakura', 'Website GitHub yang mana?']
    : ['Hide Denia', 'New tab', 'Sakura theme', 'Which site is GitHub?']
