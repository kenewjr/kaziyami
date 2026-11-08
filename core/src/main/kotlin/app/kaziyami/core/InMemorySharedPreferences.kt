package app.kaziyami.core

import android.content.SharedPreferences
import java.util.concurrent.ConcurrentHashMap

/**
 * Platform-independent thread-safe in-memory [SharedPreferences] implementation
 * for unit tests and fallback contexts.
 */
class InMemorySharedPreferences : SharedPreferences {

    private val store = ConcurrentHashMap<String, Any>()
    private val listeners = ConcurrentHashMap.newKeySet<SharedPreferences.OnSharedPreferenceChangeListener>()

    override fun getAll(): MutableMap<String, *> = HashMap(store)

    override fun getString(key: String?, defValue: String?): String? {
        val v = if (key != null) store[key] else null
        return (v as? String) ?: defValue
    }

    @Suppress("UNCHECKED_CAST")
    override fun getStringSet(key: String?, defValues: MutableSet<String>?): MutableSet<String>? {
        val v = if (key != null) store[key] else null
        return (v as? Set<String>)?.toMutableSet() ?: defValues
    }

    override fun getInt(key: String?, defValue: Int): Int {
        val v = if (key != null) store[key] else null
        return when (v) {
            is Number -> v.toInt()
            is String -> v.toIntOrNull() ?: defValue
            else -> defValue
        }
    }

    override fun getLong(key: String?, defValue: Long): Long {
        val v = if (key != null) store[key] else null
        return when (v) {
            is Number -> v.toLong()
            is String -> v.toLongOrNull() ?: defValue
            else -> defValue
        }
    }

    override fun getFloat(key: String?, defValue: Float): Float {
        val v = if (key != null) store[key] else null
        return when (v) {
            is Number -> v.toFloat()
            is String -> v.toFloatOrNull() ?: defValue
            else -> defValue
        }
    }

    override fun getBoolean(key: String?, defValue: Boolean): Boolean {
        val v = if (key != null) store[key] else null
        return when (v) {
            is Boolean -> v
            is String -> v.toBoolean()
            else -> defValue
        }
    }

    override fun contains(key: String?): Boolean = key != null && store.containsKey(key)

    override fun edit(): SharedPreferences.Editor = EditorImpl()

    override fun registerOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) {
        if (listener != null) listeners.add(listener)
    }

    override fun unregisterOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) {
        if (listener != null) listeners.remove(listener)
    }

    private inner class EditorImpl : SharedPreferences.Editor {
        private val temp = mutableMapOf<String, Any?>()
        private var clearAll = false

        override fun putString(key: String?, value: String?): SharedPreferences.Editor {
            if (key != null) {
                if (value != null) temp[key] = value else temp[key] = null
            }
            return this
        }

        override fun putStringSet(key: String?, values: MutableSet<String>?): SharedPreferences.Editor {
            if (key != null) {
                if (values != null) temp[key] = HashSet(values) else temp[key] = null
            }
            return this
        }

        override fun putInt(key: String?, value: Int): SharedPreferences.Editor {
            if (key != null) temp[key] = value
            return this
        }

        override fun putLong(key: String?, value: Long): SharedPreferences.Editor {
            if (key != null) temp[key] = value
            return this
        }

        override fun putFloat(key: String?, value: Float): SharedPreferences.Editor {
            if (key != null) temp[key] = value
            return this
        }

        override fun putBoolean(key: String?, value: Boolean): SharedPreferences.Editor {
            if (key != null) temp[key] = value
            return this
        }

        override fun remove(key: String?): SharedPreferences.Editor {
            if (key != null) temp[key] = null
            return this
        }

        override fun clear(): SharedPreferences.Editor {
            clearAll = true
            return this
        }

        override fun commit(): Boolean {
            apply()
            return true
        }

        override fun apply() {
            val changedKeys = mutableListOf<String>()
            synchronized(store) {
                if (clearAll) {
                    changedKeys.addAll(store.keys)
                    store.clear()
                }
                for ((k, v) in temp) {
                    if (v == null) {
                        store.remove(k)
                    } else {
                        store[k] = v
                    }
                    changedKeys.add(k)
                }
            }
            for (key in changedKeys) {
                for (listener in listeners) {
                    listener.onSharedPreferenceChanged(this@InMemorySharedPreferences, key)
                }
            }
        }
    }
}

