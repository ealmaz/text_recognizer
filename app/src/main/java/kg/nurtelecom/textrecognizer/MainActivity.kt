package kg.nurtelecom.textrecognizer

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageCapture
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.design.chili.view.modals.bottom_sheet.DetailedInfoBottomSheet
import kg.nurtelecom.text_recognizer.CameraXImageAnalyzer
import kg.nurtelecom.text_recognizer.ImageAnalyzerCallback
import kg.nurtelecom.textrecognizer.databinding.ActivityMainBinding
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

typealias LumaListener = (luma: Double) -> Unit


class MainActivity : AppCompatActivity(), ImageAnalyzerCallback {


    private val PASSPORT_REGEX = "(AN|ID)[0-9]{7}".toRegex()
    private val INN_REGEX = "(([12])((0[1-9])|([1-2][0-9])|(3[0-1]))((0[1-9])|(1[0-2]))((19[2-9][0-9])|(20[0-9]{2}))[0-9]{5})".toRegex()



    private val analyzer: CameraXImageAnalyzer by lazy {
        CameraXImageAnalyzer(this)
    }

    private lateinit var viewBinding: ActivityMainBinding

    private var imageCapture: ImageCapture? = null

    private lateinit var cameraExecutor: ExecutorService

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        viewBinding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(viewBinding.root)

        // Request camera permissions
        if (allPermissionsGranted()) {
            startCamera()
        } else {
            ActivityCompat.requestPermissions(
                this, REQUIRED_PERMISSIONS, REQUEST_CODE_PERMISSIONS)
        }

        // Set up the listeners for take photo and video capture buttons

        cameraExecutor = Executors.newSingleThreadExecutor()
    }

    private fun takePhoto() {}

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)

        cameraProviderFuture.addListener({
            // Used to bind the lifecycle of cameras to the lifecycle owner
            val cameraProvider: ProcessCameraProvider = cameraProviderFuture.get()

            // Preview
            val preview = Preview.Builder()
                .build()
                .also {
                    it.setSurfaceProvider(viewBinding.viewFinder.surfaceProvider)
                }

            val imageAnalyzer = ImageAnalysis.Builder()
                .build().apply {
                    setAnalyzer(Executors.newSingleThreadExecutor(), analyzer)
                }

            // Select back camera as a default
            val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

            try {
                // Unbind use cases before rebinding
                cameraProvider.unbindAll()

                // Bind use cases to camera
                cameraProvider.bindToLifecycle(
                    this, cameraSelector, preview, imageAnalyzer)

            } catch(exc: Exception) {
                Log.e(TAG, "Use case binding failed", exc)
            }

        }, ContextCompat.getMainExecutor(this))
    }

    private fun allPermissionsGranted() = REQUIRED_PERMISSIONS.all {
        ContextCompat.checkSelfPermission(
            baseContext, it) == PackageManager.PERMISSION_GRANTED
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_CODE_PERMISSIONS) {
            if (allPermissionsGranted()) {
                startCamera()
            } else {
                Toast.makeText(this,
                    "Permissions not granted by the user.",
                    Toast.LENGTH_SHORT).show()
                finish()
            }
        }
    }

    companion object {
        private const val TAG = "CameraXApp"
        private const val FILENAME_FORMAT = "yyyy-MM-dd-HH-mm-ss-SSS"
        private const val REQUEST_CODE_PERMISSIONS = 10
        private val REQUIRED_PERMISSIONS =
            mutableListOf (
                Manifest.permission.CAMERA,
            ).apply {
                if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P) {
                    add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                }
            }.toTypedArray()
    }

    override fun onSuccess(result: String) {
        DetailedInfoBottomSheet.Builder()
            .setMessage(result)
            .setPrimaryButton("Try again" to {
                analyzer.analyzeNewImage()
                dismiss()
            })
            .build()
            .show(supportFragmentManager)
    }

    override fun onFail(ex: Exception) {
        DetailedInfoBottomSheet.Builder()
            .setMessage(ex.message ?: "Error")
            .setPrimaryButton("Try again" to {
                analyzer.analyzeNewImage()
                dismiss()
            })
            .build()
            .show(supportFragmentManager)
    }

    fun getPassportNumberFromMrz(mrz: String): String? {
        return PASSPORT_REGEX.find(mrz)?.value
    }

    fun getInnFromMrz(mrz: String): String? {
        return INN_REGEX.find(mrz)?.value
    }
}