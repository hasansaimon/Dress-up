package com.example.engine

import android.content.ContentValues
import android.content.Context
import android.graphics.*
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import com.example.data.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object CompositeEngine {

    /** Re-renders the whole edit stack. Called after every edit. */
    suspend fun render(base: Bitmap, layers: List<GarmentLayer>, assets: LayerAssets): Bitmap =
        withContext(Dispatchers.Default) {
            val out = base.copy(Bitmap.Config.ARGB_8888, true)
            val canvas = Canvas(out)
            for (layer in layers.sortedBy { it.zIndex }) {
                if (!layer.visible) continue
                val (garment, mask) = assets.get(layer.assetId) ?: continue
                val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply {
                    alpha = (layer.opacity * 255).toInt()
                    colorFilter = ColorMatrixColorFilter(colorMatrix(layer))
                }
                canvas.save()
                canvas.translate(out.width / 2f + layer.offset.x, out.height / 2f + layer.offset.y)
                canvas.rotate(layer.rotationDeg)
                canvas.scale(layer.scaleX, layer.scaleY)
                mask?.let { m ->
                    val graded = Bitmap.createBitmap(garment.width, garment.height, Bitmap.Config.ARGB_8888)
                    val gc = Canvas(graded)
                    gc.drawBitmap(garment, 0f, 0f, paint)
                    gc.drawBitmap(m, 0f, 0f, Paint().apply {
                        xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_IN)
                    })
                    canvas.drawBitmap(graded, -garment.width / 2f, -garment.height / 2f, Paint())
                    graded.recycle()
                } ?: canvas.drawBitmap(garment, -garment.width / 2f, -garment.height / 2f, paint)
                canvas.restore()
            }
            out
        }

    /** HSV hue rotation + saturation + brightness + contrast in one matrix. */
    private fun colorMatrix(l: GarmentLayer): ColorMatrix {
        val m = ColorMatrix()
        if (l.hueShift != 0f) m.setRotate(0, l.hueShift)
        m.postConcat(ColorMatrix().apply { setSaturation(l.saturation) })
        if (l.contrast != 1f) {
            val c = l.contrast; val t = (1 - c) * 127.5f
            m.postConcat(ColorMatrix(floatArrayOf(
                c, 0f, 0f, 0f, t,
                0f, c, 0f, 0f, t,
                0f, 0f, c, 0f, t,
                0f, 0f, 0f, 1f, 0f)))
        }
        if (l.brightness != 0f) m.postTranslate(l.brightness * 2.55f, l.brightness * 2.55f, l.brightness * 2.55f, 0f)
        return m
    }

    /** Procedural fabric overlay — no assets needed. */
    fun fabricOverlay(garment: Bitmap, fabric: FabricStyle): Bitmap = when (fabric) {
        FabricStyle.NONE -> garment
        else -> {
            val out = garment.copy(Bitmap.Config.ARGB_8888, true)
            val c = Canvas(out)
            val paint = Paint(Paint.ANTI_ALIAS_FLAG)
            when (fabric) {
                FabricStyle.DENIM -> {
                    paint.color = 0x22213A55.toInt(); paint.strokeWidth = 2f
                    var y = 0f
                    while (y < out.height) { c.drawLine(0f, y, out.width.toFloat(), y + 8f, paint); y += 14f }
                    paint.color = 0x11000000.toInt(); paint.strokeWidth = 1.5f
                    var x = 0f
                    while (x < out.width) { c.drawLine(x, 0f, x - 6f, out.height.toFloat(), paint); x += 13f }
                }
                FabricStyle.SILK -> {
                    paint.shader = LinearGradient(0f, 0f, out.width.toFloat(), out.height.toFloat(),
                        0x33FFFFFF.toInt(), 0x00000000.toInt(), Shader.TileMode.CLAMP)
                    c.drawRect(0f, 0f, out.width.toFloat(), out.height.toFloat(), paint)
                }
                FabricStyle.LEATHER -> {
                    paint.shader = LinearGradient(0f, 0f, out.width.toFloat(), 0f,
                        0x66FFFFFF.toInt(), 0x00000000.toInt(), Shader.TileMode.CLAMP)
                    c.drawRect(0f, 0f, out.width.toFloat(), out.height.toFloat(), paint)
                }
                FabricStyle.COTTON -> {
                    paint.color = 0x08FFFFFF.toInt()
                    val rnd = java.util.Random(7)
                    repeat(3000) {
                        c.drawCircle(rnd.nextFloat() * out.width, rnd.nextFloat() * out.height,
                            rnd.nextFloat() * 1.6f + 0.3f, paint)
                    }
                }
                FabricStyle.SATIN -> {
                    paint.shader = LinearGradient(0f, 0f, 0f, out.height.toFloat(),
                        0x44FFFFFF.toInt(), 0x22000000.toInt(), Shader.TileMode.CLAMP)
                    c.drawRect(0f, 0f, out.width.toFloat(), out.height.toFloat(), paint)
                }
                FabricStyle.NONE -> {}
            }
            out
        }
    }

    fun export(context: Context, bitmap: Bitmap, name: String): Uri? {
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, "$name.png")
            put(MediaStore.Images.Media.MIME_TYPE, "image/png")
            if (Build.VERSION.SDK_INT >= 29)
                put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/VirtualTryOn")
        }
        val uri = context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values) ?: return null
        context.contentResolver.openOutputStream(uri)?.use {
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
        }
        return uri
    }
}
