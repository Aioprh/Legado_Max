package io.legado.app.model.blockrule

/**
 * Aho-Corasick 多模式匹配自动机
 *
 * 一次性构建后，可在 O(文本长度 + 匹配数) 时间内同时匹配多个关键词模式，
 * 比逐条 `String.contains` 的 O(文本长度 × 模式数) 快很多。
 *
 * 使用 HashMap 存储转移表（稀疏），避免 65536 大小数组带来的内存浪费。
 *
 * 供屏蔽规则的关键词模式批量匹配使用。
 *
 * @param patterns 要匹配的关键词列表，每个关键词关联一个 [ruleId] 用于标识来源规则
 */
class AhoCorasick private constructor(
    private val gotoFn: Array<HashMap<Int, Int>>,
    private val fail: IntArray,
    /** output[i] = 命中节点 i 时输出的 ruleId 列表 */
    private val output: Array<List<String>>,
    private val size: Int,
) {
    /**
     * 在文本中搜索所有匹配的关键词，返回命中的 ruleId 集合
     */
    fun search(text: String): Set<String> {
        if (size <= 1) return emptySet()
        val matched = mutableSetOf<String>()
        var state = 0
        for (ch in text) {
            val code = ch.code
            // 沿 fail 链直到找到能转移的状态
            while (state != 0 && gotoFn[state][code] == null) {
                state = fail[state]
            }
            state = gotoFn[state][code] ?: 0
            if (output[state].isNotEmpty()) {
                matched.addAll(output[state])
            }
        }
        return matched
    }

    companion object {
        /**
         * 构建 Aho-Corasick 自动机
         *
         * @param patterns 关键词列表，每个元素为 (关键词, ruleId)
         * @return 构建好的自动机，若 patterns 为空则返回 null
         */
        fun build(patterns: List<Pair<String, String>>): AhoCorasick? {
            if (patterns.isEmpty()) return null

            // 使用动态列表构建，最后转为数组
            val gotoList = ArrayList< HashMap<Int, Int>>(16)
            val outputList = ArrayList<MutableList<String>>(16)
            gotoList.add(HashMap())
            outputList.add(mutableListOf())
            var nextNode = 1

            // 1. 构建 goto 函数（Trie 树）
            for ((pattern, ruleId) in patterns) {
                if (pattern.isEmpty()) continue
                var current = 0
                for (ch in pattern) {
                    val code = ch.code
                    val child = gotoList[current][code]
                    if (child == null) {
                        if (nextNode >= gotoList.size) {
                            gotoList.add(HashMap())
                            outputList.add(mutableListOf())
                        }
                        gotoList[current][code] = nextNode
                        nextNode++
                        current = nextNode - 1
                    } else {
                        current = child
                    }
                }
                outputList[current].add(ruleId)
            }

            val totalSize = nextNode

            // 2. 转为数组并构建 fail 函数（BFS）
            val gotoFn = Array(totalSize) { i ->
                if (i < gotoList.size) HashMap(gotoList[i]) else HashMap()
            }
            val fail = IntArray(totalSize)
            val output = Array(totalSize) { i ->
                if (i < outputList.size) outputList[i].toList() else emptyList()
            }

            val queue = ArrayDeque<Int>()
            // 根节点的所有直接子节点：fail 指向根
            for ((c, child) in gotoFn[0]) {
                fail[child] = 0
                queue.add(child)
            }

            while (queue.isNotEmpty()) {
                val r = queue.removeFirst()
                for ((c, u) in gotoFn[r]) {
                    queue.add(u)

                    // 沿 fail 链找到能转移 c 的节点
                    var f = fail[r]
                    while (f != 0 && gotoFn[f][c] == null) {
                        f = fail[f]
                    }
                    val fc = gotoFn[f][c]
                    fail[u] = if (fc != null && fc != u) fc else 0

                    // 合并 output
                    if (output[fail[u]].isNotEmpty()) {
                        output[u] = output[u] + output[fail[u]]
                    }
                }
            }

            return AhoCorasick(gotoFn, fail, output, totalSize)
        }
    }
}
