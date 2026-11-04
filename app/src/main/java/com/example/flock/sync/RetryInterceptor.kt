package com.example.flock.sync

import okhttp3.Interceptor
import okhttp3.Request
import okhttp3.Response
import java.io.IOException
import java.io.InterruptedIOException

/**
 * Tries a Google request again when Google is busy or has a passing fault, instead of leaving it for the next load (v55):
 * "too many requests" (429) and "unavailable" (503) after 1 s, 2 s and 4 s — or as long as Google asks (Retry-After, at
 * most 20 s); a server error (500, 502, 504) or a timed-out connection the same, but only for a request that can safely
 * run twice (a read, a write of cell values, a clear): adding rows, creating or copying a file, or changing the file's
 * tabs might already have gone through, and a second one would do it twice.
 *
 * No network at all (no address, no connection) is not retried: the save is kept on the phone and sent in the background
 * once the network is back ([SyncWork]), so nobody waits on a spinner.
 */
class RetryInterceptor(
    private val delaysMs: List<Long> = listOf(1_000, 2_000, 4_000),
    private val sleep: (Long) -> Unit = { Thread.sleep(it) }
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val req = chain.request()
        val again = canRepeat(req)
        var attempt = 0
        while (true) {
            var res: Response? = null
            var err: IOException? = null
            try { res = chain.proceed(req) } catch (e: IOException) { err = e }
            val retry = attempt < delaysMs.size && !chain.call().isCanceled() && when {
                res != null -> retryCode(res.code, again)
                else -> again && err is InterruptedIOException      // a timeout (SocketTimeoutException is one)
            }
            if (!retry) return res ?: throw err!!
            val wait = res?.header("Retry-After")?.trim()?.toLongOrNull()?.times(1000)?.coerceIn(0, 20_000) ?: delaysMs[attempt]
            res?.close()
            sleep(wait)
            attempt++
        }
    }

    companion object {
        /** Google busy (429, 503): nothing was done, always safe to ask again; other server faults only for repeatable requests. */
        fun retryCode(code: Int, repeatable: Boolean): Boolean = code == 429 || code == 503 || (repeatable && code in setOf(500, 502, 504))

        /** A request that does the same thing however often it runs: reads, cell writes and clears, renames. */
        fun canRepeat(r: Request): Boolean {
            val path = r.url.encodedPath
            return when (r.method) {
                "GET", "PUT", "PATCH", "DELETE" -> true
                "POST" -> path.endsWith("/values:batchUpdate") || path.endsWith("/values:batchClear") || path.endsWith("/values:batchGet")
                else -> false
            }
        }
    }
}
