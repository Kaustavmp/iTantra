// iTantra — SemanticPacket.kt
// Core data class for the semantic relay payload.
// This is the ONLY thing that crosses the wire between Phone A and Phone B.

package dev.itantra.core

import org.json.JSONObject
import java.util.UUID

/**
 * The entire payload transmitted between devices.
 * Typical JSON size: 120–180 bytes.
 *
 * @param msgId     Short unique ID per utterance (first 8 chars of a UUID).
 * @param lang      ISO 639-1 language code (e.g. "hi", "en").
 * @param text      Recognized text from STT.
 * @param urgency   [UrgencyLevel.NORMAL] or [UrgencyLevel.HIGH].
 * @param confidence STT confidence score (0.0–1.0). Optional.
 * @param ts        Unix timestamp in milliseconds captured at utterance start.
 *                  Used for end-to-end latency measurement.
 */
data class SemanticPacket(
    val msgId: String,
    val lang: String,
    val text: String,
    val urgency: UrgencyLevel,
    val confidence: Float = 0f,
    val ts: Long,
) {
    enum class UrgencyLevel { NORMAL, HIGH }

    companion object {
        /**
         * Create a new packet with a freshly generated msgId and current timestamp.
         */
        fun create(
            lang: String,
            text: String,
            urgency: UrgencyLevel,
            confidence: Float = 0f,
        ): SemanticPacket = SemanticPacket(
            msgId = UUID.randomUUID().toString().substring(0, 8),
            lang = lang,
            text = text,
            urgency = urgency,
            confidence = confidence,
            ts = System.currentTimeMillis(),
        )

        /**
         * Deserialize from JSON string (received from the wire).
         * Returns null if parsing fails (caller should log and discard).
         */
        fun fromJson(json: String): SemanticPacket? = runCatching {
            val obj = JSONObject(json)
            SemanticPacket(
                msgId = obj.getString("msg_id"),
                lang = obj.getString("lang"),
                text = obj.getString("text"),
                urgency = if (obj.getString("urgency") == "high")
                    UrgencyLevel.HIGH else UrgencyLevel.NORMAL,
                confidence = obj.optDouble("confidence", 0.0).toFloat(),
                ts = obj.getLong("ts"),
            )
        }.getOrNull()
    }

    /**
     * Serialize to compact JSON string for transmission.
     * Keeps the key names snake_case to match the Python schema and packet_schema.json.
     */
    fun toJson(): String = JSONObject().apply {
        put("msg_id", msgId)
        put("lang", lang)
        put("text", text)
        put("urgency", urgency.name.lowercase())
        put("confidence", confidence.toDouble())
        put("ts", ts)
    }.toString()

    /**
     * Size of the wire payload in bytes (UTF-8 encoded JSON).
     * Used for the demo metrics overlay.
     */
    fun sizeBytes(): Int = toJson().toByteArray(Charsets.UTF_8).size
}
