@file:Suppress("ALL")
package com.example.spinetrack.mqtt

import android.content.Context
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import org.eclipse.paho.client.mqttv3.*
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Manejador simple de MQTT basado en Paho Android
 * - Conexión TLS usando serverUri (ej: "ssl://host:8883")
 * - Reconexión automática via isAutomaticReconnect=true
 * - Emite mensajes recibidos en [messages]
 */
class MqttManager(
    context: Context,
    private val serverUri: String,
    clientId: String,
    private val username: String? = null,
    private val password: String? = null
) {
    private val TAG = "MqttManager"
    private val client: MqttAsyncClient = MqttAsyncClient(serverUri, clientId, MemoryPersistence())
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _messages = MutableSharedFlow<Pair<String, String>>(replay = 0)
    val messages = _messages.asSharedFlow()
    private val connected = AtomicBoolean(false)
    private var currentTopic: String? = null

    init {
        Log.i(TAG, "=== MQTT MANAGER INIT ===")
        Log.i(TAG, "ServerURI: $serverUri")
        Log.i(TAG, "ClientId: $clientId")
        Log.i(TAG, "Username: $username")
        Log.i(TAG, "=========================")
    }

    fun connectAndSubscribe(topic: String, qos: Int = 0) {
        currentTopic = topic
        Log.i(TAG, "connectAndSubscribe llamado: topic=$topic, connected=${connected.get()}")

        if (connected.get()) {
            Log.i(TAG, "Ya conectado, suscribiendo directamente")
            subscribe(topic, qos)
            return
        }

        try {
            val pwChars = password?.toCharArray()
            val opts = MqttConnectOptions().apply {
                isAutomaticReconnect = true
                isCleanSession = false  // Mantener suscripción entre reconexiones
                keepAliveInterval = 120  // Aumentar a 2 minutos para evitar timeout
                connectionTimeout = 30
                username?.let { userName = it }
                pwChars?.let { setPassword(it) }
                // Use default SSL socket factory; HiveMQ Cloud uses publicly-trusted certs
            }

            Log.i(TAG, "Creando opciones MQTT - user=$username, serverUri=$serverUri")

            // Set callback
            client.setCallback(object : MqttCallbackExtended {
                override fun connectComplete(reconnect: Boolean, serverURI: String) {
                    Log.i(TAG, "connectComplete reconnect=$reconnect serverURI=$serverURI")
                    connected.set(true)
                    currentTopic?.let { subscribe(it, qos) }
                }

                override fun messageArrived(topic: String, message: MqttMessage) {
                    val payload = try { String(message.payload, Charsets.UTF_8) } catch (_: Exception) { "" }
                    val qos = message.qos
                    val retained = message.isRetained
                    Log.i(TAG, "===========================================")
                    Log.i(TAG, "messageArrived topic=$topic qos=$qos retained=$retained")
                    Log.i(TAG, "payload=${if (payload.length>200) payload.take(200)+"..." else payload}")
                    Log.i(TAG, "===========================================")
                    scope.launch { _messages.emit(topic to payload) }
                }

                override fun connectionLost(cause: Throwable?) {
                    connected.set(false)
                    Log.w(TAG, "connectionLost: ${cause?.message}")
                }

                override fun deliveryComplete(token: IMqttDeliveryToken?) {}
            })

            Log.i(TAG, "Iniciando conexión asíncrona...")
            // Connect asynchronously
            client.connect(opts, null, object : IMqttActionListener {
                override fun onSuccess(asyncActionToken: IMqttToken?) {
                    Log.i(TAG, "MQTT conectado exitosamente")
                    connected.set(true)
                    subscribe(topic, qos)
                }

                override fun onFailure(asyncActionToken: IMqttToken?, exception: Throwable?) {
                    connected.set(false)
                    Log.e(TAG, "MQTT conexión FALLIDA: ${exception?.message}", exception)
                }
            })
        } catch (t: Throwable) {
            // Capturamos Throwable (incluye NoClassDefFoundError y otros errores fatales)
            Log.e(TAG, "Error conectando MQTT (no bloquear la app): ${t.message}", t)
            // Emitir un mensaje de error para que el ViewModel no dependa de excepciones
            scope.launch { _messages.emit("__mqtt_error__" to (t.message ?: "unknown")) }
        }
    }

    private fun subscribe(topic: String, qos: Int = 0) {
        Log.i(TAG, "subscribe llamado: topic=$topic, qos=$qos, isConnected=${client.isConnected}")
        try {
            client.subscribe(topic, qos, null, object : IMqttActionListener {
                override fun onSuccess(asyncActionToken: IMqttToken?) {
                    Log.i(TAG, "Suscrito EXITOSAMENTE a $topic")
                }
                override fun onFailure(asyncActionToken: IMqttToken?, exception: Throwable?) {
                    Log.e(TAG, "Fallo subscripción $topic: ${exception?.message}", exception)
                }
            })
        } catch (e: Exception) {
            Log.e(TAG, "Excepción en subscribe: ${e.message}", e)
        }
    }

    fun disconnect() {
        try {
            if (client.isConnected) {
                client.disconnect(null, object : IMqttActionListener {
                    override fun onSuccess(asyncActionToken: IMqttToken?) { Log.i(TAG, "MQTT desconectado") }
                    override fun onFailure(asyncActionToken: IMqttToken?, exception: Throwable?) { Log.w(TAG, "Fallo desconexión: ${exception?.message}") }
                })
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error desconectando MQTT: ${e.message}")
        } finally {
            scope.cancel()
            connected.set(false)
        }
    }
}

