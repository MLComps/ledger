package com.ledger.app.di

import android.content.Context
import com.ledger.app.data.DataStoreRepository
import com.ledger.app.data.DefaultDataStoreRepository
import com.ledger.app.data.DefaultDownloadRepository
import com.ledger.app.data.DownloadRepository
import com.ledger.app.db.LedgerDao
import com.ledger.app.db.LedgerDatabase
import com.ledger.app.db.LedgerRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
internal object AppModule {

  @Provides
  @Singleton
  fun provideDataStoreRepository(
    @ApplicationContext context: Context,
  ): DataStoreRepository {
    return DefaultDataStoreRepository(context)
  }

  @Provides
  @Singleton
  fun provideDownloadRepository(
    @ApplicationContext context: Context,
  ): DownloadRepository {
    return DefaultDownloadRepository(context)
  }

  @Provides
  @Singleton
  fun provideLedgerDatabase(
    @ApplicationContext context: Context,
  ): LedgerDatabase {
    return LedgerDatabase.getInstance(context)
  }

  @Provides
  @Singleton
  fun provideLedgerDao(database: LedgerDatabase): LedgerDao {
    return database.ledgerDao()
  }

  @Provides
  @Singleton
  fun provideLedgerRepository(dao: LedgerDao): LedgerRepository {
    return LedgerRepository(dao)
  }
}
