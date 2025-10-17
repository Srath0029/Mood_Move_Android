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

class ContextIngestWorker(
    private val appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    // Get the client of location service
    private val fusedLocationClient = LocationServices.getFusedLocationProviderClient(appContext)

    override suspend fun doWork(): Result {
        Log.d(TAG, "Worker started...")

        // 1. Check location permission

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
            // 2. Get the current location
            Log.d(TAG, "Fetching current location...")
            val location = fusedLocationClient.getCurrentLocation(
                Priority.PRIORITY_BALANCED_POWER_ACCURACY,
                CancellationTokenSource().token
            ).await()

            if (location != null) {
                Log.d(TAG, "Location found: Lat=${location.latitude}, Lon=${location.longitude}")

                // 3. Create data entity
                val locationEntity = LocationEntity(
                    latitude = location.latitude,
                    longitude = location.longitude
                )

                // 4. Store data in the Room database
                Log.d(TAG, "Inserting location into database...")
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

        fun enqueue(context: Context) {
            Log.d(TAG, "Enqueuing periodic work.")
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                UNIQUE_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                buildRequest()
            )
        }

        fun cancel(context: Context) {
            Log.d(TAG, "Cancelling unique work.")
            WorkManager.getInstance(context).cancelUniqueWork(UNIQUE_NAME)
        }
    }
}