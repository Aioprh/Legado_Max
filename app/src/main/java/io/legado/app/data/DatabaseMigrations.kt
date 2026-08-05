package io.legado.app.data

import androidx.room.DeleteColumn
import androidx.room.migration.AutoMigrationSpec
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import io.legado.app.constant.AppConst
import io.legado.app.constant.BookSourceType
import io.legado.app.constant.BookType

object DatabaseMigrations {

    val migrations: Array<Migration> by lazy {
        arrayOf(
            migration_10_11, migration_11_12, migration_12_13, migration_13_14,
            migration_14_15, migration_15_17, migration_17_18, migration_18_19,
            migration_19_20, migration_20_21, migration_21_22, migration_22_23,
            migration_23_24, migration_24_25, migration_25_26, migration_26_27,
            migration_27_28, migration_28_29, migration_29_30, migration_30_31,
            migration_31_32, migration_32_33, migration_33_34, migration_34_35,
            migration_35_36, migration_36_37, migration_37_38, migration_38_39,
            migration_39_40, migration_40_41, migration_41_42, migration_42_43,
            migration_95_96, migration_96_97, migration_97_98, migration_98_99,
            migration_99_100,
            migration_100_101,
            migration_101_102   // 已修正，删除 mainJs 列
        )
    }

    // -------- 原有迁移（10~42）保持不变 --------
    private val migration_10_11 = object : Migration(10, 11) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("DROP TABLE txtTocRules")
            db.execSQL(
                """CREATE TABLE txtTocRules(id INTEGER NOT NULL, 
                    name TEXT NOT NULL, rule TEXT NOT NULL, serialNumber INTEGER NOT NULL, 
                    enable INTEGER NOT NULL, PRIMARY KEY (id))"""
            )
        }
    }

    // ... 此处省略 11~42 的所有迁移，它们与您原有代码完全相同，请保留您的原实现 ...
    // 注意：为节省篇幅，此处不重复列出，但您必须保留全部现有迁移。

    // -------- AutoMigration 规范类（保持不变） --------
    // ... 省略 Migration_54_55, Migration_64_65, Migration_80_81, Migration_83_84, Migration_84_85, Migration_90_91 ...

    // -------- 后续手动迁移（95~101）保持不变 --------
    private val migration_95_96 = object : Migration(95, 96) {
        override fun migrate(db: SupportSQLiteDatabase) {
            // 您的原实现
        }
    }

    private val migration_96_97 = object : Migration(96, 97) {
        override fun migrate(db: SupportSQLiteDatabase) {
            // 您的原实现
        }
    }

    private val migration_97_98 = object : Migration(97, 98) {
        override fun migrate(db: SupportSQLiteDatabase) {
            // 您的原实现
        }
    }

    private val migration_98_99 = object : Migration(98, 99) {
        override fun migrate(db: SupportSQLiteDatabase) {
            // 您的原实现
        }
    }

    private val migration_99_100 = object : Migration(99, 100) {
        override fun migrate(db: SupportSQLiteDatabase) {
            // 您的原实现
        }
    }

    private val migration_100_101 = object : Migration(100, 101) {
        override fun migrate(db: SupportSQLiteDatabase) {
            // 空迁移
        }
    }

    // =======================================================================
    // 修正版迁移：101 → 102
    // 创建缺失表，添加 homepageModules 列，并删除多余的 mainJs 列
    // =======================================================================
    private val migration_101_102 = object : Migration(101, 102) {
        override fun migrate(db: SupportSQLiteDatabase) {
            // 1. 创建 source_recycle_bin 表（如果不存在）
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `source_recycle_bin` (
                    `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    `type` TEXT NOT NULL,
                    `key` TEXT NOT NULL,
                    `name` TEXT NOT NULL,
                    `groupName` TEXT,
                    `payload` TEXT NOT NULL,
                    `deletedAt` INTEGER NOT NULL,
                    `expireAt` INTEGER NOT NULL
                )
                """.trimIndent()
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_source_recycle_bin_type` ON `source_recycle_bin` (`type`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_source_recycle_bin_key` ON `source_recycle_bin` (`key`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_source_recycle_bin_expireAt` ON `source_recycle_bin` (`expireAt`)")

            // 2. 创建 homepage_modules 表（如果不存在）
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `homepage_modules` (
                    `id` TEXT NOT NULL PRIMARY KEY,
                    `sourceUrl` TEXT NOT NULL,
                    `moduleKey` TEXT NOT NULL,
                    `type` TEXT NOT NULL,
                    `title` TEXT NOT NULL,
                    `args` TEXT,
                    `layoutConfig` TEXT,
                    `url` TEXT,
                    `isEnabled` INTEGER NOT NULL DEFAULT 1,
                    `sortOrder` INTEGER NOT NULL DEFAULT 0,
                    `customSetId` TEXT,
                    `isUserCreated` INTEGER NOT NULL DEFAULT 0,
                    `customTitle` TEXT,
                    `customSetTitle` TEXT,
                    `sourceJsonHash` TEXT,
                    `syncedAt` INTEGER NOT NULL DEFAULT 0
                )
                """.trimIndent()
            )

            // 3. 创建 homepage_custom_sets 表（如果不存在）
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `homepage_custom_sets` (
                    `id` TEXT NOT NULL PRIMARY KEY,
                    `name` TEXT NOT NULL,
                    `sortOrder` INTEGER NOT NULL DEFAULT 0
                )
                """.trimIndent()
            )

            // 4. 检查并添加 homepageModules 列
            var cursor = db.query("SELECT name FROM pragma_table_info('book_sources')")
            val columnNames = mutableListOf<String>()
            while (cursor.moveToNext()) {
                columnNames.add(cursor.getString(0))
            }
            cursor.close()
            if (!columnNames.contains("homepageModules")) {
                db.execSQL("ALTER TABLE book_sources ADD COLUMN homepageModules TEXT DEFAULT ''")
            }

            // 5. 如果存在 mainJs 列，则删除它（通过重建表）
            if (columnNames.contains("mainJs")) {
                db.beginTransaction()
                try {
                    // 创建新表，结构与期望一致（不含 mainJs）
                    db.execSQL("""
                        CREATE TABLE book_sources_new (
                            bookSourceUrl TEXT NOT NULL PRIMARY KEY,
                            bookSourceName TEXT NOT NULL,
                            bookSourceGroup TEXT,
                            bookSourceType INTEGER NOT NULL,
                            bookUrlPattern TEXT,
                            customOrder INTEGER NOT NULL DEFAULT 0,
                            enabled INTEGER NOT NULL DEFAULT 1,
                            enabledExplore INTEGER NOT NULL DEFAULT 1,
                            jsLib TEXT,
                            enabledCookieJar INTEGER DEFAULT 0,
                            concurrentRate TEXT,
                            header TEXT,
                            loginUrl TEXT,
                            loginUi TEXT,
                            loginCheckJs TEXT,
                            coverDecodeJs TEXT,
                            bookSourceComment TEXT,
                            variableComment TEXT,
                            lastUpdateTime INTEGER NOT NULL,
                            respondTime INTEGER NOT NULL,
                            weight INTEGER NOT NULL,
                            exploreUrl TEXT,
                            exploreScreen TEXT,
                            ruleExplore TEXT,
                            searchUrl TEXT,
                            ruleSearch TEXT,
                            ruleBookInfo TEXT,
                            ruleToc TEXT,
                            ruleContent TEXT,
                            ruleReview TEXT,
                            eventListener INTEGER NOT NULL DEFAULT 0,
                            customButton INTEGER NOT NULL DEFAULT 0,
                            nextPageLazyLoad INTEGER NOT NULL DEFAULT 0,
                            homepageModules TEXT DEFAULT ''
                        )
                    """.trimIndent())
                    // 复制数据，排除 mainJs
                    val cols = columnNames.filter { it != "mainJs" }.joinToString(", ")
                    db.execSQL("INSERT INTO book_sources_new ($cols) SELECT $cols FROM book_sources")
                    db.execSQL("DROP TABLE book_sources")
                    db.execSQL("ALTER TABLE book_sources_new RENAME TO book_sources")
                    db.execSQL("CREATE INDEX IF NOT EXISTS index_book_sources_bookSourceUrl ON book_sources (bookSourceUrl)")
                    db.setTransactionSuccessful()
                } finally {
                    db.endTransaction()
                }
            }
        }
    }
}