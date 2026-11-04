package com.bersih.cleaner

import android.Manifest
import android.app.Activity
import android.app.ActivityManager
import android.content.pm.ApplicationInfo
import android.os.Handler
import android.os.Looper
import android.app.AlertDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import android.text.format.Formatter
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import java.io.File

class MainActivity : Activity() {
    private lateinit var info: TextView
    private lateinit var cleanBtn: Button
    private var files = listOf<File>()
    private var dirs = listOf<File>()
    private val junkExt = setOf("tmp", "temp", "log", "bak", "old", "thumbdata", "cache", "crdownload", "part", "apk")

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(48, 120, 48, 48) }
        val scan = Button(this).apply { text = "Pindai"; setOnClickListener { scan() } }
        cleanBtn = Button(this).apply { text = "Bersihkan"; isEnabled = false; setOnClickListener { confirm() } }
        info = TextView(this).apply { textSize = 16f; setPadding(0, 32, 0, 0); text = "Tekan Pindai untuk mencari sampah." }
        val ram = Button(this).apply { text = "Booster RAM"; setOnClickListener { boost() } }
        val frz = Button(this).apply { text = "Freezer Aplikasi"; setOnClickListener { startActivity(Intent(this@MainActivity, FreezerActivity::class.java)) } }
        root.addView(scan); root.addView(cleanBtn); root.addView(ram); root.addView(frz); root.addView(info)
        setContentView(root)
    }

    private fun hasAccess() =
        if (Build.VERSION.SDK_INT >= 30) Environment.isExternalStorageManager()
        else checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED

    private fun scan() {
        if (!hasAccess()) {
            if (Build.VERSION.SDK_INT >= 30) {
                startActivity(Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, Uri.parse("package:$packageName")))
            } else requestPermissions(arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE), 1)
            info.text = "Beri izin akses file, lalu tekan Pindai lagi."
            return
        }
        info.text = "Memindai..."
        cleanBtn.isEnabled = false
        Thread {
            val top = Environment.getExternalStorageDirectory()
            val f = ArrayList<File>()
            val d = ArrayList<File>()
            top.walkTopDown().onEnter { !(it.parentFile == top && it.name == "Android") }.forEach {
                if (it.isFile && it.extension.lowercase() in junkExt) f.add(it)
                else if (it.isDirectory && it != top && it.parentFile != top && it.list().isNullOrEmpty()) d.add(it)
            }
            files = f; dirs = d
            val size = f.sumOf { it.length() } + (cacheDir.walkTopDown().filter { it.isFile }.sumOf { it.length() })
            runOnUiThread {
                info.text = "Sampah: ${f.size} file (.tmp/.log/.bak/.apk dll)\nFolder kosong: ${d.size}\nTotal: ${Formatter.formatFileSize(this, size)}"
                cleanBtn.isEnabled = f.isNotEmpty() || d.isNotEmpty()
            }
        }.start()
    }

    private fun confirm() {
        AlertDialog.Builder(this)
            .setTitle("Hapus permanen?")
            .setMessage("${files.size} file dan ${dirs.size} folder kosong akan dihapus. File APK di penyimpanan juga ikut terhapus.")
            .setPositiveButton("Hapus") { _, _ -> clean() }
            .setNegativeButton("Batal", null).show()
    }

    private fun clean() {
        cleanBtn.isEnabled = false
        info.text = "Membersihkan..."
        Thread {
            var n = 0
            files.forEach { if (it.delete()) n++ }
            dirs.sortedByDescending { it.path.length }.forEach { if (it.list().isNullOrEmpty()) it.delete() }
            cacheDir.listFiles()?.forEach { it.deleteRecursively() }
            files = emptyList(); dirs = emptyList()
            runOnUiThread { info.text = "Selesai. $n file terhapus." }
        }.start()
    }

    private fun freeRam(): Long {
        val mi = ActivityManager.MemoryInfo()
        (getSystemService(ACTIVITY_SERVICE) as ActivityManager).getMemoryInfo(mi)
        return mi.availMem
    }

    private fun boost() {
        val before = freeRam()
        val am = getSystemService(ACTIVITY_SERVICE) as ActivityManager
        packageManager.getInstalledApplications(0)
            .filter { it.flags and ApplicationInfo.FLAG_SYSTEM == 0 && it.packageName != packageName }
            .forEach { am.killBackgroundProcesses(it.packageName) }
        info.text = "Menutup aplikasi latar belakang..."
        Handler(Looper.getMainLooper()).postDelayed({
            info.text = "RAM kosong: ${Formatter.formatFileSize(this, before)} -> ${Formatter.formatFileSize(this, freeRam())}"
        }, 1500)
    }
}
