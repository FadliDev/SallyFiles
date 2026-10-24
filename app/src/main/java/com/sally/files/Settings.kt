package com.sally.files

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

data class AppSettings(
    val themeMode: Int = 0,          // 0 sistem, 1 terang, 2 gelap
    val amoled: Boolean = false,
    val dynamicColor: Boolean = false,
    val showHidden: Boolean = false,
    val sortBy: Int = 0,             // 0 nama, 1 ukuran, 2 tanggal
    val sortDesc: Boolean = false,
    val confirmDelete: Boolean = true
)

/** Lokasi: [path] = folder biasa (java.io.File), [tree] = tree URI SAF (Termux, Acode, SD card, dll) */
data class Location(val name: String, val path: String? = null, val tree: String? = null)

class Repo(ctx: Context) {
    private val p = ctx.getSharedPreferences("prefs", Context.MODE_PRIVATE)

    fun loadSettings() = AppSettings(
        themeMode = p.getInt("theme", 0),
        amoled = p.getBoolean("amoled", false),
        dynamicColor = p.getBoolean("dynamic", false),
        showHidden = p.getBoolean("hidden", false),
        sortBy = p.getInt("sort", 0),
        sortDesc = p.getBoolean("desc", false),
        confirmDelete = p.getBoolean("confirm", true)
    )

    fun saveSettings(s: AppSettings) {
        p.edit()
            .putInt("theme", s.themeMode)
            .putBoolean("amoled", s.amoled)
            .putBoolean("dynamic", s.dynamicColor)
            .putBoolean("hidden", s.showHidden)
            .putInt("sort", s.sortBy)
            .putBoolean("desc", s.sortDesc)
            .putBoolean("confirm", s.confirmDelete)
            .apply()
    }

    fun loadLocations(): List<Location> {
        val raw = p.getString("locations", null) ?: return emptyList()
        return try {
            val a = JSONArray(raw)
            (0 until a.length()).map {
                val o = a.getJSONObject(it)
                Location(
                    o.getString("name"),
                    o.optString("path").ifEmpty { null },
                    o.optString("tree").ifEmpty { null }
                )
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun saveLocations(l: List<Location>) {
        val a = JSONArray()
        l.forEach { a.put(JSONObject().put("name", it.name).put("path", it.path ?: "").put("tree", it.tree ?: "")) }
        p.edit().putString("locations", a.toString()).apply()
    }
}
