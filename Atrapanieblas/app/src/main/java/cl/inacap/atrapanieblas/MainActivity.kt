package cl.inacap.atrapanieblas

import android.content.Intent
import android.os.Bundle
import android.widget.SeekBar
import androidx.appcompat.app.AppCompatActivity
import cl.inacap.atrapanieblas.databinding.ActivityMainBinding
import org.json.JSONObject

class MainActivity : AppCompatActivity() {
    private lateinit var b: ActivityMainBinding
    private lateinit var mqtt: MqttManager
    private var ultimoRiego = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivityMainBinding.inflate(layoutInflater)
        setContentView(b.root)
        b.tvUsuario.text = " ${intent.getStringExtra("usuario") ?: ""}"

        mqtt = MqttManager(
            onMensaje = { topic, payload -> runOnUiThread { recibir(topic, payload) } },
            onEstado = { msg, ok -> runOnUiThread {
                b.tvConexion.text = (if (ok) "🟢 " else "🔴 ") + msg
            } }
        )

        b.btnOn.setOnClickListener { mqtt.publicar(Config.TOPIC_CMD, "ON") }
        b.btnOff.setOnClickListener { mqtt.publicar(Config.TOPIC_CMD, "OFF") }

        b.sbHum.setOnSeekBarChangeListener(listener { b.tvSimHum.text = "Humedad: $it %" })
        b.sbNivel.setOnSeekBarChangeListener(listener { b.tvSimNivel.text = "Nivel: $it %" })
        b.btnPublicar.setOnClickListener {
            val json = JSONObject().put("humedad", b.sbHum.progress).put("nivel", b.sbNivel.progress)
            mqtt.publicar(Config.TOPIC_DATOS, json.toString())
        }

        b.btnHistorial.setOnClickListener { startActivity(Intent(this, HistorialActivity::class.java)) }
        b.btnSalir.setOnClickListener {
            Api.cerrarSesion(); startActivity(Intent(this, LoginActivity::class.java)); finish()
        }
    }

    override fun onStart() { super.onStart(); mqtt.conectar(Api.host, Api.mqttUser, Api.mqttPass) }
    override fun onStop() { super.onStop(); mqtt.desconectar() }

    private fun recibir(topic: String, payload: String) {
        try {
            when (topic) {
                Config.TOPIC_DATOS -> {
                    val j = JSONObject(payload)
                    val hum = j.getDouble("humedad")
                    val nivel = j.getInt("nivel")
                    b.tvHumedad.text = "Humedad: $hum %"
                    b.tvNivel.text = "Nivel del estanque: $nivel %"
                    b.pbNivel.progress = nivel
                    if (b.swAuto.isChecked) decidirRiego(hum, nivel)
                }
                Config.TOPIC_ESTADO -> {
                    val estado = if (payload.trim().startsWith("{"))
                        JSONObject(payload).optString("riego", "?") else payload.trim()
                    b.tvRiego.text = "Riego: " + if (estado.equals("ON", true)) "💦 ACTIVO" else "⏹ DETENIDO"
                }
            }
        } catch (e: Exception) {
            b.tvConexion.text = "🟠 Mensaje inválido ignorado"
        }
    }

    /** Misma regla del Nodo B: humedad < 90 %, estanque con agua y luz suficiente. */
    private fun decidirRiego(hum: Double, nivel: Int) {
        val orden = if (Config.debeRegar(hum, nivel, b.cbLuz.isChecked)) "ON" else "OFF"
        if (orden != ultimoRiego) {
            ultimoRiego = orden
            mqtt.publicar(Config.TOPIC_CMD, orden)
        }
    }

    private fun listener(f: (Int) -> Unit) = object : SeekBar.OnSeekBarChangeListener {
        override fun onProgressChanged(s: SeekBar?, p: Int, fromUser: Boolean) = f(p)
        override fun onStartTrackingTouch(s: SeekBar?) {}
        override fun onStopTrackingTouch(s: SeekBar?) {}
    }
}
