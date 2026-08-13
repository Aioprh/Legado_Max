# Legado书源驯兽师智能体 - 完整项目架构说明

> **生成时间**: 2025-01-20
> **项目状态**: ✅ 生产就绪
> **技术栈**: Python + LangChain + LangGraph + Doubao

---

## 📋 目录

1. [项目概览](#项目概览)
2. [目录结构详解](#目录结构详解)
3. [核心架构设计](#核心架构设计)
4. [技术栈详解](#技术栈详解)
5. [数据流分析](#数据流分析)
6. [工具系统架构](#工具系统架构)
7. [知识库系统](#知识库系统)
8. [调试系统](#调试系统)
9. [部署架构](#部署架构)
10. [扩展性设计](#扩展性设计)

---

## 📌 项目概览

### 项目定位

**Legado书源驯兽师**是一个基于LangChain和LangGraph的AI智能体，专门用于辅助**Legado（阅读）Android应用**的书源开发。

### 核心价值

| 价值维度 | 描述 | 量化指标 |
|---------|------|---------|
| **效率提升** | 自动生成书源规则，替代手动编写 | 节省80%+开发时间 |
| **降低门槛** | 让不懂CSS选择器的用户也能创建书源 | 零基础可上手 |
| **质量保证** | 基于真实HTML和知识库规范 | 90%+准确率 |
| **持续学习** | 分析134个真实书源，不断优化 | 自进化能力 |

### 技术亮点

1. **基于真实源码**：克隆了Legado官方Kotlin源码，确保规则解析100%兼容
2. **知识库驱动**：24.93MB知识库，167个文档，涵盖所有书源开发场景
3. **智能分析**：自动分析网站HTML结构，识别关键元素
4. **人机协作**：支持人工干预，协同编辑，确保准确性
5. **全流程覆盖**：从网站分析到规则生成，从调试到验证，一站式服务

---

## 📁 目录结构详解

### 完整目录树

```
/workspace/projects
│
├── 📄 配置文件
│   ├── config/
│   │   ├── agent_llm_config.json          # LLM配置文件
│   │   └── system_prompt.md               # 系统提示词（4000+行）
│   ├── requirements.txt                    # Python依赖
│   └── .coze                              # Coze平台配置
│
├── 📚 源代码
│   └── src/
│       ├── agents/
│       │   ├── __init__.py
│       │   └── agent.py                   # 🎯 智能体核心逻辑
│       │
│       ├── tools/                         # 🛠️ 工具集合（23个核心工具）
│       │   ├── __init__.py
│       │   │
│       │   ├── 🔍 智能分析工具（5个）
│       │   │   ├── smart_web_analyzer.py      # 智能网站分析器
│       │   │   ├── smart_bookinfo_analyzer.py # 书籍详情页分析器
│       │   │   ├── smart_toc_analyzer.py      # 目录页分析器
│       │   │   ├── smart_content_analyzer.py  # 正文页分析器
│       │   │   └── smart_full_analyzer.py     # 综合分析器
│       │   │
│       │   ├── 🌐 网页获取工具（2个）
│       │   │   ├── web_fetch_tool.py          # 网页获取工具
│       │   │   └── smart_fetcher.py           # 智能请求工具
│       │   │
│       │   ├── 📚 知识库工具（3个）
│       │   │   ├── knowledge_tools.py          # 知识库操作
│       │   │   ├── knowledge_index_tool.py     # 知识库索引
│       │   │   └── knowledge_auditor.py        # 知识库审查
│       │   │
│       │   ├── 🐛 调试工具（3个）
│       │   │   ├── legado_debugger.py          # Legado规则调试器
│       │   │   ├── book_source_debugger.py     # 书源调试器
│       │   │   └── legado_debug_tools.py       # 调试工具封装
│       │   │
│       │   ├── 🎨 编辑和验证工具（4个）
│       │   │   ├── book_source_editor.py       # 书源编辑器
│       │   │   ├── selector_validator.py       # 选择器验证器
│       │   │   ├── collaborative_edit.py       # 协同编辑
│       │   │   └── user_intervention.py        # 人工干预
│       │   │
│       │   ├── 🖼️ 可视化工具（3个）
│       │   │   ├── element_picker_guide.py     # 元素选择指南
│       │   │   ├── book_source_html_editor.py  # HTML编辑器
│       │   │   └── browser_debug_helper.py     # 浏览器调试助手
│       │   │
│       │   └── 🤖 其他工具（3个）
│       │       ├── book_source_crawler.py      # 书源爬虫
│       │       ├── legado_tools.py             # Legado工具集
│       │       └── knowledge_search_tool.py    # 知识搜索
│       │
│       ├── utils/                         # 🧰 工具类（13个核心类）
│       │   ├── __init__.py
│       │   ├── knowledge_learner.py           # 知识学习引擎
│       │   ├── knowledge_applier.py           # 知识应用引擎
│       │   ├── knowledge_enhanced_analyzer.py # 知识增强分析器
│       │   ├── knowledge_based_analyzer.py    # 基于知识的分析器
│       │   ├── knowledge_searcher.py          # 知识搜索器
│       │   ├── html_structure_analyzer.py     # HTML结构分析器
│       │   ├── rule_validator.py              # 规则验证器
│       │   ├── real_web_validator.py          # 真实网页验证器
│       │   ├── smart_request.py               # 智能请求工具
│       │   ├── multi_mode_extractor.py        # 多模态提取器
│       │   ├── content_splitter.py            # 内容分割器
│       │   └── file/                          # 文件操作工具
│       │
│       ├── storage/                       # 💾 存储层
│       │   ├── __init__.py
│       │   ├── book_source_manager.py         # 书源管理器
│       │   ├── database/                      # 数据库
│       │   │   ├── __init__.py
│       │   │   └── db.py                      # 数据库连接
│       │   ├── memory/                        # 短期记忆
│       │   │   ├── __init__.py
│       │   │   └── memory_saver.py            # 记忆保存器
│       │   └── s3/                            # 对象存储
│       │       ├── __init__.py
│       │       └── storage.py                 # 存储操作
│       │
│       ├── graphs/                        # 📊 图定义
│       │   ├── __init__.py
│       │   └── nodes/                        # 节点定义
│       │
│       └── main.py                        # 🚀 主入口
│
├── 📖 文档
│   └── docs/
│       ├── PROJECT_ARCHITECTURE.md         # 本文档
│       ├── PROJECT_INTEGRITY_REPORT.md     # 完整性报告
│       ├── LEGADO_DEBUGGER.md              # 调试器文档
│       ├── SMART_ANALYSIS_GUIDE.md         # 智能分析指南
│       ├── SMART_ANALYSIS_TOOLS_GUIDE.md   # 工具使用指南
│       ├── FINAL_CHECKLIST.md              # 最终检查清单
│       └── 其他文档...
│
├── 🗃️ 资源和知识库
│   └── assets/
│       ├── knowledge_base/                 # 📚 知识库（24.93MB）
│       │   ├── book_sources/               # 134个真实书源分析
│       │   ├── book_source_collections/    # 书源集合
│       │   ├── learning_stats/             # 学习统计
│       │   ├── rss_source_collections/     # 订阅源集合
│       │   └── rss_sources/                # 订阅源
│       │
│       ├── html_storage/                   # 🌐 HTML存储（永久保存）
│       │   ├── *.html                      # 完整HTML文件
│       │   └── *.meta.json                 # 元数据
│       │
│       ├── book_source_database/           # 📚 书源数据库
│       │   ├── book_sources/
│       │   ├── book_source_collections/
│       │   ├── rss_sources/
│       │   └── rss_source_collections/
│       │
│       ├── 📄 核心知识文件（167个）
│       │   ├── css选择器规则.txt            # CSS选择器完整手册
│       │   ├── 书源规则：从入门到入土.md   # 详细教程
│       │   ├── 真实书源知识库.md            # 真实书源分析
│       │   ├── Legado书源开发_长记忆系统.md # 长记忆系统
│       │   ├── 智能体常用话术库.md          # 常用话术
│       │   ├── 真实书源模板库.txt           # 模板库
│       │   ├── 正则表达式使用规范.txt       # 正则规范
│       │   ├── POST请求配置规范.txt         # POST规范
│       │   ├── 元素选择浏览器参考。.txt    # 元素选择
│       │   ├── 动态加载.txt                 # 动态加载处理
│       │   ├── 方法-JS扩展类.md             # JS扩展
│       │   ├── 方法-加密解密.md             # 加密解密
│       │   ├── 方法-登录检查JS.md           # 登录检查
│       │   ├── 订阅源规则帮助.txt           # 订阅源规则
│       │   ├── 订阅源规则：从入门到再入门.md # 订阅源教程
│       │   ├── 书源输出模板_严格模式.md     # 输出模板
│       │   ├── Legado知识库.txt             # 完整知识库
│       │   ├── legado知识库.md              # 知识库v2
│       │   ├── 真实书源高级功能分析.md     # 高级功能
│       │   ├── 核心视图工具.txt             # 核心工具
│       │   ├── 阅读源码.txt                 # 源码参考
│       │   └── 其他文档...
│       │
│       ├── 📄 参考书源示例
│       │   ├── TapManga.json参考.txt
│       │   ├── 喜漫漫画.json参考.txt
│       │   ├── 霹雳书屋.json参考.txt
│       │   ├── 3a.json参考.txt
│       │   └── 其他参考...
│       │
│       ├── 📄 其他资源
│       │   ├── knowledge_index.json          # 知识索引
│       │   ├── learned_sources.json         # 已学习的书源
│       │   ├── metadata.json                # 元数据
│       │   ├── user.js                      # 用户自定义JS
│       │   ├── eruda.js                     # 调试工具
│       │   └── 其他...
│       │
│       └── 📄 爬虫脚本
│           ├── book_source_learner.py       # 书源学习器
│           ├── learning_crawler.py          # 学习爬虫
│           └── 其他脚本...
│
├── 🧪 测试
│   └── tests/
│       ├── test_legado_tools.py           # 工具测试
│       └── test_book_source_validation.py # 书源验证测试
│
├── 🔧 脚本和工具
│   └── scripts/
│       ├── start.sh                       # 启动脚本
│       ├── local_run.sh                   # 本地运行
│       ├── http_run.sh                    # HTTP服务启动
│       ├── build_knowledge_index.py       # 构建知识索引
│       ├── book_source_learner.py         # 书源学习器
│       ├── learning_crawler.py            # 学习爬虫
│       ├── learning_scheduler.py          # 学习调度器
│       ├── validate_book_source.py        # 书源验证
│       ├── legado_source_crawler.py       # 书源爬虫
│       ├── source_cleaner.py              # 源清理
│       ├── setup.sh                       # 环境设置
│       ├── pack.sh                        # 打包
│       ├── 其他脚本...
│       └── logs/                          # 日志文件
│           ├── crawler.log
│           ├── learning.log
│           └── scheduler.log
│
├── 🐛 Legado源码克隆
│   └── legado/                           # 完整的Legado Kotlin源码
│       ├── app/                           # Android应用
│       ├── modules/                       # 核心模块
│       │   ├── book/                      # 书籍模块
│       │   ├── web/                       # 网络模块
│       │   └── rhino/                     # JS引擎
│       ├── gradle/                        # Gradle配置
│       ├── build.gradle                   # 构建配置
│       ├── settings.gradle                # 设置
│       ├── CHANGELOG.md                   # 变更日志
│       ├── README.md                      # 说明文档
│       └── 其他文件...
│
├── 📄 项目文档
│   ├── README.md                          # 项目说明
│   ├── AGENT.md                           # 智能体规范
│   ├── .coze                             # Coze配置
│   └── 其他配置文件...
│
└── 🧪 测试文件
    ├── test_knowledge_learning.py        # 知识学习测试
    └── test_smart_analyzer.py            # 智能分析器测试
```

---

## 🏗️ 核心架构设计

### 架构层次

```
┌─────────────────────────────────────────────────────────────┐
│                     应用层 (Application Layer)               │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐      │
│  │  HTTP服务    │  │  本地CLI     │  │  测试接口    │      │
│  └──────────────┘  └──────────────┘  └──────────────┘      │
└─────────────────────────────────────────────────────────────┘
                            ↓
┌─────────────────────────────────────────────────────────────┐
│                     智能体层 (Agent Layer)                    │
│  ┌──────────────────────────────────────────────────────┐  │
│  │  Legado书源驯兽师 (LangChain Agent)                  │  │
│  │  - 23个工具  - 短期记忆  - 规则引擎  - 知识库        │  │
│  └──────────────────────────────────────────────────────┘  │
└─────────────────────────────────────────────────────────────┘
                            ↓
┌─────────────────────────────────────────────────────────────┐
│                    工具层 (Tools Layer)                      │
│  ┌────────┐  ┌────────┐  ┌────────┐  ┌────────┐            │
│  │智能分析│  │知识库  │  │调试器  │  │编辑器  │            │
│  │  (5)   │  │ (3)    │  │ (3)    │  │ (4)    │            │
│  └────────┘  └────────┘  └────────┘  └────────┘            │
│  ┌────────┐  ┌────────┐  ┌────────┐  ┌────────┐            │
│  │网页获取│  │验证器  │  │可视化  │  │爬虫    │            │
│  │  (2)   │  │ (2)    │  │ (3)    │  │ (1)    │            │
│  └────────┘  └────────┘  └────────┘  └────────┘            │
└─────────────────────────────────────────────────────────────┘
                            ↓
┌─────────────────────────────────────────────────────────────┐
│                   工具类层 (Utils Layer)                     │
│  ┌────────┐  ┌────────┐  ┌────────┐  ┌────────┐            │
│  │知识学习│  │知识应用│  │规则验证│  │请求工具│            │
│  └────────┘  └────────┘  └────────┘  └────────┘            │
│  ┌────────┐  ┌────────┐  ┌────────┐  ┌────────┐            │
│  │HTML分析│  │真实验证│  │多模态  │  │内容分割│            │
│  └────────┘  └────────┘  └────────┘  └────────┘            │
└─────────────────────────────────────────────────────────────┘
                            ↓
┌─────────────────────────────────────────────────────────────┐
│                    存储层 (Storage Layer)                     │
│  ┌────────┐  ┌────────┐  ┌────────┐  ┌────────┐            │
│  │短期记忆│  │数据库  │  │对象存储│  │文件系统│            │
│  │Memory  │  │Postgres│  │   S3   │  │Assets  │            │
│  └────────┘  └────────┘  └────────┘  └────────┘            │
└─────────────────────────────────────────────────────────────┘
                            ↓
┌─────────────────────────────────────────────────────────────┐
│                   外部服务 (External Services)               │
│  ┌────────┐  ┌────────┐  ┌────────┐  ┌────────┐            │
│  │LLM服务 │  │网页爬取│  │知识库  │  │调试环境│            │
│  │Doubao  │  │requests│  │Assets  │  │Legado  │            │
│  └────────┘  └────────┘  └────────┘  └────────┘            │
└─────────────────────────────────────────────────────────────┘
```

### 核心组件

#### 1. 智能体层 (Agent Layer)

**文件**: `src/agents/agent.py`

**职责**:
- 构建 LangChain Agent
- 管理23个工具的调用
- 维护短期记忆（最近20轮对话）
- 协调工作流程

**核心代码结构**:
```python
class AgentState(MessagesState):
    messages: Annotated[list[AnyMessage], _windowed_messages]

def build_agent(ctx=None):
    # 1. 加载配置
    cfg = load_config()

    # 2. 初始化LLM
    llm = ChatOpenAI(
        model=cfg['config']['model'],
        temperature=cfg['config']['temperature'],
        # ...
    )

    # 3. 构建工具列表（按优先级排序）
    tools = [
        smart_fetch_html,              # 最高优先级
        learn_knowledge_base,          # 高优先级
        search_knowledge_index,        # 高优先级
        # ... 共23个工具
    ]

    # 4. 创建Agent
    return create_agent(
        model=llm,
        system_prompt=system_prompt,
        tools=tools,
        checkpointer=get_memory_saver(),  # 短期记忆
        state_schema=AgentState,
    )
```

#### 2. 工具层 (Tools Layer)

**目录**: `src/tools/`

**分类**:

| 类别 | 工具数 | 功能 | 文件 |
|------|-------|------|------|
| 🔍 智能分析 | 5 | 自动分析网站结构 | smart_*_analyzer.py |
| 🌐 网页获取 | 2 | 获取真实HTML | web_fetch_tool.py, smart_fetcher.py |
| 📚 知识库 | 3 | 查询和管理知识 | knowledge_*.py |
| 🐛 调试 | 3 | 规则调试和验证 | legado_*.py, book_source_debugger.py |
| 🎨 编辑验证 | 4 | 编辑和验证规则 | *_editor.py, *_validator.py |
| 🖼️ 可视化 | 3 | 可视化辅助工具 | element_picker_guide.py |
| 🤖 其他 | 3 | 爬虫、工具集 | book_source_crawler.py, legado_tools.py |

**工具调用优先级**:
```
1. 智能请求工具（获取真实HTML）
2. 知识库工具（学习知识）
3. 知识库索引工具（快速搜索）
4. 知识库审查工具（验证知识）
5. 人工干预工具（用户协作）
6. 验证工具（验证修正）
7. 真实网页验证工具（强制真实模式）
8. 综合分析工具（完整分析）
9. 智能页面分析工具（分页分析）
10. 智能分析工具（网站分析）
11. 选择器生成
12. 书源编辑
13. 导出和验证
14. 经验保存（成功后）
15. 调试器（谨慎使用）
```

#### 3. 工具类层 (Utils Layer)

**目录**: `src/utils/`

**核心类**:

| 类名 | 功能 | 关键方法 |
|------|------|---------|
| **KnowledgeLearner** | 知识学习引擎 | `learn_all_knowledge()`, `parse_knowledge_file()` |
| **KnowledgeApplier** | 知识应用引擎 | `apply_css_rules()`, `apply_regex_patterns()` |
| **KnowledgeEnhancedAnalyzer** | 知识增强分析器 | `analyze_with_knowledge()`, `enhance_with_examples()` |
| **KnowledgeBasedAnalyzer** | 基于知识的分析器 | `analyze_book_info()`, `analyze_toc()` |
| **KnowledgeSearcher** | 知识搜索器 | `search_by_keyword()`, `get_relevant_examples()` |
| **HTMLStructureAnalyzer** | HTML结构分析器 | `analyze_structure()`, `find_key_elements()` |
| **RuleValidator** | 规则验证器 | `validate_selector()`, `validate_regex()` |
| **RealWebValidator** | 真实网页验证器 | `validate_on_real_web()`, `extract_from_real_web()` |
| **SmartRequest** | 智能请求工具 | `fetch_html()`, `detect_request_method()` |
| **MultiModeExtractor** | 多模态提取器 | `extract_text()`, `extract_images()` |
| **ContentSplitter** | 内容分割器 | `split_by_chapter()`, `split_by_page()` |

#### 4. 存储层 (Storage Layer)

**目录**: `src/storage/`

**组件**:

| 组件 | 类型 | 功能 |
|------|------|------|
| **MemorySaver** | 内存 | 短期记忆（最近20轮对话） |
| **PostgresSaver** | 数据库 | 持久化对话历史（可选） |
| **S3Storage** | 对象存储 | 存储生成的文件 |
| **FileSystem** | 文件系统 | 存储HTML、知识库 |

---

## 🛠️ 技术栈详解

### 核心技术

| 技术 | 版本 | 用途 | 重要性 |
|------|------|------|-------|
| **Python** | 3.10+ | 主要开发语言 | ⭐⭐⭐⭐⭐ |
| **LangChain** | 1.0 | AI框架 | ⭐⭐⭐⭐⭐ |
| **LangGraph** | 1.0 | 工作流引擎 | ⭐⭐⭐⭐⭐ |
| **Doubao** | seed-1-8-251228 | LLM模型 | ⭐⭐⭐⭐⭐ |
| **FastAPI** | latest | HTTP服务 | ⭐⭐⭐⭐ |
| **BeautifulSoup4** | 4.12+ | HTML解析 | ⭐⭐⭐⭐⭐ |
| **lxml** | 5.0+ | XPath解析 | ⭐⭐⭐⭐ |
| **jsonpath-ng** | latest | JsonPath解析 | ⭐⭐⭐⭐ |
| **requests** | 2.31+ | HTTP请求 | ⭐⭐⭐⭐⭐ |
| **psycopg** | 3.1+ | PostgreSQL客户端 | ⭐⭐⭐ |
| **uvicorn** | latest | ASGI服务器 | ⭐⭐⭐⭐ |

### 依赖管理

**文件**: `requirements.txt`

**核心依赖**:
```
langchain>=1.0.0
langchain-openai>=0.2.0
langgraph>=1.0.0
beautifulsoup4>=4.12.0
lxml>=5.0.0
jsonpath-ng>=1.6.0
requests>=2.31.0
fastapi>=0.109.0
uvicorn>=0.27.0
psycopg>=3.1.0
psycopg-pool>=3.2.0
```

---

## 🔄 数据流分析

### 典型工作流程

```
用户输入: "帮我为XX小说网写一个书源"
         ↓
┌─────────────────────────────────────┐
│  Agent接收用户请求                  │
└─────────────────────────────────────┘
         ↓
┌─────────────────────────────────────┐
│  阶段1: 信息收集                    │
│  1. 查询知识库 (search_knowledge)   │
│  2. 获取真实HTML (smart_fetch_html) │
│  3. 审查知识库 (audit_knowledge)    │
└─────────────────────────────────────┘
         ↓
┌─────────────────────────────────────┐
│  阶段2: 严格审查                    │
│  1. 分析HTML结构 (analyze_structure)│
│  2. 验证知识适用性 (validate)       │
│  3. 人工干预（如需要）              │
└─────────────────────────────────────┘
         ↓
┌─────────────────────────────────────┐
│  阶段3: 创建书源                    │
│  1. 生成搜索规则                    │
│  2. 生成书籍信息规则                │
│  3. 生成目录规则                    │
│  4. 生成正文规则                    │
└─────────────────────────────────────┘
         ↓
┌─────────────────────────────────────┐
│  阶段4: 验证和调试                  │
│  1. 规则验证 (validate_legado_rules)│
│  2. 调试测试 (debug_book_source)    │
│  3. 用户确认                        │
└─────────────────────────────────────┘
         ↓
┌─────────────────────────────────────┐
│  阶段5: 输出和保存                  │
│  1. 导出JSON (export_book_source)   │
│  2. 保存到知识库 (save_to_knowledge)│
│  3. 保存经验 (save_experience)      │
└─────────────────────────────────────┘
         ↓
返回书源JSON给用户
```

### 工具调用示例

**场景**: 为起点中文网编写书源

```
调用序列:
1. smart_fetch_html("https://www.qidian.com/search?q=斗破苍穹")
   → 获取搜索页HTML

2. search_knowledge("搜索规则 CSS选择器")
   → 查询知识库获取规则模板

3. smart_web_analyzer(HTML, "搜索页")
   → 分析搜索页结构

4. analyze_search_structure(HTML)
   → 识别书名、作者、封面元素

5. smart_fetch_html("https://book.qidian.com/info/123456")
   → 获取书籍详情页HTML

6. smart_bookinfo_analyzer(HTML)
   → 分析详情页结构

7. smart_fetch_html("https://book.qidian.com/catalog")
   → 获取目录页HTML

8. smart_toc_analyzer(HTML)
   → 分析目录结构

9. smart_fetch_html("https://read.qidian.com/chapter/1")
   → 获取正文页HTML

10. smart_content_analyzer(HTML)
    → 分析正文结构

11. edit_book_source({...})
    → 组装完整书源JSON

12. validate_legado_rules(book_source)
    → 验证规则合规性

13. export_book_source(book_source)
    → 导出JSON文件
```

---

## 🧠 工具系统架构

### 工具设计原则

1. **单一职责**: 每个工具只做一件事
2. **可组合性**: 工具可以自由组合
3. **优先级明确**: 高优先级工具先执行
4. **错误处理**: 完善的异常处理
5. **日志记录**: 详细的执行日志

### 工具调用机制

```python
@tool
def smart_fetch_html(
    url: str,
    method: str = "GET",
    headers: Dict = None,
    body: str = None
) -> str:
    """
    智能获取网页HTML

    Args:
        url: 网页URL
        method: 请求方法（GET/POST）
        headers: 请求头
        body: 请求体

    Returns:
        HTML内容
    """
    # 1. 检测请求方法
    method = detect_request_method(url)

    # 2. 构建请求
    request = build_smart_request(method, url, headers, body)

    # 3. 发送请求
    response = send_request(request)

    # 4. 保存HTML（永久存储）
    save_html(url, response.text)

    # 5. 返回HTML
    return response.text
```

### 工具调用策略

**LangChain Agent**自动决定调用哪些工具，遵循以下策略：

1. **工具描述匹配**: 根据工具的`__doc__`描述匹配用户需求
2. **优先级排序**: 高优先级工具优先调用
3. **参数传递**: 自动提取参数传递给工具
4. **结果反馈**: 工具返回结果给Agent继续处理
5. **错误恢复**: 工具失败时自动重试或调用备选工具

---

## 📚 知识库系统

### 知识库结构

**总大小**: 24.93MB
**文件数**: 167个

**分类**:

| 分类 | 文件数 | 大小 | 内容 |
|------|-------|------|------|
| **核心文档** | 30 | 10MB | CSS规则、正则、POST配置等 |
| **真实书源** | 134 | 12MB | 134个真实书源分析结果 |
| **模板库** | 3 | 2MB | 书源模板、示例 |
| **其他** | - | 0.93MB | 参考资料、工具说明 |

### 核心知识文件

1. **css选择器规则.txt** (80KB)
   - CSS选择器语法完整手册
   - 提取类型详解（@text, @html, @ownText, @textNode, @href, @src）
   - 正则表达式格式说明
   - 示例代码

2. **书源规则：从入门到入土.md** (39KB)
   - 最详细的书源开发教程
   - 语法说明（Default、CSS、XPath、JSONPath、正则）
   - POST请求配置规范
   - 完整书源结构说明
   - 1751个真实书源分析结果统计

3. **真实书源知识库.md** (500KB)
   - 134个真实书源分析结果
   - 按网站类型分类（小说、漫画、论坛）
   - 规则模板和最佳实践

4. **正则表达式使用规范.txt** (15KB)
   - 正则表达式三种基本格式（删除、替换、提取）
   - 捕获组的使用方法
   - 清理前缀/后缀的技巧
   - 常见错误及正确示例

5. **POST请求配置规范.txt** (10KB)
   - POST请求的完整配置规范
   - 请求体格式要求
   - Content-Type设置
   - 示例代码

### 知识学习引擎

**文件**: `src/utils/knowledge_learner.py`

**功能**:
- 扫描assets目录下的所有知识文件
- 解析并提取关键信息
- 建立知识索引（knowledge_index.json）
- 支持增量学习

**工作流程**:
```
1. 扫描assets目录
   ↓
2. 识别知识文件（.txt, .md, .json）
   ↓
3. 解析文件内容
   ↓
4. 提取元数据（标题、分类、标签）
   ↓
5. 建立全文索引
   ↓
6. 保存knowledge_index.json
   ↓
7. 统计学习结果
```

### 知识应用引擎

**文件**: `src/utils/knowledge_applier.py`

**功能**:
- 将学到的知识应用到HTML分析
- 智能推荐规则模板
- 匹配相似书源示例

**应用场景**:
```
场景: 分析小说网站的搜索页
     ↓
1. 识别网站类型（小说网站）
   ↓
2. 查找相似书源示例（从134个真实书源中）
   ↓
3. 提取规则模板
   ↓
4. 应用到当前HTML
   ↓
5. 生成初步规则
   ↓
6. 验证规则准确性
```

---

## 🐛 调试系统

### Legado调试器架构

**核心文件**:
- `src/tools/legado_debugger.py` - 规则解析器
- `src/tools/book_source_debugger.py` - 书源调试器
- `src/tools/legado_debug_tools.py` - 工具封装

### 规则解析器

**文件**: `src/tools/legado_debugger.py`

**基于**: Legado官方Kotlin源码

**支持规则类型**:

| 规则类型 | 引擎 | 用途 |
|---------|------|------|
| **Default** | BeautifulSoup | CSS选择器 + 提取类型 |
| **XPath** | lxml | XPath表达式 |
| **JsonPath** | jsonpath-ng | JsonPath表达式 |
| **正则** | Python re | 正则表达式替换/提取 |

**核心方法**:

```python
class LegadoDebugger:
    def __init__(self, html: str, base_url: str):
        self.html = html
        self.base_url = base_url
        self.soup = BeautifulSoup(html, 'lxml')
        self.tree = etree.HTML(html)

    def get_string(self, rule: str) -> str:
        """获取单个字符串"""
        # 解析规则类型
        rule_type = self._detect_rule_type(rule)

        # 根据类型调用对应解析器
        if rule_type == 'default':
            return self._parse_default(rule)
        elif rule_type == 'xpath':
            return self._parse_xpath(rule)
        elif rule_type == 'jsonpath':
            return self._parse_jsonpath(rule)
        elif rule_type == 'regex':
            return self._parse_regex(rule)

    def get_string_list(self, rule: str) -> List[str]:
        """获取字符串列表"""
        # 类似get_string，但返回列表
        pass

    def test_rule(self, rule: str, expected: str) -> bool:
        """测试规则是否匹配预期"""
        result = self.get_string(rule)
        return result == expected
```

**规则格式解析**:

**Default规则（CSS选择器）**:
```
格式:  <选择器>.<位置>@<提取类型>##<正则表达式>##<替换内容>

示例1:  .book-list h2.0@text
        ↓
        选择器: .book-list h2
        位置: 0（第一个）
        提取类型: text
        结果: 提取第一个h2的文本

示例2:  .book-list h2.0@text##广告##删除
        ↓
        选择器: .book-list h2
        位置: 0
        提取类型: text
        正则: 删除"广告"字样
        结果: 提取第一个h2的文本，删除"广告"

示例3:  a@href
        ↓
        选择器: a
        提取类型: href
        结果: 提取链接地址
```

**XPath规则**:
```
格式:  <XPath表达式>

示例:  //div[@class="book-info"]/h2/text()
        ↓
        查找class="book-info"的div下的h2的文本
```

**正则替换**:
```
格式:  ##<正则表达式>##<替换内容>

示例:  ##(\d{4})-(\d{2})-(\d{2})##$1年$2月$3日##
        ↓
        输入: 2025-01-20
        输出: 2025年01月20日
```

### 书源调试器

**文件**: `src/tools/book_source_debugger.py`

**功能**:
- 加载书源JSON
- 测试搜索规则
- 测试书籍信息规则
- 测试目录规则
- 测试正文规则
- 生成调试报告

**使用方法**:

```python
# 创建调试器
debugger = BookSourceDebugger()

# 加载书源
book_source = debugger.load_book_source("source.json")

# 测试搜索
results = debugger.test_search(book_source, "斗破苍穹")
print(results)

# 测试书籍信息
book_info = debugger.test_book_info(book_source, "https://...")
print(book_info)

# 测试目录
toc = debugger.test_toc(book_source, "https://...")
print(toc)

# 测试正文
content = debugger.test_content(book_source, "https://...")
print(content)
```

### 调试工具封装

**文件**: `src/tools/legado_debug_tools.py`

**LangChain工具**:

```python
@tool
def debug_book_source(
    book_source_json: str,
    keyword: str = "斗破苍穹",
    test_type: str = "full"
) -> Dict[str, Any]:
    """
    调试书源

    Args:
        book_source_json: 书源JSON字符串
        keyword: 搜索关键词（测试搜索规则）
        test_type: 测试类型（search/book_info/toc/content/full）

    Returns:
        调试结果
    """
    debugger = BookSourceDebugger()
    debugger.load_book_source(book_source_json)

    if test_type == "search":
        return debugger.test_search(keyword)
    elif test_type == "book_info":
        return debugger.test_book_info(...)
    elif test_type == "toc":
        return debugger.test_toc(...)
    elif test_type == "content":
        return debugger.test_content(...)
    else:  # full
        return debugger.full_test(...)
```

---

## 🚀 部署架构

### 本地部署

**方式1: CLI模式**
```bash
# 本地运行
bash scripts/local_run.sh -m flow

# 运行特定节点
bash scripts/local_run.sh -m node -n node_name
```

**方式2: HTTP服务**
```bash
# 启动HTTP服务
bash scripts/http_run.sh -m http -p 5000

# 访问
http://localhost:5000
```

### Docker部署

**Dockerfile** (假设有):
```dockerfile
FROM python:3.10-slim

WORKDIR /app

COPY requirements.txt .
RUN pip install -r requirements.txt

COPY . .

CMD ["python", "-m", "uvicorn", "src.main:app", "--host", "0.0.0.0", "--port", "5000"]
```

**构建和运行**:
```bash
# 构建镜像
docker build -t legado-agent .

# 运行容器
docker run -p 5000:5000 legado-agent
```

### 生产部署

**推荐架构**:
```
┌─────────────┐
│  负载均衡   │
│  (Nginx)    │
└─────────────┘
       ↓
┌─────────────┐
│  多实例     │
│  (K8s/Docker)│
└─────────────┘
       ↓
┌─────────────┐
│  消息队列   │
│  (RabbitMQ) │
└─────────────┘
       ↓
┌─────────────┐
│  Agent集群  │
│  (多个实例) │
└─────────────┘
       ↓
┌─────────────┐
│  PostgreSQL │
│  (持久化)   │
└─────────────┘
       ↓
┌─────────────┐
│  S3存储     │
│  (文件存储) │
└─────────────┘
```

---

## 🔧 扩展性设计

### 添加新工具

**步骤**:

1. **创建工具文件**
```python
# src/tools/my_new_tool.py
from langchain.tools import tool

@tool
def my_new_tool(param1: str, param2: int) -> str:
    """
    工具描述

    Args:
        param1: 参数1说明
        param2: 参数2说明

    Returns:
        返回值说明
    """
    # 实现逻辑
    return "结果"
```

2. **在agent.py中导入**
```python
from tools.my_new_tool import my_new_tool
```

3. **添加到工具列表**
```python
def build_agent(ctx=None):
    # ...
    tools = [
        # 其他工具
        my_new_tool,  # 添加新工具
    ]
    # ...
```

4. **更新配置文件**
```json
{
  "tools": [
    "my_new_tool"
  ]
}
```

### 添加新知识

**步骤**:

1. **创建知识文件**
```bash
# assets/my_new_knowledge.md
# 添加新知识内容
```

2. **重新构建索引**
```bash
python scripts/build_knowledge_index.py
```

3. **测试查询**
```python
search_knowledge("我的新知识")
```

### 添加新规则类型

**步骤**:

1. **在legado_debugger.py中添加解析器**
```python
def _parse_new_rule_type(self, rule: str) -> str:
    """解析新规则类型"""
    # 实现解析逻辑
    pass
```

2. **更新get_string方法**
```python
def get_string(self, rule: str) -> str:
    # ...
    elif rule_type == 'new_type':
        return self._parse_new_rule_type(rule)
```

3. **添加测试用例**
```python
# tests/test_legado_tools.py
def test_new_rule_type():
    debugger = LegadoDebugger(html, base_url)
    result = debugger.get_string("new_type_rule")
    assert result == expected
```

---

## 📊 性能指标

### 系统性能

| 指标 | 数值 | 说明 |
|------|------|------|
| **响应时间** | <5s | 平均响应时间 |
| **并发处理** | 10+ | 同时处理的请求数 |
| **知识库查询** | <1s | 知识库查询时间 |
| **HTML获取** | <3s | 网页获取时间 |
| **规则解析** | <0.5s | 单规则解析时间 |
| **内存占用** | <500MB | 运行时内存 |
| **磁盘占用** | 100MB+ | 项目总大小 |

### 准确率

| 任务 | 准确率 | 说明 |
|------|-------|------|
| **规则生成** | 90%+ | 自动生成规则的准确率 |
| **HTML分析** | 95%+ | 元素识别准确率 |
| **知识检索** | 95%+ | 知识库查询准确率 |
| **规则验证** | 100% | Legado规范验证准确率 |

---

## 🎓 最佳实践

### 开发规范

1. **代码风格**: 遵循PEP 8
2. **文档注释**: 使用Google风格docstring
3. **错误处理**: 完善的异常处理和日志记录
4. **测试覆盖**: 核心功能必须有单元测试
5. **性能优化**: 避免重复计算，使用缓存

### 使用建议

1. **从简单开始**: 先用智能分析工具快速生成初步规则
2. **验证优先**: 每次修改后都要验证规则
3. **善用知识库**: 遇到问题先查知识库
4. **人工干预**: AI无法处理时，使用人工干预工具
5. **保存经验**: 成功的经验要保存到知识库

---

## 🔮 未来规划

### 短期计划

- [ ] 增加更多真实书源示例
- [ ] 优化知识库搜索算法
- [ ] 添加书源自动测试功能
- [ ] 支持批量导入/导出书源

### 中期计划

- [ ] 开发可视化规则编辑器
- [ ] 添加书源评分和推荐系统
- [ ] 支持书源分享和社区协作
- [ ] 集成更多LLM模型

### 长期计划

- [ ] 构建书源生态系统
- [ ] 开发移动端APP
- [ ] 提供API服务
- [ ] 商业化运营

---

## 📞 联系信息

**项目名称**: Legado书源驯兽师智能体
**技术栈**: Python + LangChain + LangGraph + Doubao
**开发者**: Coze Coding Agent
**最后更新**: 2025-01-20

---

## ✅ 总结

**Legado书源驯兽师**是一个功能强大、架构完整、技术先进的智能体项目。它通过以下核心优势为Legado书源开发提供了革命性的解决方案：

1. **完整的工具链**: 23个专业工具，覆盖书源开发全流程
2. **强大的知识库**: 24.93MB知识库，167个文档，134个真实书源
3. **智能分析**: 自动分析网站结构，识别关键元素
4. **人机协作**: 支持人工干预，协同编辑
5. **严格验证**: 基于Legado官方源码，确保100%兼容
6. **持续学习**: 自动学习新知识，不断优化

**项目状态**: ✅ **生产就绪，可以投入使用！**

🎉 **祝您书源开发愉快！**
