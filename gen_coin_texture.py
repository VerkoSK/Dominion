"""
Generates a 16x16 RGBA PNG texture for the NationsForge Nation Coin item.
Run once to produce:
  src/main/resources/assets/nationsforge/textures/item/nation_coin.png
"""
import struct, zlib, math, os


# ── PNG writer ───────────────────────────────────────────────────────────────

def write_png(filename, width, height, pixels):
    """pixels: list[list[(r,g,b,a)]] — row-major, top to bottom."""

    def chunk(tag, data):
        crc = zlib.crc32(tag + data) & 0xFFFFFFFF
        return struct.pack('>I', len(data)) + tag + data + struct.pack('>I', crc)

    raw = b''
    for row in pixels:
        raw += b'\x00'                          # filter = None
        for r, g, b, a in row:
            raw += bytes([r, g, b, a])

    ihdr = struct.pack('>IIBBBBB', width, height, 8, 6, 0, 0, 0)
    png  = b'\x89PNG\r\n\x1a\n'
    png += chunk(b'IHDR', ihdr)
    png += chunk(b'IDAT', zlib.compress(raw, 9))
    png += chunk(b'IEND', b'')

    os.makedirs(os.path.dirname(filename), exist_ok=True)
    with open(filename, 'wb') as f:
        f.write(png)


# ── Color helpers ────────────────────────────────────────────────────────────

def lerp(a, b, t):
    return tuple(max(0, min(255, int(a[i] + (b[i] - a[i]) * t))) for i in range(4))

def clamp255(v):
    return max(0, min(255, int(v)))


# ── Coin design ──────────────────────────────────────────────────────────────
#
#  16×16 gold coin with:
#   • Circular shape with a thin dark outline
#   • Radial + directional gradient (lighter top-left, darker bottom-right)
#   • Inner engraved ring at ~r=5
#   • Crown symbol: 3 prongs (cols 5,7,9 rows 5-6) + solid band (rows 7-9)

TRANSP       = (  0,   0,   0,   0)
OUTLINE      = ( 60,  32,   0, 255)   # near-black brown edge
RIM_OUTER    = (108,  62,   4, 255)   # darker area just inside outline
RIM_INNER    = (158,  98,  12, 255)   # inner coin rim
RING_GROOVE  = (120,  72,   8, 255)   # engraved ring groove
GOLD_DARK    = (190, 120,  20, 255)
GOLD_MID     = (225, 160,  38, 255)
GOLD         = (248, 192,  52, 255)
GOLD_LIGHT   = (255, 212,  78, 255)
GOLD_HI      = (255, 230, 120, 255)
WHITE_SPOT   = (255, 248, 195, 255)

CROWN_SHADOW = ( 88,  44,   0, 255)
CROWN_DARK   = (130,  76,   6, 255)
CROWN_MID    = (172, 108,  18, 255)
CROWN_LIGHT  = (208, 148,  35, 255)


def coin_base_color(x, y, cx, cy, R):
    """Return the gold gradient colour for a point inside the coin."""
    dx, dy = x - cx, y - cy
    dist = math.sqrt(dx*dx + dy*dy)

    # Directional light from top-left (normal map fake)
    dir_t = 0.5 + 0.5 * (-dx - dy) / (R * math.sqrt(2))
    dir_t = max(0.0, min(1.0, dir_t))

    # Radial falloff (edges darker)
    rad_t = 1.0 - (dist / R)

    t = 0.55 * rad_t + 0.45 * dir_t  # blend

    if   t < 0.20: return lerp(GOLD_DARK,  GOLD_MID,   t / 0.20)
    elif t < 0.45: return lerp(GOLD_MID,   GOLD,       (t - 0.20) / 0.25)
    elif t < 0.68: return lerp(GOLD,       GOLD_LIGHT, (t - 0.45) / 0.23)
    elif t < 0.85: return lerp(GOLD_LIGHT, GOLD_HI,    (t - 0.68) / 0.17)
    else:          return lerp(GOLD_HI,    WHITE_SPOT,  (t - 0.85) / 0.15)


def crown_color(x, y, cx, cy, R):
    """Shaded crown colour — same light direction as coin body."""
    dx, dy = x - cx, y - cy
    dir_t = 0.5 + 0.5 * (-dx - dy) / (R * math.sqrt(2))
    dir_t = max(0.0, min(1.0, dir_t))
    if   dir_t > 0.70: return CROWN_LIGHT
    elif dir_t > 0.45: return CROWN_MID
    elif dir_t > 0.25: return CROWN_DARK
    else:              return CROWN_SHADOW


def make_nation_coin():
    SIZE = 16
    px = [[TRANSP] * SIZE for _ in range(SIZE)]
    cx, cy = 7.5, 7.5

    R      = 7.3   # outer usable radius (inside 16×16 grid)
    R_RIM  = 1.4   # rim band thickness (from edge inward)
    R_RING = 5.2   # engraved inner ring radius
    R_RING_W = 0.55

    for y in range(SIZE):
        for x in range(SIZE):
            dx, dy = x - cx, y - cy
            dist = math.sqrt(dx*dx + dy*dy)

            if dist > R:
                px[y][x] = TRANSP
                continue

            # ── Outline shell
            if dist > R - 0.65:
                px[y][x] = OUTLINE
                continue

            # ── Outer rim
            if dist > R - R_RIM:
                t = (dist - (R - R_RIM)) / (R_RIM - 0.65)
                t = max(0.0, min(1.0, t))
                px[y][x] = lerp(RIM_INNER, RIM_OUTER, t)
                continue

            # ── Engraved ring groove
            if abs(dist - R_RING) < R_RING_W:
                # subtle darkening groove
                groove_depth = 1.0 - abs(dist - R_RING) / R_RING_W
                base = coin_base_color(x, y, cx, cy, R)
                px[y][x] = lerp(base, RING_GROOVE, groove_depth * 0.55)
                continue

            # ── Inner coin face — gold gradient
            px[y][x] = coin_base_color(x, y, cx, cy, R)

    # ── Crown symbol  ─────────────────────────────────────────────────────
    # 3-prong crown, 5 px wide (cols 5-9), rows 5-9
    # Prongs at cols 5, 7, 9  rows 5-6
    # Band  at cols 5-9        rows 7-9  (hollow middle on row 8 sides only)

    crown_mask = set()

    # Prongs
    for r in range(5, 7):
        for c in (5, 7, 9):
            crown_mask.add((c, r))

    # Band top, middle, bottom rows
    for r in range(7, 10):
        for c in range(5, 10):
            crown_mask.add((c, r))

    for (cx_c, cy_c) in crown_mask:
        if 0 <= cx_c < SIZE and 0 <= cy_c < SIZE:
            dx, dy = cx_c - cx, cy_c - cy
            if math.sqrt(dx*dx + dy*dy) <= R - R_RIM + 0.5:  # only inside rim
                px[cy_c][cx_c] = crown_color(cx_c, cy_c, cx, cy, R)

    return px


# ── Entry point ──────────────────────────────────────────────────────────────

if __name__ == '__main__':
    HERE = os.path.dirname(os.path.abspath(__file__))
    out  = os.path.join(
        HERE,
        'src', 'main', 'resources', 'assets', 'nationsforge',
        'textures', 'item', 'nation_coin.png'
    )
    pixels = make_nation_coin()
    write_png(out, 16, 16, pixels)
    print(f'[nation_coin] Written 16×16 RGBA PNG → {out}')
