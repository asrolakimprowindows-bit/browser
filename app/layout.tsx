import { Analytics } from '@vercel/analytics/next'
import type { Metadata, Viewport } from 'next'
import { Fredoka, Nunito } from 'next/font/google'
import './globals.css'

const nunito = Nunito({ subsets: ['latin'], variable: '--font-nunito', display: 'swap' })
const fredoka = Fredoka({ subsets: ['latin'], variable: '--font-fredoka', display: 'swap' })

export const metadata: Metadata = {
  title: 'Multex Browser × Denia',
  description: 'Glass-UI mobile browser concept with Denia, an interactive chibi companion you can tap, drag, and direct.',
  generator: 'v0.app',
  icons: {
    icon: [{ url: '/icon.png', type: 'image/png' }],
    apple: '/apple-icon.png',
  },
}

export const viewport: Viewport = {
  colorScheme: 'dark',
  themeColor: '#0a0b1c',
  width: 'device-width',
  initialScale: 1,
  userScalable: false,
}

export default function RootLayout({
  children,
}: Readonly<{
  children: React.ReactNode
}>) {
  return (
    <html lang="en" className={`${nunito.variable} ${fredoka.variable}`}>
      <body className="antialiased">
        {children}
        {process.env.NODE_ENV === 'production' && <Analytics />}
      </body>
    </html>
  )
}
