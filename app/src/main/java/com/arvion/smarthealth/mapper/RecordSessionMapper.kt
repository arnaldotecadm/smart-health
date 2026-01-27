package com.arvion.smarthealth.mapper

import com.arvion.smarthealth.model.DailySummaryActivity
import com.arvion.smarthealth.model.ExerciseSession
import com.arvion.smarthealth.model.RecordSession

object RecordSessionMapper {

    fun RecordSession.toDailySummaryActivityModel(dataSource: String): DailySummaryActivity {
        if (this is ExerciseSession) {
            return DailySummaryActivity(
                calories = this.calories,
                duration = this.duration,
                exerciseType = this.exerciseType,
                dataSource = dataSource
            )
        }
        throw IllegalArgumentException("Invalid RecordSession type")
    }
}