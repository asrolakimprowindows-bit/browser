package com.multex.browser.denia

import com.multex.browser.Lang
import com.multex.browser.OPENROUTER_DEFAULT_MODEL
import com.multex.browser.tx
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URL
import java.net.UnknownHostException

/**
 * Denia's cloud brain, talking to the OpenRouter Chat Completions API
 * (https://openrouter.ai/docs/api-reference/chat-completion).
 *
 * The API key comes from Settings and is stored only on this device — it is never hardcoded,
 * never sent anywhere except api.openrouter.ai, and never echoed in an error message.
 */
object OpenRouterClient {
    private const val ENDPOINT = "https://openrouter.ai/api/v1/chat/completions"

    suspend fun ask(
        apiKey: String,
        model: String,
        message: String,
        context: String,
        lang: Lang,
    ): DeniaReply = withContext(Dispatchers.IO) {
        var conn: HttpURLConnection? = null
        try {
            val body = JSONObject()
                .put("model", model.trim().ifBlank { OPENROUTER_DEFAULT_MODEL })
                .put(
                    "messages",
                    JSONArray()
                        .put(JSONObject().put("role", "system").put("content", DENIA_SYSTEM_PROMPT))
                        .put(JSONObject().put("role", "user").put("content", deniaUserPrompt(message, context, lang))),
                )
                // No response_format: several OpenRouter models reject it, and parseReply
                // already salvages the first JSON object out of the reply text.
                .put("temperature", 0.7)

            conn = URL(ENDPOINT).openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.connectTimeout = 15_000
            conn.readTimeout = 30_000
            conn.doOutput = true
            conn.setRequestProperty("Content-Type", "application/json")
            conn.setRequestProperty("Accept", "application/json")
            conn.setRequestProperty("Authorization", "Bearer ${apiKey.trim()}")
            // Optional attribution headers OpenRouter asks apps to send.
            conn.setRequestProperty("HTTP-Referer", "https://multex.browser")
            conn.setRequestProperty("X-Title", "Multex Browser")
            conn.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }

            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val text = stream?.bufferedReader()?.use { it.readText() }.orEmpty()

            if (code !in 200..299) return@withContext failure(lang, code)
            parseReply(responseContent(text), lang)
        } catch (e: SocketTimeoutException) {
            DeniaReply(
                tx(lang, "Hmm, OpenRouter took too long to answer. Try again?", "Hmm, OpenRouter kelamaan jawab. Coba lagi ya?"),
                Mood.POUT,
            )
        } catch (e: UnknownHostException) {
            DeniaReply(
                tx(lang, "I cannot reach OpenRouter. Check the internet connection?", "OpenRouter-nya nggak kejangkau. Cek koneksi internet ya?"),
                Mood.POUT,
            )
        } catch (e: Exception) {
            // Includes malformed JSON responses, SSL failures, and everything else.
            DeniaReply(
                tx(lang, "Hmm, something broke on the way to OpenRouter. Try again in a bit?", "Hmm, ada yang error pas manggil OpenRouter. Coba lagi bentar ya?"),
                Mood.POUT,
            )
        } finally {
            conn?.disconnect()
        }
    }

    private fun responseContent(response: String): String {
        val message = JSONObject(response)
            .getJSONArray("choices")
            .getJSONObject(0)
            .getJSONObject("message")
        return when (val content = message.opt("content")) {
            is String -> content
            is JSONArray -> buildString {
                for (i in 0 until content.length()) {
                    when (val part = content.opt(i)) {
                        is String -> append(part)
                        is JSONObject -> append(part.optString("text", part.optString("content")))
                    }
                }
            }
            else -> ""
        }
    }

    private fun parseReply(content: String, lang: Lang): DeniaReply {
        val raw = content.trim()
        val json = runCatching { JSONObject(extractJsonObject(raw)) }.getOrNull()
            ?: return DeniaReply(raw.ifBlank { tx(lang, "Hmm, I got nothing~", "Hmm, aku bengong~") }, Mood.HAPPY)
        return DeniaReply(
            text = json.optString("reply").trim().ifBlank { tx(lang, "Hmm, I got nothing~", "Hmm, aku bengong~") },
            mood = Mood.fromWire(json.optString("mood")),
            action = DeniaAction.fromWire(json.optString("action")),
        )
    }

    private fun extractJsonObject(text: String): String {
        val start = text.indexOf('{')
        val end = text.lastIndexOf('}')
        return if (start >= 0 && end > start) text.substring(start, end + 1) else text
    }

    /** User-safe failure messages; the API key and raw response body are never shown. */
    private fun failure(lang: Lang, code: Int): DeniaReply {
        val text = when (code) {
            401, 403 -> tx(
                lang,
                "OpenRouter rejected that key. Double-check it in Settings~",
                "OpenRouter nolak key-nya. Cek lagi di Pengaturan ya~",
            )
            402 -> tx(
                lang,
                "The OpenRouter account is out of credits. Top it up, or switch model~",
                "Kredit OpenRouter-nya habis. Isi ulang dulu atau ganti model ya~",
            )
            404 -> tx(
                lang,
                "OpenRouter does not know that model. Check the model name in Settings~",
                "OpenRouter nggak kenal model itu. Cek nama modelnya di Pengaturan ya~",
            )
            408, 504 -> tx(lang, "OpenRouter timed out. Try again?", "OpenRouter timeout. Coba lagi ya?")
            429 -> tx(
                lang,
                "OpenRouter is rate-limiting us. Try again in a bit~",
                "OpenRouter lagi kena rate limit. Coba lagi sebentar ya~",
            )
            else -> tx(
                lang,
                "OpenRouter returned HTTP $code. Check the key and model in Settings~",
                "OpenRouter balikin HTTP $code. Cek key dan modelnya di Pengaturan ya~",
            )
        }
        return DeniaReply(text, Mood.POUT)
    }
}
