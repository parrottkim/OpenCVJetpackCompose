# OpenCVJetpackCompose

<img src="https://github.com/ParrottKim/OpenCVJetpackCompose/assets/83802425/1a94aed1-8544-4623-8427-8a5b8ed8a45a.gif" width=40% height=40%/>

Find Largest Rectangle in Image and Draw Contour Line with OpenCV and Jetpack Compose

`ImagePicker.kt`
``` Kotlin
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
```
