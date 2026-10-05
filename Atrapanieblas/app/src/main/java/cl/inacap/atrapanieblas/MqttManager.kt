package cl.inacap.atrapanieblas

import com.hivemq.client.mqtt.MqttClient
import com.hivemq.client.mqtt.datatypes.MqttQos
import com.hivemq.client.mqtt.mqtt3.Mqtt3AsyncClient
import java.util.UUID

/** Cliente MQTT sobre TLS (puerto 8883). Los callbacks llegan en hilos de red: la UI debe usar runOnUiThread. */
class MqttManager(
    private val onMensaje: (topic: String, payload: String) -> Unit,
    private val onEstado: (mensaje: String, conectado: Boolean) -> Unit
) {
    private var client: Mqtt3AsyncClient? = null

    fun conectar(host: String, usuario: String? = null, clave: String? = null) {
        if (client?.state?.isConnected == true) return
        val c = MqttClient.builder()
            .useMqttVersion3()
            .identifier("atrapanieblas-" + UUID.randomUUID().toString().take(8))
            .serverHost(host)
            .serverPort(Config.MQTT_PORT_TLS)
            .sslWithDefaultConfig()           // TLS: valida contra la CA de res/raw/ca.crt (network_security_config)
            .buildAsync()
        client = c

        onEstado("Conectando…", false)
        val req = c.connectWith().keepAlive(30).cleanSession(true)
        if (!usuario.isNullOrEmpty() && clave != null) {
            req.simpleAuth().username(usuario).password(clave.toByteArray()).applySimpleAuth()
        }
        req.send().whenComplete { _, error ->
            if (error != null) {
                onEstado("Error de conexión: ${error.message}", false)
            } else {
                onEstado("Conectado (TLS)", true)
                suscribir(Config.TOPIC_DATOS)
                suscribir(Config.TOPIC_ESTADO)
            }
        }
    }

    private fun suscribir(topic: String) {
        client?.subscribeWith()
            ?.topicFilter(topic)
            ?.qos(MqttQos.AT_LEAST_ONCE)
            ?.callback { pub -> onMensaje(pub.topic.toString(), String(pub.payloadAsBytes)) }
            ?.send()
    }

    fun publicar(topic: String, mensaje: String) {
        if (client?.state?.isConnected != true) {
            onEstado("Sin conexión: no se pudo publicar", false); return
        }
        client?.publishWith()?.topic(topic)?.qos(MqttQos.AT_LEAST_ONCE)
            ?.payload(mensaje.toByteArray())?.send()
    }

    fun desconectar() {
        client?.disconnect()
        client = null
    }
}
