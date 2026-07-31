package de.overlai.app.di

import android.content.Context
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import de.overlai.conversation.ConversationEngine
import de.overlai.core.data.SettingsStore
import de.overlai.feature.updater.ApkDownloader
import de.overlai.feature.updater.PackageInstallerSession
import de.overlai.feature.updater.UpdateChecker
import de.overlai.llm.ProviderFactory
import de.overlai.security.KeyVault
import de.overlai.security.TinkKeyVault
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import javax.inject.Singleton

// CHANGE-MARKER v0.2.1: DI-Verdrahtung (siehe CHANGELOG.md)
// Stellt die core-Bausteine app-weit bereit. core-* bleiben DI-annotationsfrei;
// die Bindung passiert hier zentral.
@Module
@InstallIn(SingletonComponent::class)
object AppModule {
    // latest.json des Updaters. GitHub Pages ist für das Repo NICHT aktiviert, daher
    // direkt den gh-pages-Branch über raw.githubusercontent lesen (liefert 200; die
    // .github.io-URL gäbe 404). Wird von release.yml auf gh-pages publiziert.
    private const val LATEST_JSON_URL =
        "https://raw.githubusercontent.com/Labushuya/overlai/gh-pages/latest.json"

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

    @Provides
    @Singleton
    fun provideConversationEngine(
        providerFactory: ProviderFactory,
        keyVault: KeyVault,
        settingsStore: SettingsStore,
    ): ConversationEngine = ConversationEngine(providerFactory, keyVault, settingsStore)

    @Provides
    @Singleton
    fun provideOkHttpClient(): OkHttpClient = OkHttpClient.Builder().build()

    @Provides
    @Singleton
    fun provideUpdateChecker(client: OkHttpClient): UpdateChecker =
        UpdateChecker(
            client = client,
            json = Json { ignoreUnknownKeys = true },
            latestJsonUrl = LATEST_JSON_URL,
        )

    @Provides
    @Singleton
    fun provideApkDownloader(
        @ApplicationContext context: Context,
        client: OkHttpClient,
    ): ApkDownloader = ApkDownloader(context, client)

    @Provides
    @Singleton
    fun providePackageInstallerSession(
        @ApplicationContext context: Context,
    ): PackageInstallerSession = PackageInstallerSession(context)
}
