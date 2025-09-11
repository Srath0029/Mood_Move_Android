package com.example.application

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequest
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import kotlinx.coroutines.delay
import java.util.concurrent.TimeUnit

/**
 * Simulates reading a slice of CSV sensor data (steps/HR) and writing it to storage.
 * Replace the TODOs with your Room/Retrofit code when ready.
 */
class ContextIngestWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        // TODO: read next CSV rows (steps, heart-rate) and insert into Room
        // TODO: optionally fetch weather via Retrofit and store for today

        // Simulate I/O work for the demo
        delay(300)
        return Result.success()
    }

    companion object {
        const val UNIQUE_NAME = "ContextFeeder"

        fun buildRequest(): PeriodicWorkRequest {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED) // needed if weather fetch is enabled
                .setRequiresBatteryNotLow(true)
                .build()

            return PeriodicWorkRequestBuilder<ContextIngestWorker>(
                15, TimeUnit.MINUTES // minimum allowed interval
            )
                .setConstraints(constraints)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 10, TimeUnit.SECONDS)
                .build()
        }

        fun enqueue(context: Context) {
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                UNIQUE_NAME,
                ExistingPeriodicWorkPolicy.UPDATE,
                buildRequest()
            )
        }

        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(UNIQUE_NAME)
        }
    }
}
