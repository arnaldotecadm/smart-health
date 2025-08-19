package com.yourname.smarthealth

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContract
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.material3.Button
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.PermissionController
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import androidx.lifecycle.lifecycleScope
import com.samsung.android.sdk.health.data.HealthDataService
import com.samsung.android.sdk.health.data.HealthDataStore
import com.samsung.android.sdk.health.data.error.HealthDataException
import com.samsung.android.sdk.health.data.error.ResolvablePlatformException
import com.samsung.android.sdk.health.data.permission.AccessType
import com.samsung.android.sdk.health.data.permission.Permission
import com.samsung.android.sdk.health.data.request.DataTypes
import com.samsung.android.sdk.health.data.request.LocalTimeFilter
import com.samsung.android.sdk.health.data.request.Ordering
import com.yourname.smarthealth.data.HealthConnectManager
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit
import com.samsung.android.sdk.health.data.*
import com.samsung.android.sdk.health.data.request.DataType
import com.samsung.android.sdk.health.data.request.LocalTimeGroup
import com.samsung.android.sdk.health.data.request.LocalTimeGroupUnit


class MainActivity() : AppCompatActivity() {

    private lateinit var healthConnectClient: HealthConnectClient

    private lateinit var mStore: HealthDataStore
    private lateinit var mHelper: HealthDataService

    private val permissions = setOf(
        HealthPermission.getReadPermission(StepsRecord::class)
    )

    private val requestPermissions =
        registerForActivityResult(
            PermissionController.createRequestPermissionResultContract()
        ) { granted: Set<String> ->
            if (granted.containsAll(permissions)) {
                // ✅ All permissions granted — safe to read data
                readStepsHistory(this)
            } else {
                // ❌ User denied permissionsToast.makeText(this, "Permissions not granted!", Toast.LENGTH_SHORT).show()
                Toast.makeText(this, "Permissions not granted!", Toast.LENGTH_SHORT).show()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        healthConnectClient = HealthConnectClient.getOrCreate(this)

        // Launch coroutine to request permissions and read data
        lifecycleScope.launch {
            val granted = hasAllPermissions(permissions)
            if (!granted) {
                requestPermissions.launch(permissions)
            } else {
                //readStepsHistory()
            }
        }
        val readStepsButton: Button = findViewById(R.id.readStepsButton)
        readStepsButton.setOnClickListener {
            readStepCount()
        }

        val readExerciseButton: Button = findViewById(R.id.readExerciseButton)
        readExerciseButton.setOnClickListener {
            readExercises()
        }
        lifecycleScope.launch {
            checkForPermissions(this@MainActivity)
        }

    }

    suspend fun hasAllPermissions(permissions: Set<String>): Boolean {
        return healthConnectClient.permissionController.getGrantedPermissions()
            .containsAll(permissions)
    }

    fun requestPermissionsActivityContract(): ActivityResultContract<Set<String>, Set<String>> {
        return PermissionController.createRequestPermissionResultContract()
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == PERMISSION_REQUEST_CODE) {
            // After returning from Health Connect permission screen
            lifecycleScope.launch {
                val granted = healthConnectClient.permissionController.getGrantedPermissions()
                if (granted.containsAll(permissions)) {
                    readStepsHistory(this@MainActivity)
                } else {
                    Log.w(TAG, "Health Connect permissions not granted")
                }
            }
        }
    }

    private fun readStepsHistory(context: Context) {
        lifecycleScope.launch {
            try {
                checkForPermissions(this@MainActivity)
                val healthDataStore = HealthDataService.getStore(context)
                val localTimeFilter =
                    LocalTimeFilter.of(LocalDateTime.now().minusDays(1), LocalDateTime.now())
                val readRequest = DataTypes.HEART_RATE.readDataRequestBuilder
                    .setLocalTimeFilter(localTimeFilter)
                    .setOrdering(Ordering.DESC)
                    .build()
                val heartRateList = healthDataStore.readData(readRequest).dataList
                Log.d(TAG, "Heart rate list: $heartRateList")
                var pageToken: String? = null
                var totalSteps = 0L

                do {
                    val response = healthConnectClient.readRecords(
                        ReadRecordsRequest(
                            recordType = StepsRecord::class,
                            timeRangeFilter = TimeRangeFilter.after(Instant.EPOCH), // everything since epoch
                            pageSize = 1000,
                            pageToken = pageToken
                        )
                    )

                    // Filter only Samsung Health
                    val samsungSteps = response.records
                        .filter { it.metadata.dataOrigin.packageName == "com.sec.android.app.shealth" }

                    totalSteps += samsungSteps.sumOf { it.count.toLong() }

                    // Get the next page token
                    pageToken = response.pageToken
                } while (pageToken != null)

            } catch (e: Exception) {
                Log.e(TAG, "Error reading steps", e)
            }
        }
    }

    private fun readStepCount(
        startTime: LocalDateTime = LocalDateTime.now().minusDays(120),
        endTime: LocalDateTime = LocalDateTime.now()
    ) {
        lifecycleScope.launch {
            try {
                val healthDataStore = HealthDataService.getStore(applicationContext)
                val localtimeFilter = LocalTimeFilter.of(startTime, endTime)

                val readRequest = DataType.StepsType.TOTAL.requestBuilder
                    .setLocalTimeFilterWithGroup(
                        localtimeFilter,
                        LocalTimeGroup.of(LocalTimeGroupUnit.DAILY, 1)
                    )
                    .setOrdering(Ordering.ASC)
                    .build()

                val dataList = healthDataStore.aggregateData(readRequest).dataList
                dataList.forEach {
                    val hourlyStepCount = it.value
                }
                val dailyStepCount = dataList.sumOf { it.value as Long }
                Log.d(TAG, "Daily step count: $dailyStepCount")
            } catch (exception: Exception) {
                Log.e(TAG, "Error reading steps", exception)
            }
        }
    }

    fun readExercises(startTime: LocalDateTime = LocalDateTime.now().minusDays(5),
                      endTime: LocalDateTime = LocalDateTime.now()) {
        lifecycleScope.launch {
            try {
                val healthDataStore = HealthDataService.getStore(applicationContext)
                val localtimeFilter = LocalTimeFilter.of(startTime, endTime)
                val readDataRequest = DataTypes.EXERCISE.readDataRequestBuilder
                    .setLocalTimeFilter(localtimeFilter)
                    .setOrdering(Ordering.DESC)
                    .build()

                val dataList = healthDataStore.readData(readDataRequest).dataList
                Log.d(TAG, "Exercise: $dataList")
            } catch (exception: Exception) {
                Log.e(TAG, "Error reading steps", exception)
            }
        }
    }

    suspend fun checkForPermissions(activity: Activity) {
        val permSet = mutableSetOf(
            Permission.of(DataTypes.HEART_RATE, AccessType.READ),
            Permission.of(DataTypes.STEPS, AccessType.READ),
            Permission.of(DataTypes.SLEEP_GOAL, AccessType.READ),
            Permission.of(DataTypes.SLEEP, AccessType.READ),
            Permission.of(DataTypes.BLOOD_OXYGEN, AccessType.READ),
            Permission.of(DataTypes.BLOOD_GLUCOSE, AccessType.READ),
            Permission.of(DataTypes.EXERCISE, AccessType.READ),
        )

        try {
            val healthDataStore = HealthDataService.getStore(activity.applicationContext)
            val grantedPermissions = healthDataStore.getGrantedPermissions(permSet)

            if (grantedPermissions.containsAll(permSet)) {
                // All Permissions already granted
            } else {
                // Partial or No permission, we need to request for the permission popup
                healthDataStore.requestPermissions(permSet, activity)
            }
        } catch (error: HealthDataException) {
            if (error is ResolvablePlatformException && error.hasResolution) {
                error.resolve(activity)
            }
            // handle other types of HealthDataException
            error.printStackTrace()
        }
    }

    companion object {
        private const val TAG = "SmartHealth"
        private const val PERMISSION_REQUEST_CODE = 1001
    }
}
