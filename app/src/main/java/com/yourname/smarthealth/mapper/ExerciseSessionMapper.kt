package com.yourname.smarthealth.mapper

import com.samsung.android.sdk.health.data.data.entries.ExerciseSession
import com.yourname.smarthealth.model.RecordSession
import com.yourname.smarthealth.model.CountType as CountTypeModel
import com.yourname.smarthealth.model.ExerciseLocation as ExerciseLocationModel
import com.yourname.smarthealth.model.ExerciseLog as ExerciseLogModel
import com.yourname.smarthealth.model.ExerciseSession as ExerciseSessionModel

object ExerciseSessionMapper {

    fun List<ExerciseSession>.toExerciseSessionModel(): List<RecordSession> {
        return this.map {
            it.toModel()
        }
    }

    fun ExerciseSession.toModel(): ExerciseSessionModel {
        return ExerciseSessionModel(
            altitudeGain = this.altitudeGain,
            altitudeLoss = this.altitudeLoss,
            calories = this.calories,
            comment = this.comment,
            count = this.count,
            countType = CountTypeModel.valueOf(this.countType.name),
            customTitle = this.customTitle,
            declineDistance = this.declineDistance,
            distance = this.distance,
            duration = this.duration,
            endTime = this.endTime,
            exerciseType = this.exerciseType.name,
            inclineDistance = this.inclineDistance,
            log = this.log?.map { log ->
                ExerciseLogModel(
                    cadence = log.cadence,
                    count = log.count,
                    heartRate = log.heartRate,
                    power = log.power,
                    speed = log.speed,
                    timestamp = log.timestamp
                )
            },
            maxAltitude = this.maxAltitude,
            maxCadence = this.maxCadence,
            maxCalorieBurnRate = this.maxCalorieBurnRate,
            maxHeartRate = this.maxHeartRate,
            maxPower = this.maxPower,
            maxRpm = this.maxRpm,
            maxSpeed = this.maxSpeed,
            meanCadence = this.meanCadence,
            meanCalorieBurnRate = this.meanCalorieBurnRate,
            meanHeartRate = this.meanHeartRate,
            meanPower = this.meanPower,
            meanRpm = this.meanRpm,
            meanSpeed = this.meanSpeed,
            minAltitude = this.minAltitude,
            minHeartRate = this.minHeartRate,
            route = this.route?.map { location ->
                ExerciseLocationModel(
                    accuracy = location.accuracy,
                    altitude = location.altitude,
                    latitude = location.latitude,
                    longitude = location.longitude,
                    timestamp = location.timestamp
                )
            },
            startTime = this.startTime,
            swimmingLog = this.swimmingLog,
            vo2Max = this.vo2Max
        )
    }
}