package com.multex.browser.denia

import com.multex.browser.Lang
import com.multex.browser.tx
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/** Shared instruction for Gemini and OpenAI-compatible chat-completions APIs. */
internal const val DENIA_SYSTEM_PROMPT = """You are Denia, a cheerful pink-haired chibi companion living inside the Multex mobile browser.
Personality: warm, playful, a little cheeky, uses "~" sometimes. Keep replies very short (max 2 sentences) so they fit in a speech bubble.
Reply in the same language the user writes in (Indonesian or English; casual Indonesian is fine).

You can control the browser by choosing exactly one action:
- hide_denia: user wants you to hide/disappear/turn off
- show_denia: user wants you to appear/come back
- new_tab: open a new tab
- open_tabs: show the tab list
- open_settings: open settings
- open_sessions: show sessions
- save_session: save current tabs as a session
- direct_mode: let the user tap a spot for you to run to
- theme_sakura / theme_midnight: switch the browser theme
- go_home: go back to the home screen
- none: just answer the question or chat

If the message is a question or general chat, answer it helpfully and briefly with action "none".

Return only one JSON object with string fields "reply", "mood", and "action". The mood must be "happy", "neutral", or "pout"."""

internal fun deniaUserPrompt(message: String, context: String, lang: Lang): String =
    listOf(
        if (context.isNotBlank()) "Browser state: $context" else "",
        if (lang == Lang.ID) "App language: Indonesian. Reply in casual Indonesian unless the user writes English." else "",
        "User: $message",
    ).filter { it.isNotEmpty() }.joinToString("\n\n")

/**
 * Android version of app/api/denia/route.ts. There is no server on the phone, so Denia calls the
 * Gemini REST API directly with the key the user typed in Settings (stored only on this device).
 */
object GeminiClient {
    private const val MODEL = "gemini-2.5-flash"

    private fun schema(): JSONObject {
        val actions = JSONArray().apply { DeniaAction.FOR_AI.forEach { put(it.wire) } }
        val moods = JSONArray().apply { Mood.entries.forEach { put(it.wire) } }
        return JSONObject()
            .put("type", "OBJECT")
            .put(
                "properties",
                JSONObject()
                    .put("reply", JSONObject().put("type", "STRING"))
                    .put("mood", JSONObject().put("type", "STRING").put("enum", moods))
                    .put("action", JSONObject().put("type", "STRING").put("enum", actions)),
            )
            .put("required", JSONArray().put("reply").put("mood").put("action"))
    }

    suspend fun ask(apiKey: String, message: String, context: String, lang: Lang): DeniaReply =
        withContext(Dispatchers.IO) {
            try {
                val prompt = deniaUserPrompt(message, context, lang)

                val body = JSONObject()
                    .put("systemInstruction", JSONObject().put("parts", JSONArray().put(JSONObject().put("text", DENIA_SYSTEM_PROMPT))))
                    .put(
                        "contents",
                        JSONArray().put(
                            JSONObject()
                                .put("role", "user")
                                .put("parts", JSONArray().put(JSONObject().put("text", prompt))),
                        ),
                    )
                    .put(
                        "generationConfig",
                        JSONObject()
                            .put("temperature", 0.7)
                            .put("responseMimeType", "application/json")
                            .put("responseSchema", schema()),
                    )

                val conn = URL("https://generativelanguage.googleapis.com/v1beta/models/$MODEL:generateContent")
                    .openConnection() as HttpURLConnection
                conn.requestMethod = "POST"
                conn.connectTimeout = 15_000
                conn.readTimeout = 30_000
                conn.doOutput = true
                conn.setRequestProperty("Content-Type", "application/json")
                conn.setRequestProperty("x-goog-api-key", apiKey)
                conn.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }

                val code = conn.responseCode
                val stream = if (code in 200..299) conn.inputStream else conn.errorStream
                val text = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
                conn.disconnect()

                if (code !in 200..299) return@withContext failure(lang, code, text)

                val out = JSONObject(text)
                    .getJSONArray("candidates").getJSONObject(0)
                    .getJSONObject("content")
                    .getJSONArray("parts").getJSONObject(0)
                    .getString("text")
                val json = JSONObject(out)
                DeniaReply(
                    text = json.optString("reply").ifBlank { tx(lang, "Hmm, I got nothing~", "Hmm, aku bengong~") },
                    mood = Mood.fromWire(json.optString("mood")),
                    action = DeniaAction.fromWire(json.optString("action")),
                )
            } catch (e: Exception) {
                DeniaReply(
                    tx(
                        lang,
                        "Hmm, I could not reach my brain. Check the connection?",
                        "Hmm, otakku nggak kejangkau. Cek koneksinya?",
                    ),
                    Mood.POUT,
                )
            }
        }

    private fun failure(lang: Lang, code: Int, body: String): DeniaReply {
        val badKey = code == 401 || code == 403 || Regex("API key not valid|API_KEY_INVALID|PERMISSION_DENIED", RegexOption.IGNORE_CASE).containsMatchIn(body)
        val text = if (badKey) {
            tx(
                lang,
                "Hmm, Google rejected that Gemini key. Double-check it in Settings~",
                "Hmm, key Gemini-nya ditolak Google. Cek lagi di Pengaturan ya~",
            )
        } else {
            tx(lang, "Uwaa, my brain froze for a second. Try again?", "Uwaa, otakku nge-freeze sebentar. Coba lagi?")
        }
        return DeniaReply(text, Mood.POUT)
    }
}
