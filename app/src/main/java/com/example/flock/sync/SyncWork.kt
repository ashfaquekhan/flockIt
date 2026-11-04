package com.example.flock.sync

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.example.flock.data.FlockDatabase
import com.example.flock.data.FlockRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import java.util.concurrent.TimeUnit

/**
 * Sends the day rows saved on this phone that have not reached the sheet (no network, Google refused) as soon as the
 * phone is online again — also with FlockIt closed (v55). Android runs it when there is a network; it tries again later
 * (30 s, then longer) while something is still unsent, and gives up after a few tries (the next save or load tries again).
 * It never shows anything: a message from the background floats over other apps (v51).
 */
object SyncWork {
    private const val NAME = "flockit_send_unsent"

    /** Has the unsent rows sent once the phone is online (one job at a time; a second call while one waits changes nothing). */
    fun schedule(ctx: Context) {
        try {
            val job = OneTimeWorkRequestBuilder<SendWorker>()
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
                .build()
            WorkManager.getInstance(ctx.applicationContext).enqueueUniqueWork(NAME, ExistingWorkPolicy.KEEP, job)
        } catch (e: Exception) {
            // WorkManager not set up (tests): the next load sends them as before
        }
    }
}

class SendWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result {
        val app = applicationContext
        val auth = GoogleAuthManager(app)
        val state = auth.authState.value
        if (!state.isSignedIn || state.isDemoMode) return Result.success()
        val db = FlockDatabase.getDatabase(app, CoroutineScope(SupervisorJob() + Dispatchers.IO))
        val repo = FlockRepository(db).also { it.sync = SheetsSyncManager(auth, db) }
        val left = try { repo.sendUnsent() } catch (e: Exception) { -1 }
        return when {
            left == 0 -> Result.success()
            runAttemptCount >= 5 -> Result.failure()
            else -> Result.retry()
        }
    }
}
