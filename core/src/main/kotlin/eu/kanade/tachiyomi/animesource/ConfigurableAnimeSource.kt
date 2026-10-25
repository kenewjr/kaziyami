package eu.kanade.tachiyomi.animesource

interface ConfigurableAnimeSource {
    fun setupPreferenceScreen(screen: SourcePreferences)
}

interface SourcePreferences {
    fun getString(key: String, defaultValue: String): String
    fun setString(key: String, value: String)
    fun getBoolean(key: String, defaultValue: Boolean): Boolean
    fun setBoolean(key: String, value: Boolean)
    fun getInt(key: String, defaultValue: Int): Int
    fun setInt(key: String, value: Int)

    fun addListPreference(
        key: String,
        title: String,
        summary: String,
        entries: Array<String>,
        entryValues: Array<String>,
        defaultValue: String
    )

    fun addEditTextPreference(
        key: String,
        title: String,
        summary: String,
        defaultValue: String
    )

    fun addSwitchPreference(
        key: String,
        title: String,
        summary: String,
        defaultValue: Boolean
    )
}

class InMemorySourcePreferences : SourcePreferences {
    private val data = mutableMapOf<String, Any>()

    override fun getString(key: String, defaultValue: String): String =
        data[key] as? String ?: defaultValue

    override fun setString(key: String, value: String) {
        data[key] = value
    }

    override fun getBoolean(key: String, defaultValue: Boolean): Boolean =
        data[key] as? Boolean ?: defaultValue

    override fun setBoolean(key: String, value: Boolean) {
        data[key] = value
    }

    override fun getInt(key: String, defaultValue: Int): Int =
        data[key] as? Int ?: defaultValue

    override fun setInt(key: String, value: Int) {
        data[key] = value
    }

    override fun addListPreference(
        key: String,
        title: String,
        summary: String,
        entries: Array<String>,
        entryValues: Array<String>,
        defaultValue: String
    ) {
        if (!data.containsKey(key)) {
            data[key] = defaultValue
        }
    }

    override fun addEditTextPreference(
        key: String,
        title: String,
        summary: String,
        defaultValue: String
    ) {
        if (!data.containsKey(key)) {
            data[key] = defaultValue
        }
    }

    override fun addSwitchPreference(
        key: String,
        title: String,
        summary: String,
        defaultValue: Boolean
    ) {
        if (!data.containsKey(key)) {
            data[key] = defaultValue
        }
    }
}
