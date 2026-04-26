package com.mindflow.app.data.skills

internal sealed interface SkillJsonValue {
    data class JsonObject(val values: Map<String, SkillJsonValue>) : SkillJsonValue
    data class JsonArray(val items: List<SkillJsonValue>) : SkillJsonValue
    data class JsonString(val value: String) : SkillJsonValue
    data class JsonNumber(val value: Double) : SkillJsonValue
    data class JsonBoolean(val value: Boolean) : SkillJsonValue
    data object JsonNull : SkillJsonValue
}

internal class SkillMiniJsonParser(
    private val raw: String,
) {
    private var index = 0

    fun parseObject(): Map<String, SkillJsonValue> {
        skipWhitespace()
        val value = parseValue()
        skipWhitespace()
        require(index == raw.length) { "Unexpected trailing JSON content." }
        return (value as? SkillJsonValue.JsonObject)?.values
            ?: throw IllegalArgumentException("Root JSON value must be an object.")
    }

    private fun parseValue(): SkillJsonValue {
        skipWhitespace()
        return when (val current = currentChar()) {
            '{' -> parseObjectValue()
            '[' -> parseArrayValue()
            '"' -> SkillJsonValue.JsonString(parseString())
            't' -> {
                consumeLiteral("true")
                SkillJsonValue.JsonBoolean(true)
            }
            'f' -> {
                consumeLiteral("false")
                SkillJsonValue.JsonBoolean(false)
            }
            'n' -> {
                consumeLiteral("null")
                SkillJsonValue.JsonNull
            }
            '-', in '0'..'9' -> SkillJsonValue.JsonNumber(parseNumber())
            else -> throw IllegalArgumentException("Unexpected JSON token '$current' at index $index.")
        }
    }

    private fun parseObjectValue(): SkillJsonValue.JsonObject {
        expect('{')
        skipWhitespace()
        if (peekChar() == '}') {
            index++
            return SkillJsonValue.JsonObject(emptyMap())
        }

        val values = linkedMapOf<String, SkillJsonValue>()
        while (true) {
            skipWhitespace()
            val key = parseString()
            skipWhitespace()
            expect(':')
            values[key] = parseValue()
            skipWhitespace()
            when (peekChar()) {
                ',' -> index++
                '}' -> {
                    index++
                    break
                }
                else -> throw IllegalArgumentException("Expected ',' or '}' at index $index.")
            }
        }
        return SkillJsonValue.JsonObject(values)
    }

    private fun parseArrayValue(): SkillJsonValue.JsonArray {
        expect('[')
        skipWhitespace()
        if (peekChar() == ']') {
            index++
            return SkillJsonValue.JsonArray(emptyList())
        }

        val items = mutableListOf<SkillJsonValue>()
        while (true) {
            items += parseValue()
            skipWhitespace()
            when (peekChar()) {
                ',' -> index++
                ']' -> {
                    index++
                    break
                }
                else -> throw IllegalArgumentException("Expected ',' or ']' at index $index.")
            }
        }
        return SkillJsonValue.JsonArray(items)
    }

    private fun parseString(): String {
        expect('"')
        val builder = StringBuilder()
        while (true) {
            require(index < raw.length) { "Unterminated JSON string." }
            when (val current = raw[index++]) {
                '"' -> return builder.toString()
                '\\' -> builder.append(parseEscape())
                else -> builder.append(current)
            }
        }
    }

    private fun parseEscape(): Char {
        require(index < raw.length) { "Unterminated JSON escape." }
        return when (val current = raw[index++]) {
            '"', '\\', '/' -> current
            'b' -> '\b'
            'f' -> '\u000C'
            'n' -> '\n'
            'r' -> '\r'
            't' -> '\t'
            'u' -> {
                require(index + 4 <= raw.length) { "Invalid unicode escape." }
                val hex = raw.substring(index, index + 4)
                index += 4
                hex.toInt(16).toChar()
            }
            else -> throw IllegalArgumentException("Unsupported escape '$current' at index $index.")
        }
    }

    private fun parseNumber(): Double {
        val start = index
        if (peekChar() == '-') index++
        consumeDigits()
        if (peekChar() == '.') {
            index++
            consumeDigits()
        }
        if (peekChar() == 'e' || peekChar() == 'E') {
            index++
            if (peekChar() == '+' || peekChar() == '-') index++
            consumeDigits()
        }
        return raw.substring(start, index).toDouble()
    }

    private fun consumeDigits() {
        require(peekChar().isDigit()) { "Expected digit at index $index." }
        while (peekChar().isDigit()) {
            index++
        }
    }

    private fun consumeLiteral(literal: String) {
        require(raw.startsWith(literal, index)) { "Expected '$literal' at index $index." }
        index += literal.length
    }

    private fun expect(expected: Char) {
        require(currentChar() == expected) { "Expected '$expected' at index $index." }
        index++
    }

    private fun currentChar(): Char {
        require(index < raw.length) { "Unexpected end of JSON input." }
        return raw[index]
    }

    private fun peekChar(): Char = raw.getOrNull(index) ?: '\u0000'

    private fun skipWhitespace() {
        while (index < raw.length && raw[index].isWhitespace()) {
            index++
        }
    }
}
