package com.yourname.smarthealth.mapper

import com.yourname.smarthealth.model.DailySummaryActivity
import com.yourname.smarthealth.model.ExerciseSession
import com.yourname.smarthealth.model.RecordSession

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