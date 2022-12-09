package kg.nurtelecom.text_recognizer.analyzer

import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import kg.nurtelecom.text_recognizer.RecognizedMrz
import kg.nurtelecom.text_recognizer.recognizer.BaseTextRecognizer
import kg.nurtelecom.text_recognizer.recognizer.TextRecognizerCallback

abstract class BaseImageAnalyzer(protected val listener: ImageAnalyzerCallback)
    : ImageAnalysis.Analyzer, TextRecognizerCallback {

    protected var imageProxy: ImageProxy? = null

    abstract val recognizer: BaseTextRecognizer

    override fun analyze(image: ImageProxy) {
        imageProxy = image
        imageProxy?.image?.let {
            recognizer.recognizeImage(it)
        }
    }

    fun stopAnalyzing() {
        recognizer.stopRecognition()
    }

    override fun requestNewImage() {
        imageProxy?.close()
    }

    override fun onSuccessRecognized(result: RecognizedMrz) {
        listener.onSuccessTextRecognized(result)
    }

    override fun onRecognitionFail(ex: Exception) {
        listener.onFailTextRecognized(ex)
    }
}


interface ImageAnalyzerCallback {
    fun onSuccessTextRecognized(result: RecognizedMrz)
    fun onFailTextRecognized(ex: Exception)
}