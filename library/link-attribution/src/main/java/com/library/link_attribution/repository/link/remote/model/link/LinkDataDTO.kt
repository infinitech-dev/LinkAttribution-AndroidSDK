package com.library.link_attribution.repository.link.remote.model.link

import android.os.Parcelable
import com.library.link_attribution.repository.link.model.link.LinkDataModel
import kotlinx.parcelize.Parcelize
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Parcelize
@Serializable
data class LinkDataDTO(
    @SerialName("analyticsTags")
    val analyticsTags: AnalyticsTags?,
    @SerialName("data")
    val data: Map<String, String?>?,
    @SerialName("slug")
    val slug: String?,
    @SerialName("socialMediaTags")
    val socialMediaTags: SocialMediaTags?
) : Parcelable {

    @Parcelize
    @Serializable
    data class AnalyticsTags(
        @SerialName("campaign")
        val campaign: String?,
        @SerialName("channel")
        val channel: String?,
        @SerialName("feature")
        val feature: String?,
        @SerialName("tags")
        val tags: String?
    ) : Parcelable {

        fun toExternal(): LinkDataModel.AnalyticsTags {
            return LinkDataModel.AnalyticsTags(
                campaign = campaign,
                channel = channel,
                feature = feature,
                tags = tags
            )
        }

    }

    @Parcelize
    @Serializable
    data class SocialMediaTags(
        @SerialName("description")
        val description: String?,
        @SerialName("title")
        val title: String?
    ) : Parcelable {
        fun toExternal(): LinkDataModel.SocialMediaTags {
            return LinkDataModel.SocialMediaTags(
                description = description,
                title = title
            )
        }
    }

    fun toExternal(): LinkDataModel {
        return LinkDataModel(
            analyticsTags = analyticsTags?.toExternal(),
            data = data,
            slug = slug,
            socialMediaTags = socialMediaTags?.toExternal()
        )
    }
}