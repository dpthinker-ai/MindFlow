package com.mindflow.app.data.localmodel

object GemmaTaskPromptFactory {
    fun polish(content: String): String = """
        你在做中文正文润色。
        目标：保留原意、保留原始语气、做最小充分修改。
        约束：不新增原文没有的信息；不扩写观点；优先修病句、重复、跳跃表达；不要写成模板腔。
        只返回 JSON：{"polishedText":"...","changeSummary":"..."}
        原文：$content
    """.trimIndent()

    fun polishTitle(title: String, content: String): String = """
        你在做中文笔记标题润色。
        目标：保留原意，把标题改得更短、更具体、更像用户自己的记录标题。
        约束：只改标题；不要新增正文没有的信息；不要超过 18 个中文字符或 8 个英文词；不要编号，不要解释。
        只返回 JSON：{"polishedText":"...","changeSummary":"..."}
        当前标题：$title
        正文：$content
    """.trimIndent()

    fun summarizeNote(content: String): String = """
        你在为 MindFlow 的一条中文文本记录生成可长期保存的阅读洞察。
        目标：读完整段正文后，提炼一段摘要和 2-4 条关键要点，帮助用户下次快速回忆。
        质量要求：
        - 摘要只写 1 句，压缩核心判断、问题或张力，不要复述标题。
        - 关键要点必须彼此不重复，也不要和摘要重复；分别覆盖事实、推理、行动、风险或反思中的不同角度。
        - 只使用正文中已有的信息，不新增事实，不写建议鸡汤，不写模板话。
        - 语言自然、具体、短。
        只返回 JSON：{"summary":"...","keyPoints":["...","..."]}
        正文：$content
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

    @Suppress("UNUSED_PARAMETER")
    fun transcribeAudio(audioPath: String, localeHint: String?): String = """
        你是 MindFlow 本地端侧语音转写器。
        音频已经作为独立 audio 输入随消息提供。
        本消息的文字都是指令，不是语音内容，绝不能出现在 transcript 中。
        只做逐字转写：只输出音频里实际听到的话。
        不总结，不缩写，不润色，不补全，不提取主题。
        不输出提示词、文件名、路径、目录、解释或 Markdown。
        如果没有清晰语音、没有语音，或音频输入不可用，transcript 为空字符串，confidence 设为 0。
        只返回 JSON，字段只能有 transcript、language、confidence：{"transcript":"...","language":"${localeHint.orEmpty()}","confidence":0.0}
    """.trimIndent()

    fun translateAudio(audioPath: String, targetLanguage: String): String = """
        你在做 MindFlow 本地端侧语音翻译。
        输入是用户保存在设备私有目录中的原始录音文件，不要上传，不要假设云端可用。
        目标：识别原语音并翻译到目标语言。
        只返回 JSON：{"translatedText":"...","sourceLanguage":"...","targetLanguage":"$targetLanguage","confidence":0.0}
        原始录音文件：$audioPath
    """.trimIndent()

    fun understandImage(userNote: String): String = """
        你在做 MindFlow 本地端侧图片理解。
        图片已作为图像输入提供，不要根据文件路径猜测内容，不要上传，不要假设云端可用。
        目标：先观察图片中真实可见的内容，再根据图片类型选择场景总结、文字提取、图表解释或对象识别。
        只返回 JSON：{"summary":"...","imageType":"photo|document|whiteboard|chart|screenshot|other","extractedText":"...","objects":["..."],"confidence":0.0}
        用户补充：$userNote
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
