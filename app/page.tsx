import { Crosshair, MousePointerClick, Move } from 'lucide-react'
import { BrowserShell } from '@/components/browser/browser-shell'

const HINTS = [
  { icon: MousePointerClick, title: 'Tap', text: 'Denia answers with a speech bubble and an expression.' },
  { icon: Move, title: 'Drag', text: 'Pick her up and drop her anywhere on the screen.' },
  { icon: Crosshair, title: 'Direct', text: 'Double-tap her, then tap any spot and she walks there.' },
]

export default function Page() {
  return (
    <main className="min-h-dvh bg-background text-foreground md:flex md:items-center md:justify-center md:gap-14 md:p-10">
      <aside className="hidden max-w-sm md:block">
        <p className="text-xs font-bold uppercase tracking-[0.2em] text-pink">Interactive preview</p>
        <h1 className="mt-3 font-display text-4xl font-semibold leading-tight text-balance">
          Multex Browser <span className="text-ink-muted">with</span> Denia
        </h1>
        <p className="mt-4 text-ink-muted">
          Glass UI redesign plus a fully interactive chibi companion. Everything in the phone works: tabs, sessions,
          settings, themes, and Denia herself.
        </p>
        <ul className="mt-8 space-y-4">
          {HINTS.map(({ icon: Icon, title, text }) => (
            <li key={title} className="flex gap-3">
              <span className="grid size-9 shrink-0 place-items-center rounded-xl bg-lav/15 text-lav">
                <Icon className="size-4" />
              </span>
              <div>
                <p className="text-sm font-bold">{title}</p>
                <p className="text-sm text-ink-muted">{text}</p>
              </div>
            </li>
          ))}
        </ul>
      </aside>

      <div className="relative h-dvh w-full md:h-[min(844px,calc(100dvh-5rem))] md:w-[390px] md:shrink-0 md:overflow-hidden md:rounded-[3rem] md:border-[10px] md:border-[#1a1b31] md:shadow-[0_40px_120px_rgba(0,0,0,0.6)]">
        <BrowserShell />
      </div>
    </main>
  )
}
