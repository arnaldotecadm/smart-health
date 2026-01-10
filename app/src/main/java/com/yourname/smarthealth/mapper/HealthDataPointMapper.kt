package com.yourname.smarthealth.mapper

import com.samsung.android.sdk.health.data.data.HealthDataPoint
import com.samsung.android.sdk.health.data.data.entries.ExerciseSession
import com.samsung.android.sdk.health.data.data.entries.SleepSession
import com.samsung.android.sdk.health.data.request.DataType
import com.yourname.smarthealth.mapper.ExerciseSessionMapper.toExerciseSessionModel
import com.yourname.smarthealth.mapper.SleepSessionMapper.toSleepSessionModel
import com.yourname.smarthealth.model.DataSource as DataSourceModel
import com.yourname.smarthealth.model.HealthDataPoint as HealthDataPointModel

object HealthDataPointMapper {
    fun List<HealthDataPoint>.toModel(dataType: DataType): List<HealthDataPointModel> {
        return this.map { it.toModel(dataType = dataType) }
    }

    fun HealthDataPoint.toModel(dataType: DataType): HealthDataPointModel {
        var sleepScore: Int? = null
        val data = when (dataType) {
            is DataType.ExerciseType -> {
                val sessionList =
                    this.getValue(DataType.ExerciseType.SESSIONS) as List<ExerciseSession>
                sessionList.toExerciseSessionModel()
            }

            is DataType.SleepType -> {
                val sessionList =
                    this.getValue(DataType.SleepType.SESSIONS) as List<SleepSession>
                sleepScore = this.getValue(DataType.SleepType.SLEEP_SCORE) as Int
                sessionList.toSleepSessionModel()
            }

            else -> {
                throw RuntimeException("")
            }
        }
        return HealthDataPointModel(
            clientDataId = this.clientDataId,
            clientVersion = this.clientVersion,
            dataSource = this.dataSource?.let { dataSource ->
                DataSourceModel(
                    appId = dataSource.appId,
                    deviceId = dataSource.deviceId
                )
            },
            endTime = this.endTime,
            startTime = this.startTime,
            uid = this.uid,
            updateTime = this.updateTime,
            zoneOffset = this.zoneOffset,
            sessions = data,
            sleepScore = sleepScore
        )
    }
}