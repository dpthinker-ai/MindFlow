package com.mindflow.app.data.localmodel

object GemmaTaskPromptFactory {
    fun polish(content: String): String = """
        你在做中文正文润色。
        目标：保留原意、保留原始语气、做最小充分修改。
        约束：不新增原文没有的信息；不扩写观点；优先修病句、重复、跳跃表达；不要写成模板腔。
        只返回 JSON：{"polishedText":"...","changeSummary":"..."}
        原文：$content
    """.trimIndent()

    fun extractTopic(content: String): String = """
        你在提取笔记主题。
        只输出一个短而可检索的主题短语。
        禁止使用“记录”“想法”“学习”“随想”等空泛主题，禁止直接复述整段正文。
        只返回 JSON：{"topic":"...","confidence":0.0,"whyThisTopic":"..."}
        原文：$content
    """.trimIndent()

    fun extractTags(content: String): String = """
        你在提取主分类和少量高密度标签。
        只返回 JSON：{"primaryCategory":"...","tags":["..."],"discardedCandidates":["..."]}
        原文：$content
    """.trimIndent()

    fun classifyFolder(content: String): String = """
        你只允许输出 work、life、project、health 四个文件夹之一。
        只返回 JSON：{"folderKey":"...","confidence":0.0}
        原文：$content
    """.trimIndent()

    fun extractGraphConcepts(context: String): String = """
        你在抽取知识图谱候选知识点。
        只返回 JSON：{"concepts":["..."]}
        上下文数据：$context
    """.trimIndent()

    fun canonicalizeGraphConcepts(context: String): String = """
        你在合并同义、近义和不同表述的知识点。
        只返回 JSON：{"canonical":{"标准概念":["别名1","别名2"]}}
        上下文数据：$context
    """.trimIndent()

    fun generateGraphRelations(context: String): String = """
        你在生成知识图谱的局部关系。
        只判断中心知识点与候选邻居之间的关系。
        只处理这一层局部关系，不要补充未提供的新节点。
        只返回 JSON：{"relations":[{"fromConceptId":"...","toConceptId":"...","relationType":"supports|advances|parallel|references|contrasts","reasonLine":"...","confidence":0.0}]}
        上下文数据：$context
    """.trimIndent()

    fun generateGraphRelations(center: String, neighbors: List<String>): String {
        val neighborList = neighbors.joinToString(separator = ", ")
        return generateGraphRelations(
            context = "中心知识点：$center\n候选邻居：$neighborList",
        )
    }
}
