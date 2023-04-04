package kg.nurtelecom.text_recognizer.recognizer

import android.media.Image
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.TextRecognizer
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kg.nurtelecom.text_recognizer.RecognizedMrz
import java.util.*
import kotlin.concurrent.schedule

abstract class BaseTextRecognizer(private val recognizerCallback: TextRecognizerCallback) {

    protected var recognizer: TextRecognizer? = null

    protected val rotationTryCount = 1
    protected val rotationDegree = 90

    protected var currentRotationTry = 0
    protected var currentImage: Image? = null

    fun recognizeImage(image: Image) {
        currentImage = image
        initTextRecognizer()
        proceedImage()
    }

    protected abstract fun proceedImage()

    fun stopRecognition() {
        onDestroy()
    }

    private fun initTextRecognizer() {
        if (recognizer == null) {
            recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        }
    }

    protected fun onSuccessRecognized(result: RecognizedMrz) {
        recognizerCallback.onSuccessRecognized(result)
        onDestroy()
    }

    protected fun onRecognitionFail(ex: Exception) {
        recognizerCallback.onRecognitionFail(ex)
        Timer().schedule(5000) {
            proceedImage()
        }
    }

    protected fun requestNewImage() {
        currentRotationTry = 0
        recognizerCallback.requestNewImage()
    }


    private fun onDestroy() {
        recognizer?.close()
        recognizer = null
        currentImage = null
    }
}

interface TextRecognizerCallback {
    fun requestNewImage()
    fun onSuccessRecognized(result: RecognizedMrz)
    fun onRecognitionFail(ex: Exception)
}