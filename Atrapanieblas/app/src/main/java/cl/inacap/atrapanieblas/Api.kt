package cl.inacap.atrapanieblas

import android.os.Handler
import android.os.Looper
import org.json.JSONObject
import java.net.URL
import javax.net.ssl.HttpsURLConnection

data class Lectura(val ts: Long, val humedad: Double, val nivel: Int, val origen: String)

/** Cliente de la API REST (HTTPS) que corre en la Raspberry y guarda en MySQL (RDS). */
object Api {
    var host = ""
    private var token = ""
    var mqttUser = ""
    var mqttPass = ""

    private val main = Handler(Looper.getMainLooper())

    /** Ejecuta [trabajo] en un hilo y entrega el resultado en el hilo de UI. */
    fun <T> ejecutar(trabajo: () -> T, ok: (T) -> Unit, error: (String) -> Unit) {
        Thread {
            try {
                val r = trabajo()
                main.post { ok(r) }
            } catch (e: Exception) {
                main.post { error(e.message ?: "Error de red") }
            }
        }.start()
    }

    private fun llamar(metodo: String, ruta: String, cuerpo: JSONObject? = null): JSONObject {
        val c = URL("https://$host:${Config.API_PORT}$ruta").openConnection() as HttpsURLConnection
        c.requestMethod = metodo
        c.connectTimeout = 8000
        c.readTimeout = 8000
        c.setRequestProperty("Content-Type", "application/json")
        if (token.isNotEmpty()) c.setRequestProperty("Authorization", "Bearer $token")
        if (cuerpo != null) {
            c.doOutput = true
            c.outputStream.use { it.write(cuerpo.toString().toByteArray()) }
        }
        val code = c.responseCode
        val txt = (if (code < 400) c.inputStream else c.errorStream)?.bufferedReader()?.use { it.readText() } ?: "{}"
        val json = try { JSONObject(txt) } catch (e: Exception) { JSONObject() }
        if (code >= 400) throw Exception(json.optString("error", "Error $code"))
        return json
    }

    fun registrar(u: String, p: String) {
        llamar("POST", "/api/registro", JSONObject().put("usuario", u).put("password", p))
    }

    fun login(u: String, p: String) {
        val j = llamar("POST", "/api/login", JSONObject().put("usuario", u).put("password", p))
        token = j.getString("token")
        mqttUser = j.getString("mqtt_user")   // credenciales del broker: llegan tras autenticarse, no van en el APK
        mqttPass = j.getString("mqtt_pass")
    }

    fun historial(): Pair<List<Lectura>, Double> {
        val j = llamar("GET", "/api/lecturas?limit=100")
        val arr = j.getJSONArray("lecturas")
        val lista = (0 until arr.length()).map {
            val o = arr.getJSONObject(it)
            Lectura(o.getLong("ts"), o.getDouble("humedad"), o.getInt("nivel"), o.getString("origen"))
        }
        return lista to j.optDouble("promedio_humedad", 0.0)
    }

    fun cerrarSesion() { token = ""; mqttUser = ""; mqttPass = "" }
}
