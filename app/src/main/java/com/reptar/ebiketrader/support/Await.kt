package com.reptar.ebiketrader.support

import com.google.android.gms.tasks.Task
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** Suspends until a Play Services [Task] finishes.
 *
 * Hand-rolled rather than pulling in kotlinx-coroutines-play-services for the
 * one function we need from it. Twelve lines against a dependency whose version
 * would be one more thing to keep in step with the Kotlin release.
 *
 * The cast is safe for Task<Void>, whose result is legitimately null. */
@Suppress("UNCHECKED_CAST")
suspend fun <T> Task<T>.await(): T = suspendCancellableCoroutine { cont ->
    addOnCompleteListener { task ->
        val error = task.exception
        when {
            error != null -> cont.resumeWithException(error)
            task.isCanceled -> cont.cancel()
            else -> cont.resume(task.result as T)
        }
    }
}
