package kg.nurtelecom.text_recognizer

object MrzHelper {

    private val RAW_MRZ_FIRST_LINE_REGEX = "([1I])(DK)([G6])(Z)(AN|ID|1D)(O|o|О|о|[0-9])[0-9]{7}".toRegex()

    private val numbersWeights = listOf(7, 3, 1)

    fun parseMrzFromRawText(rawTextLines: List<String>): String {
        var mrzBlock = ""
        rawTextLines.forEach { line ->
            val text = line.replace(" ", "")
            if (text.contains(RAW_MRZ_FIRST_LINE_REGEX)) {
                mrzBlock = text
            } else if (text.contains("<<")) {
                mrzBlock += "\n" + text
            }
        }
        return prepareMrz(mrzBlock)
    }

    private fun prepareMrz(rawMrz: String): String {
        //todo: some refactor
        return rawMrz
            .replace("\n", "")
            .replace("1D", "ID")
            .replace("(K6Z|K62|KG2)".toRegex(), "KGZ")
            .replace("(IDO|IDО)".toRegex(), "ID0")
    }

    fun isMrzValid(mrz: String): Boolean {
        var lastWeightIndex = 0
        if (mrz.length < 60) return false
        val lastControlNumber = mrz[59]
        var sum = 0
        //todo: use it >>>> Check digit over digits 6–30 (upper line), 1–7, 9–15, 19–29 (middle line)
        val mrzShort = mrz.subSequence(5, 45).replace("([MF])".toRegex(), "")
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
        return sum % 10 == Character.getNumericValue(lastControlNumber)
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