# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [1.4.1] - 2026-10-25

### Added
- **Six Adult Anime Sources**:
  - `Kaziyami: Nekopoi` (`id`): StreamPoi Dean Edwards p.a.c.k.e.r HLS unpacker, PlayMogo, multi-mirror failover.
  - `Kaziyami: IndoHentai` (`id`): Custom SSR card grid parser, Loadvid / XXContent extractors.
  - `Kaziyami: NontonHentai` (`id`): AnimeStream base with Base64 option servers (HentaiCop, HepiDrive, LocalStream).
  - `Kaziyami: Hentaimama` (`en`): DooPlay base with AJAX token video resolution.
  - `Kaziyami: JAVSubbed` (`en`): RetroTube tube-style multi-server base with Streamtape, VOE, and DoodStream extractors.
  - `Kaziyami: Hanime` (`en`): Astro / Vue SSR direct HLS extractor with guest token authorization.
- **Multisrc Engine Bases**: `generic`, `dooplay`, `animestream`, and `retrotube`.
- **Selector Drift Protection**: Ordered primary and alternate selector evaluation with session-level caching.
- **Structured Error Handling**: `SourceParseException` and `ErrorClassifier` with clear user-facing messages.
- **Mirror Resilience**: In-memory `MirrorManager`, manual domain override preferences, and opt-in static `RemoteMirrorFetcher`.
- **Library Safety**: Relative URL storage (`/anime/slug/`) with read-time normalizer surviving domain and protocol migrations.
- **Zero Identity Leakage**: Outgoing requests scrubbed of `"kaziyami"`, `"aniyomi"`, `"tachiyomi"`, or `"extension"` headers.
- **Performance & Caching**: `TtlCache` for in-flight request deduplication and filter caching.
- **Automated CI/CD**: GitHub Actions workflows for continuous integration (`ci.yml`), automated repo publishing (`publish.yml`), scheduled live smoke testing (`smoke.yml`), and weekly structure monitoring (`monitor.yml`).
- **Development Tooling**: Source generator (`scripts/new-source`), icon builder, APK assembler and signer (`tools/build_apks.py`), and secret scanner (`tools/scan_secrets.py`).

### Security
- Automated log scrubbing of cookies, bearer tokens, and signed video stream URLs via `LogScrubber`.
- Static mirror allowlist enforcing strict HTTPS domain validation.
