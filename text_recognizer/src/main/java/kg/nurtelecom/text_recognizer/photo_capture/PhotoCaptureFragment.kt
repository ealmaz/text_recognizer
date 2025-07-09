package kg.nurtelecom.text_recognizer.photo_capture

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Rect
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
import androidx.camera.core.AspectRatio
import androidx.camera.core.Camera
import androidx.camera.core.CameraControl
import androidx.camera.core.CameraSelector
import androidx.camera.core.FocusMeteringAction
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.core.SurfaceOrientedMeteringPointFactory
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.core.graphics.toRect
import androidx.core.view.children
import androidx.core.view.isInvisible
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import com.design2.chili2.view.camera_overlays.PassportCardOverlay
import com.google.common.util.concurrent.ListenableFuture
import kg.nurtelecom.text_recognizer.R
import kg.nurtelecom.text_recognizer.RecognizedMrz
import kg.nurtelecom.text_recognizer.analyzer.BaseImageAnalyzer
import kg.nurtelecom.text_recognizer.analyzer.ImageAnalyzerCallback
import kg.nurtelecom.text_recognizer.analyzer.KgPassportImageAnalyzer
import kg.nurtelecom.text_recognizer.databinding.TextRecognizerFragmentPhotoCaptureBinding
import kg.nurtelecom.text_recognizer.overlay.BlackRectangleOverlay
import kg.nurtelecom.text_recognizer.util.PictureUtils
import java.io.File
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class PhotoCaptureFragment : Fragment(), ImageAnalyzerCallback {

    private var _vb: TextRecognizerFragmentPhotoCaptureBinding? = null
    private val vb: TextRecognizerFragmentPhotoCaptureBinding
        get () = _vb!!

    private var previewOverlay: View? = null
    private var camera: Camera? = null

    private val timeoutCountLimit: Int by lazy {
        arguments?.getInt(ARG_TIMEOUT_COUNT) ?: 0
    }

    private val timeoutMills: Long by lazy {
        arguments?.getLong(ARG_TIMEOUT_MILLS) ?: 15000
    }

    private val timeoutMessage: String? by lazy {
        arguments?.getString(ARG_TIMEOUT_MESSAGE)?.takeIf { it.isNotBlank() }
    }

    private val autoPhotoCapture: Boolean by lazy {
        arguments?.getBoolean(ARG_AUTO_PHOTO_CAPTURE, true) ?: true
    }

    private val recognitionLabels: ScreenLabels? by lazy {
        arguments?.getSerializable(ARG_RECOGNITION_LABELS) as? ScreenLabels
    }

    private val photoCaptureLabels: ScreenLabels? by lazy {
        arguments?.getSerializable(ARG_PHOTO_CAPTURE_LABELS) as? ScreenLabels
    }

    private val overlayType: OverlayType? by lazy {
        arguments?.getSerializable(ARG_OVERLAY_TYPE) as? OverlayType
    }

    private val passportMask: PassportMask? by lazy {
        requireArguments().getSerializable(ARG_PASSPORT_MASK) as? PassportMask
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

    private var isPhotoCapturing = false

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
        setupPassportMask()
    }

    private fun setupPassportMask() {
        val passportMaskImage = when (passportMask) {
            PassportMask.LIGHT_GREEN_PASSPORT_MASK -> R.drawable.text_recognizer_passport_mask_light_green
            else -> R.drawable.text_recognizer_passport_mask
        }
        vb.ivMask.setImageDrawable(ContextCompat.getDrawable(requireContext(), passportMaskImage))
    }

    private fun allPermissionsGranted() = REQUIRED_PERMISSIONS.all {
        ContextCompat.checkSelfPermission(requireContext(), it) == PackageManager.PERMISSION_GRANTED
    }

    private fun startCamera() {
        vb.surfacePreview.post {
            cameraProviderFuture = ProcessCameraProvider.getInstance(requireContext())
            cameraProviderFuture?.addListener({
                val cameraProvider: ProcessCameraProvider =
                    cameraProviderFuture?.get() ?: return@addListener
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

        setupOverlayLabels(recognitionLabels)

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
            Thread.sleep(300)
            if (needToRecognizeText) {
                cameraProvider.bindToLifecycle(this, cameraSelector, imageAnalysis)
                countDownTimer.start()
            }
            Thread.sleep(300)
            camera = cameraProvider.bindToLifecycle(
                this,
                cameraSelector,
                preview,
                imageCapture
            )

            camera?.cameraControl?.let { setUpTapToFocus(it) }

        } catch (exc: Exception) {
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
            isInvisible = needToRecognizeText
        }
        vb.tvDescription.isVisible =
            needToRecognizeText && overlayType == OverlayType.RECTANGLE_OVERLAY
        vb.ivMask.isVisible = overlayType == OverlayType.RECTANGLE_OVERLAY
    }

    private fun takePhoto() {
        if (isPhotoCapturing) return
        val imageCapture = imageCapture ?: return
        isPhotoCapturing = true
        val name = SimpleDateFormat(FILENAME_FORMAT, Locale.US)
            .format(System.currentTimeMillis())

        var tempFile: File = createTemporaryFiles(name, ".jpg")

        val outputOptions = ImageCapture.OutputFileOptions
            .Builder(tempFile)
            .build()

        imageCapture.takePicture(
            outputOptions,
            ContextCompat.getMainExecutor(requireContext()),
            object : ImageCapture.OnImageSavedCallback {
                override fun onError(exc: ImageCaptureException) {
                    isPhotoCapturing = false
                    Log.e(TAG, "Photo capture failed: ${exc.message}", exc)
                }

                override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                    cropImage(tempFile)?.let { tempFile = it }
                    (tryGetActivity() as PhotoRecognizerActivityCallback).openPhotoConfirmationFragment(
                        output.savedUri
                    )
                }
            }
        )

    }

    private fun cropImage(file: File): File? {
        return PictureUtils.compressImage(file.absolutePath, 80, getImageCropRect(), resources.displayMetrics.heightPixels)
    }

    private fun getImageCropRect(): Rect? {
        return if (vb.flPreview.childCount <= 0) null
        else (vb.flPreview.children.find { it is PassportCardOverlay } as? PassportCardOverlay)?.getPassportMaskRectF()?.toRect()
    }

    private fun setButtonVisibilityOrTakePicture() {
        if (autoPhotoCapture) {
            takePhoto()
        } else {
            vb.btnCapture.visibility = View.VISIBLE
            setupOverlayLabels(photoCaptureLabels)
        }
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
        setButtonVisibilityOrTakePicture()
    }

    override fun onFailTextRecognized(ex: Exception) {
        (activity as? RecognitionFailureListener)?.onRecognitionFail(ex)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        cameraProviderFuture = null
        imageAnalyzer.stopAnalyzing()
        cameraExecutor.shutdown()
        countDownTimer.cancel()
        _vb = null
        isPhotoCapturing = false
    }

    private fun createTemporaryFiles(prefix: String, suffix: String): File {
        val directory = when (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P) {
            true -> File(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES),
                "/text_recognizer"
            )

            else -> File(
                requireContext().getExternalFilesDir(Environment.DIRECTORY_PICTURES),
                "/text_recognizer"
            )
        }
        if (!directory.exists()) {
            directory.mkdirs()
        }
        return File.createTempFile(prefix, suffix, directory)
    }

    private fun setupOverlayLabels(screenLabels: ScreenLabels?) {
        previewOverlay?.let { vb.flPreview.removeView(it) }
        if (overlayType == OverlayType.PASSPORT_OVERLAY) {
            PassportCardOverlay(requireContext()).apply {
                layoutParams = FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT
                )
                setOverlayAlpha(102)
                setHeaderText(R.string.text_recognizer_title_photo_capture)
                setDescription(R.string.recognition_description)
                screenLabels?.description?.let { setDescription(it) }
                screenLabels?.title?.let { setTitle(it) }
                screenLabels?.headerText?.let { setHeaderText(it) }
                previewOverlay = this
                vb.flPreview.addView(this)
            }
        } else {
            BlackRectangleOverlay(requireContext()).apply {
                layoutParams = FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT
                )
                vb.tvDescription.setText(R.string.recognition_description_without_button)
                previewOverlay = this
                vb.flPreview.addView(this)
            }
        }
    }

    companion object {

        const val ARG_NEED_RECOGNITION = "arg_need_recognition"
        const val ARG_TIMEOUT_MILLS = "ARG_TIMEOUT_MILLS"
        const val ARG_TIMEOUT_COUNT = "ARG_TIMEOUT_COUNT"
        const val ARG_TIMEOUT_MESSAGE = "ARG_TIMEOUT_MESSAGE"
        const val ARG_AUTO_PHOTO_CAPTURE = "ARG_AUTO_PHOTO_CAPTURE"
        const val ARG_RECOGNITION_LABELS = "ARG_RECOGNITION_LABELS"
        const val ARG_PHOTO_CAPTURE_LABELS = "ARG_PHOTO_CAPTURE_LABELS"
        const val ARG_OVERLAY_TYPE = "arg_overlay_type"
        const val ARG_PASSPORT_MASK = "arg_passport_mask"

        private const val TAG = "CameraXApp"
        private const val FILENAME_FORMAT = "yyyy-MM-dd-HH-mm-ss-SSS"
        private val REQUIRED_PERMISSIONS = when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.R ->
                arrayOf(Manifest.permission.CAMERA)

            else -> {
                arrayOf(
                    Manifest.permission.CAMERA,
                    Manifest.permission.READ_EXTERNAL_STORAGE,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE
                )
            }
        }
    }
}