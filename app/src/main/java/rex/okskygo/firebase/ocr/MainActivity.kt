package rex.okskygo.firebase.ocr

import android.graphics.Bitmap
import android.graphics.Matrix
import android.os.Bundle
import android.view.View
import com.google.firebase.ml.vision.FirebaseVision
import com.google.firebase.ml.vision.common.FirebaseVisionImage
import com.google.firebase.ml.vision.text.FirebaseVisionCloudTextRecognizerOptions
import com.google.firebase.ml.vision.text.FirebaseVisionText
import io.fotoapparat.Fotoapparat
import kotlinx.android.synthetic.main.activity_main.*
import kotlinx.coroutines.*
import java.io.File
import java.io.FileOutputStream


class MainActivity : BaseActivity() {

    private lateinit var fotoapparat: Fotoapparat


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        fotoapparat = Fotoapparat(this, cameraView)
        button.setOnClickListener {
            launch {
                cameraGroup.visibility = View.GONE
                resultGroup.visibility = View.VISIBLE
                progress.visibility = View.VISIBLE
                val bitmap = saveCoverImage().await()
                val firebaseVisionText = detectorImage(bitmap).await()
                progress.visibility = View.GONE
                resultTextView.text = firebaseVisionText.text
            }
        }
        closeButton.setOnClickListener {
            cameraGroup.visibility = View.VISIBLE
            resultGroup.visibility = View.GONE
            resultTextView.text = ""
        }
    }

    private fun detectorImage(bitmap: Bitmap): Deferred<FirebaseVisionText> {
        val deferred = CompletableDeferred<FirebaseVisionText>()
        val firebaseVisionImage = FirebaseVisionImage.fromBitmap(bitmap)
        val options = FirebaseVisionCloudTextRecognizerOptions.Builder()
            .setLanguageHints(listOf("en", "zh-TW"))
            .build()
        val detector = FirebaseVision.getInstance().getCloudTextRecognizer(options)
        detector.processImage(firebaseVisionImage)
            .addOnSuccessListener {
                deferred.complete(it)
            }
        return deferred
    }

    private fun saveCoverImage(): Deferred<Bitmap> {

        val deferred = CompletableDeferred<Bitmap>()

        launch {
            val photoResult = fotoapparat.takePicture()
            val bitmapPhoto = withContext(Dispatchers.IO) { photoResult.toBitmap().await() }

            val matrix = Matrix().apply { postRotate(-bitmapPhoto.rotationDegrees.toFloat()) }

            val rotateBitmap = Bitmap.createBitmap(
                bitmapPhoto.bitmap,
                0,
                0,
                bitmapPhoto.bitmap.width,
                bitmapPhoto.bitmap.height,
                matrix,
                true
            )

            val widthRatio = rotateBitmap.width.toFloat() / root.width
            val heightRatio = rotateBitmap.height.toFloat() / root.height
            val ratio = if (widthRatio > heightRatio) heightRatio else widthRatio

            val width = (ratio * root.width).toInt()
            val height = (ratio * root.height).toInt()

            val cropBitmap = Bitmap.createBitmap(
                rotateBitmap,
                (rotateBitmap.width - width) / 2,
                (rotateBitmap.height - height) / 2,
                width,
                height
            )

            try {
                rotateBitmap.recycle()
            } catch (ignore: Exception) {
            }

            val coverRatio = cropBitmap.width.toFloat() / root.width

            val coverBitmap = Bitmap.createBitmap(
                cropBitmap,
                (coverRatio * cover.left).toInt(),
                (coverRatio * cover.top).toInt(),
                (coverRatio * cover.width).toInt(),
                (coverRatio * cover.height).toInt()
            )
            try {
                cropBitmap.recycle()
            } catch (ignore: Exception) {
            }

            val file = File(externalCacheDir, "photo.jpg")
            FileOutputStream(file)
                .use { fileOutputStream ->
                    coverBitmap.compress(Bitmap.CompressFormat.JPEG, 100, fileOutputStream)
                    fileOutputStream.flush()
                }

//            try {
//                coverBitmap.recycle()
//            } catch (ignore: Exception) {
//            }
            deferred.complete(coverBitmap)
        }
        return deferred
    }

    override fun onStart() {
        super.onStart()
        fotoapparat.start()
    }

    override fun onStop() {
        super.onStop()
        fotoapparat.stop()
    }
}
