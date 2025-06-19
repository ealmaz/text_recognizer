package kg.nurtelecom.text_recognizer

import com.google.mlkit.vision.text.Text
import kotlin.math.abs
import kotlin.text.StringBuilder

object PassportMrzHelper {

    private val numbersWeights = listOf(7, 3, 1)

    fun parseMrzFromRawText(rawText: Text): List<String> {
        return rawText.textBlocks
            .filter { it.text.contains("<") && it.text.length in 29..45 }
            .map { prepareMrz(it.text) }
    }

    private fun prepareMrz(rawMrz: String): String {
        return rawMrz
            .replace("«", "<")
            .replace("[ce]".toRegex(), "<")
            .replace("((?![A-Za-z0-9<]).)".toRegex(), "")
            .replace("\n", "")
            .replace("1D", "ID")
            .replace("(K6Z|K62|KG2)".toRegex(), "KGZ")
            .replace("(IDO|IDО)".toRegex(), "ID0")
    }

    fun getValidMrzData(lines: List<String>): RecognizedMrz? {
        return when {
            isTD1(lines) -> parseTd1(lines)
            isTD2(lines) -> parseTd2(lines)
            isTD3(lines) -> parseTd3(lines)
            else -> null
        }
    }

    private fun isTD1(lines: List<String>): Boolean {
        return lines.size == 3 && lines.all { it.length in 29..31 }
    }

    private fun isTD2(lines: List<String>): Boolean {
        return lines.size == 2 && lines.all { it.length in 35..37 }
    }

    private fun isTD3(lines: List<String>): Boolean {
        return lines.size == 2 && lines.all { it.length in 43..45 }
    }

    private fun parseTd1(lines: List<String>): RecognizedMrz? {
        val first = validateLength(lines[0], 30)
        val second = validateSecondLength(lines[1], 30, 1)
        val third = validateLength(lines[2], 30)

        val checkStr = StringBuilder()
        checkStr.append(first.subSequence(5, 30))
        checkStr.append(second.subSequence(0, 7))
        checkStr.append(second.subSequence(8, 15))
        checkStr.append(second.subSequence(18, 29))
        val checkDigit = Character.getNumericValue(second[29])
        if (checkControlSum(checkStr, checkDigit).not()) return null

        return RecognizedMrz(
            first + second + third,
            first.substring(1, 2),
            first.substring(2, 5),
            first.substring(5, 14),
            second.substring(0, 6),
            second.substring(7, 8),
            second.substring(8, 14),
            second.substring(15, 18),
            null,
            null,
            first.substring(15, 30),
            second.substring(18, 29)
        )
    }

    private fun parseTd2(lines: List<String>): RecognizedMrz? {
        val first = validateLength(lines[0], 36)
        val second = validateSecondLength(lines[1], 36, 1)

        val checkStr = StringBuilder()
        checkStr.append(second.subSequence(0, 10))
        checkStr.append(second.subSequence(13, 20))
        checkStr.append(second.subSequence(21, 35))

        val mainControlNumber = Character.getNumericValue(second[35])
        if (checkControlSum(checkStr, mainControlNumber).not()) return null

        return RecognizedMrz(
            first + second,
            first.substring(1, 2),
            first.substring(2, 5),
            second.substring(0, 9),
            second.substring(13, 19),
            second.substring(20, 21),
            second.substring(21, 27),
            second.substring(10, 13),
            null,
            null,
            second.substring(28, 35),
            null
        )
    }

    private fun parseTd3(lines: List<String>): RecognizedMrz? {
        val first = validateLength(lines[0], 44)
        val second = validateSecondLength(lines[1], 44, 2)

        val checkStr = StringBuilder()
        checkStr.append(second.subSequence(0, 10))
        checkStr.append(second.subSequence(13, 20))
        checkStr.append(second.subSequence(21, 43))

        if (checkControlSum(checkStr, Character.getNumericValue(second[43])).not()) return null

        return RecognizedMrz(
            first + second,
            first.substring(1, 2),
            first.substring(2, 5),
            second.substring(0, 9),
            second.substring(13, 19),
            second.substring(20, 21),
            second.substring(21, 27),
            second.substring(10, 13),
            null,
            null,
            second.substring(28, 41),
            null)
    }

    private fun validateLength(line: String, length: Int): String {
        val str = StringBuilder(line)
        val times = length - line.length
        when {
            times > 0 -> repeat(times) { str.append("<") }
            times < 0 -> repeat(abs(times)) { str.deleteCharAt(str.length - 1) }
        }
        return str.toString()
    }

    private fun validateSecondLength(line: String, length: Int, position: Int): String {
        val str = StringBuilder(line)
        val times = length - line.length
        when {
            times > 0 -> repeat(times) { str.insert(str.length - position, "<") }
            times < 0 -> repeat(abs(times)) { str.deleteCharAt(str.length - 1) }
        }
        return str.toString()
    }

    private fun checkControlSum(sequence: CharSequence, controlSum: Int): Boolean {
        var sum = 0
        var lastWeightIndex = 0
        sequence.forEach {
            val num =  when (isNumber(it)) {
                true -> Character.getNumericValue(it)
                else -> getLetterAlphabetPosition(it)
            }
            val wei = getNumberWeight(lastWeightIndex)
            lastWeightIndex++
            if (lastWeightIndex >= 3) {
                lastWeightIndex = 0
            }
            sum += (num * wei)
        }
        return sum % 10 == controlSum
    }

    private fun isNumber(char: Char): Boolean {
        return char.code in 48..57
    }

    private fun getLetterAlphabetPosition(char: Char): Int {
        return when{
            (char == '<') || (char.code !in 65..90) -> 0
            else -> char.code - 64 + 9
        }
    }

    private fun getNumberWeight(position: Int): Int {
        return numbersWeights[position]
    }
}
