package kg.nurtelecom.text_recognizer.extension

import android.content.res.Resources

val Int.dp: Float
    get() = (this * Resources.getSystem().displayMetrics.density)
