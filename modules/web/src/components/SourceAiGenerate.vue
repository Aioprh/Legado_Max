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

    <div class="review-probe-row">
      <span class="review-probe-label">段评探测（生成时自动生成段评气泡规则，默认关闭）</span>
      <el-switch v-model="reviewProbe" />
    </div>

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
      <span v-if="apiEndpoints.length">
        ；自动发现 {{ apiEndpoints.length }} 个 JSON API 接口，搜索示例 {{ sampleSearch?.ok ? '成功' : '失败' }}
      </span>
    </div>

    <div v-if="apiEndpoints.length" class="api-panel">
      <div class="api-title">自动发现 JSON API 接口（SPA 站点，LLM 将据此编写 JSONPath 规则）</div>
      <div v-for="e in apiEndpoints" :key="e.type" class="api-line">
        <span class="api-type">{{ e.type }}</span>
        <span class="api-url">{{ e.url }}</span>
      </div>
      <div v-if="sampleSearch" class="api-sample">
        <div class="api-sample-title">
          {{ sampleSearch.ok ? '搜索接口示例响应：' : '搜索接口探测失败：' + sampleSearch.error }}
        </div>
        <pre v-if="sampleSearch.ok">{{ sampleSearch.json.slice(0, 600) }}</pre>
      </div>
      <div v-if="sampleCatalog && sampleCatalog.ok" class="api-sample">
        <div class="api-sample-title">目录接口示例响应（已截断）：</div>
        <pre>{{ sampleCatalog.json.slice(0, 600) }}</pre>
      </div>
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
import API, { type AiSampleResult } from '@api'
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
const apiEndpoints = ref<{ type: string; url: string }[]>([])
const sampleSearch = ref<AiSampleResult | null>(null)
const sampleCatalog = ref<AiSampleResult | null>(null)
const reviewUrl = ref('')
const reviewProbe = ref(false)

const htmlPreview = computed(() => html.value)

const checkTitle = (c: { name: string; pass: boolean; msg: string }) =>
  `${c.pass ? '✓' : '✗'} ${c.name}${c.pass ? '' : '：' + c.msg}`

const fetchHtml = async () => {
  if (!url.value.trim()) return ElMessage.warning('请先填写网站地址')
  fetching.value = true
  try {
    const { data } = await API.fetchHtml(url.value.trim(), keyword.value.trim())
    if (!data.isSuccess) throw new Error(data.errorMsg || '抓取失败')
    html.value = data.data.html
    htmlCharset.value = data.data.charset
    htmlLength.value = data.data.length
    apiEndpoints.value = data.data.apiEndpoints || []
    sampleSearch.value = data.data.sampleSearch || null
    sampleCatalog.value = data.data.sampleCatalog || null
    reviewUrl.value = data.data.reviewUrl || ''
    const apiCount = apiEndpoints.value.length
    const sampleOk = sampleSearch.value?.ok ? 1 : 0
    ElMessage.success(
      `抓取成功（编码 ${htmlCharset.value}，共 ${htmlLength.value} 字符，已截断）` +
        (apiCount ? `，发现 ${apiCount} 个接口，搜索示例 ${sampleOk ? '成功' : '失败'}` : ''),
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

const PROMPT_HTML_LIMIT = 40000

const buildPrompt = () => {
  const lines: string[] = []
  lines.push(`网站地址：${url.value.trim()}`)
  lines.push(`搜索关键词：${keyword.value.trim() || '（未提供）'}`)
  lines.push(`书源类型：${sourceTypes[sourceType.value]}（${sourceType.value}）`)
  if (apiEndpoints.value.length) {
    lines.push('')
    lines.push('【重要】本站为前端 JS 动态渲染（SPA）站点，静态 HTML 中不含书籍数据。自动发现以下 JSON API 接口，请优先基于这些接口编写 JSONPath 规则：')
    apiEndpoints.value.forEach(e => lines.push(`- ${e.type}: ${e.url}`))
  }
  const ss = sampleSearch.value
  if (ss) {
    lines.push('')
    if (ss.ok) {
      lines.push('搜索接口示例响应（JSON）：')
      lines.push('----------')
      lines.push(ss.json)
      lines.push('----------')
    } else {
      lines.push(`搜索接口探测失败：${ss.error}`)
    }
  }
  const sc = sampleCatalog.value
  if (sc) {
    lines.push('')
    if (sc.ok) {
      lines.push('目录接口示例响应（JSON，用于编写目录/正文规则）：')
      lines.push('----------')
      lines.push(sc.json)
      lines.push('----------')
    } else {
      lines.push(`目录接口探测失败：${sc.error}`)
    }
  }
  if (reviewProbe.value) {
    lines.push('')
    lines.push('【段评探测（已开启）】请为书源生成“段评气泡”：正文规则 content 用 @js: 请求段评统计接口取每段段评数，把正文按换行拆段后，对段评数>0 的段落末尾插入 <img src="dp:<段评数>,{...}"> 气泡标记，点击气泡弹出该段段评内容（完整实现见系统提示词【段评气泡】模板，HOST 与接口路径替换为本站实际值）。')
    if (reviewUrl.value) {
      lines.push(`已从页面脚本探测到段评接口：${reviewUrl.value}（请据此推断 summary/paragraph 等 action 参数与返回结构；若该地址是带占位符的模板，用实际 book_id/chapter_id 替换）`)
    } else {
      lines.push('（未在页面脚本中探测到段评接口；若你从提供的 HTML 中发现段评接口请自行使用，否则 content 用普通规则即可）')
    }
  }
  lines.push('')
  lines.push(`已抓取到的网页 HTML（预处理后，编码 ${htmlCharset.value}，共 ${htmlLength.value} 字符，已截断）：`)
  lines.push('----------')
  const h =
    html.value.length > PROMPT_HTML_LIMIT
      ? html.value.slice(0, PROMPT_HTML_LIMIT) + '\n...(已截断)'
      : html.value
  lines.push(h)
  lines.push('----------')
  lines.push('请分析以上内容，按系统要求输出完整书源 JSON 数组。若提供了 JSON API 接口与示例响应，请优先使用 JSONPath 规则。')
  return lines.join('\n')
}

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
  apiEndpoints.value = []
  sampleSearch.value = null
  sampleCatalog.value = null
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
  "loginUrl": "登录地址（可选），站点有登录页则填其 URL，无则空字符串",
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
    "tocUrl": "目录页 URL（与详情页不同时填写）",
    "downloadUrls": "整本下载地址规则（可选），如 https://host/download.php?id={{$.id}}，无则空字符串"
  },
  "ruleToc": {
    "chapterList": "章节列表选择器（必填）",
    "chapterName": "章节名规则（必填）",
    "chapterUrl": "章节 URL 规则（必填）",
    "isVip": "VIP 标记规则（返回 1 表示 VIP，0 表示免费，如 JSONPath $.vip）",
    "isPay": "付费标记规则（同上，无则空字符串）",
    "updateTime": "章节更新时间规则（无则空字符串）",
    "nextTocUrl": "下一页目录 URL"
  },
  "ruleContent": {
    "content": "正文规则（必填）。若正文接口对 VIP/付费章节要求登录态或额外请求头才返回全文（如部分起点系镜像站需 X-Content-Token 请求头并加 &vip=1 参数），content 必须写 @js: 规则：先普通请求取免费正文，取不到时先从登录态接口（如 auth.php?action=me）提取 token，再带 {\"X-Content-Token\":token} 请求 &vip=1 接口兜底（注意：java.get 只发送显式传入的请求头、不会自动带书源 header，必须同时显式带上浏览器 User-Agent，如 java.get(url+'&vip=1',{'User-Agent':'Mozilla/5.0','X-Content-Token':token})，否则站点可能因缺 UA 拒绝）。参考模板：@js:var bid=(book.bookUrl.match(/book_id=(\\\\d+)/)||[])[1]||'';var cid=(chapter.url.match(/chapter_id=(\\\\d+)/)||[])[1]||'';var u='https://host/content.php?book_id='+bid+'&chapter_id='+cid;var v='';try{var o=JSON.parse(java.ajax(u));v=(o.content||o.Content||'').replace(/\\\\r\\\\n/g,'\\n')}catch(e){}if(!v){try{var t='';try{t=JSON.parse(java.ajax('https://host/auth.php?action=me')).request_token||''}catch(e){}var o2=JSON.parse(java.get(u+'&vip=1',{'User-Agent':'Mozilla/5.0','X-Content-Token':t}).body());v=(o2.content||o2.Content||'').replace(/\\\\r\\\\n/g,'\\n');if(!v)v=o2.detail?'【'+o2.detail+'】':'【正文获取失败】'}catch(e){v='【正文获取失败】'}}v（token 字段名与请求头以站点实际为准）；站点无 VIP 或正文不要求登录则用简单 JSONPath/CSS 即可",
    "nextContentUrl": "下一章 URL",
    "webJs": "需 JS 渲染时注入的脚本",
    "replaceRegex": "正文净化正则，如 ##<script[\\s\\S]*?<\\/script>|请收藏.*##"
  },
  "exploreUrl": "发现地址（可选），多分类用 分类名::URL 换行分隔，如 热门::https://host/top/###...；分页参数写 page=1（App 会自动翻页）；无发现页则空字符串",
  "ruleExplore": {
    "bookList": "发现列表选择器（必填，若 exploreUrl 非空）",
    "name": "发现书名",
    "author": "发现作者",
    "coverUrl": "发现封面",
    "intro": "发现简介",
    "kind": "分类",
    "bookUrl": "详情页 URL 规则",
    "wordCount": "字数"
  }
}

【字段命名（重要，必须使用本版 Legado 命名）】
- 详情规则用 ruleBookInfo（不是 ruleDetail）
- 目录规则用 ruleToc（不是 ruleCatalog），章节名用 chapterName（不是 name）
- 搜索简介用 intro（不是 detail）
- 目录 VIP/付费标记用 isVip/isPay（不是 vipFlag/payFlag）

【规则语法（Default 语法优先）】
- 提取类型：@text 取文本、@html 取 HTML、@href 取链接、@src 取图片、@textNode、@ownText
- Default 语法：class.booklist@tag.li 或 .booklist li@tag.a；简单 CSS 选择器不要加 @css 前缀
- 复杂 CSS：@css:.detail p:nth-child(2)@text
- XPath：//div[@id='content']、//h3/a/text()、//img/@src
- JSONPath（返回 JSON 的网站）：bookList=$.data.records、字段 $.name、$.id，可用 {{$.id}} 拼接 URL
- 正则：规则后接 ##正则## 且必须成对，如 ".title@text##作者：##"
- 注意转义：正则里的 \\d、\\s 等需写双反斜杠
- 【段评气泡（可选）】若站点有“本章说/段评”接口（页面脚本出现 comments.php / comment.php / review.php，或用户开启段评探测并提供了段评接口），在正文规则 content 里用 @js: 生成段评气泡：① 请求段评统计接口取每段段评数（起点系 comments.php?action=summary&book_id=...&chapter_id=... → $.Data.Getparagraphscommentcounts.DataList[]，字段 ParagraphId/CommentCount，-1 为章评不用于正文）；② 正文按换行拆段（跳过空段），第 i 段对应 ParagraphId=i；③ 对段评数>0 的段落末尾插入 <img src="dp:<段评数>,{\"pclick\":\"<点击JS>\",\"status\":\"normal\"}">；④ 点击 JS（pclick）用 java.ajax 请求该段段评列表（起点系 comments.php?action=paragraph&book_id=...&chapter_id=...&paragraph_id=<段号>&type=text&page=1&page_size=20 → $.Data.DataList[]，字段 Content/Floor/AgreeAmount/CreateTime，type 参数优先用 type=text：部分起点系镜像站 type=all 会报错，仅 text/image 有效）拼文本后 java.showBrowser('', 文本) 弹出。【重要】pclick 代码禁止出现双引号/反斜杠/尖括号 >，字符串一律用单引号，换行用 String.fromCharCode(10)

【输出要求】
1. 严格输出 JSON，最外层必须是数组 [...]，即使只有一个书源
2. 所有规则必须基于提供的 HTML 真实分析，禁止编造不存在的选择器
3. 只输出 JSON 本身，不要添加解释文字或 markdown 代码块标记
4. 无法推断的字段填空字符串 ""
5. 若 HTML 是 JSON 数据，使用 JSONPath 语法
6. 必须输出完整书源：搜索、详情(ruleBookInfo)、目录(ruleToc)、正文(ruleContent) 为必填核心；发现(exploreUrl/ruleExplore)、登录(loginUrl)、下载(ruleBookInfo.downloadUrls) 若网站支持则填写，不支持/无法推断时填空字符串 ""，但字段名必须保留在输出结构中
7. 若提供了 JSON API 接口与示例响应，一律优先使用 JSONPath 规则并补齐上述全部字段`
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
.review-probe-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 8px;
  padding: 6px 10px;
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 6px;
  background: var(--el-fill-color-lighter);
}
.review-probe-label {
  font-size: 13px;
  color: var(--el-text-color-regular);
}
.hint {
  font-size: 12px;
  color: var(--el-text-color-secondary);
  margin-bottom: 8px;
}
.api-panel {
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 6px;
  padding: 8px 10px;
  margin-bottom: 8px;
  background: var(--el-fill-color-lighter);
}
.api-title {
  font-size: 12px;
  font-weight: 600;
  color: var(--el-color-primary);
  margin-bottom: 6px;
}
.api-line {
  display: flex;
  gap: 8px;
  font-size: 12px;
  margin-bottom: 4px;
  word-break: break-all;
}
.api-type {
  flex-shrink: 0;
  color: var(--el-color-primary);
  font-weight: 600;
}
.api-url {
  color: var(--el-text-color-regular);
}
.api-sample {
  margin-top: 8px;
  border-top: 1px dashed var(--el-border-color-lighter);
  padding-top: 6px;
}
.api-sample-title {
  font-size: 12px;
  color: var(--el-text-color-secondary);
  margin-bottom: 4px;
}
.api-sample pre {
  margin: 0;
  max-height: 180px;
  overflow: auto;
  font-size: 11px;
  white-space: pre-wrap;
  word-break: break-all;
  background: var(--el-fill-color);
  padding: 6px;
  border-radius: 4px;
}
.checks {
  margin-top: 8px;
  .el-alert {
    margin-bottom: 6px;
  }
}
</style>
