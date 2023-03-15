package kg.nurtelecom.text_recognizer

import java.lang.StringBuilder

object MrzHelper {

    private val numbersWeights = listOf(7, 3, 1)

    fun parseMrzFromRawText(rawTextLines: List<String>): String {
        var mrzBlock = mutableListOf<String>()
        rawTextLines.forEach { block ->
            val text = block.replace(" ", "")
            text.split("\n").forEach { line ->
                if (line.length == 44 || line.length == 36 ||  line.length == 30) {
                    mrzBlock.add(line)
                }
            }
        }
        return prepareMrz(mrzBlock.joinToString(separator = "") { it })
    }

    private fun prepareMrz(rawMrz: String): String {
        return rawMrz
            .replace("((?![A-Za-z0-9<]).)".toRegex(), "")
            .replace("\n", "")
            .replace("1D", "ID")
            .replace("(K6Z|K62|KG2)".toRegex(), "KGZ")
            .replace("(IDO|IDО)".toRegex(), "ID0")
    }

    fun isMrzValid(mrz: String): RecognizedMrz? {
        return when (mrz.length) {
            72 -> validateTd2(mrz)
            88 -> validateTd3(mrz)
            90 -> validateTd1(mrz)
            else -> null
        }
    }

    private fun validateTd1(mrz: String): RecognizedMrz? {
        var lastWeightIndex = 0
        if (mrz.length < 90) return null
        val lastControlNumber = mrz[59]
        var sum = 0
        val mrzShort = StringBuilder()
        mrzShort.append(mrz.subSequence(5, 30))
        mrzShort.append(mrz.subSequence(30, 37))
        mrzShort.append(mrz.subSequence(38, 45))
        mrzShort.append(mrz.subSequence(48, 59))
        mrzShort.forEach {
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
        return if (sum % 10 == Character.getNumericValue(lastControlNumber)) {
          RecognizedMrz(
              mrz,
              mrz.substring(1, 2),
              mrz.substring(2, 5),
              mrz.substring(5, 14),
              mrz.substring(30, 36),
              mrz.substring(37, 38),
              mrz.substring(38, 44),
              mrz.substring(45, 48),
              null,
              null,
              mrz.substring(15, 30),
              mrz.substring(48, 59)

          )
        } else null
    }

    private fun validateTd2(mrz: String): RecognizedMrz? {
        var lastWeightIndex = 0
        if (mrz.length < 72) return null
        val lastControlNumber = mrz[71]
        var sum = 0
        val mrzShort = StringBuilder()
        mrzShort.append(mrz.subSequence(36, 71))
        mrzShort.forEach {
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
        return if (sum % 10 == Character.getNumericValue(lastControlNumber)) {
            RecognizedMrz(
                mrz,
                mrz.substring(1, 2),
                mrz.substring(2, 5),
                mrz.substring(36, 45),
                mrz.substring(49, 55),
                mrz.substring(56, 57),
                mrz.substring(57, 63),
                mrz.substring(46, 49),
                null,
                null,
                mrz.substring(64, 71),
                null
            )
        } else null
    }

    private fun validateTd3(mrz: String): RecognizedMrz? {
        var lastWeightIndex = 0
        if (mrz.length < 88) return null
        val lastControlNumber = mrz[87]
        var sum = 0
        val mrzShort = StringBuilder()
        mrzShort.append(mrz.subSequence(44, 54))
        mrzShort.append(mrz.subSequence(57, 64))
        mrzShort.append(mrz.subSequence(65, 87))
        mrzShort.forEach {
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
        return if (sum % 10 == Character.getNumericValue(lastControlNumber)) {
            RecognizedMrz(
                mrz,
                mrz.substring(1, 2),
                mrz.substring(2, 5),
                mrz.substring(44, 53),
                mrz.substring(57, 63),
                mrz.substring(64, 65),
                mrz.substring(65, 71),
                mrz.substring(54, 57),
                null,
                null,
                mrz.substring(72, 86),
                null)
        } else null
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