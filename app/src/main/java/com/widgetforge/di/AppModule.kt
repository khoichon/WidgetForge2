package com.widgetforge.di

import android.content.Context
import androidx.room.Room
import com.widgetforge.data.db.WidgetForgeDatabase
import com.widgetforge.data.prefs.PrefsManager
import com.widgetforge.data.repository.WidgetRegistry
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): WidgetForgeDatabase =
        Room.databaseBuilder(
            context,
            WidgetForgeDatabase::class.java,
            WidgetForgeDatabase.DATABASE_NAME
        )
            .fallbackToDestructiveMigration()
            .build()

    @Provides
    @Singleton
    fun provideWidgetRegistry(
        @ApplicationContext context: Context,
        db: WidgetForgeDatabase
    ): WidgetRegistry = WidgetRegistry(context, db)

    @Provides
    @Singleton
    fun providePrefsManager(
        @ApplicationContext context: Context
    ): PrefsManager = PrefsManager(context)
}
