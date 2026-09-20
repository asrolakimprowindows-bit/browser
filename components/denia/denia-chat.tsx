'use client'

import { useEffect, useRef, useState, type FormEvent, type KeyboardEvent } from 'react'
import { Loader2, SendHorizontal, X } from 'lucide-react'
import { cn } from '@/lib/utils'
import type { Lang, SearchEngine } from '@/lib/browser-data'
import { deniaSuggestions, lookupSite, matchLocalCommand, tx, type DeniaReply, type PendingOpen } from '@/lib/denia-commands'

interface DeniaChatProps {
  context: string
  lang: Lang
  engine: SearchEngine
  onReply: (reply: DeniaReply) => void
  onOpenUrl: (target: PendingOpen) => void
  onClose: () => void
}

export function DeniaChat({ context, lang, engine, onReply, onOpenUrl, onClose }: DeniaChatProps) {
  const [value, setValue] = useState('')
  const [busy, setBusy] = useState(false)
  const [last, setLast] = useState<{ you: string; denia: string } | null>(null)
  const [pending, setPending] = useState<PendingOpen | null>(null)
  const inputRef = useRef<HTMLInputElement>(null)

  useEffect(() => {
    inputRef.current?.focus()
  }, [])

  const show = (you: string, reply: DeniaReply) => {
    setLast({ you, denia: reply.reply })
    setPending(reply.pending ?? null)
    // A pending open waits for Yes / No; everything else fires immediately.
    if (!reply.pending) onReply(reply)
  }

  const send = async (text: string) => {
    const message = text.trim()
    if (!message || busy) return
    setValue('')

    const site = lookupSite(message, lang, engine)
    if (site) return show(message, site)

    const local = matchLocalCommand(message, lang)
    if (local) return show(message, local)

    setBusy(true)
    try {
      const res = await fetch('/api/denia', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ message, context, lang }),
      })
      show(message, (await res.json()) as DeniaReply)
    } catch {
      show(message, {
        reply: tx(lang, 'Hmm, I could not reach my brain. Check the connection?', 'Hmm, otakku nggak kejangkau. Cek koneksinya?'),
        mood: 'pout',
        action: 'none',
      })
    } finally {
      setBusy(false)
    }
  }

  const answer = (yes: boolean) => {
    if (!pending) return
    const target = pending
    setPending(null)
    if (yes) {
      onOpenUrl(target)
      return
    }
    const reply: DeniaReply = { reply: tx(lang, 'Okay, I will leave it~', 'Oke, nggak jadi ya~'), mood: 'neutral', action: 'none' }
    setLast((l) => (l ? { ...l, denia: reply.reply } : l))
    onReply(reply)
  }

  const onSubmit = (e: FormEvent) => {
    e.preventDefault()
    void send(value)
  }

  const onKeyDown = (e: KeyboardEvent<HTMLInputElement>) => {
    if (e.key === 'Enter' && !e.nativeEvent.isComposing && e.keyCode !== 229) {
      e.preventDefault()
      void send(value)
    }
  }

  return (
    <div className="absolute inset-x-0 bottom-16 z-[65] px-3">
      <div className="glass-strong bubble-in rounded-3xl p-3 shadow-2xl">
        <div className="mb-2 flex items-center justify-between">
          <div className="flex items-center gap-2">
            <img src="/denia/face_happy.png" alt="" className="size-7 rounded-full object-cover ring-2 ring-pink/60" />
            <p className="text-sm font-bold">{tx(lang, 'Talk to Denia', 'Ngobrol sama Denia')}</p>
          </div>
          <button
            type="button"
            onClick={onClose}
            aria-label={tx(lang, 'Close chat', 'Tutup obrolan')}
            className="grid size-7 place-items-center rounded-full bg-ink/10 hover:bg-ink/20"
          >
            <X className="size-3.5" />
          </button>
        </div>

        {last ? (
          <div className="mb-2 space-y-1 text-[13px] leading-snug">
            <p className="text-ink-muted">
              <span className="font-bold text-ink">{tx(lang, 'You:', 'Kamu:')}</span> {last.you}
            </p>
            <p>
              <span className="font-bold text-pink">Denia:</span> {last.denia}
            </p>
            {pending && (
              <div className="flex gap-2 pt-2" role="group" aria-label={tx(lang, 'Open in a new tab?', 'Buka di tab baru?')}>
                <button
                  type="button"
                  onClick={() => answer(false)}
                  className="flex-1 rounded-xl bg-ink/10 py-2 text-xs font-bold hover:bg-ink/15"
                >
                  {tx(lang, 'No', 'Tidak')}
                </button>
                <button
                  type="button"
                  onClick={() => answer(true)}
                  className="flex-1 rounded-xl bg-pink py-2 text-xs font-bold text-[#2a1236] hover:brightness-105"
                >
                  {tx(lang, 'Yes, open', 'Ya, buka')}
                </button>
              </div>
            )}
          </div>
        ) : (
          <div className="no-scrollbar mb-2 flex gap-1.5 overflow-x-auto">
            {deniaSuggestions(lang).map((s) => (
              <button
                key={s}
                type="button"
                onClick={() => void send(s)}
                className="shrink-0 rounded-full bg-ink/10 px-3 py-1 text-xs font-semibold hover:bg-ink/15"
              >
                {s}
              </button>
            ))}
          </div>
        )}

        <form onSubmit={onSubmit} className="flex items-center gap-2">
          <input
            ref={inputRef}
            value={value}
            onChange={(e) => setValue(e.target.value)}
            onKeyDown={onKeyDown}
            placeholder={tx(lang, 'Ask or command Denia...', 'Tanya atau suruh Denia...')}
            aria-label={tx(lang, 'Message Denia', 'Pesan untuk Denia')}
            disabled={busy}
            className="min-w-0 flex-1 rounded-2xl bg-ink/10 px-3.5 py-2.5 text-sm outline-none placeholder:text-ink-muted focus:ring-2 focus:ring-pink/60"
          />
          <button
            type="submit"
            disabled={busy || !value.trim()}
            aria-label={tx(lang, 'Send', 'Kirim')}
            className={cn(
              'grid size-10 shrink-0 place-items-center rounded-2xl bg-gradient-to-br from-pink to-lav text-[#2a1236] shadow-lg shadow-pink/25 transition',
              (busy || !value.trim()) && 'opacity-50',
            )}
          >
            {busy ? <Loader2 className="size-4 animate-spin" /> : <SendHorizontal className="size-4" />}
          </button>
        </form>
      </div>
    </div>
  )
}
