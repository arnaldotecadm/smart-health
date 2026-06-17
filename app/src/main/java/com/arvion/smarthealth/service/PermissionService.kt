package com.arvion.smarthealth.service

import android.app.Activity
import android.content.Context
import com.samsung.android.sdk.health.data.HealthDataService
import com.samsung.android.sdk.health.data.error.HealthDataException
import com.samsung.android.sdk.health.data.permission.AccessType
import com.samsung.android.sdk.health.data.permission.Permission
import com.samsung.android.sdk.health.data.request.DataTypes

class PermissionService {

    private val requiredPermissions = setOf(
        Permission.of(DataTypes.HEART_RATE, AccessType.READ),
        Permission.of(DataTypes.STEPS, AccessType.READ),
        Permission.of(DataTypes.SLEEP_GOAL, AccessType.READ),
        Permission.of(DataTypes.SLEEP, AccessType.READ),
        Permission.of(DataTypes.BLOOD_OXYGEN, AccessType.READ),
        Permission.of(DataTypes.BLOOD_GLUCOSE, AccessType.READ),
        Permission.of(DataTypes.EXERCISE, AccessType.READ),
        Permission.of(DataTypes.EXERCISE_LOCATION, AccessType.READ),
        Permission.of(DataTypes.ACTIVITY_SUMMARY, AccessType.READ),
        Permission.of(DataTypes.NUTRITION, AccessType.READ),
        Permission.of(DataTypes.NUTRITION_GOAL, AccessType.READ),
    )

    suspend fun hasAllPermissions(context: Context): Boolean {
        return getPermissionStatus(context) == 0
    }

    /**
     * Checks if all permissions are granted without triggering the request UI.
     * Returns 0 if granted, or a negative value/HealthDataException code if not.
     */
    suspend fun getPermissionStatus(context: Context): Int {
        return try {
            val healthDataStore = HealthDataService.getStore(context)
            val grantedPermissions = healthDataStore.getGrantedPermissions(requiredPermissions)
            if (grantedPermissions.containsAll(requiredPermissions)) 0 else -1
        } catch (error: HealthDataException) {
            error.errorCode ?: -1
        } catch (e: Exception) {
            -1
        }
    }

    /**
     * Triggers the Samsung Health permission request UI if needed.
     */
    suspend fun requestPermissions(activity: Activity): Int? {
        try {
            val healthDataStore = HealthDataService.getStore(activity.applicationContext)
            val grantedPermissions = healthDataStore.getGrantedPermissions(requiredPermissions)

            if (!grantedPermissions.containsAll(requiredPermissions)) {
                healthDataStore.requestPermissions(requiredPermissions, activity)
            }
        } catch (error: HealthDataException) {
            return error.errorCode
        }
        return 0
    }
}