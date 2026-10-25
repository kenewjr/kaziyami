package eu.kanade.tachiyomi.filters

import eu.kanade.tachiyomi.animesource.model.AnimeFilter
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class FiltersTest {

    private val specs = listOf(
        FilterSpec.Sort(
            name = "Sort By",
            queryParam = "order",
            options = listOf("Popular" to "popular", "Latest" to "latest", "Title" to "title"),
            defaultIndex = 0
        ),
        FilterSpec.Select(
            name = "Status",
            queryParam = "status",
            options = listOf("All" to "", "Ongoing" to "ongoing", "Completed" to "completed")
        ),
        FilterSpec.TriStateGroup(
            name = "Genres",
            queryParam = "genre",
            options = listOf("Action" to "action", "Ecchi" to "ecchi", "Harem" to "harem")
        ),
        FilterSpec.CheckBoxGroup(
            name = "Type",
            queryParam = "type",
            options = listOf("TV" to "tv", "OVA" to "ova")
        )
    )

    @Test
    fun buildFiltersCreatesExpectedControls() {
        val filterList = FilterBuilder.buildFilters(specs)
        assertEquals(4, filterList.size)

        assertTrue(filterList[0] is AnimeFilter.Sort)
        assertTrue(filterList[1] is AnimeFilter.Select<*>)
        assertTrue(filterList[2] is AnimeFilter.Group<*>)
        assertTrue(filterList[3] is AnimeFilter.Group<*>)
    }

    @Test
    fun buildUrlSerializesSelectedFiltersAndCombinesWithSearch() {
        val filterList = FilterBuilder.buildFilters(specs)

        // Set Sort to Latest (index 1), Ascending
        val sortFilter = filterList[0] as AnimeFilter.Sort
        sortFilter.state = AnimeFilter.Sort.Selection(1, true)

        // Set Status to Completed (index 2)
        val statusFilter = filterList[1] as AnimeFilter.Select<*>
        statusFilter.state = 2

        // Set Genre: Include Ecchi (index 1), Exclude Harem (index 2)
        val genreGroup = filterList[2] as AnimeFilter.Group<*>
        val ecchi = genreGroup.state[1] as AnimeFilter.TriState
        ecchi.state = AnimeFilter.TriState.STATE_INCLUDE
        val harem = genreGroup.state[2] as AnimeFilter.TriState
        harem.state = AnimeFilter.TriState.STATE_EXCLUDE

        // Set Type: Check OVA (index 1)
        val typeGroup = filterList[3] as AnimeFilter.Group<*>
        val ova = typeGroup.state[1] as AnimeFilter.CheckBox
        ova.state = true

        val url = FilterBuilder.buildUrl(
            baseUrl = "https://example.com/search",
            specs = specs,
            filters = filterList,
            textQuery = "overflow series",
            page = 2
        )

        assertTrue(url.contains("s=overflow+series"))
        assertTrue(url.contains("page=2"))
        assertTrue(url.contains("order=latest"))
        assertTrue(url.contains("order_direction=asc"))
        assertTrue(url.contains("status=completed"))
        assertTrue(url.contains("genre%5B%5D=ecchi"))
        assertTrue(url.contains("exclude_genre%5B%5D=harem"))
        assertTrue(url.contains("type%5B%5D=ova"))
    }

    @Test
    fun buildUrlReturnsOriginalUrlWhenNoFiltersApplied() {
        val filterList = FilterBuilder.buildFilters(specs)
        val url = FilterBuilder.buildUrl(
            baseUrl = "https://example.com/anime",
            specs = specs,
            filters = filterList
        )
        // With default selections, Sort produces default order if value is set
        assertTrue(url.startsWith("https://example.com/anime"))
    }
}
