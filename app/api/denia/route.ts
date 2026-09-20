import { generateText, Output } from 'ai'
import { google } from '@ai-sdk/google'
import { z } from 'zod'
import { DENIA_ACTIONS } from '@/lib/denia-commands'

export const maxDuration = 30

// A direct Gemini key (free tier at aistudio.google.com) skips AI Gateway billing; otherwise go through the Gateway.
const model = process.env.GOOGLE_GENERATIVE_AI_API_KEY ? google('gemini-2.5-flash') : 'google/gemini-2.5-flash'

const schema = z.object({
  reply: z.string().max(240).describe('Denia speaking to the user, 1-2 short sentences, same language as the user'),
  mood: z.enum(['happy', 'neutral', 'pout']),
  action: z.enum(DENIA_ACTIONS).describe('Browser action to perform, or "none" for plain conversation'),
})

const SYSTEM = `You are Denia, a cheerful pink-haired chibi companion living inside the Multex mobile browser.
Personality: warm, playful, a little cheeky, uses "~" sometimes. Keep replies very short (max 2 sentences) so they fit in a speech bubble.
Reply in the same language the user writes in (Indonesian or English; casual Indonesian is fine).

You can control the browser by choosing exactly one action:
- hide_denia: user wants you to hide/disappear/turn off
- show_denia: user wants you to appear/come back
- new_tab: open a new tab
- open_tabs: show the tab list
- open_settings: open settings
- open_sessions: show saved sessions
- save_session: save current tabs as a session
- direct_mode: let the user tap a spot for you to run to
- theme_sakura / theme_midnight: switch the browser theme
- go_home: go back to the home screen
- none: just answer the question or chat

If the message is a question or general chat, answer it helpfully and briefly with action "none".`

export async function POST(req: Request) {
  const body = (await req.json().catch(() => null)) as { message?: string; context?: string; lang?: string } | null
  const message = body?.message?.trim()
  if (!message) return Response.json({ error: 'Empty message' }, { status: 400 })

  try {
    const { output } = await generateText({
      model,
      system: SYSTEM,
      prompt: [
        body?.context ? `Browser state: ${body.context}` : '',
        body?.lang === 'id' ? 'App language: Indonesian. Reply in casual Indonesian unless the user writes English.' : '',
        `User: ${message}`,
      ]
        .filter(Boolean)
        .join('\n\n'),
      output: Output.object({ schema }),
      temperature: 0.7,
    })
    return Response.json(output)
  } catch (error) {
    console.error('[denia] generate failed', error)
    const billing = error instanceof Error && /credit card|Unauthenticated|API key/i.test(error.message)
    return Response.json(
      {
        reply: billing
          ? 'My AI brain is not connected yet. Add a Gemini API key (GOOGLE_GENERATIVE_AI_API_KEY) or set up AI Gateway billing~'
          : 'Uwaa, my brain froze for a second. Try again?',
        mood: 'pout',
        action: 'none',
      },
      { status: 200 },
    )
  }
}
