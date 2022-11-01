package kg.nurtelecom.text_recognizer.analyzer

import kg.nurtelecom.text_recognizer.recognizer.BaseTextRecognizer
import kg.nurtelecom.text_recognizer.recognizer.KgPassportMrzRecognizer

class KgPassportImageAnalyzer(listener: ImageAnalyzerCallback) : BaseImageAnalyzer(listener) {

    override val recognizer: BaseTextRecognizer by lazy {
        KgPassportMrzRecognizer(this)
    }
}