#!/usr/bin/env python3
"""
Generates clean 192x192 PNG icons for Kaziyami extensions using pure Python stdlib (zlib/struct).
No external libraries (no PIL/Pillow) required.
"""

import os
import struct
import zlib

def create_png(width, height, rgb_bg, rgb_accent, glyph="K"):
    # Generate image buffer
    raw_data = bytearray()
    center_x = width // 2
    center_y = height // 2
    radius = int(width * 0.40)

    for y in range(height):
        raw_data.append(0)  # filter type 0 (None)
        for x in range(width):
            dx = x - center_x
            dy = y - center_y
            dist_sq = dx * dx + dy * dy

            # Outer ring
            ring_outer = radius * radius
            ring_inner = int(radius * 0.88) * int(radius * 0.88)

            # Stylized K glyph rough bounds
            in_k_stem = (abs(x - (center_x - 14)) <= 5 and abs(y - center_y) <= 32)
            in_k_arm1 = (abs(y - (center_y - 12) + (x - center_x) * 0.9) <= 6 and 0 <= (x - center_x) <= 24)
            in_k_arm2 = (abs(y - (center_y + 12) - (x - center_x) * 0.9) <= 6 and 0 <= (x - center_x) <= 24)

            if ring_inner <= dist_sq <= ring_outer:
                raw_data.extend(rgb_accent)
            elif in_k_stem or in_k_arm1 or in_k_arm2:
                raw_data.extend(rgb_accent)
            else:
                raw_data.extend(rgb_bg)

    def chunk(tag, data):
        return (
            struct.pack(">I", len(data)) +
            tag +
            data +
            struct.pack(">I", zlib.crc32(tag + data) & 0xffffffff)
        )

    # IHDR
    ihdr_data = struct.pack(">IIBBBBB", width, height, 8, 2, 0, 0, 0)
    ihdr = chunk(b"IHDR", ihdr_data)

    # IDAT
    compressed = zlib.compress(bytes(raw_data), 9)
    idat = chunk(b"IDAT", compressed)

    # IEND
    iend = chunk(b"IEND", b"")

    return b"\x89PNG\r\n\x1a\n" + ihdr + idat + iend

EXT_COLORS = {
    "nekopoi": (0, 229, 255),       # Cyan
    "indohentai": (255, 82, 82),     # Coral
    "nontonhentai": (186, 104, 200), # Orchid
    "hentaimama": (255, 167, 38),    # Amber
    "javsubbed": (41, 121, 255),     # Sapphire
    "hanime": (240, 98, 146),        # Pink
    "kaziyami": (156, 39, 176)       # Purple (Brand)
}

EXT_PACKAGES = {
    "nekopoi": "eu.kanade.tachiyomi.animeextension.id.nekopoi",
    "indohentai": "eu.kanade.tachiyomi.animeextension.id.indohentai",
    "nontonhentai": "eu.kanade.tachiyomi.animeextension.id.nontonhentai",
    "hentaimama": "eu.kanade.tachiyomi.animeextension.en.hentaimama",
    "javsubbed": "eu.kanade.tachiyomi.animeextension.en.javsubbed",
    "hanime": "eu.kanade.tachiyomi.animeextension.en.hanime",
}

def main():
    root_dir = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
    icon_dirs = [
        os.path.join(root_dir, "repo", "icon"),
        os.path.join(root_dir, "art", "icons")
    ]
    for d in icon_dirs:
        os.makedirs(d, exist_ok=True)

    bg_color = (18, 19, 26)  # Dark theme #12131A

    for name, accent in EXT_COLORS.items():
        png_bytes = create_png(192, 192, bg_color, accent)
        for d in icon_dirs:
            target = os.path.join(d, f"{name}.png")
            with open(target, "wb") as f:
                f.write(png_bytes)
            if name in EXT_PACKAGES:
                pkg_target = os.path.join(d, f"{EXT_PACKAGES[name]}.png")
                with open(pkg_target, "wb") as f:
                    f.write(png_bytes)
        print(f"[+] Generated 192x192 icon: {name}.png")

if __name__ == "__main__":
    main()
