package com.example.data

import android.graphics.Bitmap
import android.graphics.Rect
import androidx.compose.ui.geometry.Offset

enum class GarmentCategory { TOP, DRESS, BOTTOM, OUTER, SCARF, HAT, SHOES, UNDEFINED }
enum class FabricStyle { NONE, DENIM, SILK, LEATHER, COTTON, SATIN }

/** A saved garment in the wardrobe (URI kept; bitmap decoded when added as a layer). */
data class WardrobeItem(
    val id: String,
    val name: String,
    val category: GarmentCategory,
    val uri: android.net.Uri
)

/** Lightweight state — snapshots are cheap because bitmaps live in [LayerAssets]. */
data class GarmentLayer(
    val id: Long,
    val assetId: String,
    val name: String,
    val category: GarmentCategory,
    val zIndex: Int,
    val offset: Offset = Offset.Zero,       // px from image center
    val scaleX: Float = 1f,
    val scaleY: Float = 1f,
    val rotationDeg: Float = 0f,
    val opacity: Float = 1f,                // 0..1
    val visible: Boolean = true,
    val hueShift: Float = 0f,               // degrees
    val saturation: Float = 1f,
    val brightness: Float = 0f,             // -100..100
    val contrast: Float = 1f,
    val fabric: FabricStyle = FabricStyle.NONE
)

/** Heavy bitmaps, keyed by assetId — excluded from undo/redo snapshots. */
class LayerAssets {
    private val map = HashMap<String, Pair<Bitmap, Bitmap?>>() // garment, mask
    fun put(id: String, garment: Bitmap, mask: Bitmap?) { map[id] = garment to mask }
    fun get(id: String): Pair<Bitmap, Bitmap?>? = map[id]
    fun remove(id: String) { map.remove(id) }
}

data class EditSession(
    val base: Bitmap,
    val layers: List<GarmentLayer>,
    val assets: LayerAssets,
    val people: List<Rect>
)

/** Commands produced by the chat parser or tool buttons. */
sealed class EditCommand {
    data class AddGarment(val category: GarmentCategory, val assetId: String, val name: String) : EditCommand()
    data class Remove(val layerId: Long) : EditCommand()
    data class Duplicate(val layerId: Long) : EditCommand()
    data class Move(val layerId: Long, val delta: Offset) : EditCommand()
    data class Resize(val layerId: Long, val sx: Float, val sy: Float) : EditCommand()
    data class Rotate(val layerId: Long, val degrees: Float) : EditCommand()
    data class SetOpacity(val layerId: Long, val value: Float) : EditCommand()
    data class ChangeColor(val layerId: Long, val hue: Float, val saturation: Float) : EditCommand()
    data class SetBrightnessContrast(val layerId: Long, val brightness: Float, val contrast: Float) : EditCommand()
    data class SetFabric(val layerId: Long, val fabric: FabricStyle) : EditCommand()
    data class ToggleVisible(val layerId: Long) : EditCommand()
    object Undo : EditCommand()
    object Preview : EditCommand()
}

class EditHistory<T> {
    private val undo = ArrayDeque<T>()
    private val redo = ArrayDeque<T>()
    private var current: T? = null

    fun snapshot(): T? = current
    fun push(state: T) {
        current?.let { undo.addLast(it) }
        current = state
        redo.clear()
    }
    fun undo(): T? {
        if (undo.isEmpty()) return null
        redo.addLast(current!!)
        current = undo.removeLast()
        return current
    }
    fun redo(): T? {
        if (redo.isEmpty()) return null
        undo.addLast(current!!)
        current = redo.removeLast()
        return current
    }
    fun canUndo() = undo.isNotEmpty()
    fun canRedo() = redo.isNotEmpty()
}
