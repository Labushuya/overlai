package de.overlai.llm.transport

import de.overlai.llm.ChatMessage
import de.overlai.llm.Role
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.util.Base64

// CHANGE-MARKER v0.1.0: Provider-Abstraktion (siehe CHANGELOG.md)
// Baut das OpenAI-`content`-Feld. Reiner Text -> JSON-String. Mit Bildern ->
// Array aus {type:text} + {type:image_url, image_url:{url:"data:...;base64,..."}}.
internal object OpenAiContentBuilder {
    fun roleToWire(role: Role): String =
        when (role) {
            Role.SYSTEM -> "system"
            Role.USER -> "user"
            Role.ASSISTANT -> "assistant"
        }

    fun buildContent(message: ChatMessage): JsonElement {
        if (message.images.isEmpty()) {
            // Einfacher Fall: content ist ein String.
            return kotlinx.serialization.json.JsonPrimitive(message.content)
        }
        // Vision: content ist ein Array von Parts.
        return buildJsonArray {
            if (message.content.isNotEmpty()) {
                add(
                    buildJsonObject {
                        put("type", "text")
                        put("text", message.content)
                    },
                )
            }
            message.images.forEach { image ->
                val b64 = Base64.getEncoder().encodeToString(image.bytes)
                add(
                    buildJsonObject {
                        put("type", "image_url")
                        put(
                            "image_url",
                            buildJsonObject {
                                put("url", "data:${image.mimeType};base64,$b64")
                            },
                        )
                    },
                )
            }
        }
    }
}
