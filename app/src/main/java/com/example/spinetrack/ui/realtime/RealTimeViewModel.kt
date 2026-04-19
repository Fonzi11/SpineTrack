package com.example.spinetrack.ui.realtime

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.spinetrack.data.model.RealtimePostura
import com.example.spinetrack.data.preferences.UserPreferences
import com.example.spinetrack.mqtt.MqttManager
import android.util.Log
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.google.firebase.auth.FirebaseAuth
import kotlin.math.min
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

sealed class RealtimeUiState {
    object Conectando : RealtimeUiState()
    object SinDispositivo : RealtimeUiState()
    data class Activo(val datos: RealtimePostura) : RealtimeUiState()
    data class Error(val mensaje: String) : RealtimeUiState()
}

class RealtimeViewModel(application: Application) : AndroidViewModel(application) {

    private val userPreferences = UserPreferences(application)

    private val _uiState = MutableStateFlow<RealtimeUiState>(RealtimeUiState.Conectando)
    val uiState: StateFlow<RealtimeUiState> = _uiState
    // Último payload JSON recibido (para debug en UI)
    private val _rawJson = MutableStateFlow("")
    val rawJson: StateFlow<String> = _rawJson

    // --- MQTT configuration (lee credenciales desde BuildConfig) ---
    private val MQTT_HOST = "58adacd73e3b45f8872bfbf3fb9bc432.s1.eu.hivemq.cloud"
    private val MQTT_PORT = 8883
    private val MQTT_USER = com.example.spinetrack.BuildConfig.MQTT_USER
    private val MQTT_PASS = com.example.spinetrack.BuildConfig.MQTT_PASS

    private val gson = Gson()
    private var mqttJob: Job? = null
    private var lastPublishedSessionId: String? = null
    private var lastSampleMs: Long? = null
    private var accumBuenaSec: Double = 0.0
    private var accumMalaSec: Double = 0.0
    private var lastAlertCount: Int = 0
    private var activeSessionId: String? = null
    private val mqttManager by lazy {
        // ClientId único para esta app - no debe colisionar con el dispositivo
        val clientId = "spinetrack_app_" + java.util.UUID.randomUUID().toString().replace("-", "").substring(0, 12)
        val serverUri = "ssl://$MQTT_HOST:$MQTT_PORT"
        Log.i("RealtimeViewModel", "Creando MqttManager con clientId: $clientId")
        MqttManager(getApplication(), serverUri, clientId, MQTT_USER, MQTT_PASS)
    }

    init {
        iniciarEscucha()
    }

    private suspend fun resolveUid(): String? {
        val firebaseUid = FirebaseAuth.getInstance().currentUser?.uid
        val storedUid = userPreferences.userIdFlow.first()
        if (!firebaseUid.isNullOrBlank()) {
            if (storedUid != firebaseUid) {
                userPreferences.syncUserId(firebaseUid)
            }
            return firebaseUid
        }
        // En vivo requiere usuario autenticado para tópico correcto en HiveMQ.
        return null
    }

    fun iniciarEscucha() {
        viewModelScope.launch {
            _uiState.value = RealtimeUiState.Conectando
            val uid = resolveUid()
            Log.i("RealtimeViewModel", "UserPreferences userId: ${uid ?: "<null>"}")

            if (uid.isNullOrBlank()) {
                _uiState.value = RealtimeUiState.Error("Usuario no autenticado. Inicia sesion para ver datos en vivo.")
                return@launch
            }

            // En vivo solo desde HiveMQ en el topic del usuario autenticado.
            val topic = "spinetrack/$uid/realtime"

            Log.i("RealtimeViewModel", "=== MQTT CONFIG ===")
            Log.i("RealtimeViewModel", "Topic: $topic")
            Log.i("RealtimeViewModel", "Host: $MQTT_HOST:$MQTT_PORT")
            Log.i("RealtimeViewModel", "User: $MQTT_USER")
            Log.i("RealtimeViewModel", "UID: $uid")
            Log.i("RealtimeViewModel", "===================")

            try {
                Log.i("RealtimeViewModel", "Llamando a mqttManager.connectAndSubscribe($topic)")
                mqttManager.connectAndSubscribe(topic)

                // Cancel previous job if existe
                mqttJob?.cancel()
                mqttJob = viewModelScope.launch {
                    Log.i("RealtimeViewModel", "Iniciando colección de mensajes MQTT...")
                    mqttManager.messages.collect { (tpc, payload) ->
                        Log.i("RealtimeViewModel", "MENSAJE MQTT RECIBIDO - topic=$tpc, payload=$payload")
                        // mantener también el payload crudo para debugging en la UI
                        _rawJson.value = payload
                        // Manejar errores emitidos por MqttManager
                        if (tpc == "__mqtt_error__") {
                            Log.w("RealtimeViewModel", "MQTT error recibido: $payload")
                            _uiState.value = RealtimeUiState.Error("Error MQTT: $payload")
                            return@collect
                        }
                        try {
                            val type = object : TypeToken<Map<String, Any>>() {}.type
                            val map: Map<String, Any>? = try {
                                gson.fromJson<Map<String, Any>>(payload, type)
                            } catch (e: Exception) {
                                Log.e("RealtimeViewModel", "Error parseando JSON: ${e.message}")
                                null
                            }
                            if (map != null) {
                                var datos = RealtimePostura.fromMap(map)
                                                // Guardar último session_id en preferencias para que estadísticas lo importe
                                                datos.sessionId.takeIf { it.isNotBlank() }?.let { sid ->
                                                    if (sid != lastPublishedSessionId) {
                                                        lastPublishedSessionId = sid
                                                        // publicar cambio de sesión una sola vez para auto-import.
                                                        viewModelScope.launch { userPreferences.saveLastSessionId(sid) }
                                                    }
                                                }
                                datos = enrichWithSessionMath(datos)
                                Log.i("RealtimeViewModel", "MQTT payload parsed: uid=${datos.uid} icp=${datos.icpParcial} theta=${datos.thetaAbs}")
                                if (datos.uid.isEmpty()) {
                                    _uiState.value = RealtimeUiState.SinDispositivo
                                } else {
                                    _uiState.value = RealtimeUiState.Activo(datos)
                                }
                            } else {
                                Log.w("RealtimeViewModel", "Mapa nulo después de parsear JSON")
                            }
                        } catch (e: Exception) {
                            Log.e("RealtimeViewModel", "Excepción procesando payload: ${e.message}")
                        }
                    }
                }

            } catch (e: Exception) {
                Log.e("RealtimeViewModel", "Excepción en iniciarEscucha: ${e.message}")
                _uiState.value = RealtimeUiState.Error("Error: ${e.message}")
            }
        }

    }

    override fun onCleared() {
        super.onCleared()
        try {
            mqttJob?.cancel()
            mqttManager.disconnect()
        } catch (_: Exception) {}
    }

    private fun enrichWithSessionMath(input: RealtimePostura): RealtimePostura {
        val nowMs = System.currentTimeMillis()

        // Reinicia acumuladores al detectar nueva sesión.
        if (input.sessionId.isNotBlank() && input.sessionId != activeSessionId) {
            activeSessionId = input.sessionId
            lastSampleMs = null
            accumBuenaSec = 0.0
            accumMalaSec = 0.0
            lastAlertCount = 0
        }

        val prevMs = lastSampleMs
        var dtSec = 0.0
        if (prevMs != null) {
            dtSec = ((nowMs - prevMs).coerceAtLeast(0L)).toDouble() / 1000.0
            dtSec = min(dtSec, 5.0)
        }
        lastSampleMs = nowMs

        if (input.buenaPostura) accumBuenaSec += dtSec else accumMalaSec += dtSec

        // Si no llega contador acumulado desde broker, estimarlo localmente.
        val alertas = when {
            input.numAlertas > 0 -> {
                lastAlertCount = input.numAlertas
                input.numAlertas
            }
            else -> {
                if (!input.buenaPostura && dtSec > 0.0) {
                    // Incrementa al inicio de un bloque malo si no hay contador remoto.
                    if (lastAlertCount == 0 || accumMalaSec - dtSec <= 0.0) {
                        lastAlertCount += 1
                    }
                }
                lastAlertCount
            }
        }

        val icp = if (input.icpParcial in 0.0..100.0 && input.icpParcial != 100.0) {
            input.icpParcial
        } else {
            // Fallback con theta_abs cuando el payload no trae ICP útil.
            val normalized = (1.0 - (input.thetaAbs / 30.0).coerceIn(0.0, 1.0)) * 100.0
            normalized.coerceIn(0.0, 100.0)
        }

        val clase = if (input.claseIcp.isNotBlank()) input.claseIcp else when {
            icp >= 90.0 -> "Excelente"
            icp >= 75.0 -> "Bueno"
            icp >= 60.0 -> "Regular"
            icp >= 40.0 -> "Malo"
            else -> "Critico"
        }

        return input.copy(
            icpParcial = icp,
            claseIcp = clase,
            tBuenaMin = accumBuenaSec / 60.0,
            tMalaMin = accumMalaSec / 60.0,
            numAlertas = alertas
        )
    }
}