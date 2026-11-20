package com.cuotiben.notebook.util.voice

/**
 * Vosk 中文模型的输出是分词结果，字与字之间带空格。这里把中文之间的空格去掉，
 * 同时保留英文和数字之间的空格。
 */
object CjkText {

    fun joinTokens(raw: String): String {
        val tokens = raw.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
        if (tokens.isEmpty()) return ""
        val builder = StringBuilder(tokens.first())
        for (index in 1 until tokens.size) {
            val previous = builder.lastOrNull() ?: ' '
            val current = tokens[index].first()
            val needsSpace = !(isCjk(previous) && isCjk(current)) &&
                !isPunctuation(previous) &&
                !isPunctuation(current)
            if (needsSpace) builder.append(' ')
            builder.append(tokens[index])
        }
        return builder.toString()
            .replace(Regex("\\s+([，。！？；：、）】》」』,.!?;:)])"), "$1")
            .replace(Regex("([（【《「『(])\\s+"), "$1")
    }

    private fun isCjk(char: Char): Boolean =
        char.code in 0x4E00..0x9FFF || char.code in 0x3400..0x4DBF

    private fun isPunctuation(char: Char): Boolean =
        char in "，。！？；：、（）【】《》「」『』,.;:!?()[]“”‘’…-—"
}
