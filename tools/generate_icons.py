#!/usr/bin/env python3
"""
Generates clean 192x192 PNG icons for Kaziyami extensions using pure Python stdlib (zlib/struct).
Creates unique, distinctive logos for each extension:
- Nekopoi: Cute cat silhouette with pointed ears & 'N' monogram (Cyan)
- IndoHentai: Dynamic flame crest with 'IH' lettermark (Coral)
- NontonHentai: Video monitor screen with centered Play triangle (Orchid)
- Hentaimama: Maternal crown & heart curves (Amber)
- JAVSubbed: Video monitor display with subtitle bar badges (Sapphire)
- Hanime: Hexagonal TV crest with bold 'H' and play arrow (Pink)
- Kaziyami: Royal crowned 'K' emblem (Purple)
No external libraries (no PIL/Pillow) required.
"""

import os
import struct
import zlib

def is_glyph_pixel(name, dx, dy):
    if name == "nekopoi":
        # Cat ears
        in_left_ear = (-34 <= dx <= -8 and -40 <= dy <= -12 and (dy + 40) >= 1.2 * abs(dx + 21))
        in_right_ear = (8 <= dx <= 34 and -40 <= dy <= -12 and (dy + 40) >= 1.2 * abs(dx - 21))
        # Cat head
        in_head = ((dx / 28.0)**2 + ((dy - 4) / 22.0)**2 <= 1.0)
        # Stylized 'N' inside
        in_n_left = (-16 <= dx <= -10 and -10 <= dy <= 16)
        in_n_right = (10 <= dx <= 16 and -10 <= dy <= 16)
        in_n_diag = (abs((dy - 3) - 1.3 * dx) <= 3.5 and -12 <= dx <= 12 and -8 <= dy <= 14)
        return in_left_ear or in_right_ear or in_head or in_n_left or in_n_right or in_n_diag

    elif name == "indohentai":
        # 'IH' Monogram + Flame crest
        in_i = (-24 <= dx <= -16 and -22 <= dy <= 24)
        in_i_top = (-28 <= dx <= -12 and -26 <= dy <= -20)
        in_i_bot = (-28 <= dx <= -12 and 20 <= dy <= 26)
        in_h_l = (-6 <= dx <= 2 and -22 <= dy <= 24)
        in_h_r = (14 <= dx <= 22 and -22 <= dy <= 24)
        in_h_bar = (0 <= dx <= 16 and -4 <= dy <= 4)
        # Flame tongue above I
        in_flame = (abs(dx + 20) <= 5 * max(0.0, 1.0 - (dy + 30) / -12.0) and -42 <= dy <= -28)
        return in_i or in_i_top or in_i_bot or in_h_l or in_h_r or in_h_bar or in_flame

    elif name == "nontonhentai":
        # TV Frame + Play Triangle
        in_tv_box = (abs(dx) <= 34 and abs(dy) <= 25)
        in_tv_inner = (abs(dx) <= 29 and abs(dy) <= 20)
        in_tv_border = (in_tv_box and not in_tv_inner)
        # Antenna
        in_ant1 = (abs((dy + 32) - 0.7 * (dx + 12)) <= 2.5 and -20 <= dx <= 0 and -34 <= dy <= -25)
        in_ant2 = (abs((dy + 32) + 0.7 * (dx - 12)) <= 2.5 and 0 <= dx <= 20 and -34 <= dy <= -25)
        # Centered Play Button
        in_play = (-10 <= dx <= 16 and abs(dy) <= (16 - dx) * 0.75)
        return in_tv_border or in_ant1 or in_ant2 or in_play

    elif name == "hentaimama":
        # Crown & 'M' Heart Crest
        in_base = (-28 <= dx <= 28 and 14 <= dy <= 22)
        in_left_peak = (-26 <= dx <= -18 and -20 <= dy <= 16)
        in_mid_peak = (-5 <= dx <= 5 and -30 <= dy <= 16)
        in_right_peak = (18 <= dx <= 26 and -20 <= dy <= 16)
        in_diag_l = (abs((dy + 4) - 0.9 * (dx + 11)) <= 4.0 and -22 <= dx <= 0 and -18 <= dy <= 16)
        in_diag_r = (abs((dy + 4) + 0.9 * (dx - 11)) <= 4.0 and 0 <= dx <= 22 and -18 <= dy <= 16)
        # Heart below
        in_heart = ((dx / 12.0)**2 + ((dy + 2) / 10.0)**2 <= 1.0)
        return in_base or in_left_peak or in_mid_peak or in_right_peak or in_diag_l or in_diag_r or in_heart

    elif name == "javsubbed":
        # Monitor Frame + Subtitle Bars + 'JS'
        in_screen = (abs(dx) <= 34 and -26 <= dy <= 14)
        in_inner = (abs(dx) <= 29 and -22 <= dy <= 10)
        in_screen_border = (in_screen and not in_inner)
        # Stand
        in_stand = (abs(dx) <= 6 and 14 <= dy <= 22)
        in_base = (abs(dx) <= 20 and 22 <= dy <= 26)
        # Subtitle bars inside
        in_sub1 = (abs(dx) <= 22 and 0 <= dy <= 4)
        in_sub2 = (abs(dx) <= 14 and 6 <= dy <= 9)
        # Monogram 'JS'
        in_j = (-16 <= dx <= -10 and -18 <= dy <= -4)
        in_s = (10 <= dx <= 16 and -18 <= dy <= -4)
        return in_screen_border or in_stand or in_base or in_sub1 or in_sub2 or in_j or in_s

    elif name == "hanime":
        # Hexagonal crest + 'H' + Play triangle
        in_hex = (abs(dx) * 0.866 + abs(dy) * 0.5 <= 36 and abs(dy) <= 34)
        in_hex_in = (abs(dx) * 0.866 + abs(dy) * 0.5 <= 31 and abs(dy) <= 29)
        in_hex_border = (in_hex and not in_hex_in)
        # Bold 'H'
        in_h_left = (-18 <= dx <= -10 and -20 <= dy <= 20)
        in_h_right = (10 <= dx <= 18 and -20 <= dy <= 20)
        in_h_bar = (-10 <= dx <= 10 and -4 <= dy <= 4)
        # Play triangle
        in_play = (-4 <= dx <= 8 and abs(dy) <= (8 - dx) * 0.7)
        return in_hex_border or in_h_left or in_h_right or in_h_bar or in_play

    elif name == "hanime1":
        # 'H1' Monogram
        in_h_left = (-22 <= dx <= -14 and -20 <= dy <= 20)
        in_h_right = (-4 <= dx <= 4 and -20 <= dy <= 20)
        in_h_bar = (-14 <= dx <= -4 and -4 <= dy <= 4)
        in_1_stem = (14 <= dx <= 22 and -20 <= dy <= 20)
        in_1_serif = (8 <= dx <= 14 and -20 <= dy <= -12 and (dy + 20) >= (14 - dx))
        in_1_base = (8 <= dx <= 28 and 14 <= dy <= 20)
        return in_h_left or in_h_right or in_h_bar or in_1_stem or in_1_serif or in_1_base

    elif name == "3xchina":
        # '3X' Emblem
        in_3_top = (-24 <= dx <= -6 and -20 <= dy <= -12)
        in_3_mid = (-16 <= dx <= -6 and -4 <= dy <= 4)
        in_3_bot = (-24 <= dx <= -6 and 12 <= dy <= 20)
        in_3_spine = (-10 <= dx <= -4 and ((-20 <= dy <= -4) or (0 <= dy <= 20)))
        in_x_diag1 = (abs(dy - dx + 12) <= 3.5 and 0 <= dx <= 24 and -16 <= dy <= 16)
        in_x_diag2 = (abs(dy + dx - 12) <= 3.5 and 0 <= dx <= 24 and -16 <= dy <= 16)
        return in_3_top or in_3_mid or in_3_bot or in_3_spine or in_x_diag1 or in_x_diag2

    else:
        # Default / Kaziyami Crowned 'K'
        in_stem = (-18 <= dx <= -8 and -28 <= dy <= 28)
        in_arm1 = (abs(dy + 12 - (dx + 10) * 1.1) <= 5.0 and -10 <= dx <= 20 and -28 <= dy <= 0)
        in_arm2 = (abs(dy - 12 + (dx + 10) * 1.1) <= 5.0 and -10 <= dx <= 20 and 0 <= dy <= 28)
        in_star = (abs(dx) <= 5 and -36 <= dy <= -30) or (abs(dx + 20) <= 4 and -34 <= dy <= -28) or (abs(dx - 20) <= 4 and -34 <= dy <= -28)
        return in_stem or in_arm1 or in_arm2 or in_star

def create_png(width, height, rgb_bg, rgb_accent, name="kaziyami"):
    raw_data = bytearray()
    center_x = width // 2
    center_y = height // 2
    radius = int(width * 0.42)
    ring_outer = radius * radius
    ring_inner = int(radius * 0.90) * int(radius * 0.90)

    for y in range(height):
        raw_data.append(0)  # filter type 0 (None)
        for x in range(width):
            dx = x - center_x
            dy = y - center_y
            dist_sq = dx * dx + dy * dy

            if ring_inner <= dist_sq <= ring_outer:
                raw_data.extend(rgb_accent)
            elif is_glyph_pixel(name, dx, dy):
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
    "rulevid": (76, 175, 80),        # Emerald Green
    "rule34video": (255, 110, 64),   # Deep Orange
    "hanime1": (255, 46, 147),       # Rose Pink
    "3xchina": (255, 179, 0),        # Gold / Amber
    "kaziyami": (156, 39, 176)       # Purple (Brand)
}

EXT_PACKAGES = {
    "nekopoi": "eu.kanade.tachiyomi.animeextension.id.nekopoi",
    "indohentai": "eu.kanade.tachiyomi.animeextension.id.indohentai",
    "nontonhentai": "eu.kanade.tachiyomi.animeextension.id.nontonhentai",
    "hentaimama": "eu.kanade.tachiyomi.animeextension.en.hentaimama",
    "javsubbed": "eu.kanade.tachiyomi.animeextension.en.javsubbed",
    "hanime": "eu.kanade.tachiyomi.animeextension.en.hanime",
    "rulevid": "eu.kanade.tachiyomi.animeextension.en.rulevid",
    "rule34video": "eu.kanade.tachiyomi.animeextension.en.rule34video",
    "hanime1": "eu.kanade.tachiyomi.animeextension.zh.hanime1",
    "3xchina": "eu.kanade.tachiyomi.animeextension.zh.threexchina",
    "kaziyami": "app.kaziyami.kototoro",
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

    art_icons_dir = os.path.join(root_dir, "art", "icons")

    for name, accent in EXT_COLORS.items():
        art_src = os.path.join(art_icons_dir, f"{name}.png")
        if os.path.exists(art_src) and name != "kaziyami":
            with open(art_src, "rb") as f:
                png_bytes = f.read()
            source_type = "authentic"
        else:
            png_bytes = create_png(192, 192, bg_color, accent, name=name)
            source_type = "procedural"

        for d in icon_dirs:
            target = os.path.join(d, f"{name}.png")
            if os.path.abspath(target) != os.path.abspath(art_src) or source_type == "procedural":
                with open(target, "wb") as f:
                    f.write(png_bytes)
            if name in EXT_PACKAGES:
                pkg_target = os.path.join(d, f"{EXT_PACKAGES[name]}.png")
                with open(pkg_target, "wb") as f:
                    f.write(png_bytes)
        print(f"[+] Synced {source_type} 192x192 logo icon: {name}.png")

if __name__ == "__main__":
    main()
