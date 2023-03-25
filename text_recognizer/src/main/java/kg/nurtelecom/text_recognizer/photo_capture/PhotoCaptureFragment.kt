package kg.nurtelecom.text_recognizer.photo_capture

import android.Manifest
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import android.os.CountDownTimer
import android.os.Environment
import android.util.Log
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import com.google.common.util.concurrent.ListenableFuture
import kg.nurtelecom.text_recognizer.RecognizedMrz
import kg.nurtelecom.text_recognizer.analyzer.BaseImageAnalyzer
import kg.nurtelecom.text_recognizer.analyzer.KgPassportImageAnalyzer
import kg.nurtelecom.text_recognizer.analyzer.ImageAnalyzerCallback
import kg.nurtelecom.text_recognizer.databinding.TextRecognizerFragmentPhotoCaptureBinding
import kg.nurtelecom.text_recognizer.overlay.BlackRectangleOverlay
import java.io.File
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class PhotoCaptureFragment : Fragment(), ImageAnalyzerCallback {

    private var _vb: TextRecognizerFragmentPhotoCaptureBinding? = null
    private val vb: TextRecognizerFragmentPhotoCaptureBinding
        get () = _vb!!

    private val timeoutCountLimit: Int by lazy {
        arguments?.getInt(ARG_TIMEOUT_COUNT) ?: 0
    }

    private val timeoutMills: Long by lazy {
        arguments?.getLong(ARG_TIMEOUT_MILLS) ?: 15000
    }

    private val timeoutMessage: String? by lazy {
        arguments?.getString(ARG_TIMEOUT_MESSAGE)?.takeIf { it.isNotBlank() }
    }

    private var cameraProviderFuture: ListenableFuture<ProcessCameraProvider>? = null

    private var imageCapture: ImageCapture? = null

    private val cameraExecutor: ExecutorService by lazy {
        Executors.newSingleThreadExecutor()
    }

    private val imageAnalyzer: BaseImageAnalyzer by lazy {
        KgPassportImageAnalyzer(this@PhotoCaptureFragment)
    }

    private val needToRecognizeText: Boolean
        get () = arguments?.getBoolean(ARG_NEED_RECOGNITION) ?: true

    private val countDownTimer: CountDownTimer by lazy {
        object : CountDownTimer(timeoutMills, 1000) {
            override fun onTick(millisUntilFinished: Long) {}
            override fun onFinish() {
                handleOnTimeOut()
            }
        }
    }

    private var timeoutCount = 0

    private fun handleOnTimeOut() {
        timeoutCount++
        if (timeoutCount >= timeoutCountLimit) {
            imageAnalyzer.stopAnalyzing()
            timeoutCount = 0
            onFailTextRecognized(Exception())
        } else {
            timeoutMessage?.let { Toast.makeText(context, it, Toast.LENGTH_SHORT).show() }
            countDownTimer.start()
        }
    }

    private val requestPermission = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        when (allPermissionsGranted()) {
            true -> startCamera()
            else -> onPermissionDenied()
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _vb = TextRecognizerFragmentPhotoCaptureBinding.inflate(layoutInflater)
        return vb.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        when (allPermissionsGranted()) {
            true -> startCamera()
            else -> requestPermission.launch(REQUIRED_PERMISSIONS)
        }
        setupViews()
    }

    private fun allPermissionsGranted() = REQUIRED_PERMISSIONS.all {
        ContextCompat.checkSelfPermission(requireContext(), it) == PackageManager.PERMISSION_GRANTED
    }

    private fun startCamera() {
        vb.surfacePreview.post {
            cameraProviderFuture = ProcessCameraProvider.getInstance(requireContext())
            cameraProviderFuture?.addListener({
                val cameraProvider: ProcessCameraProvider = cameraProviderFuture?.get() ?: return@addListener
                bindUseCases(cameraProvider)
            }, ContextCompat.getMainExecutor(requireContext()))
        }
    }

    private fun bindUseCases(cameraProvider: ProcessCameraProvider) {

        val aspectRatio = AspectRatio.RATIO_16_9

        val rotation = vb.surfacePreview.display.rotation

        val preview = Preview.Builder()
            .setTargetRotation(rotation)
            .setTargetAspectRatio(aspectRatio)
            .build()
            .also { it.setSurfaceProvider(vb.surfacePreview.surfaceProvider) }

        vb.flPreview.addView(
            BlackRectangleOverlay(requireContext()),
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        )

        imageCapture = ImageCapture.Builder()
            .setTargetRotation(rotation)
            .setTargetAspectRatio(aspectRatio)
            .build()


        val imageAnalysis = ImageAnalysis.Builder()
            .build()
            .apply {
                setAnalyzer(cameraExecutor, imageAnalyzer)
            }


        val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

        try {
            cameraProvider.unbindAll()
            if (needToRecognizeText) {
                cameraProvider.bindToLifecycle(this, cameraSelector, imageAnalysis)
                countDownTimer.start()
            }
            val camera = cameraProvider.bindToLifecycle(
                this,
                cameraSelector,
                preview,
                imageCapture
            )
            setUpTapToFocus(camera.cameraControl)

        } catch(exc: Exception) {
            Log.e(TAG, "Use case binding failed", exc)
        }

    }

    private fun setUpTapToFocus(cameraControl: CameraControl) {
        vb.surfacePreview.setOnTouchListener { _, event ->
            if (event.action != MotionEvent.ACTION_UP) {
                return@setOnTouchListener true
            }
            vb.surfacePreview.performClick()
            return@setOnTouchListener try {
                val action = SurfaceOrientedMeteringPointFactory(
                    vb.surfacePreview.width.toFloat(),
                    vb.surfacePreview.height.toFloat()
                )
                    .createPoint(event.x, event.y)
                    .let { FocusMeteringAction.Builder(it, FocusMeteringAction.FLAG_AF).build() }
                cameraControl.startFocusAndMetering(action)
                true
            } catch (_: Exception) {
                false
            }
        }
    }

    private fun setupViews() {
        vb.btnClose.setOnClickListener {
            (tryGetActivity() as PhotoRecognizerActivityCallback).closeActivity()
        }
        vb.btnCapture.apply {
            setOnClickListener { takePhoto() }
            when (needToRecognizeText) {
                true -> {
                    visibility = View.GONE
                    vb.tvDescription.visibility = View.VISIBLE
                }
                else -> {
                    visibility = View.VISIBLE
                    vb.tvDescription.visibility = View.GONE
                }
            }
        }
    }

    private fun takePhoto() {
        val imageCapture = imageCapture ?: return
        val name = SimpleDateFormat(FILENAME_FORMAT, Locale.US)
            .format(System.currentTimeMillis())

        val tempFile = createTemporaryFiles(name, ".jpg")

        val outputOptions = ImageCapture.OutputFileOptions
            .Builder(tempFile)
            .build()

        imageCapture.takePicture(
            outputOptions,
            ContextCompat.getMainExecutor(requireContext()),
            object : ImageCapture.OnImageSavedCallback {
                override fun onError(exc: ImageCaptureException) {
                    Log.e(TAG, "Photo capture failed: ${exc.message}", exc)
                }

                override fun onImageSaved(output: ImageCapture.OutputFileResults){
                    (tryGetActivity() as PhotoRecognizerActivityCallback).openPhotoConfirmationFragment(
                        output.savedUri
                    )
                }
            }
        )

    }

    private fun tryGetActivity(): FragmentActivity {
        while (true) {
            activity?.let {
                return it
            }
        }
    }

    private fun onPermissionDenied() {
        (tryGetActivity() as PhotoRecognizerActivityCallback).onPermissionsDenied()
    }

    override fun onSuccessTextRecognized(result: RecognizedMrz) {
        (tryGetActivity() as PhotoRecognizerActivityCallback).onMrzRecognized(result)
        takePhoto()
    }

    override fun onFailTextRecognized(ex: Exception) {
        takePhoto()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        cameraProviderFuture = null
        imageAnalyzer.stopAnalyzing()
        cameraExecutor.shutdown()
        countDownTimer.cancel()
        _vb = null
    }

    private fun createTemporaryFiles(prefix: String, suffix: String) : File {
        val directory = when (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P) {
            true -> File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), "/text_recognizer")
            else -> File(requireContext().getExternalFilesDir(Environment.DIRECTORY_PICTURES), "/text_recognizer")
        }
        if (!directory.exists()) {
            directory.mkdirs()
        }
        return File.createTempFile(prefix, suffix, directory)
    }

    companion object {

        const val ARG_NEED_RECOGNITION = "arg_need_recognition"
        const val ARG_TIMEOUT_MILLS = "ARG_TIMEOUT_MILLS"
        const val ARG_TIMEOUT_COUNT = "ARG_TIMEOUT_COUNT"
        const val ARG_TIMEOUT_MESSAGE = "ARG_TIMEOUT_MESSAGE"

        private const val TAG = "CameraXApp"
        private const val FILENAME_FORMAT = "yyyy-MM-dd-HH-mm-ss-SSS"
        private val REQUIRED_PERMISSIONS =
            mutableListOf (
                Manifest.permission.CAMERA
            ).apply {
                if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P) {
                    add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                }
            }.toTypedArray()
    }
}