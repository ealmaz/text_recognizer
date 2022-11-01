package kg.nurtelecom.textrecognizer

import android.net.Uri
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import kg.nurtelecom.text_recognizer.photo_capture.PhotoCaptureActivity
import kg.nurtelecom.text_recognizer.photo_capture.RecognizePhotoContract
import kg.nurtelecom.textrecognizer.databinding.ActivityMainBinding
import java.lang.StringBuilder

class MainActivity : AppCompatActivity() {

    val textRecognizerContract = registerForActivityResult(RecognizePhotoContract()) {
        it?.getParcelableExtra<Uri>(PhotoCaptureActivity.EXTRA_PHOTO_URI)?.let {
           viewBinding.ivImage.setImageURI(it)
        }
        it?.getStringExtra(PhotoCaptureActivity.EXTRA_MRZ_STRING)?.let {
            val result = StringBuilder()
            result.append("Mrz: ")
            result.append(it)
            result.append("\n\n")
            result.append("INN: ")
            result.append(getInnFromMrz(it))
            result.append("\n")
            result.append("Passport number: ")
            result.append(getPassportNumberFromMrz(it))
            viewBinding.tvMrz.setText(result.toString())
        } ?: let {
            viewBinding.tvMrz.text = "Unable to recognize MRZ"
        }
    }


    private val PASSPORT_REGEX = "(AN|ID)[0-9]{7}".toRegex()
    private val INN_REGEX = "(([12])((0[1-9])|([1-2][0-9])|(3[0-1]))((0[1-9])|(1[0-2]))((19[2-9][0-9])|(20[0-9]{2}))[0-9]{5})".toRegex()


    private lateinit var viewBinding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        viewBinding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(viewBinding.root)
        viewBinding.btn.setOnClickListener {
            textRecognizerContract.launch(Unit)
        }
    }


    fun getPassportNumberFromMrz(mrz: String): String? {
        return PASSPORT_REGEX.find(mrz)?.value
    }

    fun getInnFromMrz(mrz: String): String? {
        return INN_REGEX.find(mrz)?.value
    }
}