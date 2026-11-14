package com.botwmap.app.core.di

import com.botwmap.app.core.datastore.UserPreferencesDataStore
import com.botwmap.app.core.network.HttpClientFactory
import com.botwmap.app.data.local.AssetDataSource
import com.botwmap.app.data.local.HdTileRepository
import com.botwmap.app.data.local.IconRepository
import com.botwmap.app.data.local.TileRepository
import com.botwmap.app.data.repository.MapRepository
import com.botwmap.app.ui.map.MapViewModel
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

/** 基础设施：DataStore、Ktor、assets 数据源、瓦片/图标缓存 */
val appModule = module {
    single { UserPreferencesDataStore(androidContext()) }
    single { HttpClientFactory.create() }
    single { AssetDataSource(androidContext()) }
    single { TileRepository(androidContext()) }
    single { HdTileRepository(androidContext()) }
    single { IconRepository(androidContext()) }
}

/** 数据层 */
val dataModule = module {
    single { MapRepository(get(), get()) }
}

/** ViewModel */
val viewModelModule = module {
    viewModelOf(::MapViewModel)
}

val appModules = listOf(appModule, dataModule, viewModelModule)
