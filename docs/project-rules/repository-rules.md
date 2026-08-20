# 数据层 / Repository 规范

> 基于 2026-08 的实际代码盘点编写。**先讲现状（三种风格混杂），再讲新代码必须遵循的标准。**
> 历史代码不强制一次性重构，但**新增数据访问逻辑一律按第 3 节标准写**。

## 1. 基础设施

- Room，`AppDatabase` 位于 `data/AppDatabase.kt`，全局单例 `appDb`（`data/appDb` 扩展属性）。
- KSP 编译（不是 kapt）。schema 导出到 `app/schemas/`，**每次改表结构必须导出新 schema 并在 `DatabaseMigrations.kt` 登记迁移**。
- DAO 在 `data/dao/`，Entity 在 `data/entities/`。表清单见 `data/README.md`。
- 数据库版本号当前 **v100**，升级必须写 migration，禁止 fallbackToDestructiveMigration。

## 2. 现状（如实记录，别当范本照抄）

| 风格 | 占比 | 典型代码 | 评价 |
|---|---|---|---|
| ViewModel 直连 DAO | 绝大多数 | `BookshelfManageViewModel`、`ReadBookViewModel`、`AudioPlayViewModel` 里全是 `appDb.bookDao.xxx` | 历史遗留。逻辑能用，但 DB 操作和 UI 状态逻辑混在 ViewModel 里，无法单测 |
| Entity 自带 `save()`/`delete()`（Active Record） | 少量 | `data/entities/Book.kt` 的 `fun save()`、`fun delete()` 内部直接碰 `appDb` | **禁止新增**。Entity 必须是纯数据类 |
| 标准 Repository（构造注入 DAO + Gateway 接口 + Entity/Model 映射） | 仅首页模块 | `data/repository/HomepageModulesRepository`（实现 `domain/gateway/HomepageModulesGateway`） | **新代码的唯一范本** |

其他存量 Repository（`BookRepository`、`CoverGalleryRepository`、`DirectLinkUploadRepository`）是"半吊子"：直接 `appDb.xxxDao` 字段初始化、无接口、无构造注入，只是把散落的 DAO 调用收拢了一下。可以存在，但不作为新代码模板。

**依赖注入**：Hilt 插件和依赖已配置（`app/build.gradle`），但全项目仅 1 处 `@Inject`，`di/AppModule.kt` 只提供一个 `AppVersionInfo`。
当前事实上的 DI 方式是：Repository 由调用方 `new`，或 DAO 通过 `appDb` 全局单例直接取。
**规则：新写的 Repository 一律用构造注入 DAO（见第 3 节）。是否补 `@Provides` 模块统一提供，等 Hilt 真正铺开后再做，现在不要在个别类上单独挂 `@Inject` 制造两种注入风格。**

## 3. 新代码标准写法（范本：HomepageModulesRepository）

### 3.1 分层与职责

```
ViewModel/UI
    ↓ 只依赖 Gateway 接口（不 import dao/、entities/ 包）
domain/gateway/XxxGateway.kt        ← interface，定义数据契约
    ↓
data/repository/XxxRepository.kt    ← 实现 Gateway，构造注入 DAO，Entity ↔ Model 转换
    ↓
data/dao/XxxDao.kt                  ← 纯 Room DAO
data/entities/Xxx.kt                ← 纯数据 Entity，禁止任何 appDb/IO 调用
```

- **ViewModel 禁止 import `io.legado.app.data.dao` 和 `io.legado.app.data.entities`。**
  唯一例外是历史遗留代码。Review 看到新代码这么 import 直接打回。
- Entity 不能出 `data/` 包。对外用 `domain/model/` 下的领域模型（或值对象），
  转换函数以私有扩展函数放在 Repository 内（参考 `HomepageModule.toModuleItem()`）。

### 3.2 接口契约（Gateway）先于实现

先定 `XxxGateway` 接口，再写 `XxxRepository`。接口里：

- 查询用 `suspend fun`（一次性）或 `Flow<T>`（持续观察，如书架列表、阅读进度）。
- **禁止**在接口/实现里返回 `LiveData`——本项目 UI 层 LiveData 只是 ViewBinding 时代的既有搭配，
  新模块优先 `Flow` + `collectAsStateWithLifecycle`（Compose）或 `viewModelScope` 内 collect 后 `postValue`（View）。

### 3.3 数据源优先级

涉及"网络 + 数据库"的组合（典型：书源内容）：

1. 需要实时性 → 网络优先，成功后落库，失败回退 DB 并暴露错误给 UI。
2. 允许离线 → 读走 DB（`Flow` 观察），写走网络后同步 DB。
3. 缓存策略（TTL、版本号）写在 Repository 里，**不允许在 ViewModel 里判断"数据旧不旧"**。

### 3.4 事务与批量

- 多次写同一组表 → DAO 层 `@Transaction`，Repository 不手动拼事务。
- 批量插入用 `insertAll`/`upsertAll`，禁止 for 循环单条 insert。

## 4. 迁移历史代码的策略

1. **不做大爆炸重构**。按"谁动谁改"原则：功能迭代碰到的旧 ViewModel，
   把它直连的 DAO 调用抽到 Repository（有现成 Gateway 接口的直接补实现，没有的先定接口）。
2. 优先级：正在高频迭代的模块 > 核心阅读链路 > 稳定不动的功能。
3. `Book.save()` 这类 Active Record 方法：新增调用一律禁止；
   触碰到的旧调用点顺手改成 Repository 方法，改完评估能否删掉 Entity 上的方法。
4. **Hilt 铺开条件**：当 `data/repository/` 下的 Repository 数量 ≥ 5 个且全部构造注入 DAO 时，
   一次性补 `di/` 下的 `@Provides`/`@Binds`（Repository 实现绑 Gateway 接口、DAO 从 `AppDatabase` 提供），
   同时把新建 Activity 的入口从 `@AndroidEntryPoint` 统一。在那之前不逐个加 `@Inject`。

## 5. 反面示例

```kotlin
// ❌ ViewModel 直连 DAO（新代码禁止）
fun deleteBook(books: List<Book>) {
    execute {
        appDb.bookDao.delete(*books.toTypedArray())   // ViewModel 里直接操作 DB
    }
}

// ❌ Entity 自己操作数据库（禁止新增）
fun save() {
    if (appDb.bookDao.has(bookUrl)) appDb.bookDao.update(this) else appDb.bookDao.insert(this)
}

// ✅ 标准写法
// domain/gateway/
interface BookshelfGateway {
    fun observeBooks(): Flow<List<BookItem>>
    suspend fun removeBooks(bookUrls: List<String>)
}

// data/repository/
class BookshelfRepository(
    private val bookDao: BookDao,
) : BookshelfGateway {
    override fun observeBooks(): Flow<List<BookItem>> =
        bookDao.observeAll().map { list -> list.map { it.toBookItem() } }

    override suspend fun removeBooks(bookUrls: List<String>) = bookDao.deleteByUrls(bookUrls)

    private fun Book.toBookItem() = BookItem(/* ... */)
}
```