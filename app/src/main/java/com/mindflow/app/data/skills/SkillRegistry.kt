package com.mindflow.app.data.skills

import android.content.Context

interface SkillRegistry {
    fun getSkill(skillId: String): SkillPackage?

    fun listSkills(): List<SkillPackage>
}

class AssetSkillRegistry(
    private val context: Context,
) : SkillRegistry {
    private val cachedSkills: List<SkillPackage> by lazy(::loadSkills)

    override fun getSkill(skillId: String): SkillPackage? = cachedSkills.firstOrNull { it.manifest.id == skillId }

    override fun listSkills(): List<SkillPackage> = cachedSkills

    private fun loadSkills(): List<SkillPackage> {
        val skillDirs = context.assets.list(ASSET_SKILLS_ROOT).orEmpty()
        return skillDirs.mapNotNull { dir ->
            val basePath = "$ASSET_SKILLS_ROOT/$dir"
            val skillMd = readAsset("$basePath/SKILL.md") ?: return@mapNotNull null
            val manifestJson = readAsset("$basePath/mindflow.skill.json") ?: return@mapNotNull null
            val instructions = SkillMarkdownParser.parse(skillMd) ?: return@mapNotNull null
            val manifest = runCatching { SkillManifestJsonParser.parse(manifestJson) }.getOrNull()
                ?: return@mapNotNull null
            SkillPackage(
                manifest = manifest,
                instructions = instructions,
                assetBasePath = basePath,
            )
        }
    }

    private fun readAsset(path: String): String? = runCatching {
        context.assets.open(path).bufferedReader().use { it.readText() }
    }.getOrNull()

    private companion object {
        const val ASSET_SKILLS_ROOT = "skills"
    }
}

