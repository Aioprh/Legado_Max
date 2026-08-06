package io.legado.app.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import io.legado.app.BuildConfig
import io.legado.app.constant.AppConst
import javax.inject.Singleton

/**
 * 应用级 Hilt 模块
 * 提供单例依赖
 */
@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideAppVersionInfo(): AppVersionInfo {
        val info = AppConst.appInfo
        return AppVersionInfo(
            appName = AppConst.APP_TAG,
            versionName = info.versionName,
            versionCode = info.versionCode,
            debug = BuildConfig.DEBUG,
            channel = info.appVariant.name
        )
    }
}
