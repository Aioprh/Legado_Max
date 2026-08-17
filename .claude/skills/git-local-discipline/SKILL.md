---
name: git-local-discipline
description: 在进行任何文件修改后必须立即使用的纪律技能——每次修改必须 git add + git commit，绝不裸改文件不提交；在用户明确许可前只做本地 commit 绝不 git push；commit message 必须使用 conventional commits 中文格式。当 Agent 修改、创建、删除代码文件后，或者完成一组逻辑相关的编辑后，必须调用此技能执行提交。哪怕只改了一行代码也要提交。
---

# Git 本地提交纪律

## 核心原则

每次对项目文件进行修改（包括新增、编辑、删除代码文件）后，必须立即将改动通过 git 提交到本地仓库。这是为了：

- **可追溯性**：每一个改动都有明确的 commit 记录，方便回溯和审查
- **安全网**：如果后续改动出了问题，可以随时 `git revert` 或 `git reset` 回到已知良好状态
- **原子性**：小步提交让每次 commit 的意图清晰，避免大杂烩式的变更
- **分步提交**：当用户给出多个任务时，必须逐个任务完成并提交，绝不能把所有任务都改完再一起提交。每完成一个任务就立即 commit，然后才开始下一个任务。这样做的好处是：如果某个任务的改动引入了问题，只需 revert 那个 commit，不会影响其他任务的成果；同时也让 git log 清晰地反映出工作进度

## 提交时机

### 必须提交的场景

1. **完成一个逻辑编辑单元后** — 当你完成了一组逻辑相关的文件修改（比如修复一个 bug 涉及的 2-3 个文件），立即提交
2. **完成多任务中的一个任务后** — 当用户给出多个任务（如「修复 A bug，然后优化 B 性能，再新增 C 功能」），每完成一个任务就立即提交，然后才开始下一个任务。绝不能把三个任务的改动攒在一起提交
3. **创建新文件后** — 新文件必须被 git 跟踪，尽快提交
4. **删除文件后** — 删除操作也需要记录在 git 历史中
5. **重构完成后** — 每个重构步骤完成后立即提交，不要积攒

### 不需要提交的场景

- 正在进行的探索性搜索（只读操作）
- 用户明确说"先别提交，我还要调整"
- 修改的是 skill 自身或临时工作文件（如 evals 工作区）

## 提交流程

### 1. 暂存改动

```bash
# 暂存所有相关文件（如果是同一次逻辑改动的多个文件，一起提交）
git add <file1> <file2> ...

# 如果不确定有哪些文件改了，先查看
git status --porcelain

# 多任务场景下，如果同一个文件包含多个任务的改动（比如任务 A 和任务 B 都改了 BookshelfHelper.kt 的不同部分），
# 用分块暂存只提交当前任务的改动：
git add -p <file>
```

### 2. 分析 diff

```bash
git diff --staged
```

查看暂存的改动内容，为撰写 commit message 做准备。

### 3. 撰写 commit message

每条 commit message 由两部分组成：**标题行**和**修改内容简述（body）**。标题行下方必须紧跟 body，简述本次提交具体做了什么修改。body 的目的是让任何人看 git log 时就能快速了解改动内容，而不需要去 diff 里翻细节。

#### 标题行格式

```
<type>(<scope>): <description>
```

- **type**：`feat`（新功能）、`fix`（修复）、`refactor`（重构）、`perf`（性能）、`style`（格式）、`docs`（文档）、`test`（测试）、`chore`（构建/工具）、`ci`（CI 配置）、`revert`（回滚）
- **scope**：受影响的模块（中文，如 `书架`、`阅读页`、`主题`、`书源`）
- **description**：用中文简述改动，动宾短语，不超过 50 字符

#### body 格式

标题行下方空一行，然后用简短的条目列出本次提交的具体修改内容：

- 每条用 `- ` 开头，描述一个具体的改动点
- 使用中文，简明扼要，每条不超过一行
- 通常 2-5 条，视改动复杂度而定
- 聚焦于「改了什么」，而不是「为什么改」（除非原因不直观）

#### 完整格式

```
<type>(<scope>): <description>

- <修改内容 1>
- <修改内容 2>
- <修改内容 3>
```

### 4. 执行提交

在 Bash（Git Bash / WSL / macOS / Linux）下：

```bash
git commit -m "$(cat <<'EOF'
<type>(<scope>): <description>

- <修改内容 1>
- <修改内容 2>
EOF
)"
```

在 PowerShell 下（Windows 默认终端）：

```powershell
git commit -m "<type>(<scope>): <description>`n`n- <修改内容 1>`n- <修改内容 2>"
```

PowerShell 中用 `` `n `` 表示换行，注意是反引号不是正斜杠。

### 5. 提交后反馈

提交成功后，向用户简要报告本次提交的信息：

```
已提交: <commit hash 前 7 位> <type>(<scope>): <description>
```

如果连续提交了多个 commit（多任务场景），列出全部新增的 commit：

```
已提交 3 个 commit:
  a1b2c3d fix(书架): 修复分组为空时返回 null 的问题
  e4f5g6h perf(阅读页): 移除翻页动画中多余的 invalidate 调用
  i7j8k9l docs: 更新 CLAUDE.md 中的构建命令说明
```

这让用户清楚知道哪些改动已经被提交，哪些可能还悬而未决。

### Commit Message 示例

```
fix(书架): 修复 BookshelfMatcher 匹配规则未正确处理分组的问题

- 新增空分组判断，遍历前先检查分组是否为空
- 匹配失败时返回空列表替代原有的 null 返回值
- 补充对应的边界条件单元测试

refactor(主题): 将 TopBarConfig 中的颜色常量提取为独立文件

- 将 12 个颜色常量从 TopBarConfig.kt 迁移到 ThemeColors.kt
- 更新所有引用点的 import 路径
- 保持 API 兼容，原文件保留 typealias 过渡

perf(阅读页): 优化翻页动画的帧率调度逻辑

- 移除 onDraw 中多余的 invalidate 调用
- 使用 Choreographer 替代 Handler 调度帧回调
- 动画结束后主动释放帧回调引用

feat(书源): 添加批量导入书源时的去重校验

- 导入前按 URL 字段去重，跳过已存在的书源
- 去重结果通过回调通知调用方（新增数/跳过数）
- 在导入对话框中展示去重统计信息

docs: 更新 CLAUDE.md 中的构建命令说明

- 补充 appMax/appLegacy/appS 三个 flavor 的具体说明
- 添加 Cronet 原生库下载命令
- 修正 Web 前端的 pnpm 版本要求
```

## 安全红线

- **绝不 `git push`** — 除非用户明确说"推送到远程"或"push"，所有提交仅限本地
- **绝不 `git push --force`** — 即使用户要求也要二次确认
- **绝不修改 git config** — 不更改用户名、邮箱、远程地址等配置
- **绝不使用 `--no-verify`** — 如果 pre-commit hook 失败，修复问题后重新提交
- **避免在 main/master 分支上直接提交** — 如果当前分支是 main 或 master，提醒用户建议先创建功能分支再提交。但如果用户确认就是要直接在 main 上提交，尊重用户的选择

## 与其他技能的关系

- `chinese-commit-conventions`：提供了完整的中文 commit 规范参考，包括 commitlint/husky 配置。本技能聚焦于"何时提交、提交纪律"，commit message 格式参考该技能的规范
- `git-commit`（全局技能）：提供了 diff 分析和 commit message 生成的通用流程。本技能在其基础上增加了"必须立即提交"的纪律约束

## 边界情况

### 多任务分步提交（重要）

当用户一次给出多个任务时，这是最常见的容易犯错的场景。正确做法是：

1. 完成任务 A 的所有文件修改 → `git add` 相关文件 → `git commit`（任务 A 的 commit）
2. 完成任务 B 的所有文件修改 → `git add` 相关文件 → `git commit`（任务 B 的 commit）
3. 完成任务 C 的所有文件修改 → `git add` 相关文件 → `git commit`（任务 C 的 commit）

**错误做法**：把 A、B、C 三个任务的改动全部做完，最后用一个 commit 提交。这样做的危害：

- 无法单独 revert 某个任务的改动
- commit message 无法准确描述（三个任务可能是不同的 type/scope）
- 如果中间某个任务出了问题，会污染其他任务的成功改动
- git log 无法反映工作进度

#### 例外：任务之间有强依赖

如果任务 B 必须在任务 A 的基础上才能完成（比如 A 是「提取接口」，B 是「实现接口」），可以合并为一个 commit，但 commit message 的 body 中要分条说明两个任务的改动。不过更推荐的做法仍然是分两次提交——A 提交后，B 在 A 的基础上继续。

### 多文件改动但属于同一逻辑

如果一次修改涉及多个文件但属于同一个逻辑变更（比如修改了一个接口签名 + 所有调用方），应该作为一个 commit 提交，而不是逐文件提交。

### 连续多步重构

如果是多步骤重构，每完成一个有意义的中间状态就提交一次，而不是等全部做完再提交一个大 commit。这样如果某一步出问题，只需 revert 那一步。

### 修改后发现需要补充

如果刚提交完发现遗漏了一个文件，使用 `git commit --amend` 补充，而不是创建一个新 commit。但如果已经 push 过（虽然本技能不允许主动 push），则只能新建 commit。

### 用户正在 review 中的分支
如果当前分支上有用户正在 review 的改动，新提交会让 review 变复杂。此时应该询问用户是否继续在此分支上提交，还是创建新分支。
