package com.multex.browser.denia

import com.multex.browser.Lang
import com.multex.browser.tx
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * Minimal Chat Completions client for APIs that use the OpenAI-compatible /v1/chat/completions
 * contract. The base URL may include /v1 already, or may be the full chat-completions endpoint.
 */
object OpenAiCompatibleClient {
    suspend fun ask(
        baseUrl: String,
        apiKey: String,
        model: String,
        message: String,
        context: String,
        lang: Lang,
    ): DeniaReply = withContext(Dispatchers.IO) {
        try {
            val body = JSONObject()
                .put("model", model.trim())
                .put(
                    "messages",
                    JSONArray()
                        .put(JSONObject().put("role", "system").put("content", DENIA_SYSTEM_PROMPT))
                        .put(JSONObject().put("role", "user").put("content", deniaUserPrompt(message, context, lang))),
                )
                // Do not send response_format: several otherwise-compatible servers reject it.
                .put("temperature", 0.7)

            val conn = URL(chatCompletionsUrl(baseUrl)).openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.connectTimeout = 15_000
            conn.readTimeout = 30_000
            conn.doOutput = true
            conn.setRequestProperty("Content-Type", "application/json")
            conn.setRequestProperty("Accept", "application/json")
            if (apiKey.isNotBlank()) conn.setRequestProperty("Authorization", "Bearer ${apiKey.trim()}")
            conn.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }

            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val text = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
            conn.disconnect()

            if (code !in 200..299) return@withContext failure(lang, code)
            parseReply(responseContent(text), lang)
        } catch (e: Exception) {
            DeniaReply(
                tx(
                    lang,
                    "Hmm, I could not reach that AI API. Check its base URL, model, and connection?",
                    "Hmm, API AI itu nggak bisa kujangkau. Cek base URL, model, dan koneksinya ya?",
                ),
                Mood.POUT,
            )
        }
    }

    private fun chatCompletionsUrl(rawBaseUrl: String): String {
        val base = rawBaseUrl.trim().trimEnd('/')
        require(base.startsWith("https://") || base.startsWith("http://")) { "Base URL must use http or https" }
        return when {
            base.endsWith("/chat/completions") -> base
            base.endsWith("/v1") -> "$base/chat/completions"
            else -> "$base/v1/chat/completions"
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
                    val part = content.opt(i)
                    when (part) {
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
        if (json == null) {
            return DeniaReply(
                raw.ifBlank { tx(lang, "Hmm, I got nothing~", "Hmm, aku bengong~") },
                Mood.HAPPY,
            )
        }
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

    private fun failure(lang: Lang, code: Int): DeniaReply {
        val text = when (code) {
            401, 403 -> tx(
                lang,
                "That AI API rejected the key. Check the key and base URL in Settings~",
                "API AI itu nolak key-nya. Cek key dan base URL di Pengaturan ya~",
            )
            404 -> tx(
                lang,
                "I could not find that Chat Completions endpoint. Check the base URL~",
                "Endpoint Chat Completions-nya nggak ketemu. Cek base URL ya~",
            )
            429 -> tx(
                lang,
                "That AI API is rate-limiting us. Try again in a bit~",
                "API AI itu lagi kena limit. Coba lagi sebentar ya~",
            )
            else -> tx(
                lang,
                "That AI API returned HTTP $code. Check its base URL, key, and model~",
                "API AI itu balikin HTTP $code. Cek base URL, key, dan modelnya ya~",
            )
        }
        return DeniaReply(text, Mood.POUT)
    }
}
