package io.legado.app.ui.main.explore

import io.legado.app.data.appDb
import io.legado.app.data.entities.BookSourcePart

/** 发现套件书源选择器使用的可用发现书源列表。 */
fun loadExploreSources(): List<BookSourcePart> =
    appDb.bookSourceDao.allEnabledPart.filter { it.enabledExplore && it.hasExploreUrl }
