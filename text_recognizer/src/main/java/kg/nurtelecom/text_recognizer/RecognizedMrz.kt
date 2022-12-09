package kg.nurtelecom.text_recognizer

import java.io.Serializable

data class RecognizedMrz(
    val mrz: String?,
    val documentType: String?,
    val countryCode: String?,
    val documentNumber: String?,
    val birthDate: String?,
    val sex: String?,
    val expiryDate: String?,
    val nationality: String?,
    val surname: String?,
    val givenNames: String?,
    val optionalData: String?,
    val optionalData2: String?
): Serializable