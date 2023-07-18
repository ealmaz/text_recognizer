package kg.nurtelecom.text_recognizer.photo_capture

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.result.contract.ActivityResultContract
import androidx.appcompat.app.AppCompatActivity
import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment
import kg.nurtelecom.text_recognizer.R
import kg.nurtelecom.text_recognizer.RecognizedMrz
import kg.nurtelecom.text_recognizer.photo_capture.PhotoRecognizerActivity.Companion.TEXT_RECOGNIZER_CONFIGS
import java.io.File
import java.io.Serializable

class PhotoRecognizerActivity : AppCompatActivity(), PhotoRecognizerActivityCallback, RecognitionFailureListener {

    private val resultDataIntent: Intent = Intent()
    private var recognizedData: RecognizedMrz? = null

    private val textRecognizerConfig: TextRecognizerConfig? by lazy {
        intent.getSerializableExtra(TEXT_RECOGNIZER_CONFIGS) as? TextRecognizerConfig
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.text_recognizer_activity_photo_capture)
        val needRecognition = intent.getBooleanExtra(NEED_RECOGNITION_INTENT_DATA, true)
        openCameraFragment(needRecognition)
    }

    override fun openCameraFragment(needRecognition: Boolean) {
        val cameraFragment = PhotoCaptureFragment().apply {
            arguments = bundleOf(
                PhotoCaptureFragment.ARG_NEED_RECOGNITION to needRecognition,
                PhotoCaptureFragment.ARG_TIMEOUT_COUNT to textRecognizerConfig?.timeoutLimit,
                PhotoCaptureFragment.ARG_TIMEOUT_MILLS to textRecognizerConfig?.timeoutMills,
                PhotoCaptureFragment.ARG_TIMEOUT_MESSAGE to textRecognizerConfig?.timeoutMessage,
                PhotoCaptureFragment.ARG_AUTO_PHOTO_CAPTURE to textRecognizerConfig?.autoPhotoCapture,
                PhotoCaptureFragment.ARG_PHOTO_CAPTURE_LABELS to textRecognizerConfig?.photoCaptureLabels,
                PhotoCaptureFragment.ARG_RECOGNITION_LABELS to textRecognizerConfig?.recognitionLabels
            )
        }
        startFragment(cameraFragment)
    }

    override fun openPhotoConfirmationFragment(uri: Uri?) {
        val confirmationFragment = PhotoConfirmationFragment()
        confirmationFragment.arguments = bundleOf(
            PhotoConfirmationFragment.ARG_FILE_URI to uri,
            PhotoConfirmationFragment.ARG_SHOULD_RECOGNIZE_ON_RETRY to (textRecognizerConfig?.shouldRecognizeOnRetry ?: false),
            PhotoConfirmationFragment.ARG_CONFIRMATION_LABELS to textRecognizerConfig?.confirmationLabels
        )
        startFragment(confirmationFragment)
    }

    override fun onPhotoConfirmed(uri: Uri) {
        resultDataIntent.putExtra(EXTRA_PHOTO_URI, uri)
        uploadFile(File(uri.path ?: ""))
    }

    override fun onMrzRecognized(result: RecognizedMrz) {
        recognizedData = result
        resultDataIntent.putExtra(EXTRA_MRZ_STRING, result)
    }

    override fun closeActivity() {
        setResult(RESULT_CANCELED)
        finish()
    }

    override fun uploadFile(file: File) {
        if (fileUploaderCallBack == null) closeActivityWithData()
        fileUploaderCallBack?.uploadRecognizedPhoto(
            file,
            recognizedData,
            { closeActivityWithData() },
            { _, _ -> closeActivity() }
        )
    }

    override fun closeActivityWithData() {
        setResult(RESULT_OK, resultDataIntent)
        finish()
    }

    override fun onPermissionsDenied() {
        setResult(RESULT_PERMISSION_DENIED)
        finish()
    }

    private fun startFragment(fragment: Fragment) {
        supportFragmentManager.beginTransaction()
            .replace(R.id.text_recognizer_container, fragment)
            .commit()
    }

    override fun onRecognitionFail(ex: Exception?) {
        resultDataIntent.putExtra(EXTRA_MRZ_RECOGNITION_FAILURE, ex)
    }

    override fun onBackPressed() {
        closeActivity()
    }

    override fun onDestroy() {
        super.onDestroy()
        fileUploaderCallBack = null
    }

    companion object {

        const val TEXT_RECOGNIZER_CONFIGS = "text_recognizer_configs"

        const val NEED_RECOGNITION_INTENT_DATA = "need_recognition"

        const val RESULT_PERMISSION_DENIED = 100

        const val EXTRA_PHOTO_URI = "result_photo"
        const val EXTRA_MRZ_STRING = "result_mrz"
        const val EXTRA_MRZ_RECOGNITION_FAILURE = "is_mrz_recognition_failure"

        var fileUploaderCallBack: FileUploader? = null
    }
}

interface PhotoRecognizerActivityCallback {
    fun openPhotoConfirmationFragment(uri: Uri?)
    fun openCameraFragment(needRecognition: Boolean = true)
    fun onPhotoConfirmed(uri: Uri)
    fun onMrzRecognized(result: RecognizedMrz)
    fun closeActivity()
    fun uploadFile(file: File)
    fun closeActivityWithData()
    fun onPermissionsDenied()
}

class RecognizePhotoContract : ActivityResultContract<TextRecognizerConfig?, Intent?>() {
    override fun createIntent(context: Context, input: TextRecognizerConfig?): Intent {
        return Intent(context, PhotoRecognizerActivity::class.java).apply {
            input?.let { putExtra(TEXT_RECOGNIZER_CONFIGS, input) }
        }
    }

    override fun parseResult(resultCode: Int, intent: Intent?): Intent? {
        if (resultCode != Activity.RESULT_OK) return null
        return intent
    }
}

interface RecognitionFailureListener {
    fun onRecognitionFail(ex: Exception? = null)
}

data class TextRecognizerConfig(
    val shouldRecognizeOnRetry: Boolean,
    val timeoutLimit: Int? = null,
    val timeoutMills: Long? = null,
    val timeoutMessage: String? = null,
    val autoPhotoCapture: Boolean = true,
    val recognitionLabels: ScreenLabels? = null,
    val photoCaptureLabels: ScreenLabels? = null,
    val confirmationLabels: ScreenLabels? = null,
): Serializable


data class ScreenLabels(
    val headerText: String? = null,
    val title: String? = null,
    val description: String? = null
): Serializable

interface FileUploader{
    fun uploadRecognizedPhoto(file: File, recognizedMrz: RecognizedMrz?, onSuccess: () -> Unit, onFail: (warningMessage: String, finishOnFail: Boolean) -> Unit)
}