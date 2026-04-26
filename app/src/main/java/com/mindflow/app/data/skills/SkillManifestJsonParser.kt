package com.mindflow.app.data.skills

internal object SkillManifestJsonParser {
    fun parse(content: String): SkillManifest {
        val json = SkillMiniJsonParser(content).parseObject()
        val remote = json.objectValue("remote")
        return SkillManifest(
            id = json.requireString("id"),
            name = json.requireString("name"),
            description = json.requireString("description"),
            version = json.requireString("version"),
            entry = json.requireString("entry"),
            executor = json.stringValue("executor")?.toSkillExecutorType() ?: SkillExecutorType.JS,
            output = json.stringArrayValue("output").mapTo(linkedSetOf()) { it.toSkillOutputType() },
            permissions = json.stringArrayValue("permissions").toSet(),
            nativeApis = json.stringArrayValue("nativeApis").toSet(),
            remote = SkillRemoteConfig(
                signatureRequired = remote.booleanValue("signatureRequired") ?: false,
            ),
        )
    }

    private fun Map<String, SkillJsonValue>.requireString(key: String): String =
        stringValue(key) ?: throw IllegalArgumentException("Missing string field '$key'.")

    private fun Map<String, SkillJsonValue>.stringValue(key: String): String? =
        (this[key] as? SkillJsonValue.JsonString)?.value?.takeIf { it.isNotBlank() }

    private fun Map<String, SkillJsonValue>.booleanValue(key: String): Boolean? =
        (this[key] as? SkillJsonValue.JsonBoolean)?.value

    private fun Map<String, SkillJsonValue>.objectValue(key: String): Map<String, SkillJsonValue> =
        (this[key] as? SkillJsonValue.JsonObject)?.values ?: emptyMap()

    private fun Map<String, SkillJsonValue>.stringArrayValue(key: String): List<String> {
        val array = this[key] as? SkillJsonValue.JsonArray ?: return emptyList()
        return array.items
            .mapNotNull { (it as? SkillJsonValue.JsonString)?.value?.takeIf(String::isNotBlank) }
    }

    private fun String.toSkillExecutorType(): SkillExecutorType = when (trim().uppercase()) {
        "NATIVE" -> SkillExecutorType.NATIVE
        else -> SkillExecutorType.JS
    }

    private fun String.toSkillOutputType(): SkillOutputType = when (trim().uppercase()) {
        "WEBVIEW" -> SkillOutputType.WEBVIEW
        "IMAGE" -> SkillOutputType.IMAGE
        "CARD" -> SkillOutputType.CARD
        else -> SkillOutputType.TEXT
    }
}
