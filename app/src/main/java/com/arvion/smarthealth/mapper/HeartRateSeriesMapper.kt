package com.arvion.smarthealth.mapper

import com.samsung.android.sdk.health.data.data.entries.HeartRate
import com.arvion.smarthealth.model.HeartRateSeries
import com.arvion.smarthealth.model.RecordSession
import java.time.Duration

object HeartRateSeriesMapper {

    fun List<HeartRate>.toHeartRateSeriesModel(): List<RecordSession> {
        return this.map {
            it.toModel()
        }
    }

    fun HeartRate.toModel(): HeartRateSeries {
        return HeartRateSeries(
            startTime = this.startTime,
            endTime = this.endTime,
            duration = Duration.between(this.startTime, this.endTime),
            heartRate = this.heartRate,
            max = this.max,
            min = this.min
        )
    }
}