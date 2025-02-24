package com.data.users.model

import android.os.Parcelable
import kotlinx.parcelize.Parcelize
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Parcelize
@Serializable
data class UserModel(
    @SerialName("unid")
    val unid: String? = null,
    @SerialName("createdAt")
    val createdAt: String? = null,
    @SerialName("updatedAt")
    val updatedAt: String? = null,
    @SerialName("email")
    val email: String? = null,
    @SerialName("name")
    val name: String? = null,
    @SerialName("fullName")
    val fullName: String? = null,
    @SerialName("phone")
    val phone: String? = null,
    @SerialName("profilePictureUrl")
    val profilePictureUrl: String? = null,
    @SerialName("onboardingComplete")
    val onboardingComplete: Boolean? = null,
    @SerialName("appBackgroundRefreshEnabled")
    val appBackgroundRefreshEnabled: Boolean? = null,
    @SerialName("enabledPushNotifications")
    val enabledPushNotifications: Boolean? = null,
    @SerialName("enabledHealthKit")
    val enabledHealthKit: Boolean? = null,
    @SerialName("gpsPermission")
    val gpsPermission: Int? = null,
    @SerialName("passwordSet")
    val passwordSet: Boolean? = null,
    @SerialName("fitbitLinked")
    val fitbitLinked: Boolean? = null,
    @SerialName("timeZoneText")
    val timeZoneText: String? = null,
    @SerialName("timeZoneOffsetMinutes")
    val timeZoneOffsetMinutes: Int? = null,
    @SerialName("stepGoal")
    val stepGoal: Int? = null,
    @SerialName("distanceUnits")
    val distanceUnits: String? = null,
    @SerialName("soundsEnabled")
    val soundsEnabled: Boolean? = null,
    @SerialName("teamsVisibility")
    val teamsVisibility: String? = null,
    @SerialName("lat")
    val lat: Double? = null,
    @SerialName("long")
    val long: Double? = null,
    @SerialName("autoGenerateStepGoal")
    val autoGenerateStepGoal: Boolean? = null,
    @SerialName("dailyChallengeStepGoal")
    val dailyChallengeStepGoal: Boolean? = null,
) : Parcelable {
}