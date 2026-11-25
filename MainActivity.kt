package com.mafia.child

import android.app.Activity
import android.os.Bundle
import android.os.BatteryManager
import android.content.Context
import android.widget.*
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import kotlin.concurrent.thread

class MainActivity : Activity() {
    private val server = "https://mafia-parental-server.sfyankbwry32.workers.dev"
    private var pairId = ""
    private var childSecret = ""
    private var childToken = ""
    private lateinit var info: TextView
    private lateinit var approve: Button
    private lateinit var reject: Button

    private fun post(path: String, body: JSONObject): JSONObject {
        val c = URL(server + path).openConnection() as HttpURLConnection
        c.requestMethod = "POST"; c.doOutput = true
        c.setRequestProperty("Content-Type", "application/json")
        c.outputStream.use { it.write(body.toString().toByteArray()) }
        val stream = if (c.responseCode < 400) c.inputStream else c.errorStream
        val result = stream.bufferedReader().readText()
        c.disconnect()
        return JSONObject(result)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val layout = LinearLayout(this).apply { orientation=LinearLayout.VERTICAL; setPadding(28,48,28,24) }
        layout.addView(TextView(this).apply { text="MAFIA CHILD"; textSize=26f })
        layout.addView(TextView(this).apply { text="هذا التطبيق يطلب موافقتك قبل ربط جهازك بحساب الوالد." })
        info = TextView(this).apply { text="اضغط إنشاء كود الربط"; textSize=18f }
        layout.addView(info)
        val start = Button(this).apply { text="إنشاء كود الربط"; setOnClickListener { createPair() } }
        layout.addView(start)
        approve = Button(this).apply { text="موافقة على الربط"; isEnabled=false; setOnClickListener { decide(true) } }
        reject = Button(this).apply { text="رفض الربط"; isEnabled=false; setOnClickListener { decide(false) } }
        layout.addView(approve); layout.addView(reject)
        setContentView(layout)
    }

    private fun createPair() {
        info.text="جارٍ إنشاء الكود..."
        thread {
            try {
                val b=JSONObject().put("deviceName",android.os.Build.MODEL).put("androidVersion",android.os.Build.VERSION.RELEASE)
                val r=post("/api/pair/start",b)
                pairId=r.getString("pairId"); childSecret=r.getString("childSecret")
                runOnUiThread { info.text="كود الربط: ${r.getString("code")}\nصالح لمدة 10 دقائق. أدخله في تطبيق الوالد."; }
                poll()
            } catch(e:Exception) { runOnUiThread { info.text="تعذر الاتصال بالخادم: ${e.message}" } }
        }
    }

    private fun poll() {
        thread {
            while (pairId.isNotEmpty()) {
                try {
                    val u=URL("$server/api/pair/child-status?pairId=$pairId&secret=$childSecret")
                    val c=u.openConnection() as HttpURLConnection
                    val r=JSONObject(c.inputStream.bufferedReader().readText()); c.disconnect()
                    val status=r.optString("status")
                    if(status=="approval_required") {
                        runOnUiThread { info.text="هناك طلب ربط من الوالد. هل توافق؟"; approve.isEnabled=true; reject.isEnabled=true }
                        return@thread
                    }
                    if(status=="paired") { runOnUiThread { info.text="تم الربط بموافقتك."; }; return@thread }
                    if(status=="rejected") { runOnUiThread { info.text="تم رفض طلب الربط."; }; return@thread }
                } catch (_:Exception) {}
                Thread.sleep(3000)
            }
        }
    }

    private fun decide(yes:Boolean) {
        approve.isEnabled=false; reject.isEnabled=false
        thread {
            try {
                val r=post("/api/pair/child-decision",JSONObject().put("pairId",pairId).put("childSecret",childSecret).put("approve",yes))
                runOnUiThread { info.text=if(yes) "وافقت على الربط." else "رفضت طلب الربط." }
                if(yes) sendHeartbeat()
            } catch(e:Exception) { runOnUiThread { info.text="تعذر تنفيذ القرار: ${e.message}" } }
        }
    }

    private fun sendHeartbeat() {
        // The first version reports battery only after a server-issued child token is added in a later build.
        val bm=getSystemService(Context.BATTERY_SERVICE) as BatteryManager
        val level=bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
        runOnUiThread { info.append("\nمستوى البطارية: $level%") }
    }
}
