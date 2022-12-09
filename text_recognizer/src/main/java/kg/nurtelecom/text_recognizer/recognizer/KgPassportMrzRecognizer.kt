package kg.nurtelecom.text_recognizer.recognizer

import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import kg.nurtelecom.text_recognizer.KgMrzHelper

class KgPassportMrzRecognizer(recognizerCallback: TextRecognizerCallback) : BaseTextRecognizer(recognizerCallback) {

    override fun proceedImage() {
        val rotatedImage = getRotatedImage()
        when (rotatedImage != null && recognizer != null) {
            true -> recognizeText(rotatedImage)
            else -> requestNewImage()
        }
    }

    private fun recognizeText(rotatedImage: InputImage) {
        recognizer
            ?.process(rotatedImage)
            ?.addOnSuccessListener { validateMrz(it) }
            ?.addOnFailureListener { onRecognitionFail(it) }
    }

    private fun validateMrz(rawText: Text) {
        val lines = rawText.textBlocks.map { it.text }
        val mrz = KgMrzHelper.parseMrzFromRawText(lines)
        val result = KgMrzHelper.isMrzValid(mrz)
        if (result != null) {
            onSuccessRecognized(result)
        } else {
            proceedImage()
        }
    }

    private fun getRotatedImage(): InputImage? {
        if (currentRotationTry >= rotationTryCount) return null
        return currentImage?.let {
            InputImage.fromMediaImage(it, rotationDegree * currentRotationTry++)
        }
    }
}



