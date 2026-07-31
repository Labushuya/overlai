package de.overlai.app.di

import android.content.Context
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import de.overlai.core.data.SettingsStore
import de.overlai.llm.ProviderFactory
import de.overlai.security.KeyVault
import de.overlai.security.TinkKeyVault
import javax.inject.Singleton

// CHANGE-MARKER v0.1.0: DI-Verdrahtung (siehe CHANGELOG.md)
// Stellt die core-Bausteine app-weit bereit. core-* bleiben DI-annotationsfrei;
// die Bindung passiert hier zentral.
@Module
@InstallIn(SingletonComponent::class)
object AppModule {
    @Provides
    @Singleton
    fun provideKeyVault(
        @ApplicationContext context: Context,
    ): KeyVault = TinkKeyVault(context)

    @Provides
    @Singleton
    fun provideProviderFactory(): ProviderFactory = ProviderFactory()

    @Provides
    @Singleton
    fun provideSettingsStore(
        @ApplicationContext context: Context,
    ): SettingsStore = SettingsStore(context)
}
