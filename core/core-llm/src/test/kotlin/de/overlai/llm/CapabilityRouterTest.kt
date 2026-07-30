package de.overlai.llm

import com.google.common.truth.Truth.assertThat
import de.overlai.llm.CapabilityRouter.FeatureState
import de.overlai.llm.providers.ProviderRegistry
import org.junit.Test

// CHANGE-MARKER v0.1.0: Provider-Abstraktion (siehe CHANGELOG.md)
class CapabilityRouterTest {
    private val router = CapabilityRouter()

    @Test
    fun `openai supports vision`() {
        assertThat(router.visionState(ProviderRegistry.OPENAI)).isEqualTo(FeatureState.AVAILABLE)
    }

    @Test
    fun `deepseek vision is unavailable (grayed out, not silently no-op)`() {
        assertThat(router.visionState(ProviderRegistry.DEEPSEEK)).isEqualTo(FeatureState.UNAVAILABLE)
    }

    @Test
    fun `openai has native web search`() {
        assertThat(router.webSearchState(ProviderRegistry.OPENAI, hasExternalSearchKey = false))
            .isEqualTo(FeatureState.AVAILABLE)
    }

    @Test
    fun `deepseek web search needs external key when none present`() {
        assertThat(router.webSearchState(ProviderRegistry.DEEPSEEK, hasExternalSearchKey = false))
            .isEqualTo(FeatureState.NEEDS_EXTERNAL_KEY)
    }

    @Test
    fun `deepseek web search available once external key present`() {
        assertThat(router.webSearchState(ProviderRegistry.DEEPSEEK, hasExternalSearchKey = true))
            .isEqualTo(FeatureState.AVAILABLE)
    }

    @Test
    fun `anthropic has no transcription`() {
        assertThat(router.transcriptionState(ProviderRegistry.ANTHROPIC)).isEqualTo(FeatureState.UNAVAILABLE)
    }

    @Test
    fun `openai has transcription`() {
        assertThat(router.transcriptionState(ProviderRegistry.OPENAI)).isEqualTo(FeatureState.AVAILABLE)
    }
}
