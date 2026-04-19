package com.example.spinetrack.util

import com.google.android.gms.tasks.Task
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Extensión para convertir una Task de Firebase en una suspending function.
 * Permite usar await() en lugar de addOnSuccessListener/addOnFailureListener.
 */
suspend fun <T> Task<T>.await(): T {
    return suspendCancellableCoroutine { continuation ->
        addOnSuccessListener { result ->
            if (result != null) {
                continuation.resume(result)
            } else {
                continuation.resumeWithException(Exception("Resultado null inesperado"))
            }
        }
        addOnFailureListener { exception ->
            continuation.resumeWithException(exception)
        }
    }
}

/**
 * Extensión para Tasks que no retornan valor (Void).
 */
suspend fun Task<Void>.awaitVoid() {
    return suspendCancellableCoroutine { continuation ->
        addOnSuccessListener {
            continuation.resume(Unit)
        }
        addOnFailureListener { exception ->
            continuation.resumeWithException(exception)
        }
    }
}
