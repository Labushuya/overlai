package de.overlai.llm.transport

import de.overlai.llm.ChatMessage
import de.overlai.llm.ChatRequest
import de.overlai.llm.Role
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.util.Base64

// CHANGE-MARKER v0.1.0: Anthropic-Adapter (siehe CHANGELOG.md)
// Mappt neutrale ChatRequest/ChatMessage auf das Anthropic-Messages-Format.
// Die vier Nähte, an denen sich Anthropic von OpenAI unterscheidet (Feasibility §4):
//  1. system ist Top-Level, nicht eine message-Rolle -> hier herausgezogen.
//  2. max_tokens ist Pflicht.
//  3. Vision: {type:"image", source:{type:"base64", media_type, data}} statt image_url.
//  4. Kein temperature/top_p/thinking (400 auf neuen Modellen) -> weggelassen.
internal object AnthropicMessageMapper {
    private const val DEFAULT_MAX_TOKENS = 4096

    fun toRequest(request: ChatRequest): AnthropicRequest {
        // system-Nachrichten aus der Liste ziehen und als Top-Level-system zusammenfassen.
        val systemText =
            request.messages
                .filter { it.role == Role.SYSTEM }
                .joinToString("\n\n") { it.content }
                .ifBlank { request.system.orEmpty() }
                .ifBlank { null }

        val wireMessages =
            request.messages
                .filter { it.role != Role.SYSTEM }
                .map { msg ->
                    AnthropicMessage(
                        role = roleToWire(msg.role),
                        content = buildContent(msg),
                    )
                }

        return AnthropicRequest(
            model = request.model,
            maxTokens = request.maxTokens ?: DEFAULT_MAX_TOKENS,
            messages = wireMessages,
            system = systemText,
            stream = true,
        )
    }

    private fun roleToWire(role: Role): String =
        when (role) {
            Role.ASSISTANT -> "assistant"
            else -> "user" // USER; SYSTEM wird vorher herausgefiltert
        }

    private fun buildContent(message: ChatMessage): JsonElement {
        if (message.images.isEmpty()) {
            return JsonPrimitive(message.content)
        }
        return buildJsonArray {
            // Anthropic empfiehlt Text NACH den Bildern; hier zuerst Bilder, dann Text.
            message.images.forEach { image ->
                val b64 = Base64.getEncoder().encodeToString(image.bytes)
                add(
                    buildJsonObject {
                        put("type", "image")
                        put(
                            "source",
                            buildJsonObject {
                                put("type", "base64")
                                put("media_type", image.mimeType)
                                put("data", b64)
                            },
                        )
                    },
                )
            }
            if (message.content.isNotEmpty()) {
                add(
                    buildJsonObject {
                        put("type", "text")
                        put("text", message.content)
                    },
                )
            }
        }
    }
}
