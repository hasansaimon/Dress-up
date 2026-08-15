package com.example.engine

import androidx.compose.ui.geometry.Offset
import com.example.BuildConfig
import com.example.data.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

interface ChatIntentParser {
    suspend fun parse(text: String, ctx: ChatContext): Pair<List<EditCommand>, String>
}

data class ChatContext(
    val layers: List<GarmentLayer>,
    val selectedLayerId: Long?,
    val wardrobe: List<WardrobeItem>
)

/** On-device parser — no network, works offline. */
class RuleBasedParser : ChatIntentParser {

    private val colors = mapOf(
        "red" to 0f, "crimson" to -10f, "orange" to 30f, "yellow" to 60f, "lime" to 90f,
        "green" to 120f, "teal" to 165f, "cyan" to 180f, "blue" to 240f, "navy" to 245f,
        "purple" to 280f, "magenta" to 300f, "pink" to 330f, "rose" to 345f,
        "black" to -1f, "white" to -2f, "gray" to -3f, "grey" to -3f
    )

    private val categories = mapOf(
        "dress" to GarmentCategory.DRESS, "gown" to GarmentCategory.DRESS,
        "top" to GarmentCategory.TOP, "shirt" to GarmentCategory.TOP, "blouse" to GarmentCategory.TOP,
        "t-shirt" to GarmentCategory.TOP, "tee" to GarmentCategory.TOP,
        "pant" to GarmentCategory.BOTTOM, "pants" to GarmentCategory.BOTTOM, "trouser" to GarmentCategory.BOTTOM,
        "jeans" to GarmentCategory.BOTTOM, "skirt" to GarmentCategory.BOTTOM, "bottom" to GarmentCategory.BOTTOM,
        "jacket" to GarmentCategory.OUTER, "coat" to GarmentCategory.OUTER, "blazer" to GarmentCategory.OUTER,
        "hoodie" to GarmentCategory.OUTER, "cardigan" to GarmentCategory.OUTER,
        "scarf" to GarmentCategory.SCARF, "hat" to GarmentCategory.HAT, "cap" to GarmentCategory.HAT,
        "shoe" to GarmentCategory.SHOES, "shoes" to GarmentCategory.SHOES
    )

    private val fabrics = mapOf(
        "denim" to FabricStyle.DENIM, "jean" to FabricStyle.DENIM,
        "silk" to FabricStyle.SILK, "satin" to FabricStyle.SATIN,
        "leather" to FabricStyle.LEATHER, "cotton" to FabricStyle.COTTON
    )

    override suspend fun parse(text: String, ctx: ChatContext): Pair<List<EditCommand>, String> {
        val t = text.lowercase()
        val cmds = ArrayList<EditCommand>()
        val replies = ArrayList<String>()
        val target = resolveTarget(t, ctx)

        if (t.contains("undo")) return listOf(EditCommand.Undo) to "Undid the last edit."

        if (listOf("remove", "delete", "take off", "get rid of", "hide").any { t.contains(it) }) {
            if (target != null) {
                cmds += EditCommand.Remove(target.id)
                replies += "Removed ${target.name}."
            } else replies += "Which garment should I remove? Tap one on the canvas first."
        }

        if (t.contains("add") || t.contains("put on") || t.contains("wear")) {
            val cat = categories.entries.firstOrNull { t.contains(it.key) }?.value
            val item = cat?.let { c -> ctx.wardrobe.firstOrNull { it.category == c } }
            if (item != null) {
                cmds += EditCommand.AddGarment(item.category, item.id, item.name)
                replies += "Added ${item.name} from your wardrobe."
            } else replies += "I couldn't find that in your wardrobe. Add it first."
        }

        if (t.contains("color") || t.contains("dye") || t.contains("paint") || t.contains("make it")) {
            val color = colors.entries.firstOrNull { t.contains(it.key) }
            if (color != null && target != null) {
                val hue = color.value
                val sat = if (hue < 0f) 0.1f else 1.2f
                cmds += EditCommand.ChangeColor(target.id, maxOf(hue, 0f), sat)
                replies += "Dyed ${target.name} ${color.key}."
            }
        }

        if (target != null) {
            var sx = 0f; var sy = 0f
            if (t.contains("bigger") || t.contains("larger") || t.contains("looser")) { sx = 1.1f; sy = 1.1f }
            if (t.contains("smaller")) { sx = 0.9f; sy = 0.9f }
            if (t.contains("longer")) sy = 1.15f
            if (t.contains("shorter")) sy = 0.85f
            if (t.contains("tighter")) { sx = 0.92f; sy = 1.0f }
            if (sx != 0f || sy != 0f) {
                cmds += EditCommand.Resize(target.id, sx, sy)
                replies += "Adjusted the fit of ${target.name}."
            }
        }

        if (target != null) {
            Regex("rotate\\s*(\\d+)").find(t)?.let {
                cmds += EditCommand.Rotate(target.id, it.groupValues[1].toFloat())
                replies += "Rotated ${target.name}."
            }
            if (t.contains("move up")) { cmds += EditCommand.Move(target.id, Offset(0f, -40f)); replies += "Moved up." }
            if (t.contains("move down")) { cmds += EditCommand.Move(target.id, Offset(0f, 40f)); replies += "Moved down." }
            if (t.contains("move left")) { cmds += EditCommand.Move(target.id, Offset(-40f, 0f)); replies += "Moved left." }
            if (t.contains("move right")) { cmds += EditCommand.Move(target.id, Offset(40f, 0f)); replies += "Moved right." }
        }

        if (target != null && t.contains("transparent")) {
            cmds += EditCommand.SetOpacity(target.id, 0.4f); replies += "Made ${target.name} translucent."
        }
        Regex("(\\d{1,3})\\s*%\\s*opacity").find(t)?.let {
            cmds += EditCommand.SetOpacity(target.id, it.groupValues[1].toFloat() / 100f)
            replies += "Set opacity."
        }

        if (target != null) {
            if (t.contains("brighter")) { cmds += EditCommand.SetBrightnessContrast(target.id, 25f, 1.05f); replies += "Brightened ${target.name}." }
            if (t.contains("darker")) { cmds += EditCommand.SetBrightnessContrast(target.id, -25f, 1.0f); replies += "Darkened ${target.name}." }
            if (t.contains("contrast")) { cmds += EditCommand.SetBrightnessContrast(target.id, 0f, 1.25f); replies += "Increased contrast." }
        }

        if (target != null) {
            fabrics.entries.firstOrNull { t.contains(it.key) }?.let {
                cmds += EditCommand.SetFabric(target.id, it.value)
                replies += "Switched ${target.name} to ${it.key}."
            }
        }

        if (target != null && (t.contains("duplicate") || t.contains("copy"))) {
            cmds += EditCommand.Duplicate(target.id); replies += "Duplicated ${target.name}."
        }

        if (t.contains("preview") || t.contains("compare") || t.contains("show me")) {
            cmds += EditCommand.Preview; replies += "Here's the preview."
        }

        if (cmds.isEmpty())
            replies += "I couldn't map that to an edit. Try: \"make it red\", \"remove the jacket\", \"make it longer\", \"denim\"."

        return cmds to replies.joinToString(" ")
    }

    private fun resolveTarget(t: String, ctx: ChatContext): GarmentLayer? {
        val cat = categories.entries.firstOrNull { t.contains(it.key) }?.value
        if (cat != null) return ctx.layers.lastOrNull { it.category == cat && it.visible }
        return ctx.selectedLayerId?.let { id -> ctx.layers.firstOrNull { it.id == id } }
            ?: ctx.layers.maxByOrNull { it.zIndex }
    }
}

/** Optional cloud LLM parser — uses the Gemini API already wired into this project. */
class GeminiParser : ChatIntentParser {
    override suspend fun parse(text: String, ctx: ChatContext): Pair<List<EditCommand>, String> =
        withContext(Dispatchers.IO) {
            val model = com.google.firebase.ai.firebaseai.GenerativeModel(
                modelName = "gemini-2.0-flash",
                apiKey = BuildConfig.GEMINI_API_KEY
            )
            val prompt = """
                You are the edit engine of an AI fashion try-on app.
                Layers: ${ctx.layers.map { "${it.name}(${it.id})" }}
                Selected: ${ctx.selectedLayerId}
                Wardrobe: ${ctx.wardrobe.map { it.name }}
                User: "$text"
                Reply ONLY with JSON: {"commands":[{"op":"change_color|remove|resize|move|rotate|opacity|fabric|duplicate|add","layerId":123,"hue":240,"saturation":1.2,"sx":1.1,"sy":1.1,"dx":0,"dy":40,"degrees":15,"opacity":0.5,"fabric":"denim","category":"DRESS"}],"reply":"short confirmation"}
            """.trimIndent()
            val response = model.generateContent(prompt)
            val raw = response.text.orEmpty()
            val json = raw.substringAfter('{').substringBeforeLast('}').let { "{$it}" }
            val obj = org.json.JSONObject(json)
            val arr = obj.getJSONArray("commands")
            val cmds = ArrayList<EditCommand>()
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                val id = o.optLong("layerId", ctx.selectedLayerId ?: -1)
                when (o.getString("op")) {
                    "remove" -> cmds += EditCommand.Remove(id)
                    "change_color" -> cmds += EditCommand.ChangeColor(id, o.optDouble("hue").toFloat(), o.optDouble("saturation", 1.2).toFloat())
                    "resize" -> cmds += EditCommand.Resize(id, o.optDouble("sx", 1.0).toFloat(), o.optDouble("sy", 1.0).toFloat())
                    "move" -> cmds += EditCommand.Move(id, Offset(o.optDouble("dx").toFloat(), o.optDouble("dy").toFloat()))
                    "rotate" -> cmds += EditCommand.Rotate(id, o.optDouble("degrees").toFloat())
                    "opacity" -> cmds += EditCommand.SetOpacity(id, o.optDouble("opacity").toFloat())
                    "fabric" -> cmds += EditCommand.SetFabric(id, FabricStyle.valueOf(o.getString("fabric").uppercase()))
                    "duplicate" -> cmds += EditCommand.Duplicate(id)
                    "add" -> ctx.wardrobe.firstOrNull { it.category.name == o.optString("category") }?.let {
                        cmds += EditCommand.AddGarment(it.category, it.id, it.name)
                    }
                }
            }
            cmds to obj.optString("reply", "Done.")
        }
}
