package com.websockeexample.data

internal sealed interface JVal {
    data class Obj(val fields: Map<String, JVal>) : JVal
    data class Arr(val items: List<JVal>) : JVal
    data class Str(val value: String) : JVal
    data class Num(val raw: String) : JVal
    data class Bool(val value: Boolean) : JVal
    data object Null : JVal
}

internal fun parseJson(text: String): JVal = JsonParser(text).parse()

internal fun Map<String, JVal>.text(key: String): String? = (this[key] as? JVal.Str)?.value

internal fun Map<String, JVal>.decimal(key: String): Double? {
    val raw = when (val value = this[key]) {
        is JVal.Str -> value.value
        is JVal.Num -> value.raw
        else -> return null
    }
    return raw.toDoubleOrNull()
}

internal fun Map<String, JVal>.long(key: String): Long? {
    val raw = when (val value = this[key]) {
        is JVal.Num -> value.raw
        is JVal.Str -> value.value
        else -> return null
    }
    return raw.substringBefore('.').toLongOrNull()
}

internal fun Map<String, JVal>.bool(key: String): Boolean? = when (val value = this[key]) {
    is JVal.Bool -> value.value
    is JVal.Str -> value.value.equals("true", ignoreCase = true)
    else -> null
}

private class JsonParser(private val text: String) {
    private var index = 0

    fun parse(): JVal {
        skip()
        return readValue()
    }

    private fun readValue(): JVal {
        skip()
        if (index >= text.length) throw IllegalArgumentException("неожиданный конец JSON")
        return when (text[index]) {
            '{' -> readObject()
            '[' -> readArray()
            '"' -> JVal.Str(readString())
            't' -> {
                expect("true")
                JVal.Bool(true)
            }
            'f' -> {
                expect("false")
                JVal.Bool(false)
            }
            'n' -> {
                expect("null")
                JVal.Null
            }
            else -> readNumber()
        }
    }

    private fun readObject(): JVal.Obj {
        expectChar('{')
        val fields = linkedMapOf<String, JVal>()
        skip()
        if (peek('}')) {
            index++
            return JVal.Obj(fields)
        }
        while (true) {
            skip()
            val key = readString()
            skip()
            expectChar(':')
            fields[key] = readValue()
            skip()
            when {
                peek(',') -> index++
                peek('}') -> {
                    index++
                    break
                }
                else -> throw IllegalArgumentException("ожидалась запятая или }")
            }
        }
        return JVal.Obj(fields)
    }

    private fun readArray(): JVal.Arr {
        expectChar('[')
        val items = mutableListOf<JVal>()
        skip()
        if (peek(']')) {
            index++
            return JVal.Arr(items)
        }
        while (true) {
            items += readValue()
            skip()
            when {
                peek(',') -> index++
                peek(']') -> {
                    index++
                    break
                }
                else -> throw IllegalArgumentException("ожидалась запятая или ]")
            }
        }
        return JVal.Arr(items)
    }

    private fun readString(): String {
        expectChar('"')
        val out = StringBuilder()
        while (index < text.length) {
            when (val char = text[index++]) {
                '"' -> return out.toString()
                '\\' -> {
                    if (index >= text.length) throw IllegalArgumentException("оборванный escape")
                    when (val escaped = text[index++]) {
                        '"', '\\', '/' -> out.append(escaped)
                        'b' -> out.append('\b')
                        'f' -> out.append('\u000C')
                        'n' -> out.append('\n')
                        'r' -> out.append('\r')
                        't' -> out.append('\t')
                        'u' -> {
                            if (index + 4 > text.length) throw IllegalArgumentException("оборванный unicode")
                            val hex = text.substring(index, index + 4)
                            index += 4
                            out.append(hex.toInt(16).toChar())
                        }
                        else -> throw IllegalArgumentException("неизвестный escape")
                    }
                }
                else -> out.append(char)
            }
        }
        throw IllegalArgumentException("строка не закрыта")
    }

    private fun readNumber(): JVal.Num {
        val start = index
        if (text[index] == '-') index++
        if (index >= text.length || !text[index].isDigit()) {
            throw IllegalArgumentException("некорректное число")
        }
        while (index < text.length && text[index].isDigit()) index++
        if (index < text.length && text[index] == '.') {
            index++
            while (index < text.length && text[index].isDigit()) index++
        }
        if (index < text.length && (text[index] == 'e' || text[index] == 'E')) {
            index++
            if (index < text.length && (text[index] == '+' || text[index] == '-')) index++
            while (index < text.length && text[index].isDigit()) index++
        }
        return JVal.Num(text.substring(start, index))
    }

    private fun expect(literal: String) {
        if (!text.startsWith(literal, index)) throw IllegalArgumentException("ожидалось $literal")
        index += literal.length
    }

    private fun expectChar(char: Char) {
        if (index >= text.length || text[index] != char) {
            throw IllegalArgumentException("ожидался символ $char")
        }
        index++
    }

    private fun peek(char: Char): Boolean = index < text.length && text[index] == char

    private fun skip() {
        while (index < text.length && text[index].isWhitespace()) index++
    }
}
