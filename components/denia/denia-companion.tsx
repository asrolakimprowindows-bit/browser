'use client'

import {
  useCallback,
  useEffect,
  useRef,
  useState,
  type PointerEvent as ReactPointerEvent,
  type RefObject,
  type TransitionEvent,
} from 'react'
import { Crosshair, X } from 'lucide-react'
import { cn } from '@/lib/utils'
import type { CompanionSize, Cue, Mood, PetId } from '@/lib/browser-data'

type Pose = 'front' | 'side' | 'back' | 'sit'
type Facing = 'left' | 'right'
interface Line {
  text: string
  mood: Mood
}

const HEIGHTS: Record<CompanionSize, number> = { sm: 104, md: 136, lg: 172 }
const POSE_SRC: Record<Pose, string> = {
  front: '/denia/denia_front.png',
  side: '/denia/denia_side.png',
  back: '/denia/denia_back.png',
  sit: '/denia/denia_sit.png',
}
const FACE_SRC: Record<Mood, string> = {
  happy: '/denia/face_happy.png',
  neutral: '/denia/face_neutral.png',
  pout: '/denia/face_pout.png',
}
const PET_SRC: Record<Exclude<PetId, 'none'>, string> = {
  bunny: '/denia/bunny.png',
  rabbit: '/denia/animal_rabbit.png',
  cat: '/denia/animal_cat.png',
  fox: '/denia/animal_fox.png',
  bear: '/denia/animal_bear.png',
  panda: '/denia/animal_panda.png',
  frog: '/denia/animal_frog.png',
}

const TAP_LINES: Line[] = [
  { text: 'Hehe, need something?', mood: 'happy' },
  { text: 'Denia, reporting for duty~', mood: 'happy' },
  { text: 'Hmph! Stop poking me.', mood: 'pout' },
  { text: 'Want me to open a new tab?', mood: 'neutral' },
  { text: 'You have been scrolling a while. Water break?', mood: 'neutral' },
  { text: 'Double-tap me and I will go wherever you point!', mood: 'happy' },
]
const ARRIVE_LINES: Line[] = [
  { text: 'Here I am!', mood: 'happy' },
  { text: 'Made it~', mood: 'happy' },
  { text: 'Is this the spot?', mood: 'neutral' },
  { text: 'Phew, that was far.', mood: 'pout' },
]
const DROP_LINES: Line[] = [
  { text: 'Wheee!', mood: 'happy' },
  { text: 'Careful, I am fragile!', mood: 'pout' },
  { text: 'New spot, new view~', mood: 'happy' },
]

const pick = <T,>(arr: T[]) => arr[Math.floor(Math.random() * arr.length)]
const TOP_RESERVE = 28
const BOTTOM_RESERVE = 64
const IDLE_MS = 18000

interface DeniaCompanionProps {
  size: CompanionSize
  pet: PetId
  chatty: boolean
  cue: Cue | null
  scrollTick: number
  boundsRef: RefObject<HTMLDivElement | null>
  directMode: boolean
  onDirectModeChange: (value: boolean) => void
  onTap?: () => void
}

export function DeniaCompanion({
  size,
  pet,
  chatty,
  cue,
  scrollTick,
  boundsRef,
  directMode,
  onDirectModeChange,
  onTap,
}: DeniaCompanionProps) {
  const h = HEIGHTS[size]
  const w = Math.round(h * 0.78)

  const [pos, setPos] = useState<{ x: number; y: number } | null>(null)
  const [pose, setPose] = useState<Pose>('front')
  const [facing, setFacing] = useState<Facing>('left')
  const [walking, setWalking] = useState(false)
  const [walkMs, setWalkMs] = useState(600)
  const [dragging, setDragging] = useState(false)
  const [bubble, setBubble] = useState<Line | null>(null)
  const [marker, setMarker] = useState<{ x: number; y: number; id: number } | null>(null)

  const timers = useRef<{ bubble?: number; idle?: number; scroll?: number }>({})
  const drag = useRef({ startX: 0, startY: 0, originX: 0, originY: 0, moved: false, lastTap: 0 })
  const lastCue = useRef(0)

  const clamp = useCallback(
    (x: number, y: number) => {
      const b = boundsRef.current
      if (!b) return { x, y }
      return {
        x: Math.min(Math.max(0, x), b.clientWidth - w),
        y: Math.min(Math.max(TOP_RESERVE, y), b.clientHeight - h - BOTTOM_RESERVE),
      }
    },
    [boundsRef, w, h],
  )

  const say = useCallback((line: Line, ms = 3200) => {
    setBubble(line)
    window.clearTimeout(timers.current.bubble)
    timers.current.bubble = window.setTimeout(() => setBubble(null), ms)
  }, [])

  const poke = useCallback(() => {
    window.clearTimeout(timers.current.idle)
    setPose((p) => (p === 'sit' ? 'front' : p))
    timers.current.idle = window.setTimeout(() => {
      setPose('sit')
      if (chatty) say({ text: 'Zzz... just resting my eyes.', mood: 'neutral' }, 2600)
    }, IDLE_MS)
  }, [chatty, say])

  useEffect(() => {
    const b = boundsRef.current
    if (!b) return
    setPos((p) => (p ? clamp(p.x, p.y) : clamp(b.clientWidth - w - 10, b.clientHeight - h - 104)))
  }, [boundsRef, clamp, w, h])

  useEffect(() => {
    poke()
    const t = timers.current
    return () => {
      window.clearTimeout(t.idle)
      window.clearTimeout(t.bubble)
      window.clearTimeout(t.scroll)
    }
  }, [poke])

  useEffect(() => {
    if (scrollTick === 0) return
    setPose((p) => (p === 'side' ? p : 'back'))
    window.clearTimeout(timers.current.scroll)
    timers.current.scroll = window.setTimeout(() => setPose((p) => (p === 'back' ? 'front' : p)), 1200)
    poke()
  }, [scrollTick, poke])

  useEffect(() => {
    if (!cue || cue.id === lastCue.current) return
    lastCue.current = cue.id
    if (!chatty && !cue.force) return
    say({ text: cue.text, mood: cue.mood })
  }, [cue, chatty, say])

  const walkTo = (tx: number, ty: number) => {
    if (!pos) return
    const target = clamp(tx, ty)
    const dist = Math.hypot(target.x - pos.x, target.y - pos.y)
    if (dist < 4) return
    setFacing(target.x > pos.x ? 'right' : 'left')
    setWalkMs(Math.min(2600, Math.max(450, dist * 6)))
    setPose('side')
    setWalking(true)
    setPos(target)
  }

  const onWalkEnd = (e: TransitionEvent<HTMLDivElement>) => {
    if (e.target !== e.currentTarget || e.propertyName !== 'transform' || !walking) return
    setWalking(false)
    setPose('front')
    say(pick(ARRIVE_LINES))
    poke()
  }

  const onPointerDown = (e: ReactPointerEvent<HTMLDivElement>) => {
    if (!pos) return
    e.preventDefault()
    e.currentTarget.setPointerCapture(e.pointerId)
    drag.current = { ...drag.current, startX: e.clientX, startY: e.clientY, originX: pos.x, originY: pos.y, moved: false }
    setWalking(false)
  }

  const onPointerMove = (e: ReactPointerEvent<HTMLDivElement>) => {
    if (!e.currentTarget.hasPointerCapture(e.pointerId)) return
    const dx = e.clientX - drag.current.startX
    const dy = e.clientY - drag.current.startY
    if (!drag.current.moved && Math.hypot(dx, dy) < 6) return
    drag.current.moved = true
    setDragging(true)
    setPose('front')
    setPos(clamp(drag.current.originX + dx, drag.current.originY + dy))
  }

  const onPointerUp = (e: ReactPointerEvent<HTMLDivElement>) => {
    if (e.currentTarget.hasPointerCapture(e.pointerId)) e.currentTarget.releasePointerCapture(e.pointerId)
    if (drag.current.moved) {
      setDragging(false)
      if (chatty) say(pick(DROP_LINES), 2200)
    } else {
      const now = Date.now()
      if (now - drag.current.lastTap < 320) {
        drag.current.lastTap = 0
        const next = !directMode
        onDirectModeChange(next)
        say(next ? { text: 'Tap anywhere and I will run there!', mood: 'happy' } : { text: 'Okay, staying put~', mood: 'neutral' })
      } else {
        drag.current.lastTap = now
        say(pick(TAP_LINES))
        onTap?.()
      }
    }
    poke()
  }

  const onPointerCancel = () => setDragging(false)

  const onDirectTap = (e: ReactPointerEvent<HTMLDivElement>) => {
    const b = boundsRef.current?.getBoundingClientRect()
    if (!b) return
    const x = e.clientX - b.left
    const y = e.clientY - b.top
    setMarker({ x, y, id: Date.now() })
    walkTo(x - w / 2, y - h)
    poke()
  }

  if (!pos) return null

  const boundsW = boundsRef.current?.clientWidth ?? 390
  const align = pos.x < 70 ? 'left' : pos.x + w > boundsW - 70 ? 'right' : 'center'
  const below = pos.y < 90
  const flip = pose === 'side' && facing === 'right'

  return (
    <>
      {directMode && (
        <>
          <div aria-hidden className="absolute inset-0 z-[35] cursor-crosshair" onPointerDown={onDirectTap} />
          <div className="pointer-events-none absolute inset-x-0 top-11 z-[36] flex justify-center">
            <div className="glass-strong pointer-events-auto flex items-center gap-2 rounded-full py-1.5 pl-3 pr-1.5 text-xs font-bold text-ink shadow-lg">
              <Crosshair className="size-3.5 text-pink" />
              Direct mode · tap anywhere
              <button
                type="button"
                onClick={() => onDirectModeChange(false)}
                aria-label="Exit direct mode"
                className="grid size-6 place-items-center rounded-full bg-ink/10 hover:bg-ink/20"
              >
                <X className="size-3" />
              </button>
            </div>
          </div>
          {marker && (
            <span
              key={marker.id}
              aria-hidden
              onAnimationEnd={() => setMarker(null)}
              className="ripple pointer-events-none absolute z-[35] -ml-5 -mt-5 size-10 rounded-full border-2 border-pink"
              style={{ left: marker.x, top: marker.y }}
            />
          )}
        </>
      )}

      <div
        role="button"
        tabIndex={0}
        aria-label="Denia, your companion. Tap to talk, drag to move, double-tap for direct mode."
        onPointerDown={onPointerDown}
        onPointerMove={onPointerMove}
        onPointerUp={onPointerUp}
        onPointerCancel={onPointerCancel}
        onContextMenu={(e) => e.preventDefault()}
        onTransitionEnd={onWalkEnd}
        className={cn('absolute left-0 top-0 z-[60] select-none outline-none', dragging ? 'cursor-grabbing' : 'cursor-grab')}
        style={{
          width: w,
          height: h,
          touchAction: 'none',
          transform: `translate3d(${pos.x}px, ${pos.y}px, 0)`,
          transition: walking ? `transform ${walkMs}ms cubic-bezier(0.45, 0.05, 0.55, 0.95)` : 'none',
        }}
      >
        {bubble && (
          <div
            className={cn(
              'pointer-events-none absolute z-10 w-max max-w-[190px]',
              below ? 'top-full mt-2' : 'bottom-full mb-2',
              align === 'left' ? 'left-0' : align === 'right' ? 'right-0' : 'left-1/2 -translate-x-1/2',
            )}
          >
            <div className="bubble-in glass-strong relative flex items-center gap-2 rounded-2xl px-3 py-2 text-[13px] font-semibold leading-snug text-ink shadow-lg">
              <img
                src={FACE_SRC[bubble.mood]}
                alt=""
                className="size-7 shrink-0 rounded-full object-cover ring-2 ring-pink/60"
              />
              <span>{bubble.text}</span>
              <span
                aria-hidden
                className={cn(
                  'absolute size-3 rotate-45',
                  below ? '-top-1.5' : '-bottom-1.5',
                  align === 'left' ? 'left-6' : align === 'right' ? 'right-6' : 'left-1/2 -ml-1.5',
                )}
                style={{ background: 'var(--glass-strong)' }}
              />
            </div>
          </div>
        )}

        <div
          className={cn('relative h-full w-full', walking && 'denia-walk', !walking && !dragging && pose !== 'sit' && 'denia-float')}
          style={{ transform: dragging ? 'scale(1.06) rotate(-4deg)' : undefined, transition: 'transform 180ms ease' }}
        >
          {pet !== 'none' && (
            <img
              src={PET_SRC[pet]}
              alt=""
              draggable={false}
              className="denia-hop pointer-events-none absolute bottom-0 select-none [filter:drop-shadow(0_6px_10px_rgba(0,0,0,0.3))]"
              style={{ height: h * 0.38, left: -h * 0.2 }}
            />
          )}
          <img
            src={POSE_SRC[pose]}
            alt="Denia, a pink-haired chibi girl holding a bunny plush"
            draggable={false}
            className="pointer-events-none absolute bottom-0 left-1/2 h-full w-auto max-w-none select-none [filter:drop-shadow(0_10px_16px_rgba(0,0,0,0.35))]"
            style={{ transform: `translateX(-50%)${flip ? ' scaleX(-1)' : ''}` }}
          />
        </div>
      </div>
    </>
  )
}
