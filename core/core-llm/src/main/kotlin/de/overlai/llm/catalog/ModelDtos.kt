package de.overlai.llm.catalog

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// CHANGE-MARKER v0.4.0: Modell-Katalog (siehe CHANGELOG.md)
// Wire-Formate der /models-Endpoints. Alle Felder nullable/default -> tolerant
// gegen fehlende/zusätzliche Felder (pro-Zeile-Toleranz beim Parsen).

// --- OpenAI-Shape (auch DeepSeek): { "data": [ { "id": ... } ] } ---
@Serializable
internal data class OpenAiModelsResponse(
    val data: List<OpenAiModelEntry> = emptyList(),
)

@Serializable
internal data class OpenAiModelEntry(
    val id: String? = null,
)

// --- Anthropic: { "data": [ { "id", "display_name" } ], "has_more", "last_id" } ---
@Serializable
internal data class AnthropicModelsResponse(
    val data: List<AnthropicModelEntry> = emptyList(),
    @SerialName("has_more") val hasMore: Boolean = false,
    @SerialName("last_id") val lastId: String? = null,
)

@Serializable
internal data class AnthropicModelEntry(
    val id: String? = null,
    @SerialName("display_name") val displayName: String? = null,
)

// --- OpenRouter: reicher; pricing als String, architecture.output_modalities ---
@Serializable
internal data class OpenRouterModelsResponse(
    val data: List<OpenRouterModelEntry> = emptyList(),
)

@Serializable
internal data class OpenRouterModelEntry(
    val id: String? = null,
    val name: String? = null,
    @SerialName("context_length") val contextLength: Int? = null,
    val architecture: OpenRouterArchitecture? = null,
    val pricing: OpenRouterPricing? = null,
)

@Serializable
internal data class OpenRouterArchitecture(
    @SerialName("output_modalities") val outputModalities: List<String> = emptyList(),
)

@Serializable
internal data class OpenRouterPricing(
    val prompt: String? = null,
    val completion: String? = null,
)

// --- OpenRouter Guthaben: GET /api/v1/credits → { "data": { "total_credits", "total_usage" } } ---
// (P2.5 E3). Beträge in USD. Felder nullable/default → tolerant.
@Serializable
internal data class OpenRouterCreditsResponse(
    val data: OpenRouterCreditsData? = null,
)

@Serializable
internal data class OpenRouterCreditsData(
    @SerialName("total_credits") val totalCredits: Double? = null,
    @SerialName("total_usage") val totalUsage: Double? = null,
)
