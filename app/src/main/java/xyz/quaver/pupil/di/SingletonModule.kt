package xyz.quaver.pupil.di

import android.content.Context
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import xyz.quaver.pupil.networking.FileImageCache
import xyz.quaver.pupil.networking.ImageCache
import java.io.File
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object SingletonModule {
    @Singleton
    @Provides
    fun provideImageCache(
        @ApplicationContext context: Context
    ): ImageCache {
        return FileImageCache(File(context.cacheDir, "image_cache"))
    }
}