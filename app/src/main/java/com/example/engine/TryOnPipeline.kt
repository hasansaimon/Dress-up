package com.example.engine

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import com.example.BuildConfig
import com.example.data.EditSession
import com.example.data.GarmentCategory
import com.example.data.GarmentLayer
import com.example.data.LayerAssets
import com.google.mediapipe.framework.image.BitmapExtractor
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.imagesegmenter.ImageSegmenter
import com.google.mediapipe.tasks.vision.objectdetector.ObjectDetector
import com.google.mediapipe.tasks.vision.poselandmarker.PoseLandmarker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.MediaType.Companion.toMediaType
import org.json.JSONObject
import org.opencv.android.OpenCVLoader
import org.opencv.android.Utils
import org.opencv.core.*
import org.opencv.imgproc.Imgproc
import java.io.ByteArrayOutputStream

class TryOnPipeline(private val context: Context) {

    // ---------------------------------------------------------------- models
    private val detector by lazy {
        val opts = ObjectDetector.ObjectDetectorOptions.builder()
            .setBaseOptions(BaseOptions.builder().setModelAssetPath("efficientdet_lite0.tflite").build())
            .setScoreThreshold(0.5f)
            .setMaxResults(10)
            .build()
        ObjectDetector.createFromOptions(context, opts)
    }

    private val segmenter by lazy {
        val opts = ImageSegmenter.ImageSegmenterOptions.builder()
            .setBaseOptions(BaseOptions.builder().setModelAssetPath("selfie_segmentation.tflite").build())
            .setOutputCategoryMask(true)
            .build()
        ImageSegmenter.createFromOptions(context, opts)
    }

    private val poseLandmarker by lazy {
        val opts = PoseLandmarker.PoseLandmarkerOptions.builder()
            .setBaseOptions(BaseOptions.builder().setModelAssetPath("pose_landmarker_lite.task").build())
            .setRunningMode(RunningMode.IMAGE)
            .build()
        PoseLandmarker.createFromOptions(context, opts)
    }

    // ------------------------------------------------------------- utilities
    private fun decode(uri: Uri): Bitmap {
        val opts = BitmapFactory.Options().apply { inSampleSize = 2 }
        val bmp = context.contentResolver.openInputStream(uri)!!.use {
            BitmapFactory.decodeStream(it, null, opts)
        }
        val maxDim = maxOf(bmp.width, bmp.height)
        if (maxDim <= 1024) return bmp
        val scale = 1024f / maxDim
        return Bitmap.createScaledBitmap(bmp, (bmp.width * scale).toInt(), (bmp.height * scale).toInt(), true)
    }

    private fun ensureOpenCV() {
        if (!OpenCVLoader.initLocal()) throw IllegalStateException("OpenCV failed to load")
    }

    // -------------------------------------------------------- multi-person
    private fun detectPeople(bmp: Bitmap): List<android.graphics.Rect> {
        val mp = BitmapImageBuilder(bmp).build()
        val res = detector.detect(mp)
        return res.detections()
            .filter { d -> d.categories().any { it.categoryName() == "person" } }
            .mapNotNull { d ->
                val bb = d.boundingBox().orElse(null) ?: return@mapNotNull null
                android.graphics.Rect(bb.left, bb.top, bb.right, bb.bottom)
            }
            .sortedByDescending { it.width() * it.height() }
    }

    // ---------------------------------------------------- session creation
    /** Detect the person, warp the garment onto them, and return an editable session. */
    suspend fun createSession(modelUri: Uri, garmentUri: Uri, personIndex: Int): EditSession =
        withContext(Dispatchers.Default) {
            ensureOpenCV()
            val model = decode(modelUri)
            val garment = decode(garmentUri)
            val people = detectPeople(model)
            require(people.isNotEmpty()) { "No person detected in the model photo" }
            val box = people[personIndex.coerceIn(0, people.size - 1)]

            val personMask = personMask(model, box)
            val torso = torsoQuad(model, box) ?: throw IllegalStateException("Could not find shoulders/hips")
            val gMat = Mat(); Utils.bitmapToMat(garment, gMat)
            val gMask = garmentMask(gMat)
            val (warped, warpedMask) = warpGarment(gMat, gMask, torso, model.width, model.height)

            val garmentBmp = Bitmap.createBitmap(warped.cols(), warped.rows(), Bitmap.Config.ARGB_8888)
            val maskBmp = Bitmap.createBitmap(warpedMask.cols(), warpedMask.rows(), Bitmap.Config.ARGB_8888)
            Utils.matToBitmap(warped, garmentBmp)
            Utils.matToBitmap(warpedMask, maskBmp)

            val (hue, sat, brightness) = skinTonePreset(model, personMask)
            val id = "layer_${System.nanoTime()}"
            val assets = LayerAssets().apply { put(id, garmentBmp, maskBmp) }
            val layer = GarmentLayer(
                id = 1, assetId = id, name = "Dress", category = GarmentCategory.DRESS, zIndex = 0,
                hueShift = hue, saturation = sat, brightness = brightness
            )
            EditSession(model, listOf(layer), assets, people)
        }

    /** Decode + background-remove a flat-lay garment photo into (bitmap, mask). */
    suspend fun prepareGarment(uri: Uri): Pair<Bitmap, Bitmap> = withContext(Dispatchers.Default) {
        ensureOpenCV()
        val g = decode(uri)
        val mat = Mat(); Utils.bitmapToMat(g, mat)
        val mask = garmentMask(mat)
        val maskBmp = Bitmap.createBitmap(mask.cols(), mask.rows(), Bitmap.Config.ARGB_8888)
        Utils.matToBitmap(mask, maskBmp)
        g to maskBmp
    }

    // --------------------------------------------------------- segmentation
    private fun personMask(bmp: Bitmap, box: android.graphics.Rect): Mat {
        val mp = BitmapImageBuilder(bmp).build()
        val res = segmenter.segment(mp)
        val maskBmp = BitmapExtractor.extract(res.categoryMask())
        val full = Mat(); Utils.bitmapToMat(maskBmp, full)
        val bin = Mat()
        Imgproc.threshold(full, bin, 0.0, 255.0, Imgproc.THRESH_BINARY)
        val roi = Mat(bin, org.opencv.core.Rect(box.left, box.top, box.width(), box.height()))

        // Keep only the largest connected component (the person in this box).
        val labels = Mat(); val stats = Mat(); val cents = Mat()
        val n = Imgproc.connectedComponentsWithStats(roi, labels, stats, cents, 8, CvType.CV_32S)
        var best = -1; var bestArea = 0
        for (i in 1 until n) {
            val area = stats.get(i, Imgproc.CC_STAT_AREA)[0].toInt()
            if (area > bestArea) { bestArea = area; best = i }
        }
        var comp = Mat.zeros(roi.size(), CvType.CV_8UC1)
        if (best > 0) {
            val tmp = Mat.zeros(roi.size(), CvType.CV_8UC1)
            for (r in 0 until roi.rows())
                for (c in 0 until roi.cols())
                    if (labels.get(r, c)[0].toInt() == best) tmp.put(r, c, 255.0)
            comp = tmp
        }
        val placed = Mat.zeros(bmp.height, bmp.width, CvType.CV_8UC1)
        comp.copyTo(placed.submat(box.top, box.bottom, box.left, box.right))

        // Feather the silhouette so the dress edge blends into skin naturally.
        Imgproc.GaussianBlur(placed, placed, Size(9.0, 9.0), 0.0)
        return placed
    }

    // ------------------------------------------------------------- pose quad
    /** Returns [topLeft, topRight, bottomRight, bottomLeft] of the torso. */
    private fun torsoQuad(bmp: Bitmap, box: android.graphics.Rect): Array<Point>? {
        val mp = BitmapImageBuilder(bmp).build()
        val res = poseLandmarker.detect(mp)
        val lm = res.landmarks().firstOrNull() ?: return null
        fun p(idx: Int): Point = Point(lm.get(idx).x() * bmp.width, lm.get(idx).y() * bmp.height)

        val sL = p(11)  // left shoulder
        val sR = p(12)  // right shoulder
        val hL = p(23)  // left hip
        val hR = p(24)  // right hip
        val mid = Point((sL.x + sR.x) / 2, (sL.y + sR.y) / 2)
        val spread = Math.hypot(sL.x - sR.x, sL.y - sR.y) * 0.12
        val dirX = (sR.x - sL.x) / spread * 0.5
        val dirY = (sR.y - sL.y) / spread * 0.5
        val a = Point(sL.x - dirX * spread, sL.y - dirY * spread)
        val b = Point(sR.x + dirX * spread, sR.y + dirY * spread)
        return arrayOf(a, b, hR, hL)
    }

    // ------------------------------------------------------------ garment mask
    /** Background removal for a flat-lay garment photo (plain background). */
    private fun garmentMask(img: Mat): Mat {
        val h = img.rows(); val w = img.cols()
        val corners = arrayOf(
            doubleArrayOf(img.get(5, 5)), doubleArrayOf(img.get(5, w - 6)),
            doubleArrayOf(img.get(h - 6, 5)), doubleArrayOf(img.get(h - 6, w - 6))
        )
        val bg = doubleArrayOf(
            corners.sortedBy { it[0] }[1][0],
            corners.sortedBy { it[1] }[1][1],
            corners.sortedBy { it[2] }[1][2]
        )
        val diff = Mat(); Core.absdiff(img, Scalar(bg[0], bg[1], bg[2]), diff)
        val gray = Mat(); Imgproc.cvtColor(diff, gray, Imgproc.COLOR_RGBA2GRAY)
        val mask = Mat()
        Imgproc.threshold(gray, mask, 30.0, 255.0, Imgproc.THRESH_BINARY)

        val labels = Mat(); val stats = Mat(); val cents = Mat()
        val n = Imgproc.connectedComponentsWithStats(mask, labels, stats, cents, 8, CvType.CV_32S)
        var best = -1; var bestArea = 0
        for (i in 1 until n) {
            val area = stats.get(i, Imgproc.CC_STAT_AREA)[0].toInt()
            if (area > bestArea) { bestArea = area; best = i }
        }
        val clean = Mat.zeros(mask.size(), CvType.CV_8UC1)
        if (best > 0) {
            for (r in 0 until mask.rows())
                for (c in 0 until mask.cols())
                    if (labels.get(r, c)[0].toInt() == best) clean.put(r, c, 255.0)
        }
        val kernel = Imgproc.getStructuringElement(Imgproc.MORPH_ELLIPSE, Size(5.0, 5.0))
        Imgproc.morphologyEx(clean, clean, Imgproc.MORPH_CLOSE, kernel)
        return clean
    }

    // ----------------------------------------------------------------- warp
    private fun warpGarment(
        garment: Mat, mask: Mat, torso: Array<Point>,
        canvasW: Int, canvasH: Int
    ): Pair<Mat, Mat> {
        val contours = ArrayList<MatOfPoint>()
        val m = mask.clone()
        Imgproc.findContours(m, contours, Mat(), Imgproc.RETR_EXTERNAL, Imgproc.CHAIN_APPROX_SIMPLE)
        val contour = contours.maxByOrNull { Imgproc.contourArea(it) } ?: return garment to mask
        val rect = Imgproc.minAreaRect(MatOfPoint2f(*contour.toArray()))
        val pts = rect.points().sortedWith(compareBy({ it.y }, { it.x }))
        val top = pts.take(2).sortedBy { it.x }   // [TL, TR]
        val bottom = pts.drop(2).sortedBy { it.x } // [BL, BR]
        val src = MatOfPoint2f(top[0], top[1], bottom[1], bottom[0])
        val dst = MatOfPoint2f(torso[0], torso[1], torso[2], torso[3])
        val H = Imgproc.getPerspectiveTransform(src, dst)

        val size = Size(canvasW.toDouble(), canvasH.toDouble())
        val warped = Mat(); Imgproc.warpPerspective(garment, warped, H, size, Imgproc.INTER_LINEAR)
        val warpedMask = Mat(); Imgproc.warpPerspective(mask, warpedMask, H, size, Imgproc.INTER_NEAREST)
        return warped to warpedMask
    }

    // ------------------------------------------- skin tone + lighting blend
    private fun harmonize(garment: Mat, gMask: Mat, model: Mat, personMask: Mat): Mat {
        val out = garment.clone()

        // --- skin pixels (YCrCb heuristics) inside the person silhouette
        val ycrcb = Mat(); Imgproc.cvtColor(model, ycrcb, Imgproc.COLOR_RGBA2YCrCb)
        val skin = Mat()
        Core.inRange(ycrcb, Scalar(0.0, 133.0, 77.0), Scalar(255.0, 173.0, 127.0), skin)
        Core.bitwise_and(skin, personMask, skin)

        if (Core.countNonZero(skin) > 1000) {
            val skinMean = Core.mean(model, skin)
            val garMean = Core.mean(garment, gMask)
            val delta = Scalar(
                (skinMean.`val`[0] - garMean.`val`[0]) * 0.35,
                (skinMean.`val`[1] - garMean.`val`[1]) * 0.35,
                (skinMean.`val`[2] - garMean.`val`[2]) * 0.35
            )
            Core.add(out, delta, out, gMask)
        }

        // --- relight in float to match scene illumination
        val gray = Mat(); Imgproc.cvtColor(model, gray, Imgproc.COLOR_RGBA2GRAY)
        val illum = Mat(); Imgproc.GaussianBlur(gray, illum, Size(151.0, 151.0), 0.0)
        val ref = Core.mean(illum, personMask).`val`[0]
        if (ref > 1.0) Core.divide(illum, Scalar(ref), illum)
        val fout = Mat(); out.convertTo(fout, CvType.CV_32FC3, 1.0 / 255.0)
        val illumNorm = Mat(); Core.convertTo(illum, illumNorm, CvType.CV_32F, 1.0 / 255.0)
        val lit = Mat(); Core.multiply(fout, illumNorm, lit)
        val tmp = Mat(); lit.convertTo(tmp, CvType.CV_8UC3, 255.0)
        tmp.copyTo(out, gMask)

        // --- feather the mask edge for a soft, natural boundary
        val soft = Mat(); Imgproc.GaussianBlur(gMask, soft, Size(17.0, 17.0), 0.0)
        Core.multiply(out, Scalar(1.0), out, soft)
        return out
    }

    // ------------------------------------------------------------- composite
    private fun blendAlpha(base: Mat, overlay: Mat, warpedMask: Mat, personMask: Mat): Mat {
        val clip = Mat(); Core.bitwise_and(warpedMask, personMask, clip)
        val alpha = Mat(); Imgproc.GaussianBlur(clip, alpha, Size(11.0, 11.0), 0.0)

        val fbase = Mat(); base.convertTo(fbase, CvType.CV_32FC3, 1.0 / 255.0)
        val fover = Mat(); overlay.convertTo(fover, CvType.CV_32FC3, 1.0 / 255.0)
        val fa = Mat(); alpha.convertTo(fa, CvType.CV_32FC1, 1.0 / 255.0)
        val inv = Mat(); Core.subtract(Scalar(1.0), fa, inv)

        val t1 = Mat(); Core.multiply(fbase, inv, t1)
        val t2 = Mat(); Core.multiply(fover, fa, t2)
        val out = Mat(); Core.add(t1, t2, out)
        val res = Mat(); out.convertTo(res, CvType.CV_8UC3, 255.0)
        return res
    }

    // ------------------------------------------------------------- helpers
    /** Pre-grade the layer so the dress starts close to the model's skin tone. */
    private fun skinTonePreset(model: Bitmap, personMask: Mat): Triple<Float, Float, Float> {
        val m = Mat(); Utils.bitmapToMat(model, m)
        val ycrcb = Mat(); Imgproc.cvtColor(m, ycrcb, Imgproc.COLOR_RGBA2YCrCb)
        val skin = Mat()
        Core.inRange(ycrcb, Scalar(0.0, 133.0, 77.0), Scalar(255.0, 173.0, 127.0), skin)
        Core.bitwise_and(skin, personMask, skin)
        val mean = Core.mean(m, skin)
        val luma = 0.299 * mean.`val`[0] + 0.587 * mean.`val`[1] + 0.114 * mean.`val`[2]
        val brightness = ((luma - 128.0) / 128.0 * 30.0).toFloat()
        return Triple(0f, 1.05f, brightness.coerceIn(-40f, 40f))
    }

    private fun fullMask(m: Mat): Mat {
        val mask = Mat.zeros(m.size(), CvType.CV_8UC1)
        mask.setTo(Scalar(255.0))
        return mask
    }

    // ------------------------------------------------------ cloud (IDM-VTON)
    /** Optional photorealistic draping via Replicate; needs REPLICATE_TOKEN. */
    suspend fun processCloud(modelUri: Uri, garmentUri: Uri, personIndex: Int): Bitmap {
        ensureOpenCV()
        require(BuildConfig.REPLICATE_TOKEN.isNotBlank()) { "Set REPLICATE_TOKEN in .env to use the cloud model" }

        val model = decode(modelUri)
        val people = detectPeople(model)
        require(people.isNotEmpty()) { "No person detected in the model photo" }
        val box = people[personIndex.coerceIn(0, people.size - 1)]
        val crop = Bitmap.createBitmap(model, box.left, box.top, box.width(), box.height())
        val garment = decode(garmentUri)

        val client = OkHttpClient()
        val input = JSONObject()
            .put("human_img", dataUri(crop))
            .put("garment_img", dataUri(garment))
            .put("category", "upper_body")
        val body = JSONObject()
            .put("version", BuildConfig.REPLICATE_MODEL)
            .put("input", input)

        val req = Request.Builder()
            .url("https://api.replicate.com/v1/predictions")
            .header("Authorization", "Bearer ${BuildConfig.REPLICATE_TOKEN}")
            .post(body.toString().toRequestBody("application/json".toMediaType()))
            .build()

        val resp = JSONObject(client.newCall(req).execute().body!!.string())
        val url = JSONObject(resp.getString("urls")).getString("get")
        var outUrl: String? = null
        repeat(60) {
            Thread.sleep(3000)
            val r = JSONObject(
                client.newCall(Request.Builder().url(url).header("Authorization", "Bearer ${BuildConfig.REPLICATE_TOKEN}").build()).execute().body!!.string()
            )
            when (r.getString("status")) {
                "succeeded" -> { outUrl = r.getJSONArray("output").getString(0); return@repeat }
                "failed" -> throw IllegalStateException("Cloud model failed: ${r.optString("error")}")
            }
        }
        requireNotNull(outUrl) { "Cloud model timed out" }

        val outBmp = downloadBitmap(client, outUrl)
        val oMat = Mat(); Utils.bitmapToMat(outBmp, oMat)
        val mMat = Mat(); Utils.bitmapToMat(model, mMat)
        val personMask = personMask(model, box)
        val harmonized = harmonize(oMat, fullMask(oMat), mMat, personMask)
        val final = blendAlpha(mMat, harmonized, fullMask(oMat), personMask)
        val result = Bitmap.createBitmap(final.cols(), final.rows(), Bitmap.Config.ARGB_8888)
        Utils.matToBitmap(final, result)
        return result
    }

    private fun dataUri(bmp: Bitmap): String {
        val bos = ByteArrayOutputStream()
        bmp.compress(Bitmap.CompressFormat.JPEG, 90, bos)
        val b64 = if (android.os.Build.VERSION.SDK_INT >= 26)
            java.util.Base64.getEncoder().encodeToString(bos.toByteArray())
        else
            android.util.Base64.encodeToString(bos.toByteArray(), android.util.Base64.NO_WRAP)
        return "data:image/jpeg;base64,$b64"
    }

    private fun downloadBitmap(client: OkHttpClient, url: String): Bitmap {
        val bytes = client.newCall(Request.Builder().url(url).build()).execute().body!!.bytes()
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
    }
}
