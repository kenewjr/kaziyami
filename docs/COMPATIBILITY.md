# Aniyomi Extension Loader Compatibility & Invariants

This document enumerates the immutable interfaces, package naming conventions, and metadata attributes required for extension APKs to be discovered, validated, and loaded by Aniyomi and compatible forks.

---

## 1. Upstream Package Names (Must Remain Intact)

| Identifier / Package | Role | Retained Reason |
|---|---|---|
| `eu.kanade.tachiyomi.animesource.*` | Extension API Interface | Host app classloader casts loaded classes to `AnimeSource`, `AnimeHttpSource`, `ParsedAnimeHttpSource`. |
| `eu.kanade.tachiyomi.animesource.model.*` | Core Data Models | Host app consumes `SAnime`, `SEpisode`, `Video`, `AnimesPage`, `AnimeFilterList`. |
| `eu.kanade.tachiyomi.animeextension.<lang>.<name>` | Extension APK Android Package ID | Host app uses `PackageManager.getInstalledPackages` filtering for `eu.kanade.tachiyomi.animeextension.*`. |
| `tachiyomi.animeextension.class` | AndroidManifest meta-data | Points to fully-qualified entry class name for reflection instantiation. |
| `tachiyomi.animeextension.hasReadme` | AndroidManifest meta-data | Aniyomi UI flag indicating embedded README documentation. |
| `tachiyomi.animeextension.hasChangelog` | AndroidManifest meta-data | Aniyomi UI flag indicating changelog presence. |
| `tachiyomi.animeextension.nsfw` | AndroidManifest meta-data | Aniyomi content filter flag (set to `1` for adult/18+ extensions). |

---

## 2. Loader Verification Pipeline

1. **Package Discovery**:
   The host application scans installed packages matching `eu.kanade.tachiyomi.animeextension.*`. Changing this prefix hides extensions from the manager.
2. **Classloader Resolution**:
   The host creates a `PathClassLoader` for the extension APK and loads the class defined in `tachiyomi.animeextension.class`.
3. **Type Casting**:
   The loaded instance is cast to `eu.kanade.tachiyomi.animesource.AnimeSource`. Any custom package for these base interfaces results in a `ClassCastException`.
4. **Internal Modularity (Kaziyami Scope)**:
   All internal helpers (`app.kaziyami.core`, `app.kaziyami.multisrc.*`, `app.kaziyami.extractors.*`, `app.kaziyami.network`, `app.kaziyami.filters`) reside entirely within the extension DEX and are completely independent of host reflection.
