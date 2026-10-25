# Kototoro Compatibility & Architecture Evaluation

## 1. Executive Finding
**Kototoro does NOT load Aniyomi-style Android extension APKs.**
Building or maintaining a second architecture for Kototoro within this repository is out of scope and contrary to maintaining a lean Aniyomi multisrc repository.

---

## 2. Technical Architectural Comparison

| Dimension | Aniyomi / Tachiyomi Fork Ecosystem | Kototoro (Kotatsu Fork) |
|---|---|---|
| **Extension Mechanism** | External standalone APKs (`.apk`) installed via Android PackageManager. | Internal in-app compiled parsers or dynamically fetched script bundles. |
| **Execution Sandbox** | Separate process/classloader (`PathClassLoader`) loading `tachiyomi.animeextension.class`. | App internal process execution via bundled Kotlin parser classes or JavaScript/Lua runtime. |
| **Source Interface** | `eu.kanade.tachiyomi.animesource.AnimeHttpSource` | Kotatsu `MangaParser` / `AnimeParser` domain model interfaces. |
| **Repository Index** | Custom `index.min.json` + `repo.json` listing APK URLs and hashes. | Parsers registry built directly into application releases or parser JSON lists. |

---

## 3. Scope of a Separate Kototoro Port
Porting Kaziyami sources to Kototoro would require:
1. Re-writing all 6 source scrapers to conform to Kotatsu's parser API (`MangaParser`/`AnimeParser`) rather than `AnimeHttpSource`.
2. Porting stream extraction logic into Kotatsu's media pipeline.
3. Submitting pull requests directly to the upstream Kototoro repository or maintaining a bespoke fork of the Kototoro Android app itself.

**Decision**: Continue targeting Aniyomi and direct Aniyomi forks using the established extension APK standard.
