package com.mindflow.app.data.skills

internal object SkillMarkdownParser {
    fun parse(content: String): SkillInstructions? {
        val parts = content.split("---")
        if (parts.size < 3) return null

        val header = parts[1].trim()
        val body = parts.drop(2).joinToString("---").trim()

        var name: String? = null
        var description: String? = null
        header.lines().forEach { line ->
            val trimmed = line.trim()
            when {
                trimmed.startsWith("name:") -> name = trimmed.substringAfter("name:").trim()
                trimmed.startsWith("description:") -> description = trimmed.substringAfter("description:").trim()
            }
        }

        val parsedName = name?.takeIf { it.isNotBlank() } ?: return null
        val parsedDescription = description?.takeIf { it.isNotBlank() } ?: return null
        return SkillInstructions(
            name = parsedName,
            description = parsedDescription,
            body = body,
        )
    }
}

