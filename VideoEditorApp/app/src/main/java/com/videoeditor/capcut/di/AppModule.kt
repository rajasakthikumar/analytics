package com.videoeditor.capcut.di

import android.content.Context
import androidx.room.Room
import com.videoeditor.capcut.core.engine.VideoEngine
import com.videoeditor.capcut.core.engine.VideoEngineImpl
import com.videoeditor.capcut.core.export.ExportManager
import com.videoeditor.capcut.core.export.ExportManagerImpl
import com.videoeditor.capcut.core.filters.FilterEngine
import com.videoeditor.capcut.core.filters.FilterEngineImpl
import com.videoeditor.capcut.core.audio.AudioEngine
import com.videoeditor.capcut.core.audio.AudioEngineImpl
import com.videoeditor.capcut.core.text.TextRenderer
import com.videoeditor.capcut.core.text.TextRendererImpl
import com.videoeditor.capcut.core.transitions.TransitionEngine
import com.videoeditor.capcut.core.transitions.TransitionEngineImpl
import com.videoeditor.capcut.core.effects.EffectsEngine
import com.videoeditor.capcut.core.effects.EffectsEngineImpl
import com.videoeditor.capcut.core.utils.FileManager
import com.videoeditor.capcut.core.utils.ThumbnailGenerator
import com.videoeditor.capcut.data.local.ProjectDatabase
import com.videoeditor.capcut.data.local.ProjectDao
import com.videoeditor.capcut.data.repository.MediaRepository
import com.videoeditor.capcut.data.repository.MediaRepositoryImpl
import com.videoeditor.capcut.data.repository.ProjectRepository
import com.videoeditor.capcut.data.repository.ProjectRepositoryImpl
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import javax.inject.Qualifier
import javax.inject.Singleton

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class IoDispatcher

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class MainDispatcher

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class DefaultDispatcher

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideProjectDatabase(@ApplicationContext context: Context): ProjectDatabase {
        return Room.databaseBuilder(
            context,
            ProjectDatabase::class.java,
            "video_editor_db"
        )
            .fallbackToDestructiveMigration()
            .build()
    }

    @Provides
    @Singleton
    fun provideProjectDao(database: ProjectDatabase): ProjectDao {
        return database.projectDao()
    }

    @Provides
    @Singleton
    fun provideFileManager(@ApplicationContext context: Context): FileManager {
        return FileManager(context)
    }

    @Provides
    @Singleton
    fun provideThumbnailGenerator(@ApplicationContext context: Context): ThumbnailGenerator {
        return ThumbnailGenerator(context)
    }

    @Provides
    @IoDispatcher
    fun provideIoDispatcher(): CoroutineDispatcher = Dispatchers.IO

    @Provides
    @MainDispatcher
    fun provideMainDispatcher(): CoroutineDispatcher = Dispatchers.Main

    @Provides
    @DefaultDispatcher
    fun provideDefaultDispatcher(): CoroutineDispatcher = Dispatchers.Default
}

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    @Singleton
    abstract fun bindMediaRepository(impl: MediaRepositoryImpl): MediaRepository

    @Binds
    @Singleton
    abstract fun bindProjectRepository(impl: ProjectRepositoryImpl): ProjectRepository
}

@Module
@InstallIn(SingletonComponent::class)
abstract class EngineModule {

    @Binds
    @Singleton
    abstract fun bindVideoEngine(impl: VideoEngineImpl): VideoEngine

    @Binds
    @Singleton
    abstract fun bindFilterEngine(impl: FilterEngineImpl): FilterEngine

    @Binds
    @Singleton
    abstract fun bindAudioEngine(impl: AudioEngineImpl): AudioEngine

    @Binds
    @Singleton
    abstract fun bindTextRenderer(impl: TextRendererImpl): TextRenderer

    @Binds
    @Singleton
    abstract fun bindTransitionEngine(impl: TransitionEngineImpl): TransitionEngine

    @Binds
    @Singleton
    abstract fun bindEffectsEngine(impl: EffectsEngineImpl): EffectsEngine

    @Binds
    @Singleton
    abstract fun bindExportManager(impl: ExportManagerImpl): ExportManager
}
