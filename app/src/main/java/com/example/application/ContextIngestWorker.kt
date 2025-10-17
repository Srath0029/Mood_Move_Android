package com.example.application

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import androidx.core.app.ActivityCompat
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequest
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.example.application.db.AppDatabase
import com.example.application.db.LocationEntity
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import kotlinx.coroutines.tasks.await
import java.util.concurrent.TimeUnit

/**
 * ContextIngestWorker
 *
 * Periodic background worker that captures a single location sample and stores
 * it in Room. Intended to run under user consent with appropriate location
 * permissions (foreground and, on API 29+, background).
 *
 * Flow
 * 1) Validate runtime permissions.
 * 2) Request a current location (balanced accuracy).
 * 3) Insert (lat, lon) into the local database.
 *
 * Scheduling
 * - Use [enqueue] to register a unique periodic job (15-minute interval).
 * - Use [cancel] to stop the job.
 */
class ContextIngestWorker(
    private val appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    // Fused Location Provider for on-demand location fetches.
    private val fusedLocationClient = LocationServices.getFusedLocationProviderClient(appContext)

    override suspend fun doWork(): Result {
        Log.d(TAG, "Worker started...")

        // Permission gate: fail fast if the app lacks required location access.
        if (!hasLocationPerms()) {
            Log.e(TAG, "Missing foreground/background location permissions for background work.")
            return Result.failure()
        }

        if (ActivityCompat.checkSelfPermission(
                appContext,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            Log.e(TAG, "Location permission not granted. Worker cannot proceed.")
            return Result.failure()
        }

        try {
            // Fetch one location fix with balanced power accuracy.
            Log.d(TAG, "Fetching current location...")
            val location = fusedLocationClient.getCurrentLocation(
                Priority.PRIORITY_BALANCED_POWER_ACCURACY,
                CancellationTokenSource().token
            ).await()

            if (location != null) {
                Log.d(TAG, "Location found: Lat=${location.latitude}, Lon=${location.longitude}")

                // 3) Persist to Room.
                val locationEntity = LocationEntity(
                    latitude = location.latitude,
                    longitude = location.longitude
                )

                val database = AppDatabase.getDatabase(appContext)
                database.locationDao().insert(locationEntity)

                Log.d(TAG, "Worker finished successfully.")
                return Result.success()

            } else {
                Log.w(TAG, "Failed to get location (location is null).")
                return Result.retry()
            }

        } catch (e: Exception) {
            Log.e(TAG, "An error occurred in doWork", e)
            return Result.retry()
        }
    }

    /**
     * Returns true if foreground (fine or coarse) and, on API 29+, background
     * location permissions are granted.
     */
    private fun hasLocationPerms(): Boolean {
        val fine = ActivityCompat.checkSelfPermission(appContext, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val coarse = ActivityCompat.checkSelfPermission(appContext, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val bgGranted = if (android.os.Build.VERSION.SDK_INT >= 29) {
            ActivityCompat.checkSelfPermission(appContext, Manifest.permission.ACCESS_BACKGROUND_LOCATION) == PackageManager.PERMISSION_GRANTED
        } else true
        return (fine || coarse) && bgGranted
    }

    companion object {
        const val UNIQUE_NAME = "ContextFeeder"
        private const val TAG = "ContextIngestWorker"

        /**
         * Builds a periodic request with basic constraints and exponential backoff.
         * Interval is fixed at 15 minutes (WorkManager minimum).
         */
        fun buildRequest(): PeriodicWorkRequest {
            val constraints = Constraints.Builder()
                // .setRequiredNetworkType(NetworkType.CONNECTED)
                .setRequiresBatteryNotLow(true)
                .build()

            return PeriodicWorkRequestBuilder<ContextIngestWorker>(
                15, TimeUnit.MINUTES
            )
                .setConstraints(constraints)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
                .build()
        }

        /**
         * Enqueues the unique periodic job; existing work is kept if present.
         */
        fun enqueue(context: Context) {
            Log.d(TAG, "Enqueuing periodic work.")
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                UNIQUE_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                buildRequest()
            )
        }

        /**
         * Cancels the unique periodic job and any of its scheduled runs.
         */
        fun cancel(context: Context) {
            Log.d(TAG, "Cancelling unique work.")
            WorkManager.getInstance(context).cancelUniqueWork(UNIQUE_NAME)
        }
    }
}
