package eu.kanade.tachiyomi.animesource.model

import okhttp3.Headers
import java.io.Serializable

data class Track(
    val url: String,
    val lang: String
) : Serializable

data class Video(
    val url: String,
    val quality: String,
    val videoUrl: String? = null,
    val headers: Headers? = null,
    val subtitleTracks: List<Track> = emptyList(),
    val audioTracks: List<Track> = emptyList()
) : Serializable {
    @Transient
    var status: State = State.NOT_FETCHED

    enum class State {
        NOT_FETCHED,
        LOAD_VIDEO,
        READY,
        ERROR
    }
}
