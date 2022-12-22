package kg.nurtelecom.textrecognizer

import android.net.Uri
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
import kg.nurtelecom.text_recognizer.RecognizedMrz
import kg.nurtelecom.text_recognizer.photo_capture.PhotoRecognizerActivity
import kg.nurtelecom.text_recognizer.photo_capture.RecognizePhotoContract
import kg.nurtelecom.textrecognizer.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    val textRecognizerContract = registerForActivityResult(RecognizePhotoContract()) {
        it?.getParcelableExtra<Uri>(PhotoRecognizerActivity.EXTRA_PHOTO_URI)?.let {
           viewBinding.ivImage.setImageURI(it)
        }
        it?.getSerializableExtra(PhotoRecognizerActivity.EXTRA_MRZ_STRING)?.let {

            viewBinding.tvMrz.setText((it as RecognizedMrz).toString())
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
        AppCompatDelegate.setDefaultNightMode(MODE_NIGHT_FOLLOW_SYSTEM)
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