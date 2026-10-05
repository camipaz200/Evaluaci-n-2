package cl.inacap.atrapanieblas

/** Parámetros del sistema. La IP de la Raspberry se ingresa en el login (cambia según la red WiFi). */
object Config {
    const val API_PORT = 5000                 // API REST (HTTPS) en la Raspberry -> MySQL RDS
    const val MQTT_PORT_TLS = 8883            // Mosquitto con TLS en la Raspberry

    private const val BASE = "atrapanieblas/meneses-quintoman"
    const val TOPIC_DATOS = "$BASE/captador/datos"   // Nodo A -> {"humedad":85.2,"nivel":64}
    const val TOPIC_ESTADO = "$BASE/riego/estado"    // Nodo B -> {"riego":"ON"}
    const val TOPIC_CMD = "$BASE/riego/cmd"          // App -> "ON" / "OFF"

    const val HUMEDAD_MAX = 90.0
    const val NIVEL_MIN = 20

    fun debeRegar(humedad: Double, nivel: Int, hayLuz: Boolean) =
        humedad < HUMEDAD_MAX && nivel >= NIVEL_MIN && hayLuz
}
