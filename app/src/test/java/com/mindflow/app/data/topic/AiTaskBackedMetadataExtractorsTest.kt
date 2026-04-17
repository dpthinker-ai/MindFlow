package com.mindflow.app.data.topic

import com.google.common.truth.Truth.assertThat
import com.mindflow.app.data.ai.AiExecutionMode
import com.mindflow.app.data.ai.AiTaskInput
import com.mindflow.app.data.ai.AiTaskPayload
import com.mindflow.app.data.ai.AiTaskProvider
import com.mindflow.app.data.ai.AiTaskRequest
import com.mindflow.app.data.ai.AiTaskRouter
import kotlinx.coroutines.test.runTest
import org.junit.Test

class AiTaskBackedMetadataExtractorsTest {
    @Test
    fun `topic extractor uses router payload and keeps normalized topic`() = runTest {
        val extractor = AiTopicExtractor(
            aiTaskRouter = routerWith(AiTaskPayload.Topic(topic = "睡眠恢复", confidence = 0.86f)),
        )

        val result = extractor.extract("睡眠影响恢复")

        assertThat(result.topic).isEqualTo("睡眠恢复")
        assertThat(result.notice).isNull()
    }

    @Test
    fun `tag extractor uses router payload`() = runTest {
        val extractor = AiTagExtractor(
            aiTaskRouter = routerWith(AiTaskPayload.Tags(tags = listOf("睡眠", "恢复"), primaryCategory = "health")),
        )

        val result = extractor.extract("睡眠影响恢复")

        assertThat(result.tags).containsExactly("睡眠", "恢复").inOrder()
    }

    @Test
    fun `folder classifier normalizes router payload`() = runTest {
        val classifier = AiFolderClassifier(
            aiTaskRouter = routerWith(AiTaskPayload.Folder(folderKey = "HEALTH", confidence = 0.9f)),
        )

        val result = classifier.classify("睡眠影响恢复")

        assertThat(result.folderKey).isEqualTo("health")
    }

    private fun routerWith(payload: AiTaskPayload): AiTaskRouter = AiTaskRouter(
        resolveMode = { AiExecutionMode.AUTOMATIC },
        onDeviceProvider = FakeProvider(payload),
        cloudProvider = FakeProvider(null),
    )

    private class FakeProvider(
        private val payload: AiTaskPayload?,
    ) : AiTaskProvider {
        @Suppress("UNCHECKED_CAST")
        override suspend fun <T : AiTaskPayload> run(request: AiTaskRequest<T>): T? = payload as T?
    }
}
