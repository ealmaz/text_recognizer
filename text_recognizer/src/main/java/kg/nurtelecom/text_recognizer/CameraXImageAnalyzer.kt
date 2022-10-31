package kg.nurtelecom.text_recognizer

import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import kg.nurtelecom.text_recognizer.base.TextRecognizerCallback

class CameraXImageAnalyzer(private val listener: ImageAnalyzerCallback) : ImageAnalysis.Analyzer, TextRecognizerCallback {

    private var imageProxy: ImageProxy? = null

    private val kgPassportMrzRecognizer: KgPassportMrzRecognizer by lazy {
        KgPassportMrzRecognizer(this)
    }

    fun analyzeNewImage() {
        requestNewImage()
    }

    override fun analyze(image: ImageProxy) {
        imageProxy = image
        imageProxy?.image?.let {
            kgPassportMrzRecognizer.recognizeImage(it)
        }
    }

    override fun requestNewImage() {
        imageProxy?.close()
    }

    override fun onSuccessRecognized(result: String) {
        listener.onSuccess(result)
    }

    override fun onRecognitionFail(ex: Exception) {
        listener.onFail(ex)
    }
}

interface ImageAnalyzerCallback {
    fun onSuccess(result: String)
    fun onFail(ex: Exception)
}