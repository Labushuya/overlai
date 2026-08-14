package de.overlai.feature.share

import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import de.overlai.core.data.SettingsStore
import de.overlai.core.data.chat.SessionRepository
import de.overlai.llm.HttpModelCatalog
import de.overlai.llm.ProviderFactory
import de.overlai.security.KeyVault

// CHANGE-MARKER: Entry-Points (P2.4, siehe CHANGELOG.md)
// Die Entry-Activities (PROCESS_TEXT/Share) sind zwar @AndroidEntryPoint, aber die
// ViewModels haben parametrisierte Konstruktoren. Über diesen EntryPoint holen wir
// die app-weit bereitgestellten Bausteine (aus AppModule) in der Activity.
// P2.4: sessionRepository + modelCatalog für den NewChat-Flow (Share → persistenter Chat).
@EntryPoint
@InstallIn(SingletonComponent::class)
interface ShareDependencies {
    fun keyVault(): KeyVault

    fun providerFactory(): ProviderFactory

    fun settingsStore(): SettingsStore

    fun sessionRepository(): SessionRepository

    fun modelCatalog(): HttpModelCatalog
}
