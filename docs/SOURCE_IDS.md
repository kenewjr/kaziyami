# Permanent Source Identifiers Specification

Source IDs in Tachiyomi/Aniyomi are 63-bit positive integers derived from the source `name`, `lang`, and version step. Changing an ID invalidates user library bookmarks, categories, watch history, and tracking links.

---

## 1. Upstream Hashing Algorithm

```kotlin
val key = "${name.lowercase()}/$lang/1"
val bytes = MessageDigest.getInstance("MD5").digest(key.toByteArray())
val id = (0..7).fold(0L) { acc, i -> (acc shl 8) or (bytes[i].toLong() and 0xFF) } and Long.MAX_VALUE
```

---

## 2. Permanent Source ID Registry

| Source Name | Language | Status | Source ID (Long) | Key Seed |
|---|---|---|---|---|
| **Nekopoi** | `id` | Active | `7073589375897212735L` | `nekopoi/id/1` |
| **IndoHentai** | `id` | Active | `7369497329911250289L` | `indohentai/id/1` |
| **NontonHentai** | `id` | Active | `7400791378172011716L` | `nontonhentai/id/1` |
| **Hentaimama** | `en` | Active | `8058423919997530503L` | `hentaimama/en/1` |
| **JAVSubbed** | `en` | Active | `9192959167101688940L` | `javsubbed/en/1` |
| **Hanime** | `en` | Active | `2150907185602526076L` | `hanime/en/1` |
| **JavSubIndo** | `id` | Backup | `3834839640077976573L` | `javsubindo/id/1` |
| **Ohentai** | `en` | Backup | `7247837649602913976L` | `ohentai/en/1` |

---

## 3. Regression Protection

Automated test at [core/src/test/kotlin/app/kaziyami/core/SourceIdContractTest.kt](file:///e:/project/kaziyami/core/src/test/kotlin/app/kaziyami/core/SourceIdContractTest.kt) validates that re-running the hash calculation matches these exact 64-bit constants. Any deviation fails CI.
