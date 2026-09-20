'use client'

import { X } from 'lucide-react'
import { cn } from '@/lib/utils'

export function LetterTile({
  label,
  tint,
  size = 'md',
  className,
}: {
  label: string
  tint: string
  size?: 'sm' | 'md' | 'lg'
  className?: string
}) {
  const sizing =
    size === 'lg'
      ? 'size-14 rounded-2xl text-xl'
      : size === 'sm'
        ? 'size-6 rounded-md text-[10px]'
        : 'size-9 rounded-xl text-sm'
  return (
    <span
      aria-hidden
      className={cn('grid shrink-0 place-items-center font-display font-bold text-[#17172f] shadow-md', sizing, className)}
      style={{ background: `linear-gradient(145deg, ${tint}, color-mix(in oklab, ${tint} 65%, white))` }}
    >
      {label.charAt(0)}
    </span>
  )
}

export function Switch({
  checked,
  onChange,
  label,
}: {
  checked: boolean
  onChange: (value: boolean) => void
  label: string
}) {
  return (
    <button
      type="button"
      role="switch"
      aria-checked={checked}
      aria-label={label}
      onClick={() => onChange(!checked)}
      className={cn(
        'relative h-7 w-12 shrink-0 rounded-full transition-colors',
        checked ? 'bg-pink' : 'bg-ink/15',
      )}
    >
      <span
        className={cn(
          'absolute top-1 size-5 rounded-full bg-white shadow transition-transform',
          checked ? 'translate-x-6' : 'translate-x-1',
        )}
      />
    </button>
  )
}

export function Segmented<T extends string>({
  value,
  options,
  onChange,
  label,
}: {
  value: T
  options: { value: T; label: string }[]
  onChange: (value: T) => void
  label: string
}) {
  return (
    <div role="radiogroup" aria-label={label} className="flex rounded-xl bg-ink/10 p-1">
      {options.map((o) => (
        <button
          key={o.value}
          type="button"
          role="radio"
          aria-checked={value === o.value}
          onClick={() => onChange(o.value)}
          className={cn(
            'flex-1 rounded-lg px-3 py-1.5 text-xs font-bold transition-colors',
            value === o.value ? 'bg-pink text-[#2a1236] shadow' : 'text-ink-muted hover:text-ink',
          )}
        >
          {o.label}
        </button>
      ))}
    </div>
  )
}

export function SettingRow({
  title,
  description,
  children,
  stacked = false,
}: {
  title: string
  description?: string
  children: React.ReactNode
  stacked?: boolean
}) {
  return (
    <div className={cn('flex gap-4 py-3', stacked ? 'flex-col' : 'items-center justify-between')}>
      <div className="min-w-0">
        <p className="text-sm font-bold">{title}</p>
        {description && <p className="text-xs text-ink-muted">{description}</p>}
      </div>
      {children}
    </div>
  )
}

export function OverlaySheet({
  title,
  subtitle,
  onClose,
  children,
  footer,
}: {
  title: string
  subtitle?: string
  onClose: () => void
  children: React.ReactNode
  footer?: React.ReactNode
}) {
  return (
    <div className="absolute inset-0 z-50 flex flex-col justify-end">
      <button
        type="button"
        aria-label="Close"
        onClick={onClose}
        className="absolute inset-0 bg-black/45 animate-in fade-in duration-200"
      />
      <section
        role="dialog"
        aria-modal="true"
        aria-label={title}
        className="glass-strong relative flex max-h-[86%] flex-col rounded-t-[30px] shadow-2xl animate-in fade-in slide-in-from-bottom-10 duration-300"
      >
        <div className="mx-auto mt-3 h-1.5 w-12 rounded-full bg-ink/20" />
        <header className="flex items-center justify-between px-5 pb-3 pt-3">
          <div>
            <h2 className="font-display text-xl font-semibold">{title}</h2>
            {subtitle && <p className="text-xs text-ink-muted">{subtitle}</p>}
          </div>
          <button
            type="button"
            onClick={onClose}
            aria-label="Close"
            className="grid size-9 place-items-center rounded-full bg-ink/10 hover:bg-ink/20"
          >
            <X className="size-4" />
          </button>
        </header>
        <div className="no-scrollbar flex-1 overflow-y-auto px-5 pb-5">{children}</div>
        {footer && <footer className="border-t border-glass-border px-5 py-4">{footer}</footer>}
      </section>
    </div>
  )
}

export function PillButton({
  children,
  onClick,
  variant = 'glass',
  className,
}: {
  children: React.ReactNode
  onClick?: () => void
  variant?: 'glass' | 'accent'
  className?: string
}) {
  return (
    <button
      type="button"
      onClick={onClick}
      className={cn(
        'flex flex-1 items-center justify-center gap-2 rounded-2xl px-4 py-3 text-sm font-bold transition-transform active:scale-[0.98]',
        variant === 'accent'
          ? 'bg-gradient-to-br from-pink to-lav text-[#2a1236] shadow-lg shadow-pink/25'
          : 'glass text-ink',
        className,
      )}
    >
      {children}
    </button>
  )
}
