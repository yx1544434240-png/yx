"""
Generate an ultra-crisp, high-definition botanical floral vine decoration
specifically optimized for the home header bar.
Canvas: 220 x 64 (Aspect ratio ~3.44 : 1, perfectly matching the ~210rpx header gap).
"""
import math
import os
from PIL import Image, ImageDraw

BASE_W = 220
BASE_H = 64
SCALE = 8.0
W = int(BASE_W * SCALE)
H = int(BASE_H * SCALE)

def pt(x, y):
    return (x * SCALE, y * SCALE)

def bezier_point(p0, p1, p2, p3, t):
    x = (1-t)**3 * p0[0] + 3*(1-t)**2 * t * p1[0] + 3*(1-t) * t**2 * p2[0] + t**3 * p3[0]
    y = (1-t)**3 * p0[1] + 3*(1-t)**2 * t * p1[1] + 3*(1-t) * t**2 * p2[1] + t**3 * p3[1]
    return (x, y)

def draw_bezier_curve(draw, p0, p1, p2, p3, color, width=2.0, steps=100, fade_ends=True):
    pts = [bezier_point(p0, p1, p2, p3, i / float(steps)) for i in range(steps + 1)]
    base_r, base_g, base_b, base_a = color
    for i in range(len(pts) - 1):
        x1, y1 = pts[i]
        x2, y2 = pts[i+1]
        mid_x = (x1 + x2) / 2.0
        if fade_ends:
            # Fade towards x=10 and x=210
            dist_center = abs(mid_x - 110.0)
            norm = min(1.0, dist_center / 100.0)
            alpha = int(base_a * (1.0 - (norm ** 2) * 0.85))
        else:
            alpha = base_a
        c = (base_r, base_g, base_b, max(15, alpha))
        draw.line([pt(x1, y1), pt(x2, y2)], fill=c, width=int(width * SCALE))

def draw_leaf(draw, origin, angle_deg, length, width, fill_rgba, stroke_rgba):
    cx, cy = origin
    rad = math.radians(angle_deg)
    cos_a, sin_a = math.cos(rad), math.sin(rad)
    steps = 20
    pts_l, pts_r = [], []
    for i in range(steps + 1):
        t = i / float(steps)
        dist_x = t * length
        w_factor = math.sin(t * math.pi) * (width / 2.0)
        lx, ly = dist_x, -w_factor
        rx, ry = dist_x, w_factor
        pts_l.append(pt(cx + lx * cos_a - ly * sin_a, cy + lx * sin_a + ly * cos_a))
        pts_r.append(pt(cx + rx * cos_a - ry * sin_a, cy + rx * sin_a + ry * cos_a))
    poly = pts_l + pts_r[::-1]
    draw.polygon(poly, fill=fill_rgba)
    draw.line(poly, fill=stroke_rgba, width=int(1.2 * SCALE))
    # Midrib
    tip_x = cx + length * cos_a
    tip_y = cy + length * sin_a
    draw.line([pt(cx, cy), pt(tip_x, tip_y)], fill=stroke_rgba, width=int(0.9 * SCALE))

def draw_floret(draw, center, radius, num_petals=5, petal_color=(23, 107, 93, 75), stroke_color=(23, 107, 93, 220), center_color=(217, 119, 54, 240)):
    cx, cy = center
    for p in range(num_petals):
        angle = (p * 360.0 / num_petals) + 18
        rad = math.radians(angle)
        px = cx + math.cos(rad) * (radius * 0.85)
        py = cy + math.sin(rad) * (radius * 0.85)
        draw.ellipse([pt(px - radius*0.58, py - radius*0.58), pt(px + radius*0.58, py + radius*0.58)],
                     fill=petal_color, outline=stroke_color, width=int(1.1 * SCALE))
    # Pistil
    draw.ellipse([pt(cx - radius*0.38, cy - radius*0.38), pt(cx + radius*0.38, cy + radius*0.38)], fill=center_color)

def generate_png():
    img = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    draw = ImageDraw.Draw(img)

    # 1. 主藤蔓曲线 (Graceful S-curved vine)
    # Left half: (14, 32) -> (55, 20) -> (85, 42) -> (110, 32)
    draw_bezier_curve(draw, (14, 32), (55, 20), (85, 42), (110, 32), (23, 107, 93, 235), width=2.1)
    # Right half: (110, 32) -> (135, 22) -> (165, 44) -> (206, 32)
    draw_bezier_curve(draw, (110, 32), (135, 22), (165, 44), (206, 32), (23, 107, 93, 235), width=2.1)

    # 2. 伴生流线 (Companion lighter undulating vine)
    draw_bezier_curve(draw, (36, 32), (72, 44), (100, 20), (110, 32), (35, 125, 108, 140), width=1.3)
    draw_bezier_curve(draw, (110, 32), (120, 44), (148, 20), (184, 32), (35, 125, 108, 140), width=1.3)

    # 3. 中心主花簇 (Centerpiece Flower at 110, 32)
    draw_floret(draw, (110, 32), radius=7.2, num_petals=5,
                petal_color=(23, 107, 93, 60),
                stroke_color=(23, 107, 93, 240),
                center_color=(217, 119, 54, 255))
    # 中心花蕊高亮微圈
    draw.ellipse([pt(110-4.5, 32-4.5), pt(110+4.5, 32+4.5)], outline=(217, 119, 54, 140), width=int(0.9 * SCALE))
    
    # 中心上下舒展叶片
    draw_leaf(draw, (110, 24), -90, 15, 6.0, (23, 107, 93, 70), (23, 107, 93, 230))
    draw_leaf(draw, (110, 40), 90, 15, 6.0, (23, 107, 93, 70), (23, 107, 93, 230))

    # 4. 左侧花草族群 (Left wing botanicals)
    # 紧邻中心小叶对
    draw_leaf(draw, (96, 34), 145, 14, 5.2, (23, 107, 93, 65), (23, 107, 93, 220))
    draw_leaf(draw, (98, 38), 220, 12, 4.6, (23, 107, 93, 55), (23, 107, 93, 200))
    # 左侧小花 (at 74, 38)
    draw_floret(draw, (74, 38), radius=5.0, num_petals=4,
                petal_color=(35, 125, 108, 60),
                stroke_color=(23, 107, 93, 220),
                center_color=(217, 119, 54, 240))
    # 左侧外叶簇 (at 52, 23)
    draw_leaf(draw, (55, 23), 130, 15, 5.5, (23, 107, 93, 65), (23, 107, 93, 220))
    draw_leaf(draw, (50, 21), 60, 11, 4.2, (23, 107, 93, 50), (23, 107, 93, 190))
    # 左侧嫩梢微叶 (at 32, 33)
    draw_leaf(draw, (34, 33), 165, 12, 4.0, (23, 107, 93, 45), (23, 107, 93, 170))

    # 5. 右侧花草族群 (Right wing botanicals)
    # 紧邻中心小叶对
    draw_leaf(draw, (124, 30), 35, 14, 5.2, (23, 107, 93, 65), (23, 107, 93, 220))
    draw_leaf(draw, (122, 26), -40, 12, 4.6, (23, 107, 93, 55), (23, 107, 93, 200))
    # 右侧小花 (at 146, 26)
    draw_floret(draw, (146, 26), radius=5.0, num_petals=4,
                petal_color=(35, 125, 108, 60),
                stroke_color=(23, 107, 93, 220),
                center_color=(217, 119, 54, 240))
    # 右侧外叶簇 (at 168, 41)
    draw_leaf(draw, (165, 41), -50, 15, 5.5, (23, 107, 93, 65), (23, 107, 93, 220))
    draw_leaf(draw, (170, 43), 20, 11, 4.2, (23, 107, 93, 50), (23, 107, 93, 190))
    # 右侧嫩梢微叶 (at 188, 31)
    draw_leaf(draw, (186, 31), -15, 12, 4.0, (23, 107, 93, 45), (23, 107, 93, 170))

    # 6. 精致晨露点 (Crisp Dewdrops)
    draw.ellipse([pt(22-2.0, 32-2.0), pt(22+2.0, 32+2.0)], fill=(23, 107, 93, 150))
    draw.ellipse([pt(198-2.0, 32-2.0), pt(198+2.0, 32+2.0)], fill=(23, 107, 93, 150))
    draw.ellipse([pt(82-1.8, 18-1.8), pt(82+1.8, 18+1.8)], fill=(217, 119, 54, 180))
    draw.ellipse([pt(138-1.8, 46-1.8), pt(138+1.8, 46+1.8)], fill=(217, 119, 54, 180))
    draw.ellipse([pt(66-1.5, 48-1.5), pt(66+1.5, 48+1.5)], fill=(23, 107, 93, 110))
    draw.ellipse([pt(154-1.5, 16-1.5), pt(154+1.5, 16+1.5)], fill=(23, 107, 93, 110))

    final = img.resize((440, 128), Image.Resampling.LANCZOS)
    final.save("miniprogram/assets/icons/floral-deco.png", "PNG")
    print("New floral-deco.png (440x128) generated successfully!")

def generate_svg():
    svg_content = '''<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 220 64" fill="none">
  <defs>
    <linearGradient id="mainVineGrad" x1="0%" y1="0%" x2="100%" y2="0%">
      <stop offset="0%" stop-color="#176b5d" stop-opacity="0.12" />
      <stop offset="20%" stop-color="#176b5d" stop-opacity="0.85" />
      <stop offset="50%" stop-color="#176b5d" stop-opacity="0.95" />
      <stop offset="80%" stop-color="#176b5d" stop-opacity="0.85" />
      <stop offset="100%" stop-color="#176b5d" stop-opacity="0.12" />
    </linearGradient>
    <linearGradient id="subVineGrad" x1="0%" y1="0%" x2="100%" y2="0%">
      <stop offset="0%" stop-color="#237d6c" stop-opacity="0.08" />
      <stop offset="30%" stop-color="#237d6c" stop-opacity="0.55" />
      <stop offset="50%" stop-color="#237d6c" stop-opacity="0.65" />
      <stop offset="70%" stop-color="#237d6c" stop-opacity="0.55" />
      <stop offset="100%" stop-color="#237d6c" stop-opacity="0.08" />
    </linearGradient>
  </defs>

  <!-- 主藤蔓与伴生流线 (Graceful S-Curving Vines) -->
  <path d="M 14 32 C 55 20, 85 42, 110 32 C 135 22, 165 44, 206 32" stroke="url(#mainVineGrad)" stroke-width="2.1" stroke-linecap="round" />
  <path d="M 36 32 C 72 44, 100 20, 110 32 C 120 44, 148 20, 184 32" stroke="url(#subVineGrad)" stroke-width="1.3" stroke-linecap="round" />

  <!-- 中心主花瓣群 (Center 5-Petal Camellia/Jasmine Blossom at 110, 32) -->
  <g transform="translate(110, 32)">
    <circle cx="0" cy="-5.8" r="3.8" fill="#176b5d" fill-opacity="0.22" stroke="#176b5d" stroke-width="1.1" />
    <circle cx="5.5" cy="-1.8" r="3.8" fill="#176b5d" fill-opacity="0.22" stroke="#176b5d" stroke-width="1.1" />
    <circle cx="3.4" cy="4.7" r="3.8" fill="#176b5d" fill-opacity="0.22" stroke="#176b5d" stroke-width="1.1" />
    <circle cx="-3.4" cy="4.7" r="3.8" fill="#176b5d" fill-opacity="0.22" stroke="#176b5d" stroke-width="1.1" />
    <circle cx="-5.5" cy="-1.8" r="3.8" fill="#176b5d" fill-opacity="0.22" stroke="#176b5d" stroke-width="1.1" />
    <!-- 暖橘花蕊 (Matching Brand Orange Dot) -->
    <circle cx="0" cy="0" r="3.0" fill="#d97736" />
    <circle cx="0" cy="0" r="4.6" stroke="#d97736" stroke-width="0.8" stroke-opacity="0.45" />
  </g>

  <!-- 中心上下主叶片 (Center Vertical Leaves) -->
  <path d="M 110 24 C 104 16, 108 9, 110 9 C 112 9, 116 16, 110 24 Z" fill="#176b5d" fill-opacity="0.28" stroke="#176b5d" stroke-width="1.2" stroke-linejoin="round" />
  <line x1="110" y1="24" x2="110" y2="10" stroke="#176b5d" stroke-width="0.8" />
  <path d="M 110 40 C 104 48, 108 55, 110 55 C 112 55, 116 48, 110 40 Z" fill="#176b5d" fill-opacity="0.28" stroke="#176b5d" stroke-width="1.2" stroke-linejoin="round" />
  <line x1="110" y1="40" x2="110" y2="54" stroke="#176b5d" stroke-width="0.8" />

  <!-- 左侧花叶族群 (Left Wing Botanicals) -->
  <!-- 左紧邻叶对 -->
  <path d="M 96 34 C 86 31, 83 23, 86 21 C 89 21, 95 28, 96 34 Z" fill="#176b5d" fill-opacity="0.25" stroke="#176b5d" stroke-width="1.1" />
  <path d="M 98 38 C 91 43, 88 49, 90 51 C 92 51, 98 44, 98 38 Z" fill="#176b5d" fill-opacity="0.22" stroke="#176b5d" stroke-width="1.0" />
  <!-- 左侧小花 (at 74, 38) -->
  <g transform="translate(74, 38)">
    <circle cx="0" cy="-3.6" r="2.7" fill="#176b5d" fill-opacity="0.22" stroke="#176b5d" stroke-width="1.0" />
    <circle cx="3.6" cy="0" r="2.7" fill="#176b5d" fill-opacity="0.22" stroke="#176b5d" stroke-width="1.0" />
    <circle cx="0" cy="3.6" r="2.7" fill="#176b5d" fill-opacity="0.22" stroke="#176b5d" stroke-width="1.0" />
    <circle cx="-3.6" cy="0" r="2.7" fill="#176b5d" fill-opacity="0.22" stroke="#176b5d" stroke-width="1.0" />
    <circle cx="0" cy="0" r="2.0" fill="#d97736" />
  </g>
  <!-- 左外侧叶簇 -->
  <path d="M 55 23 C 45 20, 42 12, 45 10 C 48 10, 54 17, 55 23 Z" fill="#176b5d" fill-opacity="0.25" stroke="#176b5d" stroke-width="1.1" />
  <path d="M 50 21 C 49 14, 55 10, 57 11 C 58 13, 53 19, 50 21 Z" fill="#176b5d" fill-opacity="0.20" stroke="#176b5d" stroke-width="0.9" />
  <!-- 左嫩梢微叶 -->
  <path d="M 34 33 C 24 33, 21 28, 23 27 C 26 27, 31 31, 34 33 Z" fill="#176b5d" fill-opacity="0.20" stroke="#176b5d" stroke-width="0.9" />

  <!-- 右侧花叶族群 (Right Wing Botanicals) -->
  <!-- 右紧邻叶对 -->
  <path d="M 124 30 C 134 27, 137 19, 134 17 C 131 17, 125 24, 124 30 Z" fill="#176b5d" fill-opacity="0.25" stroke="#176b5d" stroke-width="1.1" />
  <path d="M 122 26 C 129 21, 132 15, 130 13 C 128 13, 122 20, 122 26 Z" fill="#176b5d" fill-opacity="0.22" stroke="#176b5d" stroke-width="1.0" />
  <!-- 右侧小花 (at 146, 26) -->
  <g transform="translate(146, 26)">
    <circle cx="0" cy="-3.6" r="2.7" fill="#176b5d" fill-opacity="0.22" stroke="#176b5d" stroke-width="1.0" />
    <circle cx="3.6" cy="0" r="2.7" fill="#176b5d" fill-opacity="0.22" stroke="#176b5d" stroke-width="1.0" />
    <circle cx="0" cy="3.6" r="2.7" fill="#176b5d" fill-opacity="0.22" stroke="#176b5d" stroke-width="1.0" />
    <circle cx="-3.6" cy="0" r="2.7" fill="#176b5d" fill-opacity="0.22" stroke="#176b5d" stroke-width="1.0" />
    <circle cx="0" cy="0" r="2.0" fill="#d97736" />
  </g>
  <!-- 右外侧叶簇 -->
  <path d="M 165 41 C 175 44, 178 52, 175 54 C 172 54, 166 47, 165 41 Z" fill="#176b5d" fill-opacity="0.25" stroke="#176b5d" stroke-width="1.1" />
  <path d="M 170 43 C 171 50, 165 54, 163 53 C 162 51, 167 45, 170 43 Z" fill="#176b5d" fill-opacity="0.20" stroke="#176b5d" stroke-width="0.9" />
  <!-- 右嫩梢微叶 -->
  <path d="M 186 31 C 196 31, 199 36, 197 37 C 194 37, 189 33, 186 31 Z" fill="#176b5d" fill-opacity="0.20" stroke="#176b5d" stroke-width="0.9" />

  <!-- 晶莹晨露微点 (Dewdrops) -->
  <circle cx="22" cy="32" r="2.0" fill="#176b5d" fill-opacity="0.55" />
  <circle cx="198" cy="32" r="2.0" fill="#176b5d" fill-opacity="0.55" />
  <circle cx="82" cy="18" r="1.8" fill="#d97736" fill-opacity="0.75" />
  <circle cx="138" cy="46" r="1.8" fill="#d97736" fill-opacity="0.75" />
  <circle cx="66" cy="48" r="1.5" fill="#176b5d" fill-opacity="0.45" />
  <circle cx="154" cy="16" r="1.5" fill="#176b5d" fill-opacity="0.45" />
</svg>'''
    with open("miniprogram/assets/icons/floral-deco.svg", "w", encoding="utf-8") as f:
        f.write(svg_content)
    print("New floral-deco.svg (220x64) generated successfully!")

if __name__ == "__main__":
    generate_png()
    generate_svg()
