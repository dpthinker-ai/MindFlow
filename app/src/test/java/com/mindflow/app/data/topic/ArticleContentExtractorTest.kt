package com.mindflow.app.data.topic

import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.junit.Test

class ArticleContentExtractorTest {
    @Test
    fun normalizeArticleUrl_acceptsBareDomainsAndRejectsInvalidInput() {
        assertThat(normalizeArticleUrl("example.com/post")).isEqualTo("https://example.com/post")
        assertThat(normalizeArticleUrl("https://example.com/post")).isEqualTo("https://example.com/post")
        assertThat(normalizeArticleUrl("not a url")).isNull()
        assertThat(normalizeArticleUrl("")).isNull()
    }

    @Test
    fun parseArticleHtml_prefersArticleBodyAndMetadataTitle() {
        val parsed = parseArticleHtml(
            url = "https://example.com/post/1",
            html = """
                <html>
                  <head>
                    <meta property="og:title" content="链接页需要正文提取" />
                    <meta name="description" content="这是一段页面描述。" />
                  </head>
                  <body>
                    <nav>首页 导航</nav>
                    <article>
                      <h1>链接页需要正文提取</h1>
                      <p>第一段正文说明用户粘贴链接后应该看到真正内容。</p>
                      <p>第二段正文强调 AI 洞察应该统一承载摘要和要点。</p>
                    </article>
                    <footer>Copyright 2026</footer>
                  </body>
                </html>
            """.trimIndent(),
        )

        assertThat(parsed.host).isEqualTo("example.com")
        assertThat(parsed.title).isEqualTo("链接页需要正文提取")
        assertThat(parsed.excerpt).contains("页面描述")
        assertThat(parsed.body).contains("第一段正文")
        assertThat(parsed.body).contains("第二段正文")
        assertThat(parsed.body).doesNotContain("首页 导航")
    }

    @Test
    fun extractorFetchesHtmlAndReturnsReadableBody() = runTest {
        val extractor = ArticleContentExtractor(
            fetcher = ArticleHtmlFetcher {
                """
                <html><body><main>
                    <p>MindFlow 链接输入需要把网页正文提取出来。</p>
                    <p>提取结果会写回正文区域，后续再生成 AI 洞察。</p>
                </main></body></html>
                """.trimIndent()
            },
        )

        val result = extractor.extract("https://mindflow.local/article")

        assertThat(result).isInstanceOf(ArticleExtractionResult.Success::class.java)
        val success = result as ArticleExtractionResult.Success
        assertThat(success.article.body).contains("网页正文提取")
        assertThat(success.article.body).contains("AI 洞察")
    }

    @Test
    fun extractorUsesTwitterOEmbedForXStatusPages() = runTest {
        val extractor = ArticleContentExtractor(
            fetcher = ArticleHtmlFetcher { url ->
                if (url.startsWith("https://publish.twitter.com/oembed")) {
                    """
                    {
                      "url":"https:\/\/twitter.com\/RohOnChain\/status\/2044462381997363334",
                      "author_name":"Roan",
                      "html":"\u003Cblockquote class=\"twitter-tweet\"\u003E\u003Cp lang=\"en\" dir=\"ltr\"\u003EThis 2 hour Stanford lecture will teach you more about how LLMs like ChatGPT &amp; Claude are built.\u003Cbr\u003E\u003Cbr\u003EBookmark this &amp; give 2 hours today. \u003Ca href=\"https:\/\/t.co\/M40bJuyrFx\"\u003Epic.twitter.com\/M40bJuyrFx\u003C\/a\u003E\u003C\/p\u003E&mdash; Roan (@RohOnChain) \u003Ca href=\"https:\/\/twitter.com\/RohOnChain\/status\/2044462381997363334?ref_src=twsrc%5Etfw\"\u003EApril 15, 2026\u003C\/a\u003E\u003C\/blockquote\u003E"
                    }
                    """.trimIndent()
                } else {
                    """
                    <html><body><main>
                        <p>We’ve detected that JavaScript is disabled in this browser.</p>
                        <p>Please enable JavaScript or switch to a supported browser to continue using x.com.</p>
                    </main></body></html>
                    """.trimIndent()
                }
            },
        )

        val result = extractor.extract("https://x.com/i/status/2044462381997363334")

        assertThat(result).isInstanceOf(ArticleExtractionResult.Success::class.java)
        val success = result as ArticleExtractionResult.Success
        assertThat(success.article.title).contains("Roan")
        assertThat(success.article.body).contains("Stanford lecture")
        assertThat(success.article.body).contains("ChatGPT & Claude")
        assertThat(success.article.body).doesNotContain("JavaScript is disabled")
    }
}
