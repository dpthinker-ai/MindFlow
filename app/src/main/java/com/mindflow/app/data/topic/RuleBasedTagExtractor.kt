package com.mindflow.app.data.topic

import com.mindflow.app.data.model.NoteTagCodec
import com.mindflow.app.data.model.TagSource
import com.mindflow.app.data.model.TagSuggestion

class RuleBasedTagExtractor {
    private val noiseWords = setOf(
        "我", "我们", "这个", "那个", "一下", "一些", "然后", "需要", "可以", "自己",
        "记录", "想法", "事情", "内容", "今天", "最近", "应该", "感觉", "问题",
    )

    fun extract(content: String): List<String> {
        val candidates = buildList {
            Regex("[\\u4E00-\\u9FFF]{2,8}|[A-Za-z][A-Za-z0-9+-]{2,15}")
                .findAll(content)
                .map { it.value.trim() }
                .filter { token -> token !in noiseWords }
                .take(12)
                .forEach(::add)

            val topicLike = RuleBasedTopicExtractor().extract(content)
            if (topicLike.isNotBlank() && topicLike != "未命名想法") {
                add(topicLike)
            }
        }
        return NoteTagCodec.normalize(candidates)
    }

    fun toSuggestion(content: String): TagSuggestion =
        TagSuggestion(
            tags = extract(content),
            source = TagSource.RULE,
        )
}
