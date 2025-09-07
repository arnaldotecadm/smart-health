package com.yourname.smarthealth

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.gson.ExclusionStrategy
import com.google.gson.FieldAttributes
import com.google.gson.GsonBuilder
import com.samsung.android.sdk.health.data.HealthDataService
import com.samsung.android.sdk.health.data.data.AggregateOperation
import com.samsung.android.sdk.health.data.data.DataPoint
import com.samsung.android.sdk.health.data.data.HealthDataPoint
import com.samsung.android.sdk.health.data.data.entries.ExerciseSession
import com.samsung.android.sdk.health.data.error.HealthDataException
import com.samsung.android.sdk.health.data.error.ResolvablePlatformException
import com.samsung.android.sdk.health.data.permission.AccessType
import com.samsung.android.sdk.health.data.permission.Permission
import com.samsung.android.sdk.health.data.request.AggregateRequest
import com.samsung.android.sdk.health.data.request.DataType
import com.samsung.android.sdk.health.data.request.DataTypes
import com.samsung.android.sdk.health.data.request.LocalTimeFilter
import com.samsung.android.sdk.health.data.request.LocalTimeGroup
import com.samsung.android.sdk.health.data.request.LocalTimeGroupUnit
import com.samsung.android.sdk.health.data.request.Ordering
import com.samsung.android.sdk.health.data.request.ReadDataRequest
import com.yourname.smarthealth.adapter.DurationAdapter
import com.yourname.smarthealth.adapter.InstantAdapter
import com.yourname.smarthealth.adapter.ZoneOffsetAdapter
import com.yourname.smarthealth.service.ApiService
import kotlinx.coroutines.launch
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.time.Duration
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneOffset
import com.yourname.smarthealth.model.CountType as CountTypeModel
import com.yourname.smarthealth.model.DataSource as DataSourceModel
import com.yourname.smarthealth.model.ExerciseLocation as ExerciseLocationModel
import com.yourname.smarthealth.model.ExerciseLog as ExerciseLogModel
import com.yourname.smarthealth.model.ExerciseSession as ExerciseSessionModel
import com.yourname.smarthealth.model.HealthDataPoint as HealthDataPointModel

class MainActivity() : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

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


    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
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

    fun readExercises(
        startTime: LocalDateTime = LocalDateTime.now().minusDays(15),
        endTime: LocalDateTime = LocalDateTime.now()
    ) {
        lifecycleScope.launch {

            val strategy: ExclusionStrategy = object : ExclusionStrategy {
                override fun shouldSkipField(field: FieldAttributes): Boolean {
                    return field.name == "logByteList"
                }

                override fun shouldSkipClass(clazz: Class<*>?): Boolean {
                    return false
                }
            }
            try {
                val healthDataStore = HealthDataService.getStore(applicationContext)
                val localtimeFilter = LocalTimeFilter.of(startTime, endTime)
                val readDataRequest = DataTypes.EXERCISE.readDataRequestBuilder
                    .setLocalTimeFilter(localtimeFilter)
                    .setOrdering(Ordering.DESC)
                    .build()

                val dataList = healthDataStore.readData(readDataRequest).dataList
                val allFields = DataTypes.EXERCISE.allFields

                val healthDataPoints = dataList.map { dataPoint ->
                    val sessionList = dataPoint.getValue(allFields[0]) as List<ExerciseSession>
                    HealthDataPointModel(
                        clientDataId = dataPoint.clientDataId,
                        clientVersion = dataPoint.clientVersion,
                        dataSource = dataPoint.dataSource?.let { dataSource ->
                            DataSourceModel(
                                appId = dataSource.appId,
                                deviceId = dataSource.deviceId
                            )
                        },
                        endTime = dataPoint.endTime,
                        startTime = dataPoint.startTime,
                        uid = dataPoint.uid,
                        updateTime = dataPoint.updateTime,
                        zoneOffset = dataPoint.zoneOffset,
                        value = sessionList.map { session ->
                            ExerciseSessionModel(
                                altitudeGain = session.altitudeGain,
                                altitudeLoss = session.altitudeLoss,
                                calories = session.calories,
                                comment = session.comment,
                                count = session.count,
                                countType = CountTypeModel.valueOf(session.countType.name),
                                customTitle = session.customTitle,
                                declineDistance = session.declineDistance,
                                distance = session.distance,
                                duration = session.duration,
                                endTime = session.endTime,
                                exerciseType = session.exerciseType.name,
                                inclineDistance = session.inclineDistance,
                                log = session.log?.map { log ->
                                    ExerciseLogModel(
                                        cadence = log.cadence,
                                        count = log.count,
                                        heartRate = log.heartRate,
                                        power = log.power,
                                        speed = log.speed,
                                        timestamp = log.timestamp
                                    )
                                },
                                maxAltitude = session.maxAltitude,
                                maxCadence = session.maxCadence,
                                maxCalorieBurnRate = session.maxCalorieBurnRate,
                                maxHeartRate = session.maxHeartRate,
                                maxPower = session.maxPower,
                                maxRpm = session.maxRpm,
                                maxSpeed = session.maxSpeed,
                                meanCadence = session.meanCadence,
                                meanCalorieBurnRate = session.meanCalorieBurnRate,
                                meanHeartRate = session.meanHeartRate,
                                meanPower = session.meanPower,
                                meanRpm = session.meanRpm,
                                meanSpeed = session.meanSpeed,
                                minAltitude = session.minAltitude,
                                minHeartRate = session.minHeartRate,
                                route = session.route?.map { location ->
                                    ExerciseLocationModel(
                                        accuracy = location.accuracy,
                                        altitude = location.altitude,
                                        latitude = location.latitude,
                                        longitude = location.longitude,
                                        timestamp = location.timestamp
                                    )
                                },
                                startTime = session.startTime,
                                swimmingLog = session.swimmingLog,
                                vo2Max = session.vo2Max
                            )
                        }
                    )
                }

                val gson = GsonBuilder()
                    .setPrettyPrinting()
                    .registerTypeAdapter(Instant::class.java, InstantAdapter())
                    .registerTypeAdapter(Duration::class.java, DurationAdapter())
                    .registerTypeAdapter(ZoneOffset::class.java, ZoneOffsetAdapter())
                    .create()

                val retrofit = Retrofit.Builder()
                    .baseUrl("http://192.168.1.131:8080/") // Replace with your actual base URL
                    .addConverterFactory(GsonConverterFactory.create(gson))
                    .build()

                val apiService = retrofit.create(ApiService::class.java)

                healthDataPoints.forEach { dataPoint ->
                    Log.i("MyAppTag", gson.toJson(dataPoint))
                    val call = apiService.createPost(dataPoint)

                    call.enqueue(object : Callback<HealthDataPointModel> {
                        override fun onResponse(
                            call: Call<HealthDataPointModel>,
                            response: Response<HealthDataPointModel>
                        ) {
                            if (response.isSuccessful) {
                                val data = response.body()
                                // Handle your HealthDataPoint here
                            } else {
                                Log.e("MyAppTag", "Erro")
                            }
                        }

                        override fun onFailure(call: Call<HealthDataPointModel>, t: Throwable) {
                            // Handle failure
                            Log.e("MyAppTag", "Erro")
                        }
                    })
                }

                val value = (dataList.first() as HealthDataPoint).getValue(allFields.first())
                val gsonb = GsonBuilder().addSerializationExclusionStrategy(strategy).create()
                val toJson = gson.toJson((dataList[15] as HealthDataPoint).getValue(allFields[0]))
                Log.d(TAG, "Exercise: $dataList")
                val value1 = (dataList.first() as HealthDataPoint).getValue(allFields.first())
                println(value1)
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
            Permission.of(DataTypes.EXERCISE_LOCATION, AccessType.READ),
            Permission.of(DataTypes.ACTIVITY_SUMMARY, AccessType.READ),
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
