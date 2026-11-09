package org.openturnly.mobile.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import org.openturnly.mobile.api.ApiException
import org.openturnly.mobile.localization.Copy

/**
 * Something a screen loads from the server: the value once it arrives, the
 * first load (skeletons) apart from a refresh (the pull indicator), and the
 * failure in plain words.
 */
class Loader<T>(private val scope: CoroutineScope, private val fetch: suspend () -> T) {
    var value by mutableStateOf<T?>(null)
    var error by mutableStateOf<String?>(null); private set
    var loading by mutableStateOf(true); private set
    var refreshing by mutableStateOf(false); private set
    private var job: Job? = null

    fun load(refresh: Boolean = false) {
        job?.cancel()
        if (refresh && value != null) refreshing = true else loading = value == null
        job = scope.launch {
            try {
                value = fetch()
                error = null
            } catch (failure: CancellationException) {
                throw failure
            } catch (failure: ApiException) {
                error = failure.message
            } catch (failure: Exception) {
                error = Copy.errors.generic
            } finally {
                loading = false
                refreshing = false
            }
        }
    }
}

@Composable
fun <T> rememberLoader(vararg keys: Any?, fetch: suspend () -> T): Loader<T> {
    val scope = rememberCoroutineScope()
    val loader = remember(*keys) { Loader(scope, fetch) }
    LaunchedEffect(loader) { loader.load() }
    return loader
}

/** Runs one action (a save, a cancel) with its busy flag, and the server's words when it fails. */
class Action(private val scope: CoroutineScope, private val onError: (ApiException) -> Unit) {
    var busy by mutableStateOf(false); private set

    fun run(block: suspend () -> Unit) {
        if (busy) return
        busy = true
        scope.launch {
            try {
                block()
            } catch (failure: CancellationException) {
                throw failure
            } catch (failure: ApiException) {
                onError(failure)
            } catch (failure: Exception) {
                onError(ApiException(0, "unknown_error"))
            } finally {
                busy = false
            }
        }
    }
}

@Composable
fun rememberAction(onError: (ApiException) -> Unit): Action {
    val scope = rememberCoroutineScope()
    return remember { Action(scope, onError) }
}
