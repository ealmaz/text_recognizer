package kg.nurtelecom.text_recognizer

import com.google.mlkit.vision.text.Text
import kotlin.math.abs

object PassportMrzHelper {

    private val numbersWeights = listOf(7, 3, 1)
    private const val TD3_LENGTH = 44
    private const val TD2_LENGTH = 36
    private const val TD1_LENGTH = 30

    fun parseMrzFromRawText(rawText: Text): List<String> {
        rawText.textBlocks.forEach {
            println(it.text)
        }
        println("------------------>>")
        return rawText.textBlocks
            .flatMap { it.text.split("\n") }
            .filter {
                val text = it.replace(" ", "")
               (text.contains("<") && text.length in TD1_LENGTH-2..TD3_LENGTH)
                       || (text.length in TD1_LENGTH-2..TD1_LENGTH)
                       || (text.length in TD2_LENGTH-2..TD2_LENGTH)
                       || (text.length in TD3_LENGTH-2..TD3_LENGTH)
                       || isNonFullFirstLine(text)
            }
            .map { prepareMrz(it) }
    }

    private fun prepareMrz(rawMrz: String): String {
        val text = if (isNonFullFirstLine(rawMrz)) rawMrz.padEnd(TD3_LENGTH, '<') else rawMrz
        return text
            .replace("«", "<")
            .replace("[ce]".toRegex(), "<")
            .replace("((?![A-Za-z0-9<]).)".toRegex(), "")
            .replace("\n", "")
            .replace("1D", "ID")
            .replace("(K6Z|K62|KG2)".toRegex(), "KGZ")
            .replace("(IDO|IDО)".toRegex(), "ID0")
    }

    private fun isNonFullFirstLine(line: String): Boolean {
        return (line.startsWith("P") && line.contains("<<<") && line.length in 10..33)
    }

    fun getValidMrzData(lines: List<String>): RecognizedMrz? {
        lines.forEach {
            println(it)
        }
        println("------------------")
        val td3Lines = isTD3(lines)
        val td2Lines = isTD2(lines)
        return when {
            td3Lines != null -> parseTd3(td3Lines)
            td2Lines != null -> parseTd2(td2Lines)
            else -> parseTd1(lines)
        }
    }

    private fun isTD2(lines: List<String>): List<String>? {
        val lastTwoLines  = lines.filter { it.length in TD2_LENGTH-2..TD2_LENGTH }.takeLast(2).takeIf {
            it.size == 2 && it.all { line -> line.length in TD2_LENGTH-2..TD2_LENGTH } }
        return lastTwoLines
    }

    private fun isTD3(lines: List<String>): List<String>? {
        val lastTwoLines  = lines.filter { it.length in TD3_LENGTH-2..TD3_LENGTH }.takeLast(2).takeIf {
            it.size == 2 && it.all { line -> line.length in TD3_LENGTH-2..TD3_LENGTH } }
        return lastTwoLines
    }

    private fun parseTd1(lines: List<String>): RecognizedMrz? {
        val lastThreeLines = lines.filter { it.length in TD1_LENGTH-2..TD1_LENGTH }.takeLast(3)
        if (lastThreeLines.size < 2) return null
        val first = validateLength(lines[0], TD1_LENGTH)
        val second = validateSecondLength(lines[1], TD1_LENGTH, 1)
        val third = lines.getOrNull(2)?.let { validateLength(it, TD1_LENGTH) } ?: String()

        // Check first line -> 14(check number index): 5-14 (number sequence, index from - index to + 1)
        if (checkControlSum(first.subSequence(5, 14), getCharNumericValue(first[14])).not()) return null

        // Check second line -> 36: 30-36
        if (checkControlSum(second.subSequence(0, 6), getCharNumericValue(second[6])).not()) return null

        // Check second line -> 44: 38-44
        if (checkControlSum(second.subSequence(8, 14), getCharNumericValue(second[14])).not()) return null

        val checkStr = StringBuilder()
        checkStr.append(first.subSequence(5, 30))
        checkStr.append(second.subSequence(0, 7))
        checkStr.append(second.subSequence(8, 15))
        checkStr.append(second.subSequence(18, 29))

        val checkDigit = getCharNumericValue(second[29])
        if (checkControlSum(checkStr, checkDigit).not()) return null

        val nameSection = third.split("<<")
        val surname = nameSection.getOrNull(0)?.replace("<", " ")?.trim()
        val givenNames = nameSection.getOrNull(1)?.replace("<", " ")?.trim()

        return RecognizedMrz(
            first + second + third,
            first.substring(0, 2).replace("<", ""),
            first.substring(2, 5),
            first.substring(5, 14),
            second.substring(0, 6),
            second.substring(7, 8),
            second.substring(8, 14),
            second.substring(15, 18),
            surname,
            givenNames,
            first.substring(15, 30),
            second.substring(18, 29)
        )
    }

    private fun parseTd2(lines: List<String>): RecognizedMrz? {
        val first = getValidTD2FirstLine(lines[0]) ?: return null
        val second = validateSecondLength(lines[1], TD2_LENGTH, 1)

        // Check Passport number control sum
        if (checkControlSum(second.subSequence(0, 9), getCharNumericValue(second[9])).not()) return null

        // Check Date of Birth control sum
        if (checkControlSum(second.subSequence(13, 19), getCharNumericValue(second[19])).not()) return null

        // Check Date of Expiry control sum
        if (checkControlSum(second.subSequence(21, 27), getCharNumericValue(second[27])).not()) return null

        val checkStr = StringBuilder()
        checkStr.append(second.subSequence(0, 10))
        checkStr.append(second.subSequence(13, 20))
        checkStr.append(second.subSequence(21, 35))

        // Check overall control sum
        val mainControlNumber = getCharNumericValue(second[35])
        if (checkControlSum(checkStr, mainControlNumber).not()) return null

        val nameSection = first.substring(5).split("<<")
        val surname = nameSection.getOrNull(0)?.replace("<", " ")?.trim()
        val givenNames = nameSection.getOrNull(1)?.replace("<", " ")?.trim()

        return RecognizedMrz(
            first + second,
            first.substring(0, 2).replace("<", ""),
            first.substring(2, 5),
            second.substring(0, 9),
            second.substring(13, 19),
            second.substring(20, 21),
            second.substring(21, 27),
            second.substring(10, 13),
            surname,
            givenNames,
            second.substring(28, 35),
            null
        )
    }

    private fun parseTd3(lines: List<String>): RecognizedMrz? {
        val lastTwoLines = lines.takeLast(2)
        var first = getValidTD3FirstLine(lastTwoLines[0])
        var second = getValidTD3SecondLine(lastTwoLines[1])
        if (first == null) {
            first = getValidTD3FirstLine(lastTwoLines[1]) ?: return null
            second = getValidTD3SecondLine(lastTwoLines[0])
        }

        // Check passport number controls sum
        if (checkControlSum(second.subSequence(0, 9), getCharNumericValue(second[9])).not()) return null

        // Check date of birth control sum
        if (checkControlSum(second.subSequence(13, 19), getCharNumericValue(second[19])).not()) return null

        // Check date of expiry control sum
        if (checkControlSum(second.subSequence(21, 27), getCharNumericValue(second[27])).not()) return null

        val checkStr = StringBuilder()
        checkStr.append(second.subSequence(0, 10))
        checkStr.append(second.subSequence(13, 20))
        checkStr.append(second.subSequence(21, 43))

        // Check overall control sum
        if (checkControlSum(checkStr, getCharNumericValue(second[43])).not()) return null

        val nameSection = first.substring(5).split("<<")
        val surname = nameSection.getOrNull(0)?.replace("<", " ")?.trim()
        val givenNames = nameSection.getOrNull(1)?.replace("<", " ")?.trim()

        return RecognizedMrz(
            first + second,
            first.substring(0, 2).replace("<", ""),
            first.substring(2, 5),
            second.substring(0, 9),
            second.substring(13, 19),
            second.substring(20, 21),
            second.substring(21, 27),
            second.substring(10, 13),
            surname,
            givenNames,
            second.substring(28, 41),
            null)
    }

    private fun getValidTD3FirstLine(line: String): String? {

        val validated = validateLength(line, TD3_LENGTH).uppercase()

        val validChars = Regex("^[A-Z0-9<]{44}$")
        if (!validChars.matches(validated)) return null

        if (validated[0] != 'P') return null

        val countryCode = validated.substring(2, 5)
        if (!countryCode.matches(Regex("^[A-Z]{3}$"))) return null

        val firstDoubleFillerIndex = validated.indexOf("<<", 5)
        if (firstDoubleFillerIndex == -1) return validated

        val consecutiveFillersRegex = Regex("<{2,}")
        val match = consecutiveFillersRegex.find(validated, firstDoubleFillerIndex + 2)
        val cutOffIndex = match?.range?.first ?: -1
        if (cutOffIndex != -1) {
            return validated.substring(0, cutOffIndex).padEnd(TD3_LENGTH, '<')
        }

        return validated
    }

    private fun getValidTD3SecondLine(line: String): String {
        val validated = validateSecondLength(line, TD3_LENGTH, 2)
        val str = StringBuilder().apply {
            append(validated.substring(0, 13))
            append(validated.substring(13, 28).replace(Regex("[Oo]"), "0"))
            append(validated.substring(28, 42))
            append(validated.substring(42, 44).replace(Regex("[Oo]"), "0"))
        }
        return str.toString()
    }

    private fun getValidTD2FirstLine(line: String): String? {

        val validated = validateLength(line, TD2_LENGTH)

        val validChars = Regex("^[A-Z0-9<]{36}$")
        if (!validChars.matches(validated)) return null

        if (validated[0] != 'P' && validated[0] != 'I') return null

        val countryCode = validated.substring(2, 5)
        if (!countryCode.matches(Regex("^[A-Z]{3}$"))) return null

        val firstDoubleFillerIndex = validated.indexOf("<<", 5)
        if (firstDoubleFillerIndex == -1) return validated

        val consecutiveFillersRegex = Regex("<{2,}")
        val match = consecutiveFillersRegex.find(validated, firstDoubleFillerIndex + 2)
        val cutOffIndex = match?.range?.first ?: -1
        if (cutOffIndex != -1) {
            return validated.substring(0, cutOffIndex).padEnd(TD2_LENGTH, '<')
        }

        return validated
    }

    private fun validateLength(line: String, length: Int): String {
        return when {
            length > line.length -> line.padEnd(length, '<')
            length < line.length -> line.substring(0, length)
            else -> return line
        }
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

    private fun getCharNumericValue(char: Char): Int {
        return if(char == '<') 0
        else Character.getNumericValue(char)
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
