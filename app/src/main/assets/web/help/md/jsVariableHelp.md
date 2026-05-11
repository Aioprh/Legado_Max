# 书源规则 JS 变量存储机制详解

## 快速理解

想象一下这个场景：

> 你在一家**图书馆**（Legado App）工作。每当你需要处理一本书时：
> 1. **你不能在墙上随便写字**（不能用 `var`/`let` 声明全局变量）
> 2. 图书馆给你发了一张**工作便签**（临时 JS 作用域），用完就扔
> 3. 但有些信息需要**长期保存**，比如「这本书看到第几章了」——这时候你要用图书馆的**档案柜**（`put`/`get` 系统）
> 4. 而且档案柜还分了**不同层级**：书的层级、章节的层级、书源的层级

---

## 1️⃣ 核心问题：为什么不能用 var/let 直接声明？

### 根本原因：每次 JS 执行都是"一次性"的

看看 `AnalyzeRule.kt` 中的 `evalJS` 方法：

```kotlin
fun evalJS(jsStr: String, result: Any? = null): Any? {
    val bindings = buildScriptBindings { bindings ->
        bindings["java"] = this
        bindings["cookie"] = CookieStore
        bindings["cache"] = CacheManager
        bindings["source"] = source
        bindings["book"] = book
        // ... 其他绑定
    }

    // 每次调用都创建一个新的作用域！
    val scope = if (topScope == null) {
        RhinoScriptEngine.getRuntimeScope(bindings)  // ← 全新作用域
    } else {
        bindings.apply { prototype = topScope }
    }

    return RhinoScriptEngine.eval(jsStr, scope, coroutineContext)
    // ← 执行完后，scope 就被丢弃了！
}
```

**关键点理解**：
- 每次 `evalJS()` 都**创建一个新的 `ScriptBindings` 对象**（新的作用域）
- JS 代码执行完，这个作用域对象就**没人引用，等待 GC 回收**
- 你在 JS 中用 `var x = 1` 声明变量，这个变量是**存在这个临时作用域上的**
- 下一次 `evalJS()` 调用又是一个**全新的作用域**，之前的 `var x` 就丢了

### 举个例子

```javascript
// 规则1：声明变量
var myVar = "你好";  // 存在临时作用域A上

// 规则2：使用变量
myVar  // ← 报错！作用域B上根本没有 myVar
```

这就好比每次给你一张**新的白纸**，你在第一张纸上写了东西，然后纸被收走了。下次再给你一张新白纸，上次写的内容自然就找不到了。

---

## 2️⃣ 那共享作用域（SharedJsScope）是怎么回事？

你可能会说：「等等，代码里不是有 `topScopeRef` 和 `SharedJsScope` 吗？」

没错！确实有一个**共享作用域**机制，但它的设计是给你**加载工具函数库**用的，而不是给你存变量用的。

看看 `SharedJsScope.kt` 的关键代码：

```kotlin
fun getScope(jsLib: String?, coroutineContext: CoroutineContext?): Scriptable? {
    val scope = RhinoScriptEngine.getRuntimeScope(ScriptBindings())

    // 加载 jsLib 中定义的全局函数（工具函数）
    resolveJsLibString(jsLib)?.let {
        RhinoScriptEngine.eval(it, scope, coroutineContext)
    }

    // ★ 关键！阻止扩展 —— 不允许新增属性！
    if (scope is ScriptableObject) {
        scope.preventExtensions()
    }

    return scope
}
```

**关键点理解**：
- `SharedJsScope` 的作用是加载**书源的 `jsLib`**（公共 JS 库代码）
- 加载完后立刻调用了 **`scope.preventExtensions()`** — 这意味着你不能在这个共享作用域上新增任何变量！
- 为啥这么做？为了**安全**和**隔离**，防止不同规则之间互相污染全局命名空间
- 所以 `jsLib` 是用来定义**函数**（工具方法）的，不是给你存变量的

---

## 3️⃣ 那存变量应该用啥？—— `put()` / `get()` 系统

既然不能直接用 JS 变量，Legado 提供了一套完善的**存储 API**。

### 存储层级

看一下 `AnalyzeRule.kt` 中的实现：

```kotlin
fun put(key: String, value: String): String {
    // 按优先级从高到低选择存储位置
    chapter?.putVariable(key, value)       // ① 章节级
        ?: book?.putVariable(key, value)   // ② 书籍级
        ?: ruleData?.putVariable(key, value) // ③ 规则数据级
        ?: source?.put(key, value)        // ④ 书源级
    return value
}

fun get(key: String): String {
    when (key) {
        "bookName" -> book?.let { return it.name }  // 特殊：书名
        "title" -> chapter?.let { return it.title }  // 特殊：章节标题
    }
    return chapter?.getVariable(key)?.takeIf { it.isNotEmpty() }      // ①
        ?: book?.getVariable(key)?.takeIf { it.isNotEmpty() }        // ②
        ?: ruleData?.getVariable(key)?.takeIf { it.isNotEmpty() }    // ③
        ?: source?.get(key)?.takeIf { it.isNotEmpty() }              // ④
        ?: ""
}
```

### 四个存储层级

| 层级 | 存储对象 | 生命周期 | 典型用途 |
|------|---------|---------|---------|
| ① **章节级** | `BookChapter` | 当前章节处理期间 | 当前章节的临时数据 |
| ② **书籍级** | `Book` (BaseBook) | 整本书处理期间，持久化 | 书籍相关信息 |
| ③ **规则数据级** | `RuleData` | 单次规则解析期间 | 临时中间数据 |
| ④ **书源级** | `BaseSource` | 书源级别，通过 `CacheManager` 持久化 | 跨书籍的全局数据 |

### 存储实现细节

看 `RuleDataInterface.kt`：

```kotlin
interface RuleDataInterface {
    val variableMap: HashMap<String, String>

    fun putVariable(key: String, value: String?): Boolean {
        return when {
            value == null -> {
                variableMap.remove(key)    // null = 删除
                putBigVariable(key, null)
                keyExist
            }
            value.length < 10000 -> {
                putBigVariable(key, null)  // 小数据，存内存
                variableMap[key] = value
                true
            }
            else -> {
                variableMap.remove(key)     // 大数据（>1万字符）
                putBigVariable(key, value)  // 存文件！
                keyExist
            }
        }
    }
}
```

**关键点理解**：
- **小数据（<10000字符）**：直接存在内存的 `HashMap` 中
- **大数据（>=10000字符）**：通过 `RuleBigDataHelp` 写入**文件系统**
- 数据会随 `Book`/`BookChapter` 持久化到**数据库**
- 所以即使 App 重启，你的变量数据还在！

---

## 4️⃣ 数据流全流程

```
                        JS 规则代码
                            │
                            ▼
                    ┌────────────────┐
                    │   evalJS()     │
                    │  (临时作用域)   │
                    └────────────────┘
                            │
              ┌─────────────┼─────────────┐
              ▼             ▼             ▼
         put("k","v")   var x = 1     get("k")
              │             │             │
              ▼             ▼             ▼
       ┌──────────┐   ┌──────────┐   ┌──────────┐
       │ 存储系统  │   │ 临时变量  │   │ 存储系统  │
       │ 持久化   │   │ 用完就丢  │   │ 查询返回  │
       └──────────┘   └──────────┘   └──────────┘
              │
     ┌────────┼────────┐
     ▼        ▼        ▼
  章节级    书籍级   书源级
  HashMap   HashMap  CacheManager
     │        │           │
  BookChapter Book     持久化存储
```

---

## 5️⃣ 为什么这么设计？

从架构角度看，这种设计有几个深思熟虑的考虑：

### 1. 跨请求持久化

```
搜索 → 获得书籍列表 → 点击书籍 → 获取详情 → 获取目录 → 获取正文
  ↑        ↑            ↑          ↑         ↑          ↑
 都可能是不同的 HTTP 请求，每次都会创建新的 AnalyzeRule 实例
```

变量数据需要**跨多个请求、多个 AnalyzeRule 实例**存活。

### 2. 安全性

```kotlin
scope.preventExtensions()
```

防止恶意/有问题的规则代码污染全局命名空间，造成变量冲突。

### 3. 内存管理

每次 `evalJS` 都创建新作用域，执行完后 GC 可以回收。如果所有 JS 变量都长期存活，Android 设备的内存很快就撑爆了。

### 4. 作用域隔离

- **章节级变量**：只在本章节有效（比如当前章节的图片列表）
- **书籍级变量**：整本书共享（比如累计阅读字数）
- **书源级变量**：所有使用同个书源的书籍共享（比如登录 token）

---

## 6️⃣ 实践对比

| 操作 | 普通 JS | Legado 规则 JS |
|-----|---------|---------------|
| 存字符串 | `let x = "hello"` | `put("x", "hello")` |
| 读字符串 | `x` | `get("x")` |
| 存数字 | `let n = 42` | `put("n", "42")` |
| 读数字 | `n` | `parseInt(get("n"))` |
| 存对象 | `let o = {a:1}` | `put("o", JSON.stringify({a:1}))` |
| 读对象 | `o.a` | `JSON.parse(get("o")).a` |
| 删变量 | `delete x` | `put("x", null)` |

**为什么这么麻烦？**

因为规则引擎的设计哲学是：**每个 JS 代码片段是一个"函数式"的纯计算单元**，输入 → 处理 → 输出。状态的持久化交给专门的存储层管理，而不是依赖 JS 作用域。

---

## 总结

1. **不能用 `var`/`let` 的原因**：每次 `evalJS()` 都是新的临时作用域，执行完就销毁
2. **共享作用域**（`SharedJsScope`）：用来加载 `jsLib` 工具库，且调用了 `preventExtensions()` 禁止新增变量
3. **正确的存变量方式**：使用 `put(key, value)` / `get(key)` API
4. **存储层级**：章节级 → 书籍级 → 规则数据级 → 书源级，按优先级自动选择
5. **设计目的**：持久化、安全隔离、内存管理、作用域隔离

虽然多了一步 `put`/`get` 的操作，但这换来了数据的**持久化存储**（App 重启还在）、**跨请求共享**、以及**清晰的变量作用域**，对于书源规则这种需要多次网络请求、跨页面操作的场景来说，是更合适的设计。
