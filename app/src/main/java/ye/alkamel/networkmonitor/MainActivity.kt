package ye.alkamel.networkmonitor

import android.Manifest
import android.app.*
import android.content.*
import android.content.pm.PackageManager
import android.os.*
import android.widget.*
import java.net.InetSocketAddress
import java.net.Socket
import java.util.concurrent.Executors

class MainActivity : Activity() {
    private lateinit var list: TextView
    private lateinit var summary: TextView
    private val executor = Executors.newFixedThreadPool(16)
    private val prefs by lazy { getSharedPreferences("devices", MODE_PRIVATE) }
    private val devices = mutableListOf<Device>()

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        setContentView(R.layout.activity_main)
        list = findViewById(R.id.list)
        summary = findViewById(R.id.summary)
        load()
        findViewById<Button>(R.id.scan).setOnClickListener { scanAll() }
        findViewById<Button>(R.id.addRange).setOnClickListener { addRange() }
        findViewById<Button>(R.id.addDevice).setOnClickListener { addDevice() }
        findViewById<Button>(R.id.monitor).setOnClickListener {
            startService(Intent(this, MonitorService::class.java))
            Toast.makeText(this, "تم تشغيل المراقبة", Toast.LENGTH_SHORT).show()
        }
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 10)
        }
        render()
    }

    private fun addRange() {
        val v = EditText(this)
        v.hint = "10.0.0.2-10.0.0.100"
        AlertDialog.Builder(this).setTitle("إضافة رنج IP").setView(v)
            .setPositiveButton("إضافة") { _, _ ->
                val p = v.text.toString().trim().split("-")
                if (p.size == 2) {
                    val a = ipToLong(p[0].trim())
                    val z = ipToLong(p[1].trim())
                    if (a != null && z != null && z >= a && z - a <= 1000) {
                        for (i in a..z) {
                            val ip = longToIp(i)
                            if (devices.none { it.ip == ip }) devices.add(Device("AP-" + ip.substringAfterLast('.'), ip, "", "", ""))
                        }
                        save()
                        render()
                    }
                }
            }.setNegativeButton("إلغاء", null).show()
    }

    private fun addDevice() {
        val box = LinearLayout(this)
        box.orientation = LinearLayout.VERTICAL
        val name = EditText(this); name.hint = "اسم الجهاز"
        val ip = EditText(this); ip.hint = "IP"
        val place = EditText(this); place.hint = "المكان"
        val person = EditText(this); person.hint = "اسم الشخص"
        val note = EditText(this); note.hint = "ملاحظة"
        listOf(name, ip, place, person, note).forEach { box.addView(it) }
        AlertDialog.Builder(this).setTitle("إضافة جهاز").setView(box)
            .setPositiveButton("حفظ") { _, _ ->
                val address = ip.text.toString().trim()
                if (ipToLong(address) != null && devices.none { it.ip == address }) {
                    devices.add(Device(name.text.toString().ifBlank { "AP-" + address.substringAfterLast('.') }, address, place.text.toString(), person.text.toString(), note.text.toString()))
                    save()
                    render()
                }
            }.setNegativeButton("إلغاء", null).show()
    }

    private fun scanAll() {
        if (devices.isEmpty()) {
            Toast.makeText(this, "أضف الأجهزة أولًا", Toast.LENGTH_SHORT).show()
            return
        }
        Toast.makeText(this, "بدأ الفحص", Toast.LENGTH_SHORT).show()
        for (d in devices.filter { it.monitor }) {
            executor.execute {
                d.online = probe(d.ip)
                runOnUiThread { render() }
            }
        }
    }

    private fun probe(ip: String): Boolean {
        return try {
            Socket().use { it.connect(InetSocketAddress(ip, 80), 900); true }
        } catch (_: Exception) {
            try {
                Socket().use { it.connect(InetSocketAddress(ip, 443), 900); true }
            } catch (_: Exception) { false }
        }
    }

    private fun render() {
        var on = 0
        val s = StringBuilder()
        for (d in devices) {
            if (d.online) on++
            s.append(if (d.monitor) (if (d.online) "🟢" else "🔴") else "⚪")
                .append(" ").append(d.name).append("  ").append(d.ip).append("\n")
                .append("   ").append(d.person).append(" — ").append(d.place).append("\n")
                .append("   ").append(d.note).append("\n\n")
        }
        summary.text = "🟢 " + on + " متصل   🔴 " + (devices.size - on) + " غير متصل   📡 " + devices.size + " جهاز"
        list.text = if (s.isEmpty()) "لا توجد أجهزة بعد" else s.toString()
    }

    private fun save() {
        prefs.edit().putString("data", devices.joinToString("\n") {
            listOf(it.name, it.ip, it.place, it.person, it.note, it.monitor.toString()).joinToString("|\\")
        }).apply()
    }

    private fun load() {
        val x = prefs.getString("data", "") ?: ""
        if (x.isNotBlank()) for (row in x.split("\n")) {
            val p = row.split("|\\")
            if (p.size >= 6) devices.add(Device(p[0], p[1], p[2], p[3], p[4], p[5].toBoolean()))
        }
    }

    override fun onDestroy() {
        executor.shutdownNow()
        super.onDestroy()
    }

    data class Device(var name:String, var ip:String, var place:String, var person:String, var note:String, var monitor:Boolean=true, var online:Boolean=false)

    companion object {
        fun ipToLong(s:String):Long? = try {
            s.split('.').let {
                if (it.size == 4 && it.all { p -> p.toIntOrNull() in 0..255 })
                    it.fold(0L) { a,v -> (a shl 8) + v.toLong() } else null
            }
        } catch(_:Exception) { null }

        fun longToIp(x:Long) = (x shr 24 and 255).toString() + "." + (x shr 16 and 255) + "." + (x shr 8 and 255) + "." + (x and 255)
    }
}
