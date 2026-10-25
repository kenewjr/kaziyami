package eu.kanade.tachiyomi.filters

import eu.kanade.tachiyomi.animesource.model.AnimeFilter
import eu.kanade.tachiyomi.animesource.model.AnimeFilterList
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

/**
 * Filter specification metadata used to generate Aniyomi [AnimeFilterList] controls
 * and encode query parameters for HTTP search/filter requests.
 */
sealed class FilterSpec {
    abstract val name: String
    abstract val queryParam: String

    /**
     * Single-selection dropdown filter (e.g. Status, Type, Season).
     */
    data class Select(
        override val name: String,
        override val queryParam: String,
        val options: List<Pair<String, String>>, // (DisplayName, QueryValue)
        val defaultIndex: Int = 0
    ) : FilterSpec()

    /**
     * Sort order filter with ascending/descending toggling.
     */
    data class Sort(
        override val name: String = "Sort By",
        override val queryParam: String = "order",
        val options: List<Pair<String, String>>,
        val defaultIndex: Int = 0,
        val defaultAscending: Boolean = false
    ) : FilterSpec()

    /**
     * Multi-select Tri-state filter group (Include, Exclude, Ignore) for Genres/Tags.
     */
    data class TriStateGroup(
        override val name: String = "Genres",
        override val queryParam: String = "genre",
        val options: List<Pair<String, String>> // (DisplayName, Slug/ID)
    ) : FilterSpec()

    /**
     * Multi-select checkbox group for toggles.
     */
    data class CheckBoxGroup(
        override val name: String,
        override val queryParam: String,
        val options: List<Pair<String, String>>
    ) : FilterSpec()
}

open class DynamicSelect(name: String, values: Array<String>, state: Int = 0) : AnimeFilter.Select<String>(name, values, state)
open class DynamicSort(name: String, values: Array<String>, state: AnimeFilter.Sort.Selection? = null) : AnimeFilter.Sort(name, values, state)
open class DynamicTriState(name: String, state: Int = STATE_IGNORE) : AnimeFilter.TriState(name, state)
open class DynamicCheckBox(name: String, state: Boolean = false) : AnimeFilter.CheckBox(name, state)
open class DynamicGroup<V>(name: String, state: List<V>) : AnimeFilter.Group<V>(name, state)

/**
 * Builder and URL encoder for extension filters.
 */
object FilterBuilder {

    /**
     * Builds Aniyomi UI filter controls from declarative [FilterSpec] definitions.
     */
    fun buildFilters(specs: List<FilterSpec>): AnimeFilterList {
        val filterList = mutableListOf<AnimeFilter<*>>()

        for (spec in specs) {
            when (spec) {
                is FilterSpec.Select -> {
                    val labels = spec.options.map { it.first }.toTypedArray()
                    filterList.add(DynamicSelect(spec.name, labels, spec.defaultIndex))
                }
                is FilterSpec.Sort -> {
                    val labels = spec.options.map { it.first }.toTypedArray()
                    filterList.add(DynamicSort(spec.name, labels, AnimeFilter.Sort.Selection(spec.defaultIndex, spec.defaultAscending)))
                }
                is FilterSpec.TriStateGroup -> {
                    val triStates = spec.options.map { (label, _) ->
                        DynamicTriState(label)
                    }
                    filterList.add(DynamicGroup(spec.name, triStates))
                }
                is FilterSpec.CheckBoxGroup -> {
                    val checkBoxes = spec.options.map { (label, _) ->
                        DynamicCheckBox(label)
                    }
                    filterList.add(DynamicGroup(spec.name, checkBoxes))
                }
            }
        }

        return AnimeFilterList(filterList)
    }

    /**
     * Serializes selected filter states into URL query parameters or appends them to a base URL.
     */
    fun buildUrl(
        baseUrl: String,
        specs: List<FilterSpec>,
        filters: AnimeFilterList,
        textQuery: String? = null,
        page: Int = 1
    ): String {
        val params = mutableListOf<Pair<String, String>>()

        if (!textQuery.isNullOrBlank()) {
            params.add("s" to textQuery.trim())
        }
        if (page > 1) {
            params.add("page" to page.toString())
        }

        for ((index, filter) in filters.list.withIndex()) {
            if (index >= specs.size) break
            val spec = specs[index]

            when {
                filter is AnimeFilter.Select<*> && spec is FilterSpec.Select -> {
                    val selectedIdx = filter.state
                    if (selectedIdx in spec.options.indices) {
                        val value = spec.options[selectedIdx].second
                        if (value.isNotEmpty()) {
                            params.add(spec.queryParam to value)
                        }
                    }
                }
                filter is AnimeFilter.Sort && spec is FilterSpec.Sort -> {
                    val sel = filter.state ?: continue
                    if (sel.index in spec.options.indices) {
                        val value = spec.options[sel.index].second
                        if (value.isNotEmpty()) {
                            params.add(spec.queryParam to value)
                            params.add("order_direction" to if (sel.ascending) "asc" else "desc")
                        }
                    }
                }
                filter is AnimeFilter.Group<*> && spec is FilterSpec.TriStateGroup -> {
                    for ((optIdx, item) in filter.state.withIndex()) {
                        if (item is AnimeFilter.TriState && optIdx in spec.options.indices) {
                            val slug = spec.options[optIdx].second
                            when (item.state) {
                                AnimeFilter.TriState.STATE_INCLUDE -> params.add("${spec.queryParam}[]" to slug)
                                AnimeFilter.TriState.STATE_EXCLUDE -> params.add("exclude_${spec.queryParam}[]" to slug)
                                else -> Unit
                            }
                        }
                    }
                }
                filter is AnimeFilter.Group<*> && spec is FilterSpec.CheckBoxGroup -> {
                    for ((optIdx, item) in filter.state.withIndex()) {
                        if (item is AnimeFilter.CheckBox && item.state && optIdx in spec.options.indices) {
                            val slug = spec.options[optIdx].second
                            params.add("${spec.queryParam}[]" to slug)
                        }
                    }
                }
            }
        }

        if (params.isEmpty()) return baseUrl

        val queryStr = params.joinToString("&") { (key, value) ->
            val encKey = URLEncoder.encode(key, StandardCharsets.UTF_8.name())
            val encVal = URLEncoder.encode(value, StandardCharsets.UTF_8.name())
            "$encKey=$encVal"
        }

        val separator = if (baseUrl.contains("?")) "&" else "?"
        return "$baseUrl$separator$queryStr"
    }
}
