package de.overlai.feature.share

import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import de.overlai.core.data.SettingsStore
import de.overlai.llm.ProviderFactory
import de.overlai.security.KeyVault

// CHANGE-MARKER v0.1.0: Entry-Points (siehe CHANGELOG.md)
// Die Entry-Activities (PROCESS_TEXT/Share) sind zwar @AndroidEntryPoint, aber die
// ViewModels haben parametrisierte Konstruktoren. Über diesen EntryPoint holen wir
// die app-weit bereitgestellten Bausteine (aus AppModule) in der Activity.
@EntryPoint
@InstallIn(SingletonComponent::class)
interface ShareDependencies {
    fun keyVault(): KeyVault

    fun providerFactory(): ProviderFactory

    fun settingsStore(): SettingsStore
}
