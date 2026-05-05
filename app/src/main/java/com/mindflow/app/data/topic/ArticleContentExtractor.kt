package com.mindflow.app.data.topic

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.URI
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

data class ExtractedArticleContent(
    val url: String,
    val title: String,
    val host: String,
    val body: String,
    val excerpt: String = "",
)

sealed interface ArticleExtractionResult {
    data class Success(val article: ExtractedArticleContent) : ArticleExtractionResult
    data class Failure(val message: String) : ArticleExtractionResult
}

fun interface ArticleHtmlFetcher {
    suspend fun fetch(url: String): String
}

class OkHttpArticleHtmlFetcher(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(12, TimeUnit.SECONDS)
        .followRedirects(true)
        .build(),
) : ArticleHtmlFetcher {
    override suspend fun fetch(url: String): String = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(url)
            .header(
                "User-Agent",
                "Mozilla/5.0 (Linux; Android 16) AppleWebKit/537.36 MindFlow/3.3",
            )
            .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
            .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                error("HTTP ${response.code}")
            }
            response.body?.string().orEmpty()
        }
    }
}

class ArticleContentExtractor(
    private val fetcher: ArticleHtmlFetcher = OkHttpArticleHtmlFetcher(),
) {
    suspend fun extract(rawUrl: String): ArticleExtractionResult {
        val normalizedUrl = normalizeArticleUrl(rawUrl)
            ?: return ArticleExtractionResult.Failure("请先粘贴有效链接")
        return runCatching {
            extractTwitterStatus(normalizedUrl)?.let { return@runCatching it }
            val html = fetcher.fetch(normalizedUrl)
            parseArticleHtml(normalizedUrl, html)
        }.fold(
            onSuccess = { article ->
                if (article.body.isBlank()) {
                    ArticleExtractionResult.Failure("没有提取到可用正文")
                } else {
                    ArticleExtractionResult.Success(article)
                }
            },
            onFailure = { error ->
                ArticleExtractionResult.Failure(error.message ?: "链接正文提取失败")
            },
        )
    }

    private suspend fun extractTwitterStatus(url: String): ExtractedArticleContent? {
        if (!isTwitterStatusUrl(url)) return null
        val oEmbedUrl = buildTwitterOEmbedUrl(url)
        return runCatching {
            parseTwitterOEmbedArticle(
                url = url,
                json = fetcher.fetch(oEmbedUrl),
            )
        }.getOrNull()?.takeIf { it.body.isNotBlank() }
    }
}

internal fun normalizeArticleUrl(rawUrl: String): String? {
    val trimmed = rawUrl.trim()
    if (trimmed.isBlank()) return null
    val candidate = if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) {
        trimmed
    } else {
        "https://$trimmed"
    }
    val uri = runCatching { URI(candidate) }.getOrNull() ?: return null
    val scheme = uri.scheme?.lowercase()
    val host = uri.host?.trim().orEmpty()
    if (scheme !in setOf("http", "https") || host.isBlank()) return null
    return candidate
}

internal fun parseArticleHtml(
    url: String,
    html: String,
): ExtractedArticleContent {
    val host = runCatching { URI(url).host.orEmpty().removePrefix("www.") }
        .getOrDefault("")
        .ifBlank { "未知来源" }
    val title = firstNonBlank(
        metaContent(html, "property", "og:title"),
        metaContent(html, "name", "twitter:title"),
        tagText(html, "h1"),
        tagText(html, "title"),
    ).take(80)
    val excerpt = firstNonBlank(
        metaContent(html, "name", "description"),
        metaContent(html, "property", "og:description"),
    ).take(180)
    val articleBlock = largestReadableBlock(html)
    val body = htmlBlockToPlainText(articleBlock)
        .lineSequence()
        .map { it.trim() }
        .filter { line ->
            line.length >= 8 &&
                !line.equals(title, ignoreCase = true) &&
                !line.startsWith("Copyright", ignoreCase = true) &&
                !line.startsWith("版权所有")
        }
        .distinct()
        .joinToString("\n")
        .take(8_000)
        .trim()
    return ExtractedArticleContent(
        url = url,
        title = title.ifBlank { host },
        host = host,
        body = body,
        excerpt = excerpt,
    )
}

internal fun isTwitterStatusUrl(url: String): Boolean {
    val uri = runCatching { URI(url) }.getOrNull() ?: return false
    val host = uri.host?.lowercase().orEmpty().removePrefix("www.")
    val path = uri.path.orEmpty()
    return host in setOf("x.com", "twitter.com", "mobile.twitter.com") &&
        (Regex("""/(i/)?status/\d+""").containsMatchIn(path) || Regex("""/[^/]+/status/\d+""").containsMatchIn(path))
}

internal fun buildTwitterOEmbedUrl(url: String): String =
    "https://publish.twitter.com/oembed?omit_script=1&url=${URLEncoder.encode(url, "UTF-8")}"

internal fun parseTwitterOEmbedArticle(
    url: String,
    json: String,
): ExtractedArticleContent {
    val html = jsonStringField(json, "html")
    val author = jsonStringField(json, "author_name")
    val canonicalUrl = jsonStringField(json, "url").ifBlank { url }
    val body = htmlBlockToPlainText(html)
        .lineSequence()
        .map { it.trim() }
        .filter { line ->
            line.length >= 8 &&
                !line.startsWith("pic.twitter.com", ignoreCase = true)
        }
        .distinct()
        .joinToString("\n")
        .take(8_000)
        .trim()
    val host = runCatching { URI(canonicalUrl).host.orEmpty().removePrefix("www.") }
        .getOrDefault("twitter.com")
        .ifBlank { "twitter.com" }
    return ExtractedArticleContent(
        url = canonicalUrl,
        title = author.takeIf { it.isNotBlank() }?.let { "$it 的 X 动态" } ?: "X 动态",
        host = host,
        body = body,
        excerpt = body.take(180),
    )
}

private fun firstNonBlank(vararg values: String): String =
    values.firstOrNull { it.isNotBlank() }.orEmpty()

private fun metaContent(
    html: String,
    attrName: String,
    attrValue: String,
): String {
    val quoted = Regex.escape(attrValue)
    val attr = Regex.escape(attrName)
    val pattern = Regex(
        """<meta\b(?=[^>]*\b$attr\s*=\s*["']$quoted["'])(?=[^>]*\bcontent\s*=\s*["']([^"']*)["'])[^>]*>""",
        RegexOption.IGNORE_CASE,
    )
    return pattern.find(html)?.groupValues?.getOrNull(1)?.let(::decodeHtmlEntities)?.trim().orEmpty()
}

private fun tagText(html: String, tag: String): String {
    val pattern = Regex("""<$tag\b[^>]*>(.*?)</$tag>""", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
    return pattern.find(html)?.groupValues?.getOrNull(1)?.let(::htmlBlockToPlainText).orEmpty()
}

private fun largestReadableBlock(html: String): String {
    val cleaned = html
        .replace(Regex("""<script\b[^>]*>.*?</script>""", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)), " ")
        .replace(Regex("""<style\b[^>]*>.*?</style>""", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)), " ")
        .replace(Regex("""<(nav|footer|header|aside|form|svg)\b[^>]*>.*?</\1>""", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)), " ")
    val candidates = buildList {
        listOf("article", "main", "section", "body").forEach { tag ->
            Regex("""<$tag\b[^>]*>.*?</$tag>""", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
                .findAll(cleaned)
                .forEach { add(it.value) }
        }
    }
    return candidates.maxByOrNull { htmlBlockToPlainText(it).length } ?: cleaned
}

internal fun htmlBlockToPlainText(block: String): String =
    block
        .replace(Regex("""<(br|hr)\s*/?>""", RegexOption.IGNORE_CASE), "\n")
        .replace(Regex("""</(p|div|li|h[1-6]|blockquote|section|article|main)>""", RegexOption.IGNORE_CASE), "\n")
        .replace(Regex("""<[^>]+>"""), " ")
        .let(::decodeHtmlEntities)
        .replace(Regex("""[ \t\u00A0]+"""), " ")
        .replace(Regex("""\n\s*\n+"""), "\n")
        .trim()

private fun decodeHtmlEntities(raw: String): String {
    val named = raw
        .replace("&nbsp;", " ")
        .replace("&amp;", "&")
        .replace("&lt;", "<")
        .replace("&gt;", ">")
        .replace("&quot;", "\"")
        .replace("&#39;", "'")
        .replace("&apos;", "'")
    return Regex("""&#(x?[0-9A-Fa-f]+);""").replace(named) { match ->
        val rawCode = match.groupValues[1]
        val codePoint = if (rawCode.startsWith("x", ignoreCase = true)) {
            rawCode.drop(1).toIntOrNull(16)
        } else {
            rawCode.toIntOrNull()
        }
        codePoint?.takeIf { it > 0 }?.let { String(Character.toChars(it)) } ?: match.value
    }
}

private fun jsonStringField(
    json: String,
    fieldName: String,
): String {
    val pattern = Regex(""""${Regex.escape(fieldName)}"\s*:\s*"((?:\\.|[^"\\])*)"""")
    return pattern.find(json)
        ?.groupValues
        ?.getOrNull(1)
        ?.let(::decodeJsonString)
        ?.trim()
        .orEmpty()
}

private fun decodeJsonString(raw: String): String {
    val builder = StringBuilder(raw.length)
    var index = 0
    while (index < raw.length) {
        val char = raw[index]
        if (char != '\\' || index == raw.lastIndex) {
            builder.append(char)
            index += 1
            continue
        }
        when (val escaped = raw[index + 1]) {
            '"', '\\', '/' -> {
                builder.append(escaped)
                index += 2
            }
            'b' -> {
                builder.append('\b')
                index += 2
            }
            'f' -> {
                builder.append('\u000C')
                index += 2
            }
            'n' -> {
                builder.append('\n')
                index += 2
            }
            'r' -> {
                builder.append('\r')
                index += 2
            }
            't' -> {
                builder.append('\t')
                index += 2
            }
            'u' -> {
                val code = raw.substring(index + 2, (index + 6).coerceAtMost(raw.length)).takeIf { it.length == 4 }
                    ?.toIntOrNull(16)
                if (code != null) {
                    builder.append(code.toChar())
                    index += 6
                } else {
                    builder.append("\\u")
                    index += 2
                }
            }
            else -> {
                builder.append(escaped)
                index += 2
            }
        }
    }
    return builder.toString()
}
