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

class PhotoCaptureActivity : AppCompatActivity(), PhotoCaptureActivityCallback {

    private val resultDataIntent: Intent = Intent()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.text_recognizer_activity_photo_capture)
        openCameraFragment()
    }

    override fun openCameraFragment(needRecognition: Boolean) {
        val cameraFragment = PhotoCaptureFragment().apply {
            arguments = bundleOf(PhotoCaptureFragment.ARG_NEED_RECOGNITION to needRecognition)
        }
        startFragment(cameraFragment)
    }

    override fun openPhotoConfirmationFragment(uri: Uri?) {
        val confirmationFragment = PhotoConfirmationFragment()
        confirmationFragment.arguments = bundleOf(PhotoConfirmationFragment.ARG_FILE_URI to uri)
        startFragment(confirmationFragment)
    }

    override fun onPhotoConfirmed(uri: Uri) {
        resultDataIntent.putExtra(EXTRA_PHOTO_URI, uri)
        closeActivityWithData()
    }

    override fun onMrzRecognized(result: String) {
        resultDataIntent.putExtra(EXTRA_MRZ_STRING, result)
    }

    override fun closeActivity() {
        setResult(RESULT_CANCELED)
        finish()
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

    companion object {

        const val RESULT_PERMISSION_DENIED = 100

        const val EXTRA_PHOTO_URI = "result_photo"
        const val EXTRA_MRZ_STRING = "result_mrz"
    }
}

interface PhotoCaptureActivityCallback {
    fun openPhotoConfirmationFragment(uri: Uri?)
    fun openCameraFragment(needRecognition: Boolean = true)
    fun onPhotoConfirmed(uri: Uri)
    fun onMrzRecognized(result: String)
    fun closeActivity()
    fun closeActivityWithData()
    fun onPermissionsDenied()
}

class RecognizePhotoContract : ActivityResultContract<Unit, Intent?>() {
    override fun createIntent(context: Context, input: Unit): Intent {
        return Intent(context, PhotoCaptureActivity::class.java)
    }

    override fun parseResult(resultCode: Int, intent: Intent?): Intent? {
        if (resultCode != Activity.RESULT_OK) return null
        return intent
    }
}