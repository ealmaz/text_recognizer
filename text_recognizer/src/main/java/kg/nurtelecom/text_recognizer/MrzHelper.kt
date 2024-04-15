package kg.nurtelecom.text_recognizer

object MrzHelper {

    private val numbersWeights = listOf(7, 3, 1)

    fun parseMrzFromRawText(rawTextLines: List<String>): String {
        var mrzBlock = mutableListOf<String>()
        rawTextLines.forEach { block ->
            val text = block.replace(" ", "")
            text.split("\n").forEach { line ->
                if (line.length == 44 || line.length == 36 || line.length == 30) {
                    mrzBlock.add(line)
                }
            }
        }
        return prepareMrz(mrzBlock.joinToString(separator = "") { it })
    }

    private fun prepareMrz(rawMrz: String): String {
        return rawMrz
            .replace("«", "<")
            .replace("((?![A-Za-z0-9<]).)".toRegex(), "")
            .replace("\n", "")
            .replace("1D", "ID")
            .replace("(K6Z|K62|KG2)".toRegex(), "KGZ")
            .replace("(IDO|IDО)".toRegex(), "ID0")
    }

    fun isMrzValid(mrz: String): RecognizedMrz? {
        return when (mrz.length) {
            72 -> validateTd2(mrz)
            88 -> validateTd3(processMrz(mrz))
            else -> validateTd1(mrz)
        }
    }

    private fun validateTd1(mrz: String): RecognizedMrz? {
        if (mrz.length < 60) return null
        val lastControlNumber = mrz[59]

        // Check first line -> 14(check number index): 5-14 (number sequence, index from - index to + 1)
        if (checkControlSum(
                mrz.subSequence(5, 14),
                Character.getNumericValue(mrz[14])
            ).not()
        ) return null

        // Check second line -> 36: 30-36
        if (checkControlSum(
                mrz.subSequence(30, 36),
                Character.getNumericValue(mrz[36])
            ).not()
        ) return null

        // Check second line -> 44: 38-44
        if (checkControlSum(
                mrz.subSequence(38, 44),
                Character.getNumericValue(mrz[44])
            ).not()
        ) return null

        // Main control number
        val mainSequence = StringBuilder()
        mainSequence.append(mrz.subSequence(5, 30))
        mainSequence.append(mrz.subSequence(30, 37))
        mainSequence.append(mrz.subSequence(38, 45))
        mainSequence.append(mrz.subSequence(48, 59))

        val mainControlNumber = Character.getNumericValue(lastControlNumber)
        if (checkControlSum(mainSequence.toString(), mainControlNumber).not()) return null

        val extendedMRZ = StringBuilder(mrz)
        val times = 90 - mrz.length
        if (times > 0) repeat(times) { extendedMRZ.append('<') }
        return RecognizedMrz(
            extendedMRZ.take(90).toString(),
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
    }

    private fun checkControlSum(sequence: CharSequence, controlSum: Int): Boolean {
        var sum = 0
        var lastWeightIndex = 0
        sequence.forEach {
            val num = when (isNumber(it)) {
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

    private fun validateTd2(mrz: String): RecognizedMrz? {
        if (mrz.length < 72) return null


        if (checkControlSum(
                mrz.subSequence(36, 45),
                Character.getNumericValue(mrz[45])
            ).not()
        ) return null

        if (checkControlSum(
                mrz.subSequence(49, 55),
                Character.getNumericValue(mrz[55])
            ).not()
        ) return null

        if (checkControlSum(
                mrz.subSequence(57, 63),
                Character.getNumericValue(mrz[63])
            ).not()
        ) return null


        val lastControlNumber = mrz[71]
        val mrzShort = StringBuilder()
        mrzShort.append(mrz.subSequence(36, 46))
        mrzShort.append(mrz.subSequence(49, 56))
        mrzShort.append(mrz.subSequence(57, 71))

        val mainControlNumber = Character.getNumericValue(lastControlNumber)
        if (checkControlSum(mrzShort.toString(), mainControlNumber).not()) return null


        return RecognizedMrz(
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
    }

    private fun validateTd3(mrz: String): RecognizedMrz? {
        if (mrz.length < 88) return null

        if (checkControlSum(
                mrz.subSequence(44, 53),
                Character.getNumericValue(mrz[53])
            ).not()
        ) return null

        if (checkControlSum(
                mrz.subSequence(57, 63),
                Character.getNumericValue(mrz[63])
            ).not()
        ) return null

        if (!isPlaceholder(mrz.substring(65, 71))) {
            if (checkControlSum(
                    mrz.subSequence(65, 71),
                    Character.getNumericValue(mrz[71])
                ).not()
            ) return null
        }

        if (checkControlSum(
                mrz.subSequence(72, 86),
                Character.getNumericValue(mrz[86])
            ).not()
        ) return null

        val lastControlNumber = mrz[87]
        val mrzShort = StringBuilder()
        mrzShort.append(mrz.subSequence(44, 54))
        mrzShort.append(mrz.subSequence(57, 64))
        mrzShort.append(mrz.subSequence(65, 87))

        if (checkControlSum(
                mrzShort.toString(),
                Character.getNumericValue(lastControlNumber)
            ).not()
        ) return null

        return RecognizedMrz(
            mrz,
            mrz.substring(0, 2),
            mrz.substring(2, 5),
            mrz.substring(44, 53),
            mrz.substring(57, 63),
            mrz.substring(64, 65),
            mrz.substring(65, 71),
            mrz.substring(54, 57),
            null,
            null,
            mrz.substring(72, 86),
            null
        )
    }

    private fun processMrz(originalMrz: String): String {
        val firstLine = originalMrz.substring(0, 44)
        val secondLine = originalMrz.substring(44).uppercase()

        val processedSecondLine = secondLine.replace("NO".toRegex(), "N0")
            .replace("CO".toRegex(), "C0")
            .replace("OM".toRegex(), "0M")

        return firstLine + processedSecondLine
    }

    private fun isPlaceholder(value: String): Boolean {
        return value.all { it == '<' }
    }

    private fun isNumber(char: Char): Boolean {
        return char.code in 48..57
    }

    private fun getLetterAlphabetPosition(char: Char): Int {
        return when {
            (char == '<') || (char.code !in 65..90) -> 0
            else -> char.code - 64 + 9
        }
    }

    private fun getNumberWeight(position: Int): Int {
        return numbersWeights[position]
    }
}