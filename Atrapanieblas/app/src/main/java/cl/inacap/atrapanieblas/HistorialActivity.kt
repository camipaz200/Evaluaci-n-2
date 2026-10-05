package cl.inacap.atrapanieblas

import android.os.Bundle
import android.widget.ArrayAdapter
import androidx.appcompat.app.AppCompatActivity
import cl.inacap.atrapanieblas.databinding.ActivityHistorialBinding
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class HistorialActivity : AppCompatActivity() {
    private lateinit var b: ActivityHistorialBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivityHistorialBinding.inflate(layoutInflater)
        setContentView(b.root)
        b.btnBorrar.setOnClickListener { cargar() }
        cargar()
    }

    /** Consulta el historial guardado en MySQL (RDS) a través de la API de la Raspberry. */
    private fun cargar() {
        b.tvResumen.text = "Cargando…"
        Api.ejecutar({ Api.historial() },
            ok = { (datos, prom) ->
                val fmt = SimpleDateFormat("dd/MM HH:mm:ss", Locale.getDefault())
                b.lvLecturas.adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, datos.map {
                    "${fmt.format(Date(it.ts))}  ·  Hum ${it.humedad}%  ·  Nivel ${it.nivel}%"
                })
                b.tvResumen.text = if (datos.isEmpty()) "Sin lecturas aún"
                else "${datos.size} lecturas · humedad promedio %.1f %%".format(prom)
            },
            error = { b.tvResumen.text = "Error: $it" })
    }
}
