package com.arvion.smarthealth.mapper

import com.samsung.android.sdk.health.data.data.entries.SleepSession
import com.arvion.smarthealth.model.RecordSession
import com.arvion.smarthealth.model.SleepSession as SleepSessionModel
import com.arvion.smarthealth.model.SleepStage as SleepStageModel
import com.arvion.smarthealth.model.SleepStageType as SleepStageTypeModel

object SleepSessionMapper {

    fun List<SleepSession>.toSleepSessionModel(): List<RecordSession> {
        return this.map {
            it.toModel()
        }
    }

    fun SleepSession.toModel(): SleepSessionModel {
        return SleepSessionModel(
            startTime = this.startTime,
            endTime = this.endTime,
            duration = this.duration,
            stages = this.stages?.map { stage ->
                SleepStageModel(
                    startTime = stage.startTime,
                    endTime = stage.endTime,
                    stage = SleepStageTypeModel.valueOf(stage.stage.name)
                )
            }
        )
    }
}