package com.example.opencvjetpackcompose

import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.*
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.rememberPermissionState
import org.opencv.android.OpenCVLoader
import org.opencv.android.Utils
import org.opencv.core.Mat
import org.opencv.core.MatOfPoint
import org.opencv.core.MatOfPoint2f
import org.opencv.core.Scalar
import org.opencv.imgproc.Imgproc
import java.io.File
import kotlin.math.*


fun createTempPictureUri(
  context: Context,
  provider: String = "com.example.opencvjetpackcompose.provider",
  fileName: String = "picture_${System.currentTimeMillis()}",
  fileExtension: String = ".png"
): Uri {
  val tempFile = File.createTempFile(
    fileName, fileExtension, context.cacheDir
  ).apply {
    createNewFile()
  }

  return FileProvider.getUriForFile(context, provider, tempFile)
}


fun processImage(context: Context, uri: Uri) : Bitmap {
  OpenCVLoader.initDebug()

  // Load the image into a Bitmap
  val bitmap:Bitmap = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
    // For Android version P (9.0) and above, use ImageDecoder
    ImageDecoder.decodeBitmap(ImageDecoder.createSource(context.contentResolver, uri))
    { decoder, _, _ ->
      decoder.isMutableRequired = true
    }
  } else {
    // For older Android versions, use MediaStore
    MediaStore.Images.Media.getBitmap(context.contentResolver, uri)
  }

  // Convert the Bitmap to an OpenCV Mat and change color space to RGB
  val src = Mat()
  // Utils.bitmapToMat() makes [B,G,R] values are stored in [R,G,B] channels. The red and blue channels are interchanged.
  Utils.bitmapToMat(bitmap, src)
  Imgproc.cvtColor(src, src, Imgproc.COLOR_BGR2RGB)

  // Clone the source Mat for further processing
  val dst = src.clone()

  // Convert the image to grayscale for Canny edge detection
  val gray = Mat()
  Imgproc.cvtColor(src, gray, Imgproc.COLOR_BGR2GRAY)

  // Apply Canny edge detection
  val edges = Mat()
  Imgproc.Canny(gray, edges, 10.0, 30.0, 3)

  // Find contours in the image
  val contours = ArrayList<MatOfPoint>()
  val hierarchy = Mat()
  Imgproc.findContours(edges, contours, hierarchy, Imgproc.RETR_EXTERNAL, Imgproc.CHAIN_APPROX_SIMPLE)

  // Iterate through contours, sort by area, and process the largest one
  for (contour in contours.sortedByDescending { Imgproc.contourArea(it) }) {
    // The information on the contours found with the findContours function is of MatOfPoint type.
    // In case of arcLength, MatOfPoint2f type is required as a parameter. In this case, MatOfPoint can be changed to MatOfPoint2f as follows.
    //
    // val contour: MatOfPoint = …
    // val contour2f: MatOfPoint2F = MatOfPoint2f(*contour.toArray())

    // Calculate epsilon for polygonal approximation
    val epsilon = 0.02 * Imgproc.arcLength(MatOfPoint2f(*contour.toArray()), true)
    val approx = MatOfPoint2f()
    Imgproc.approxPolyDP(MatOfPoint2f(*contour.toArray()), approx, epsilon, true)

    val points = approx.toList()

    // If the polygon has 4 vertices, it is likely a square
    if (points.size == 4) {
      // Draw the square on the destination Mat
      Imgproc.drawContours(dst, listOf(MatOfPoint(*points.toTypedArray())), -1, Scalar(0.0, 255.0, 0.0), 3)
      // Convert the destination Mat back to BGR color space
      Imgproc.cvtColor(dst, dst, Imgproc.COLOR_RGB2BGR)
      break
    }
  }

  // Convert the processed Mat back to Bitmap
  Utils.matToBitmap(dst, bitmap)

  // Return the processed Bitmap
  return bitmap
}

@Composable
@OptIn(ExperimentalPermissionsApi::class)
fun ImagePicker(
  modifier: Modifier = Modifier,
) {
  var currentUri by remember { mutableStateOf(value = Uri.EMPTY) }
  var tempUri by remember { mutableStateOf(value = Uri.EMPTY) }

  val context = LocalContext.current

  val imagePicker = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.GetContent(),
    onResult = { uri ->
      currentUri = uri ?: tempUri
//      hasImage = uri != null
//      imageUri = uri
    }
  )

  val cameraLauncher = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.TakePicture(),
    onResult = { success ->
      if (success) currentUri = tempUri
    }
  )

  val cameraPermissionState = rememberPermissionState(
    permission = android.Manifest.permission.CAMERA,
    onPermissionResult = { granted ->
      if (granted) {
        tempUri = createTempPictureUri(context)
        cameraLauncher.launch(tempUri)
      } else print("camera permission is denied")
    }
  )

  Column(
    modifier = modifier.padding(16.dp),
  ) {
    Box(
      modifier = Modifier
        .fillMaxWidth()
        .weight(1f),
    ) {
      if (currentUri.toString().isNotEmpty()) {
        val result = processImage(context, currentUri!!)

        Image(
          bitmap = result.asImageBitmap(),
          modifier = Modifier
            .fillMaxWidth()
            .fillMaxHeight(),
          contentScale = ContentScale.Fit,
          contentDescription = "Selected image",
        )
      }
    }
    Spacer(Modifier.height(32.dp))
    Button(
      modifier = Modifier
        .fillMaxWidth()
        .height(50.dp),
      onClick = {
        imagePicker.launch("image/*")
      }
    ) {
      Text(text = "Select image")
    }
    Spacer(Modifier.height(16.dp))
    Button(
      modifier = Modifier
        .fillMaxWidth()
        .height(50.dp),
        onClick = cameraPermissionState::launchPermissionRequest
//      onClick = {
//        cameraPermissionState::launchPermissionRequest
//        val uri = ComposeFileProvider.getImageUri(context)
//        imageUri = uri
//        cameraLauncher.launch(uri)
//      }
    ) {
      Text(text = "Take a photo")
    }
  }
}

