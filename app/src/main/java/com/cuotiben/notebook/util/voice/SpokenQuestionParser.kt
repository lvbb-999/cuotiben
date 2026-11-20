package com.cuotiben.notebook.util.voice

/**
 * 把口述内容拆成题号、题干、选项、答案。语音识别本身没有标点，
 * 这里只用启发式规则做一次粗分，结果都会放进可编辑输入框里由用户确认。
 */
object SpokenQuestionParser {

    data class Parsed(
        val questionNo: String = "",
        val stem: String = "",
        val options: String = "",
        val answer: String = "",
    )

    private val questionNoRegex = Regex("""第\s*([一二三四五六七八九十百零两\d]{1,4})\s*题""")
    private val answerLetterRegex = Regex(
        """(?:答案|正确答案|所以选|应该选|故选|选择|选)\s*(?:是|为|应选|择)?\s*([A-Da-d])(?![A-Za-z])""",
    )
    private val answerTextRegex = Regex("""(?:答案|正确答案)(?:是|为|应该是)?\s*(.+)$""")

    fun parse(raw: String): Parsed {
        val text = CjkText.joinTokens(raw).trim()
        if (text.isEmpty()) return Parsed()

        val questionNo = questionNoRegex.find(text)?.let { match ->
            "第${toArabic(match.groupValues[1])}题"
        } ?: ""

        val answerIndex = listOf("答案", "正确答案")
            .map { text.indexOf(it) }
            .filter { it >= 0 }
            .minOrNull() ?: text.length
        val body = text.substring(0, answerIndex).trim()

        val answerLetter = answerLetterRegex.find(text)
            ?.groupValues?.get(1)
            ?.uppercase()
            .orEmpty()
        val answerText = if (answerLetter.isNotBlank()) {
            answerLetter
        } else {
            answerTextRegex.find(text)?.groupValues?.get(1)?.trim().orEmpty()
        }

        val optionMatches = optionMarkers(body)
        if (optionMatches.size >= 2) {
            val stem = body.substring(0, optionMatches.first().range.first)
                .trim(' ', '，', ',', '。', '：', ':', '、')
            val options = optionMatches.mapIndexedNotNull { index, match ->
                val letter = match.groupValues[1].uppercase()
                val start = match.range.last + 1
                val end = if (index + 1 < optionMatches.size) {
                    optionMatches[index + 1].range.first
                } else {
                    body.length
                }
                val content = body.substring(start, end)
                    .trim(' ', '，', ',', '。', '：', ':', '、')
                if (content.isBlank()) null else "$letter. $content"
            }.joinToString("\n")
            if (options.isNotBlank()) {
                return Parsed(
                    questionNo = questionNo,
                    stem = stem.ifBlank { body },
                    options = options,
                    answer = answerText,
                )
            }
        }

        return Parsed(
            questionNo = questionNo,
            stem = body.ifBlank { text },
            options = "",
            answer = answerText,
        )
    }

    /**
     * 从正文里找出按顺序递增的选项标记（A、B、C、D）。只接受连续递增的序列，
     * 避免把题干里的英文单词误判成选项。
     */
    private fun optionMarkers(body: String): List<MatchResult> {
        val matches = Regex("""(?:^|[\s，,。；;、])([A-Da-d])\s*[.、,，:：]?\s*""")
            .findAll(body)
            .toList()
        if (matches.isEmpty()) return emptyList()

        val firstLetter = matches.first().groupValues[1].uppercase()[0]
        if (firstLetter !in 'A'..'C') return emptyList()

        val sequence = mutableListOf<MatchResult>()
        var expected = firstLetter
        for (match in matches) {
            val letter = match.groupValues[1].uppercase()[0]
            if (letter == expected) {
                sequence += match
                expected = (expected.code + 1).toChar()
            } else if (sequence.isNotEmpty()) {
                break
            }
        }
        return if (sequence.size >= 2) sequence else emptyList()
    }

    private fun toArabic(text: String): String {
        val direct = text.toIntOrNull()
        if (direct != null) return direct.toString()
        val digits = mapOf(
            '一' to 1, '二' to 2, '两' to 2, '三' to 3, '四' to 4,
            '五' to 5, '六' to 6, '七' to 7, '八' to 8, '九' to 9, '零' to 0,
        )
        if (text == "十") return "10"
        val tenIndex = text.indexOf('十')
        if (tenIndex >= 0) {
            val tens = if (tenIndex == 0) 1 else digits[text[0]] ?: 1
            val ones = if (tenIndex == text.lastIndex) 0 else digits[text[tenIndex + 1]] ?: 0
            return (tens * 10 + ones).toString()
        }
        return digits[text.firstOrNull()]?.toString() ?: text
    }
}
