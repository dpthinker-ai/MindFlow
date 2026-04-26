package com.mindflow.app.data.skills

data class SkillExecutionRequest(
    val skill: SkillPackage,
    val invocation: SkillInvocation,
    val secret: String = "",
)

interface JsSkillExecutor {
    suspend fun execute(request: SkillExecutionRequest): SkillResult
}

class UnsupportedJsSkillExecutor : JsSkillExecutor {
    override suspend fun execute(request: SkillExecutionRequest): SkillResult =
        SkillResult.failure("JS skill executor is not attached for skill=${request.skill.manifest.id}")
}

interface SkillRuntime {
    suspend fun execute(invocation: SkillInvocation): SkillResult
}

class DefaultSkillRuntime(
    private val registry: SkillRegistry,
    private val jsSkillExecutor: JsSkillExecutor,
) : SkillRuntime {
    override suspend fun execute(invocation: SkillInvocation): SkillResult {
        val skill = registry.getSkill(invocation.skillId)
            ?: return SkillResult.failure("Skill not found: ${invocation.skillId}")
        return when (skill.manifest.executor) {
            SkillExecutorType.JS -> jsSkillExecutor.execute(
                SkillExecutionRequest(
                    skill = skill,
                    invocation = invocation,
                )
            )
            SkillExecutorType.NATIVE -> SkillResult.failure("Native skill executor is not implemented: ${skill.manifest.id}")
        }
    }
}

