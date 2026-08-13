<template>
  <div class="ai-generate">
    <el-collapse v-model="openConfig">
      <el-collapse-item title="AI 接口配置（OpenAI 兼容，保存到本地）" name="config">
        <el-input
          v-model="baseUrl"
          placeholder="接口地址，如 https://api.deepseek.com/v1"
          style="margin-bottom: 8px"
        >
          <template #prepend>接口地址</template>
        </el-input>
        <el-input
          v-model="apiKey"
          type="password"
          show-password
          placeholder="API Key"
          style="margin-bottom: 8px"
        >
          <template #prepend>API Key</template>
        </el-input>
        <el-input
          v-model="model"
          placeholder="模型名，如 deepseek-chat / gpt-4o-mini"
          style="margin-bottom: 8px"
        >
          <template #prepend>模型</template>
        </el-input>
      </el-collapse-item>
    </el-collapse>

    <el-input
      v-model="url"
      placeholder="网站地址，如 https://www.example.com（抓取用于 AI 分析）"
      style="margin-top: 8px; margin-bottom: 8px"
      @keydown.enter="fetchHtml"
    >
      <template #prepend>网站</template>
    </el-input>

    <el-input
      v-model="keyword"
      placeholder="搜索关键词，如：斗破苍穹（用于推断搜索地址，可留空）"
      style="margin-bottom: 8px"
    >
      <template #prepend>关键词</template>
    </el-input>

    <el-select v-model="sourceType" style="width: 100%; margin-bottom: 8px">
      <el-option
        v-for="(name, index) in sourceTypes"
        :key="index"
        :label="name"
        :value="index"
      />
    </el-select>

    <div class="actions">
      <el-button type="primary" :loading="fetching" :icon="Link" @click="fetchHtml">
        抓取HTML
      </el-button>
      <el-button
        type="success"
        :loading="generating"
        :disabled="!html && !resultText"
        :icon="MagicStick"
        @click="generate"
      >
        AI生成
      </el-button>
      <el-button :icon="CircleCheck" :disabled="!resultText" @click="validate">
        验证规则
      </el-button>
      <el-button
        type="warning"
        :disabled="!resultText"
        :icon="Download"
        @click="importToEditor"
      >
        导入编辑器
      </el-button>
      <el-button :icon="Delete" @click="clearAll">清空</el-button>
    </div>

    <el-input
      v-if="html"
      v-model="htmlPreview"
      type="textarea"
      readonly
      :rows="6"
      placeholder="抓取到的网页 HTML（截断预览）"
      class="html-preview"
    />
    <div v-if="html" class="hint">
      编码：{{ htmlCharset }}，共 {{ htmlLength }} 字符（已截断）
    </div>

    <el-input
      v-model="resultText"
      type="textarea"
      :rows="10"
      placeholder="AI 生成的书源 JSON（可手动修改后再验证/导入）"
      class="result-text"
    />

    <div v-if="checks.length" class="checks">
      <el-alert
        v-for="c in checks"
        :key="c.name"
        :type="c.pass ? 'success' : 'warning'"
        :closable="false"
        show-icon
        :title="checkTitle(c)"
      />
    </div>
  </div>
</template>

<script setup lang="ts">
import API from '@api'
import { normalizeSource } from '@utils/souce'
import {
  Link,
  MagicStick,
  CircleCheck,
  Download,
  Delete,
} from '@element-plus/icons-vue'

const store = useSourceStore()

const openConfig = ref<string[]>(['config'])
const baseUrl = ref(
  localStorage.getItem('legado_ai_base_url') || 'https://api.deepseek.com/v1',
)
const apiKey = ref(localStorage.getItem('legado_ai_api_key') || '')
const model = ref(
  localStorage.getItem('legado_ai_model') || 'deepseek-chat',
)

watch([baseUrl, apiKey, model], () => {
  localStorage.setItem('legado_ai_base_url', baseUrl.value)
  localStorage.setItem('legado_ai_api_key', apiKey.value)
  localStorage.setItem('legado_ai_model', model.value)
})

const url = ref('')
const keyword = ref('')
const sourceTypes = ['文本', '音频', '图片', '文件', '视频']
const sourceType = ref(0)

const fetching = ref(false)
const generating = ref(false)
const html = ref('')
const htmlCharset = ref('')
const htmlLength = ref(0)
const resultText = ref('')
const checks = ref<{ name: string; pass: boolean; msg: string }[]>([])

const htmlPreview = computed(() => html.value)

const checkTitle = (c: { name: string; pass: boolean; msg: string }) =>
  `${c.pass ? '✓' : '✗'} ${c.name}${c.pass ? '' : '：' + c.msg}`

const fetchHtml = async () => {
  if (!url.value.trim()) return ElMessage.warning('请先填写网站地址')
  fetching.value = true
  try {
    const { data } = await API.fetchHtml(url.value.trim())
    if (!data.isSuccess) throw new Error(data.errorMsg || '抓取失败')
    html.value = data.data.html
    htmlCharset.value = data.data.charset
    htmlLength.value = data.data.length
    ElMessage.success(
      `抓取成功（编码 ${htmlCharset.value}，共 ${htmlLength.value} 字符，已截断）`,
    )
  } catch (e) {
    ElMessage.error('抓取失败: ' + (e as Error).message)
  } finally {
    fetching.value = false
  }
}

/** 剥离 markdown 代码块，提取 JSON 片段 */
const stripCodeFence = (text: string) => {
  const m = text.match(/```(?:json)?\s*([\s\S]*?)```/)
  if (m) return m[1].trim()
  const start = text.search(/[[{]/)
  const end = Math.max(text.lastIndexOf('}'), text.lastIndexOf(']'))
  if (start > -1 && end > start) return text.slice(start, end + 1)
  return text.trim()
}

const buildPrompt = () =>
  `网站地址：${url.value.trim()}
搜索关键词：${keyword.value.trim() || '（未提供）'}
书源类型：${sourceTypes[sourceType.value]}（${sourceType.value}）
已抓取到的网页 HTML（编码 ${htmlCharset.value}，共 ${htmlLength.value} 字符）：
----------
${html.value}
----------
请分析以上 HTML 结构，按系统要求输出完整书源 JSON 数组。`

const generate = async () => {
  if (!baseUrl.value.trim() || !apiKey.value.trim()) {
    return ElMessage.warning('请先在上方配置 AI 接口地址和 API Key')
  }
  if (!url.value.trim()) return ElMessage.warning('请先填写网站地址')
  generating.value = true
  checks.value = []
  try {
    const endpoint =
      baseUrl.value.trim().replace(/\/+$/, '') + '/chat/completions'
    const resp = await fetch(endpoint, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        Authorization: 'Bearer ' + apiKey.value.trim(),
      },
      body: JSON.stringify({
        model: model.value.trim() || 'gpt-4o-mini',
        temperature: 0.3,
        messages: [
          { role: 'system', content: SYSTEM_PROMPT },
          { role: 'user', content: buildPrompt() },
        ],
      }),
    })
    if (!resp.ok) {
      throw new Error(
        'HTTP ' + resp.status + ': ' + (await resp.text()).slice(0, 200),
      )
    }
    const data = await resp.json()
    const content: string = data?.choices?.[0]?.message?.content ?? ''
    if (!content.trim()) throw new Error('模型未返回内容')
    resultText.value = stripCodeFence(content)
    ElMessage.success('生成完成，可查看/修改 JSON，再验证规则或导入编辑器')
  } catch (e) {
    ElMessage.error('AI 生成失败: ' + (e as Error).message)
  } finally {
    generating.value = false
  }
}

const validate = () => {
  let parsed: any
  try {
    parsed = JSON.parse(resultText.value)
  } catch {
    checks.value = [{ name: 'JSON 格式', pass: false, msg: '无法解析为 JSON' }]
    return
  }
  const src = Array.isArray(parsed) ? parsed[0] : parsed
  const list: { name: string; pass: boolean; msg: string }[] = []
  const check = (name: string, pass: boolean, msg = '') =>
    list.push({ name, pass, msg })
  if (!src || typeof src !== 'object') {
    checks.value = [{ name: '书源结构', pass: false, msg: '书源对象无效' }]
    return
  }

  check('源名称', !!src.bookSourceName, 'bookSourceName 不能为空')
  check('源地址', !!src.bookSourceUrl, 'bookSourceUrl 不能为空')
  check(
    '源类型',
    typeof src.bookSourceType === 'number',
    'bookSourceType 应为数字(0/1/2/3/4)',
  )
  check('搜索地址', !!src.searchUrl, 'searchUrl 不能为空')

  const rs = src.ruleSearch || {}
  check('搜索列表规则', !!rs.bookList, 'ruleSearch.bookList 不能为空')
  check('搜索书名规则', !!rs.name, 'ruleSearch.name 不能为空')
  check('搜索详情地址规则', !!rs.bookUrl, 'ruleSearch.bookUrl 不能为空')

  const rt = src.ruleToc || {}
  check('目录列表规则', !!rt.chapterList, 'ruleToc.chapterList 不能为空')
  check('章节名规则', !!rt.chapterName, 'ruleToc.chapterName 不能为空')
  check('章节地址规则', !!rt.chapterUrl, 'ruleToc.chapterUrl 不能为空')

  check('正文规则', !!(src.ruleContent || {}).content, 'ruleContent.content 不能为空')

  // 正则成对检查
  const rules: string[] = []
  ;[rs, src.ruleBookInfo || {}, rt, src.ruleContent || {}].forEach(obj =>
    Object.values(obj).forEach(
      v => typeof v === 'string' && v.includes('##') && rules.push(v),
    ),
  )
  const oddRules = rules.filter(r => (r.match(/##/g) || []).length % 2 !== 0)
  check(
    '正则成对',
    oddRules.length === 0,
    oddRules.length
      ? `以下规则 ## 未成对：${oddRules.map(r => r.slice(0, 30)).join('、')}`
      : '所有 ## 正则均已成对',
  )

  checks.value = list
  const passCount = list.filter(c => c.pass).length
  ElMessage[list.every(c => c.pass) ? 'success' : 'warning'](
    `验证完成：${passCount}/${list.length} 项通过`,
  )
}

const importToEditor = () => {
  try {
    const parsed = JSON.parse(resultText.value)
    const source = Array.isArray(parsed) ? parsed[0] : parsed
    if (!source || typeof source !== 'object') throw new Error('书源格式不正确')
    normalizeSource(source)
    if (source.bookSourceType == null) source.bookSourceType = 0
    store.changeCurrentSource(source)
    store.changeEditTabSource(source)
    store.changeTabName('editTab')
    ElMessage.success('已导入到编辑器，可继续编辑、保存或切换到「调试源」验证')
  } catch (e) {
    ElMessage.error('导入失败: ' + (e as Error).message)
  }
}

const clearAll = () => {
  html.value = ''
  resultText.value = ''
  checks.value = []
}

/**
 * AI 生成书源系统提示词
 * 提炼自 .claude/skills/legado-book-source-tamer/（DandanLLab/legadoSkill，MIT）
 */
const SYSTEM_PROMPT = `你是"Legado书源驯兽师"，精通 Legado（阅读）App 书源 JSON 开发的专家。你的任务是分析用户提供的网站 HTML，生成符合 Legado 规范的完整书源 JSON。

【书源 JSON 字段结构】
{
  "bookSourceName": "书源名称（必填）",
  "bookSourceUrl": "网站首页地址（必填，http/https）",
  "bookSourceGroup": "分组名（可选）",
  "bookSourceType": 0,
  "bookSourceComment": "说明（可选）",
  "searchUrl": "搜索地址（必填），搜索关键字用 {{key}} 占位，如 /search?q={{key}}；POST 请求写成 /search,{"method":"POST","body":"keyword={{key}}","charset":"gbk"}",
  "ruleSearch": {
    "bookList": "书籍列表选择器（必填）",
    "name": "书名规则（必填）",
    "author": "作者规则",
    "coverUrl": "封面规则",
    "intro": "简介规则",
    "kind": "分类规则",
    "wordCount": "字数规则",
    "lastChapter": "最新章节规则",
    "bookUrl": "详情页 URL 规则（必填）"
  },
  "ruleBookInfo": {
    "name": "详情页书名",
    "author": "详情页作者",
    "coverUrl": "详情页封面",
    "intro": "详情页简介",
    "kind": "分类",
    "lastChapter": "最新章节",
    "tocUrl": "目录页 URL（与详情页不同时填写）"
  },
  "ruleToc": {
    "chapterList": "章节列表选择器（必填）",
    "chapterName": "章节名规则（必填）",
    "chapterUrl": "章节 URL 规则（必填）",
    "nextTocUrl": "下一页目录 URL"
  },
  "ruleContent": {
    "content": "正文规则（必填）",
    "nextContentUrl": "下一章 URL",
    "webJs": "需 JS 渲染时注入的脚本",
    "replaceRegex": "正文净化正则，如 ##<script[\\s\\S]*?<\\/script>|请收藏.*##"
  }
}

【规则语法（Default 语法优先）】
- 提取类型：@text 取文本、@html 取 HTML、@href 取链接、@src 取图片、@textNode、@ownText
- Default 语法：class.booklist@tag.li 或 .booklist li@tag.a；简单 CSS 选择器不要加 @css 前缀
- 复杂 CSS：@css:.detail p:nth-child(2)@text
- XPath：//div[@id='content']、//h3/a/text()、//img/@src
- JSONPath（返回 JSON 的网站）：bookList=$.data.records、字段 $.name、$.id，可用 {{$.id}} 拼接 URL
- 正则：规则后接 ##正则## 且必须成对，如 ".title@text##作者：##"
- 注意转义：正则里的 \\d、\\s 等需写双反斜杠

【输出要求】
1. 严格输出 JSON，最外层必须是数组 [...]，即使只有一个书源
2. 所有规则必须基于提供的 HTML 真实分析，禁止编造不存在的选择器
3. 只输出 JSON 本身，不要添加解释文字或 markdown 代码块标记
4. 无法推断的字段填空字符串 ""
5. 若 HTML 是 JSON 数据，使用 JSONPath 语法`
</script>

<style lang="scss" scoped>
.ai-generate {
  height: calc(100vh - 60px);
  overflow-y: auto;
  padding-right: 4px;
}
.actions {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  margin-bottom: 8px;
}
.hint {
  font-size: 12px;
  color: var(--el-text-color-secondary);
  margin-bottom: 8px;
}
.checks {
  margin-top: 8px;
  .el-alert {
    margin-bottom: 6px;
  }
}
</style>
