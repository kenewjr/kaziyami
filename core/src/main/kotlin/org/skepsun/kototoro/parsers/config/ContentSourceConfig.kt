package org.skepsun.kototoro.parsers.config

sealed class ConfigKey<T>(val key: String, val title: String, val defaultValue: T) {
    class Domain(defaultDomain: String, vararg val alternatives: String) : ConfigKey<String>("domain", "Domain", defaultDomain)
    class Text(key: String, title: String, defaultValue: String) : ConfigKey<String>(key, title, defaultValue)
    class Toggle(key: String, title: String, defaultValue: Boolean) : ConfigKey<Boolean>(key, title, defaultValue)
}

interface ContentSourceConfig {
    operator fun <T> get(key: ConfigKey<T>): T
}

class SimpleContentSourceConfig(
    private val values: Map<String, Any> = emptyMap()
) : ContentSourceConfig {
    @Suppress("UNCHECKED_CAST")
    override fun <T> get(key: ConfigKey<T>): T {
        return (values[key.key] as? T) ?: key.defaultValue
    }
}

