package com.yourname.smarthealth

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.samsung.android.sdk.health.data.HealthDataService
import com.samsung.android.sdk.health.data.HealthDataStore
import com.samsung.android.sdk.health.data.data.HealthDataPoint
import com.samsung.android.sdk.health.data.data.entries.ExerciseSession
import com.samsung.android.sdk.health.data.data.entries.SleepSession
import com.samsung.android.sdk.health.data.error.HealthDataException
import com.samsung.android.sdk.health.data.error.ResolvablePlatformException
import com.samsung.android.sdk.health.data.permission.AccessType
import com.samsung.android.sdk.health.data.permission.Permission
import com.samsung.android.sdk.health.data.request.DataType
import com.samsung.android.sdk.health.data.request.DataTypes
import com.samsung.android.sdk.health.data.request.LocalTimeFilter
import com.samsung.android.sdk.health.data.request.LocalTimeGroup
import com.samsung.android.sdk.health.data.request.LocalTimeGroupUnit
import com.samsung.android.sdk.health.data.request.Ordering
import com.yourname.smarthealth.model.RecordSession
import com.yourname.smarthealth.service.ApiService
import com.yourname.smarthealth.utils.Utilities.gson
import kotlinx.coroutines.launch
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.time.LocalDateTime
import java.time.LocalTime
import com.yourname.smarthealth.model.CountType as CountTypeModel
import com.yourname.smarthealth.model.DataSource as DataSourceModel
import com.yourname.smarthealth.model.ExerciseLocation as ExerciseLocationModel
import com.yourname.smarthealth.model.ExerciseLog as ExerciseLogModel
import com.yourname.smarthealth.model.ExerciseSession as ExerciseSessionModel
import com.yourname.smarthealth.model.HealthDataPoint as HealthDataPointModel
import com.yourname.smarthealth.model.SleepSession as SleepSessionModel
import com.yourname.smarthealth.model.SleepStage as SleepStageModel
import com.yourname.smarthealth.model.SleepStageType as SleepStageTypeModel

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

        val readSleepButton: Button = findViewById(R.id.readSleepButton)
        readSleepButton.setOnClickListener {
            readSleep()
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
            try {
                val healthDataStore = HealthDataService.getStore(applicationContext)
                val localtimeFilter = LocalTimeFilter.of(startTime, endTime)
                val readDataRequest = DataTypes.EXERCISE.readDataRequestBuilder
                    .setLocalTimeFilter(localtimeFilter)
                    .setOrdering(Ordering.DESC)
                    .build()

                val dataList = healthDataStore.readData(readDataRequest).dataList

                val healthDataPoints = dataList.map { dataPoint ->
                    dataPointToModel(dataPoint = dataPoint, dataType = DataTypes.EXERCISE)
                }

                val retrofit = Retrofit.Builder()
                    .baseUrl("http://192.168.1.131:8080/")
                    .addConverterFactory(GsonConverterFactory.create(gson))
                    .build()

                val apiService = retrofit.create(ApiService::class.java)

                sendToAPI(
                    operationToBeExecuted = apiService::postExercise,
                    healthDataPoints = healthDataPoints
                )
                Log.d(TAG, "Exercise: $dataList")
            } catch (exception: Exception) {
                Log.e(TAG, "Error reading steps", exception)
            }
        }
    }

    fun readSleep(
        startTime: LocalDateTime = LocalDateTime.now().minusDays(15),
        endTime: LocalDateTime = LocalDateTime.now()
    ) {
        lifecycleScope.launch {
            try {
                val healthDataStore = HealthDataService.getStore(applicationContext)
                val localtimeFilter = LocalTimeFilter.of(startTime, endTime)
                val readDataRequest = DataTypes.SLEEP.readDataRequestBuilder
                    .setLocalTimeFilter(localtimeFilter)
                    .setOrdering(Ordering.DESC)
                    .build()

                readSleepScore()
                val dataList = healthDataStore.readData(readDataRequest).dataList

                val healthDataPoints = dataList.map { dataPoint ->
                    dataPointToModel(dataPoint = dataPoint, dataType = DataTypes.SLEEP)
                }

                val retrofit = Retrofit.Builder()
                    .baseUrl("http://192.168.1.131:8080/")
                    .addConverterFactory(GsonConverterFactory.create(gson))
                    .build()

                val apiService = retrofit.create(ApiService::class.java)

                sendToAPI(
                    operationToBeExecuted = apiService::postSleep,
                    healthDataPoints = healthDataPoints
                )
                Log.d(TAG, "Exercise: $dataList")
            } catch (exception: Exception) {
                Log.e(TAG, "Error reading steps", exception)
            }
        }
    }

    fun readSleepScore(
        startTime: LocalDateTime = LocalDateTime.now().minusDays(15),
        endTime: LocalDateTime = LocalDateTime.now()
    ){
        lifecycleScope.launch {
            try {
                val healthDataStore = HealthDataService.getStore(applicationContext)
                val localtimeFilter = LocalTimeFilter.of(startTime, endTime)

                val readRequest = DataType.ExerciseType.TOTAL_CALORIES.requestBuilder
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
    private fun sendToAPI(
        operationToBeExecuted: (HealthDataPointModel) -> Call<HealthDataPointModel>,
        healthDataPoints: List<HealthDataPointModel>
    ) {
        healthDataPoints.forEach { dataPoint ->
            Log.i(TAG, gson.toJson(dataPoint))
            operationToBeExecuted.invoke(dataPoint)
            val call = operationToBeExecuted.invoke(dataPoint)

            call.enqueue(object : Callback<HealthDataPointModel> {
                override fun onResponse(
                    call: Call<HealthDataPointModel>,
                    response: Response<HealthDataPointModel>
                ) {
                    if (response.isSuccessful) {
                        val data = response.body()
                        // Handle your HealthDataPoint here
                    } else {
                        Log.e(TAG, "Erro")
                    }
                }

                override fun onFailure(call: Call<HealthDataPointModel>, t: Throwable) {
                    // Handle failure
                    Log.e(TAG, "Erro")
                }
            })
        }
    }

    fun dataPointToModel(dataPoint: HealthDataPoint, dataType: DataType): HealthDataPointModel {
        val data = when (dataType) {
            is DataType.ExerciseType -> {
                val sessionList =
                    dataPoint.getValue(DataType.ExerciseType.SESSIONS) as List<ExerciseSession>
                sessionList.toExerciseSessionModel()
            }

            is DataType.SleepType -> {
                val sessionList =
                    dataPoint.getValue(DataType.SleepType.SESSIONS) as List<SleepSession>
                sessionList.toSleepSessionModel()
            }

            else -> {
                throw RuntimeException("")
            }
        }
        return HealthDataPointModel(
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
            sessions = data
        )
    }

    fun List<SleepSession>.toSleepSessionModel(): List<RecordSession> {
        return this.map { session ->
            SleepSessionModel(
                startTime = session.startTime,
                endTime = session.endTime,
                duration = session.duration,
                stages = session.stages?.map { stage ->
                    SleepStageModel(
                        startTime = stage.startTime,
                        endTime = stage.endTime,
                        stage = SleepStageTypeModel.valueOf(stage.stage.name)
                    )
                }
            )
        }
    }

    fun List<ExerciseSession>.toExerciseSessionModel(): List<RecordSession> {
        return this.map { session ->
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

    suspend fun getTotalStepsPerDay(healthDataStore: HealthDataStore) {
        val todayDateTime = LocalDateTime.now().with(LocalTime.MIDNIGHT)
        val todayLocalTimeFilter = LocalTimeFilter.of(todayDateTime, todayDateTime.plusDays(1))
        val hourlyTimeGroup = LocalTimeGroup.of(LocalTimeGroupUnit.HOURLY, 1)
        val stepsAggregateRequest = DataType.StepsType.TOTAL.requestBuilder
            .setLocalTimeFilterWithGroup(todayLocalTimeFilter, hourlyTimeGroup)
            .setOrdering(Ordering.ASC)
            .build()
        val hourlyStepsResponse = healthDataStore.aggregateData(stepsAggregateRequest)
        val dataListSteps = hourlyStepsResponse.dataList
        val sumOf = dataListSteps.sumOf { it.value!! }
        Log.i(TAG, sumOf.toString())
        dataListSteps.forEach { stepData ->
            val hourlySteps: Long = stepData.value!!
            Log.i(TAG, hourlySteps.toString())
        }

    }

    companion object {
        private const val TAG = "SmartHealth"
        private const val PERMISSION_REQUEST_CODE = 1001
    }
}
