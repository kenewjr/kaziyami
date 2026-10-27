# Permanent Source Identifiers Specification

Source IDs in Tachiyomi/Aniyomi and Kototoro are 63-bit positive integers derived from source metadata. Changing an ID invalidates user library bookmarks, categories, watch history, and tracking links.

---

## 1. Upstream Hashing Algorithm (Variant A: Aniyomi)

```kotlin
val key = "${name.lowercase()}/$lang/1"
val bytes = MessageDigest.getInstance("MD5").digest(key.toByteArray())
val id = (0..7).fold(0L) { acc, i -> (acc shl 8) or (bytes[i].toLong() and 0xFF) } and Long.MAX_VALUE
```

### Permanent Aniyomi Source ID Registry (Variant A)

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

## 2. Kototoro Hashing Algorithm & Identifiers (Variant B: Kototoro)

In Kototoro native parser plugins:
- Wire identity: `SourceRef(name: String, locale: String, contentType: String)`
- The primary string key is the enum constant name: `ContentParserSource.name` (e.g. `NEKOPOI`)
- Deterministic 64-bit integer ID for unified storage:
  ```kotlin
  val key = "kototoro:${name.lowercase()}/$lang"
  val bytes = MessageDigest.getInstance("MD5").digest(key.toByteArray())
  val id = (0..7).fold(0L) { acc, i -> (acc shl 8) or (bytes[i].toLong() and 0xFF) } and Long.MAX_VALUE
  ```

### Permanent Kototoro Source ID Registry (Variant B)

| Enum Constant | Source Title | Locale | ContentType | Kototoro ID (Long) | Key Seed |
|---|---|---|---|---|---|
| **NEKOPOI** | Nekopoi | `id` | `HENTAI_VIDEO` | `5719133896636556881L` | `kototoro:nekopoi/id` |
| **INDO_HENTAI** | IndoHentai | `id` | `HENTAI_VIDEO` | `4404885773423021844L` | `kototoro:indohentai/id` |
| **NONTON_HENTAI** | NontonHentai | `id` | `HENTAI_VIDEO` | `702783498723389045L` | `kototoro:nontonhentai/id` |
| **HENTAIMAMA** | Hentaimama | `en` | `HENTAI_VIDEO` | `4363242715237708113L` | `kototoro:hentaimama/en` |
| **JAVSUBBED** | JAVSubbed | `en` | `HENTAI_VIDEO` | `3548241241858622381L` | `kototoro:javsubbed/en` |
| **HANIME** | Hanime | `en` | `HENTAI_VIDEO` | `1681524797754884851L` | `kototoro:hanime/en` |

---

## 3. Regression Protection

Automated test at [core/src/test/kotlin/app/kaziyami/core/SourceIdContractTest.kt](file:///e:/project/kaziyami/core/src/test/kotlin/app/kaziyami/core/SourceIdContractTest.kt) validates that re-running the hash calculation for both Variant A (Aniyomi) and Variant B (Kototoro) matches these exact 64-bit constants. Any deviation fails CI.
