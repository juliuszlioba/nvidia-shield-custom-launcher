package com.still.launcher.data

import com.still.launcher.BuildConfig
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.postgrest.query.Order
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneId

/** One entry of "Recently seen", as the launcher shows it. */
data class RecentRecord(
    val id: Long,
    val title: String,
    val year: Int?,
    /** "Movie", "Series" or "Anime". */
    val kind: String,
    val posterUrl: String?,
    /** 1–10, shown as five stars with halves; 0 = unrated. */
    val rating: Int,
    val recommendation: String?,
    val watchedOn: LocalDate?,
)

/**
 * Reads the newest records from the moviesNight Supabase project. The publishable key
 * only allows what RLS grants the public role (SELECT on records).
 */
class RecentRepository(
    private val supabase: SupabaseClient = createSupabaseClient(
        supabaseUrl = BuildConfig.SUPABASE_URL,
        supabaseKey = BuildConfig.SUPABASE_KEY,
    ) { install(Postgrest) },
) {
    suspend fun latest(limit: Int = 10): List<RecentRecord> =
        supabase.from("records")
            .select(Columns.raw(COLUMNS)) {
                // same order as the website and the moviesNight app: newest watched first
                order("watchedat", Order.DESCENDING, nullsFirst = false)
                order("id", Order.DESCENDING)
                limit(limit.toLong())
            }
            .decodeList<RecentRecordDto>()
            .map { it.toModel() }

    private companion object {
        const val COLUMNS = "id,title,year,mediatype,poster,rating,recommendation,watchedat,sort_order_anime"
    }
}

@Serializable
internal data class RecentRecordDto(
    val id: Long,
    val title: String,
    val year: Int? = null,
    val mediatype: String? = null,
    val poster: String? = null,
    val rating: Int? = null,
    val recommendation: String? = null,
    val watchedat: String? = null,
    @SerialName("sort_order_anime") val sortOrderAnime: Int? = null,
) {
    fun toModel() = RecentRecord(
        id = id,
        title = title,
        year = year,
        kind = when {
            sortOrderAnime != null -> "Anime"
            mediatype == "series" -> "Series"
            else -> "Movie"
        },
        posterUrl = poster?.takeIf { it.isNotBlank() }?.let { "https://image.tmdb.org/t/p/w500$it" },
        rating = rating ?: 0,
        recommendation = recommendation?.trim()?.takeIf { it.isNotEmpty() },
        watchedOn = watchedat?.let {
            runCatching { OffsetDateTime.parse(it).atZoneSameInstant(ZoneId.systemDefault()).toLocalDate() }.getOrNull()
        },
    )
}
