package com.example.data

import android.content.Context
import com.example.data.db.AppDatabase
import com.example.data.repository.MusicRepository

/**
 * Single place that constructs shared dependencies, replacing the ad hoc
 * `MusicRepository(context, AppDatabase.getDatabase(context).musicDao())` that used to be
 * duplicated in both MusicViewModel and MusicService. Deliberately not a DI framework (Hilt/Koin)
 * - this app has too few dependencies to justify the extra build step and indirection for a
 * solo-maintained project; a lazily-memoized singleton is the whole of what's needed here.
 */
object AppContainer {
    @Volatile
    private var repository: MusicRepository? = null

    fun getRepository(context: Context): MusicRepository {
        return repository ?: synchronized(this) {
            repository ?: MusicRepository(
                context.applicationContext,
                AppDatabase.getDatabase(context).musicDao()
            ).also { repository = it }
        }
    }
}
