package com.bersih.cleaner

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.widget.ArrayAdapter
import android.widget.ListView
import android.widget.Toast

class FreezerActivity : Activity() {
    private lateinit var list: ListView
    private var apps = listOf<ApplicationInfo>()
    private val vital = setOf("android", "com.android.systemui", "com.android.settings", "com.android.phone", "com.android.shell", "com.android.providers.settings")

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        list = ListView(this)
        setContentView(list)
        list.setOnItemClickListener { _, _, i, _ -> act(apps[i]) }
    }

    override fun onResume() { super.onResume(); load() }

    private fun label(a: ApplicationInfo) = packageManager.getApplicationLabel(a).toString()
    private fun isSys(a: ApplicationInfo) = a.flags and ApplicationInfo.FLAG_SYSTEM != 0

    private fun load() {
        apps = packageManager.getInstalledApplications(0)
            .filter { it.packageName != packageName }
            .sortedBy { label(it).lowercase() }
        val rows = apps.map { (if (it.enabled) "" else "[BEKU] ") + label(it) + (if (isSys(it)) "  (sistem)" else "") + "\n" + it.packageName }
        list.adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, rows)
    }

    private fun act(a: ApplicationInfo) {
        val p = a.packageName
        val warn = if (isSys(a)) "\n\nAplikasi sistem: membekukan yang salah bisa membuat HP bermasalah." else ""
        val d = AlertDialog.Builder(this).setTitle(label(a)).setMessage(p + warn)
            .setNeutralButton("Info aplikasi") { _, _ ->
                startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$p")))
            }
            .setNegativeButton("Batal", null)
        if (p in vital) d.setMessage("$p adalah komponen vital dan tidak boleh dibekukan.")
        else d.setPositiveButton(if (a.enabled) "Bekukan" else "Aktifkan") { _, _ ->
            val cmd = if (a.enabled) "pm disable-user --user 0 $p" else "pm enable $p"
            Thread {
                val ok = try { Runtime.getRuntime().exec(arrayOf("su", "-c", cmd)).waitFor() == 0 } catch (e: Exception) { false }
                runOnUiThread {
                    if (ok) load()
                    else {
                        Toast.makeText(this, "Butuh root. Membuka Info aplikasi...", Toast.LENGTH_LONG).show()
                        startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$p")))
                    }
                }
            }.start()
        }
        d.show()
    }
}
