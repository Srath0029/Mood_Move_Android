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
    private val appContext: Context, // 将 appContext 设为属性以便复用
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    // 获取位置服务的客户端
    private val fusedLocationClient = LocationServices.getFusedLocationProviderClient(appContext)

    override suspend fun doWork(): Result {
        Log.d(TAG, "Worker started...")

        // 1. 检查位置权限
        // Worker 不能请求权限，它只能检查权限是否已被授予
        if (!hasLocationPerms()) {
            Log.e(TAG, "Missing foreground/background location permissions for background work.")
            // 这里返回 failure 而不是 retry，避免无限重试；让 UI 引导用户去授权
            return Result.failure()
        }

        if (ActivityCompat.checkSelfPermission(
                appContext,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            Log.e(TAG, "Location permission not granted. Worker cannot proceed.")
            // 可以返回 failure()，这样 WorkManager 可以根据重试策略决定是否重试
            return Result.failure()
        }

        try {
            // 2. 获取当前位置
            // 我们使用 getCurrentLocation 来获取一个最新的位置信息
            Log.d(TAG, "Fetching current location...")
            val location = fusedLocationClient.getCurrentLocation(
                Priority.PRIORITY_BALANCED_POWER_ACCURACY, // 平衡功耗与精度
                CancellationTokenSource().token
            ).await() // 使用 await 将 Task 转换为 suspend 函数

            if (location != null) {
                Log.d(TAG, "Location found: Lat=${location.latitude}, Lon=${location.longitude}")

                // 3. 创建数据实体
                val locationEntity = LocationEntity(
                    latitude = location.latitude,
                    longitude = location.longitude
                )

                // 4. 将数据存入 Room 数据库
                Log.d(TAG, "Inserting location into database...")
                val database = AppDatabase.getDatabase(appContext)
                database.locationDao().insert(locationEntity)

                Log.d(TAG, "Worker finished successfully.")
                return Result.success()

            } else {
                Log.w(TAG, "Failed to get location (location is null).")
                // 位置获取失败，但可能不是永久性错误，可以稍后重试
                return Result.retry()
            }

        } catch (e: Exception) {
            Log.e(TAG, "An error occurred in doWork", e)
            // 发生任何异常都返回 retry，让 WorkManager 稍后重试
            return Result.retry()
        }
    }

    private fun hasLocationPerms(): Boolean {
        val fine = ActivityCompat.checkSelfPermission(appContext, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val coarse = ActivityCompat.checkSelfPermission(appContext, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val bgGranted = if (android.os.Build.VERSION.SDK_INT >= 29) {
            ActivityCompat.checkSelfPermission(appContext, Manifest.permission.ACCESS_BACKGROUND_LOCATION) == PackageManager.PERMISSION_GRANTED
        } else true
        // 前台（fine / coarse 任一）+ 后台（Q+）
        return (fine || coarse) && bgGranted
    }

    companion object {
        const val UNIQUE_NAME = "ContextFeeder"
        private const val TAG = "ContextIngestWorker" // 为日志添加一个 TAG

        fun buildRequest(): PeriodicWorkRequest {
            val constraints = Constraints.Builder()
                // 获取位置不需要网络，除非你还想同时获取天气
                // .setRequiredNetworkType(NetworkType.CONNECTED)
                .setRequiresBatteryNotLow(true)
                .build()

            return PeriodicWorkRequestBuilder<ContextIngestWorker>(
                15, TimeUnit.MINUTES // 最小允许间隔
            )
                .setConstraints(constraints)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS) // 增加退避时间
                .build()
        }

        fun enqueue(context: Context) {
            Log.d(TAG, "Enqueuing periodic work.")
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                UNIQUE_NAME,
                ExistingPeriodicWorkPolicy.KEEP, // 如果任务已存在，则保持不变，不更新
                buildRequest()
            )
        }

        fun cancel(context: Context) {
            Log.d(TAG, "Cancelling unique work.")
            WorkManager.getInstance(context).cancelUniqueWork(UNIQUE_NAME)
        }
    }
}