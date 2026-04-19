package com.example.spinetrack

import android.app.Application
import android.util.Log

class SpineTrackApp : Application() {
    override fun onCreate() {
        super.onCreate()
        // Evitar que ciertas excepciones en hilos secundarios (p.ej. de la librería Paho)
        // provoquen el cierre de la aplicación. Capturamos el handler por defecto y
        // filtramos SecurityException relacionadas con registro de receivers (Android 14+).
        val previousHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { t, e ->
            try {
                if (e is SecurityException && e.message?.contains("RECEIVER_EXPORTED") == true) {
                    Log.w("SpineTrackApp", "Ignorado SecurityException en hilo ${t.name}: ${e.message}")
                    // No delegar al handler anterior para evitar crash
                    return@setDefaultUncaughtExceptionHandler
                }
            } catch (inner: Throwable) {
                // Ignorar
            }
            // Delegar al handler anterior para no ocultar otros errores
            previousHandler?.uncaughtException(t, e)
        }
    }
}