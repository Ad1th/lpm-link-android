package cx.lpm.link.di

import android.content.Context
import cx.lpm.link.network.HostProbe
import cx.lpm.link.network.LpmClient
import cx.lpm.link.network.MessageRouter
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.serialization.json.Json
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideJson(): Json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        encodeDefaults = true
    }

    @Provides
    @Singleton
    fun provideLpmClient(): LpmClient = LpmClient()

    @Provides
    @Singleton
    fun provideMessageRouter(client: LpmClient): MessageRouter = MessageRouter(client)

    @Provides
    @Singleton
    fun provideHostProbe(): HostProbe = HostProbe()
}
