"""
Script to generate ultra-high precision, anti-aliased role line arts for user profile header card.
Outputs:
- miniprogram/assets/icons/role-line-resident.png (.svg)
- miniprogram/assets/icons/role-line-staff.png (.svg)
- miniprogram/assets/icons/role-line-admin.png (.svg)
- miniprogram/assets/icons/role-line-guest.png (.svg)
"""
import math
import os
from PIL import Image, ImageDraw

BASE_W = 280
BASE_H = 160
SCALE = 4.0
W = int(BASE_W * SCALE)
H = int(BASE_H * SCALE)

OUT_DIR = "miniprogram/assets/icons"
os.makedirs(OUT_DIR, exist_ok=True)

def pt(x, y):
    return (x * SCALE, y * SCALE)

def bezier_point(p0, p1, p2, p3, t):
    x = (1-t)**3 * p0[0] + 3*(1-t)**2 * t * p1[0] + 3*(1-t) * t**2 * p2[0] + t**3 * p3[0]
    y = (1-t)**3 * p0[1] + 3*(1-t)**2 * t * p1[1] + 3*(1-t) * t**2 * p2[1] + t**3 * p3[1]
    return (x, y)

def draw_bezier_curve(draw, p0, p1, p2, p3, color, width=1.8, steps=80, alpha_fade_start=0.0):
    pts = [bezier_point(p0, p1, p2, p3, i / float(steps)) for i in range(steps + 1)]
    base_r, base_g, base_b, base_a = color
    for i in range(len(pts) - 1):
        x1, y1 = pts[i]
        x2, y2 = pts[i+1]
        t = i / float(steps)
        if t < alpha_fade_start:
            curr_a = int(base_a * (t / max(0.01, alpha_fade_start)))
        else:
            curr_a = base_a
        c = (base_r, base_g, base_b, max(5, curr_a))
        draw.line([pt(x1, y1), pt(x2, y2)], fill=c, width=int(width * SCALE))

def draw_smooth_leaf(draw, origin, angle_deg, length, width, fill_rgba, stroke_rgba):
    cx, cy = origin
    rad = math.radians(angle_deg)
    cos_a, sin_a = math.cos(rad), math.sin(rad)
    
    steps = 24
    pts_left = []
    pts_right = []
    for i in range(steps + 1):
        t = i / float(steps)
        dist_x = t * length
        w_factor = math.sin(t * math.pi) * (width / 2.0)
        
        # Left side
        lx = dist_x
        ly = -w_factor
        rx = dist_x
        ry = w_factor
        
        glx = cx + lx * cos_a - ly * sin_a
        gly = cy + lx * sin_a + ly * cos_a
        grx = cx + rx * cos_a - ry * sin_a
        gry = cy + rx * sin_a + ry * cos_a
        
        pts_left.append(pt(glx, gly))
        pts_right.append(pt(grx, gry))
        
    poly = pts_left + pts_right[::-1]
    draw.polygon(poly, fill=fill_rgba)
    draw.line(poly, fill=stroke_rgba, width=int(1.0 * SCALE))
    
    # Midrib vein
    tip_x = cx + length * cos_a
    tip_y = cy + length * sin_a
    draw.line([pt(cx, cy), pt(tip_x, tip_y)], fill=stroke_rgba, width=int(0.8 * SCALE))

def draw_floret(draw, center, radius, num_petals=5, petal_color=(23, 107, 93, 110), center_color=(217, 119, 54, 230)):
    cx, cy = center
    for p in range(num_petals):
        angle = (p * 360.0 / num_petals)
        rad = math.radians(angle)
        px = cx + math.cos(rad) * (radius * 0.8)
        py = cy + math.sin(rad) * (radius * 0.8)
        draw.ellipse([pt(px - radius*0.5, py - radius*0.5), pt(px + radius*0.5, py + radius*0.5)],
                     fill=petal_color, outline=(23, 107, 93, 160), width=int(0.9 * SCALE))
    draw.ellipse([pt(cx - radius*0.35, cy - radius*0.35), pt(cx + radius*0.35, cy + radius*0.35)], fill=center_color)

# ==============================================================================
# 1. RESIDENT: 银杏福叶、温馨屋檐、舒展花藤与闲适茶韵
# ==============================================================================
def make_resident_line():
    img = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    draw = ImageDraw.Draw(img)

    # 屋檐双弧 (Home Roof Arcs)
    p0, p1, p2, p3 = (100, 68), (150, 42), (185, 42), (245, 78)
    draw_bezier_curve(draw, p0, p1, p2, p3, (23, 107, 93, 180), width=1.8, alpha_fade_start=0.25)
    p4, p5, p6, p7 = (120, 78), (155, 56), (185, 56), (235, 88)
    draw_bezier_curve(draw, p4, p5, p6, p7, (35, 125, 108, 110), width=1.2, alpha_fade_start=0.3)

    # 升腾暖茶香韵微波 (Rising Warmth Steams)
    s0, s1, s2, s3 = (170, 40), (166, 26), (176, 16), (170, 6)
    draw_bezier_curve(draw, s0, s1, s2, s3, (217, 119, 54, 180), width=1.1)
    s4, s5, s6, s7 = (182, 38), (178, 28), (186, 20), (182, 10)
    draw_bezier_curve(draw, s4, s5, s6, s7, (217, 119, 54, 130), width=1.0)

    # 主生机藤蔓 (Main organic vine across lower-right)
    v0, v1, v2, v3 = (40, 132), (90, 145), (145, 100), (210, 122)
    draw_bezier_curve(draw, v0, v1, v2, v3, (23, 107, 93, 160), width=1.7, alpha_fade_start=0.35)
    v4, v5, v6, v7 = (210, 122), (235, 130), (255, 120), (272, 110)
    draw_bezier_curve(draw, v4, v5, v6, v7, (23, 107, 93, 130), width=1.4)

    # 辅生藤条
    sv0, sv1, sv2, sv3 = (75, 135), (120, 110), (160, 135), (225, 115)
    draw_bezier_curve(draw, sv0, sv1, sv2, sv3, (35, 125, 108, 95), width=1.1, alpha_fade_start=0.3)

    # 康养银杏叶 (Ginkgo Biloba Leaf - Large) at (195, 88)
    # 叶柄 (Petiole)
    draw_bezier_curve(draw, (185, 110), (188, 102), (192, 95), (195, 88), (23, 107, 93, 190), width=1.4)
    # 银杏扇形冠 (Fan blade with central notch)
    g_origin = (195, 88)
    fan_angles = [-68, -50, -32, -16, 0, 16, 32, 50, 68]
    outer_pts = []
    base_len = 26.0
    for ang in fan_angles:
        rad = math.radians(ang - 45) # pointing upper right
        # notch at middle (ang near 0)
        notch = 0.82 if abs(ang) < 10 else 1.0
        r = base_len * notch
        gx = g_origin[0] + math.cos(rad) * r
        gy = g_origin[1] + math.sin(rad) * r
        outer_pts.append((gx, gy))
        # Draw delicate radiant vein lines
        draw.line([pt(g_origin[0], g_origin[1]), pt(gx, gy)], fill=(35, 125, 108, 100), width=int(0.8 * SCALE))

    # Leaf fan outline polygon
    poly_pts = [pt(g_origin[0], g_origin[1])] + [pt(x, y) for x, y in outer_pts] + [pt(g_origin[0], g_origin[1])]
    draw.polygon(poly_pts, fill=(23, 107, 93, 40))
    for i in range(len(outer_pts) - 1):
        draw.line([pt(outer_pts[i][0], outer_pts[i][1]), pt(outer_pts[i+1][0], outer_pts[i+1][1])],
                  fill=(23, 107, 93, 180), width=int(1.2 * SCALE))

    # 伴生小银杏叶 at (228, 105)
    draw_bezier_curve(draw, (222, 120), (225, 114), (227, 108), (228, 105), (23, 107, 93, 160), width=1.1)
    g2_origin = (228, 105)
    fan2_angles = [-55, -30, 0, 30, 55]
    outer2_pts = []
    for ang in fan2_angles:
        rad = math.radians(ang - 60)
        r = 16.0 * (0.85 if abs(ang) < 10 else 1.0)
        gx = g2_origin[0] + math.cos(rad) * r
        gy = g2_origin[1] + math.sin(rad) * r
        outer2_pts.append((gx, gy))
        draw.line([pt(g2_origin[0], g2_origin[1]), pt(gx, gy)], fill=(35, 125, 108, 80), width=int(0.7 * SCALE))
    poly2_pts = [pt(g2_origin[0], g2_origin[1])] + [pt(x, y) for x, y in outer2_pts] + [pt(g2_origin[0], g2_origin[1])]
    draw.polygon(poly2_pts, fill=(23, 107, 93, 35))
    for i in range(len(outer2_pts) - 1):
        draw.line([pt(outer2_pts[i][0], outer2_pts[i][1]), pt(outer2_pts[i+1][0], outer2_pts[i+1][1])],
                  fill=(23, 107, 93, 150), width=int(1.0 * SCALE))

    # 舒展绿叶簇 (Leaves along vines)
    draw_smooth_leaf(draw, (115, 130), -50, 16, 6.0, (23, 107, 93, 90), (23, 107, 93, 180))
    draw_smooth_leaf(draw, (140, 108), 35, 14, 5.5, (23, 107, 93, 85), (23, 107, 93, 175))
    draw_smooth_leaf(draw, (168, 122), -35, 13, 5.0, (23, 107, 93, 80), (23, 107, 93, 165))
    draw_smooth_leaf(draw, (248, 124), -70, 12, 4.5, (23, 107, 93, 75), (23, 107, 93, 155))

    # 微绽小花 (2 Florets with warm orange pistil)
    draw_floret(draw, (126, 116), radius=5.0, num_petals=5, petal_color=(35, 125, 108, 100), center_color=(217, 119, 54, 230))
    draw_floret(draw, (215, 128), radius=4.2, num_petals=5, petal_color=(35, 125, 108, 90), center_color=(217, 119, 54, 210))

    # 晶莹露珠 (Dewdrops)
    draw.ellipse([pt(92-2, 142-2), pt(92+2, 142+2)], fill=(23, 107, 93, 110))
    draw.ellipse([pt(155-1.6, 96-1.6), pt(155+1.6, 96+1.6)], fill=(217, 119, 54, 150))
    draw.ellipse([pt(265-1.5, 112-1.5), pt(265+1.5, 112+1.5)], fill=(23, 107, 93, 80))

    final = img.resize((560, 320), Image.Resampling.LANCZOS)
    final.save(f"{OUT_DIR}/role-line-resident.png", "PNG")
    print("role-line-resident.png created")

# ==============================================================================
# 2. STAFF: 守护之手、生机脉搏波、爱心与敏捷风翼
# ==============================================================================
def make_staff_line():
    img = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    draw = ImageDraw.Draw(img)

    # 守护之手优美托举双弧 (Caring Palms Foundation)
    h0, h1, h2, h3 = (110, 145), (145, 152), (185, 142), (240, 118)
    draw_bezier_curve(draw, h0, h1, h2, h3, (22, 122, 104, 185), width=2.0, alpha_fade_start=0.3)
    h4, h5, h6, h7 = (135, 155), (170, 158), (205, 148), (255, 128)
    draw_bezier_curve(draw, h4, h5, h6, h7, (35, 145, 125, 115), width=1.3, alpha_fade_start=0.35)

    # 生机脉搏波折线 (Vitality ECG Pulse Line)
    # Smooth entry from left, pulse in middle, smooth exit to right
    ecg_pts = [
        (35, 82), (65, 82), (85, 82),
        (95, 76), (103, 82),        # P wave
        (110, 82), (115, 89),       # Q valley
        (124, 38),                  # R peak (sharp vital spike)
        (133, 96),                  # S valley
        (142, 82),
        (154, 72), (166, 82),       # T wave
        (185, 82), (205, 82), (240, 82), (268, 82)
    ]
    for i in range(len(ecg_pts) - 1):
        x1, y1 = ecg_pts[i]
        x2, y2 = ecg_pts[i+1]
        mid_x = (x1 + x2) / 2.0
        # Fade at extreme left
        alpha = int(min(1.0, mid_x / 80.0) * 190)
        draw.line([pt(x1, y1), pt(x2, y2)], fill=(22, 122, 104, max(15, alpha)), width=int(1.8 * SCALE))

    # R 峰顶端光斑
    draw.ellipse([pt(124-2.8, 38-2.8), pt(124+2.8, 38+2.8)], fill=(217, 119, 54, 230))
    draw.ellipse([pt(124-5, 38-5), pt(124+5, 38+5)], outline=(217, 119, 54, 100), width=int(1.0 * SCALE))

    # 守护爱心 (Heart of Compassion) at (175, 48)
    # Parametric heart curve
    heart_cx, heart_cy = (175, 52)
    heart_scale = 1.6
    heart_pts = []
    for t_deg in range(0, 360, 5):
        t_rad = math.radians(t_deg)
        # Standard parametric heart
        hx = 16 * (math.sin(t_rad) ** 3)
        hy = -(13 * math.cos(t_rad) - 5 * math.cos(2*t_rad) - 2 * math.cos(3*t_rad) - math.cos(4*t_rad))
        heart_pts.append(pt(heart_cx + hx * heart_scale * 0.58, heart_cy + hy * heart_scale * 0.58))
    
    draw.polygon(heart_pts, fill=(217, 119, 54, 38))
    for i in range(len(heart_pts)):
        p_start = heart_pts[i]
        p_end = heart_pts[(i + 1) % len(heart_pts)]
        draw.line([p_start, p_end], fill=(217, 119, 54, 220), width=int(1.5 * SCALE))

    # 爱心核心柔光
    draw.ellipse([pt(heart_cx-3, heart_cy-3), pt(heart_cx+3, heart_cy+3)], fill=(217, 119, 54, 200))

    # 敏捷上门微风弧翼 (Swift Service Response Wind Arcs)
    w0, w1, w2, w3 = (195, 24), (220, 20), (242, 28), (268, 38)
    draw_bezier_curve(draw, w0, w1, w2, w3, (35, 145, 125, 150), width=1.4)
    w4, w5, w6, w7 = (212, 38), (234, 36), (252, 45), (272, 54)
    draw_bezier_curve(draw, w4, w5, w6, w7, (35, 145, 125, 120), width=1.2)
    w8, w9, w10, w11 = (226, 54), (244, 54), (258, 62), (275, 70)
    draw_bezier_curve(draw, w8, w9, w10, w11, (35, 145, 125, 90), width=1.0)

    # 照护绿叶与萌芽 (Sprout of Care) at bottom of caring palm
    draw_smooth_leaf(draw, (238, 118), -65, 14, 5.0, (22, 122, 104, 85), (22, 122, 104, 180))
    draw_smooth_leaf(draw, (250, 112), -25, 12, 4.5, (22, 122, 104, 75), (22, 122, 104, 160))

    # 守护微芒
    draw.ellipse([pt(154-1.8, 72-1.8), pt(154+1.8, 72+1.8)], fill=(22, 122, 104, 140))
    draw.ellipse([pt(202-1.5, 48-1.5), pt(202+1.5, 48+1.5)], fill=(217, 119, 54, 160))

    final = img.resize((560, 320), Image.Resampling.LANCZOS)
    final.save(f"{OUT_DIR}/role-line-staff.png", "PNG")
    print("role-line-staff.png created")

# ==============================================================================
# 3. ADMIN: 智慧中枢、网格互联、六边形护盾与同心轨道
# ==============================================================================
def make_admin_line():
    img = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    draw = ImageDraw.Draw(img)

    # 六边形数智护盾中枢 (Hexagonal Shield Matrix) at (195, 78)
    hx_c = (195, 78)
    r_hex = 32.0
    hex_pts = []
    for i in range(6):
        ang_deg = i * 60 + 30
        rad = math.radians(ang_deg)
        x = hx_c[0] + r_hex * math.cos(rad)
        y = hx_c[1] + r_hex * math.sin(rad)
        hex_pts.append((x, y))

    # Fill and outer stroke
    draw.polygon([pt(x, y) for x, y in hex_pts], fill=(16, 82, 70, 35))
    for i in range(6):
        p_start = hex_pts[i]
        p_end = hex_pts[(i+1)%6]
        draw.line([pt(p_start[0], p_start[1]), pt(p_end[0], p_end[1])],
                  fill=(23, 107, 93, 200), width=int(1.8 * SCALE))

    # 内层微型六边形 (Inner concentric hex)
    r_inner = 18.0
    inner_hex = []
    for i in range(6):
        ang_deg = i * 60 + 30
        rad = math.radians(ang_deg)
        x = hx_c[0] + r_inner * math.cos(rad)
        y = hx_c[1] + r_inner * math.sin(rad)
        inner_hex.append((x, y))
    for i in range(6):
        p_start = inner_hex[i]
        p_end = inner_hex[(i+1)%6]
        draw.line([pt(p_start[0], p_start[1]), pt(p_end[0], p_end[1])],
                  fill=(35, 145, 125, 130), width=int(1.1 * SCALE))
        # Radial spokes to outer corners
        draw.line([pt(hx_c[0], hx_c[1]), pt(hex_pts[i][0], hex_pts[i][1])],
                  fill=(35, 145, 125, 90), width=int(0.9 * SCALE))

    # 中枢金橘核心点 (Amber Hub Core)
    draw.ellipse([pt(hx_c[0]-4.2, hx_c[1]-4.2), pt(hx_c[0]+4.2, hx_c[1]+4.2)], fill=(230, 126, 34, 240))
    draw.ellipse([pt(hx_c[0]-7, hx_c[1]-7), pt(hx_c[0]+7, hx_c[1]+7)], outline=(230, 126, 34, 120), width=int(1.0 * SCALE))

    # 网格化互联链路 (Grid Network Links to external nodes)
    links = [
        ((167, 62), (105, 45), (230, 126, 34, 180), 3.2),    # To top-left station node
        ((167, 94), (85, 118), (35, 145, 125, 170), 3.8),   # To bottom-left service node
        ((85, 118), (45, 95), (23, 107, 93, 120), 2.8),     # Sub-link to edge resident
        ((195, 46), (185, 16), (35, 145, 125, 160), 3.0),   # To top dispatch node
        ((223, 94), (258, 125), (23, 107, 93, 170), 3.5),   # To bottom-right terminal
        ((223, 62), (265, 42), (35, 145, 125, 160), 3.2)    # To top-right IoT node
    ]

    for p_from, p_to, node_color, node_r in links:
        draw.line([pt(p_from[0], p_from[1]), pt(p_to[0], p_to[1])],
                  fill=(23, 107, 93, 140), width=int(1.3 * SCALE))
        # Node circle
        draw.ellipse([pt(p_to[0]-node_r, p_to[1]-node_r), pt(p_to[0]+node_r, p_to[1]+node_r)],
                     fill=node_color, outline=(23, 107, 93, 200), width=int(1.0 * SCALE))

    # 同心轨道协调大圆弧 (Orbital Coordination Arcs)
    # Upper orbital arc
    o0, o1, o2, o3 = (130, 48), (160, 22), (230, 22), (260, 56)
    draw_bezier_curve(draw, o0, o1, o2, o3, (35, 145, 125, 110), width=1.2, alpha_fade_start=0.2)
    # Lower orbital arc
    lo0, lo1, lo2, lo3 = (120, 118), (155, 148), (235, 148), (268, 112)
    draw_bezier_curve(draw, lo0, lo1, lo2, lo3, (35, 145, 125, 100), width=1.1, alpha_fade_start=0.2)

    final = img.resize((560, 320), Image.Resampling.LANCZOS)
    final.save(f"{OUT_DIR}/role-line-admin.png", "PNG")
    print("role-line-admin.png created")

# ==============================================================================
# 4. GUEST: 社区迎宾之门、旭日晨晖与温润拂面微风
# ==============================================================================
def make_guest_line():
    img = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    draw = ImageDraw.Draw(img)

    # 双重迎宾拱门 (Welcoming Portal Arches) at (195, 95)
    # Outer arch
    oa0, oa1, oa2, oa3 = (155, 145), (155, 52), (235, 52), (235, 145)
    draw_bezier_curve(draw, oa0, oa1, oa2, oa3, (27, 115, 100, 180), width=1.9)
    # Inner arch
    ia0, ia1, ia2, ia3 = (168, 145), (168, 70), (222, 70), (222, 145)
    draw_bezier_curve(draw, ia0, ia1, ia2, ia3, (35, 135, 115, 120), width=1.3)
    # Base baseline
    draw.line([pt(140, 145), pt(250, 145)], fill=(27, 115, 100, 160), width=int(1.6 * SCALE))

    # 初升晨阳 (Rising Morning Sun) at (195, 52)
    sun_c = (195, 52)
    sun_r = 13.0
    draw.ellipse([pt(sun_c[0]-sun_r, sun_c[1]-sun_r), pt(sun_c[0]+sun_r, sun_c[1]+sun_r)],
                 fill=(229, 142, 60, 210), outline=(217, 119, 54, 240), width=int(1.1 * SCALE))
    
    # 晨曦放射光束 (5 Sunrise Rays)
    ray_angles = [-140, -115, -90, -65, -40]
    for ang in ray_angles:
        rad = math.radians(ang)
        r1 = 18.0
        r2 = 29.0
        x1 = sun_c[0] + r1 * math.cos(rad)
        y1 = sun_c[1] + r1 * math.sin(rad)
        x2 = sun_c[0] + r2 * math.cos(rad)
        y2 = sun_c[1] + r2 * math.sin(rad)
        draw.line([pt(x1, y1), pt(x2, y2)], fill=(229, 142, 60, 175), width=int(1.2 * SCALE))

    # 温润拂面清风双波 (Gentle Breeze Waves across left)
    b0, b1, b2, b3 = (35, 132), (80, 120), (120, 138), (150, 132)
    draw_bezier_curve(draw, b0, b1, b2, b3, (35, 135, 115, 140), width=1.5, alpha_fade_start=0.35)
    b4, b5, b6, b7 = (65, 144), (105, 136), (135, 148), (158, 144)
    draw_bezier_curve(draw, b4, b5, b6, b7, (35, 135, 115, 100), width=1.1, alpha_fade_start=0.3)

    # 地面破土新苗 (Fresh Sprouts)
    draw_smooth_leaf(draw, (115, 130), -60, 13, 4.8, (27, 115, 100, 85), (27, 115, 100, 175))
    draw_smooth_leaf(draw, (126, 127), -30, 11, 4.2, (27, 115, 100, 75), (27, 115, 100, 155))

    # 迎宾光芒粒子
    draw.ellipse([pt(140-1.6, 68-1.6), pt(140+1.6, 68+1.6)], fill=(229, 142, 60, 150))
    draw.ellipse([pt(250-1.8, 62-1.8), pt(250+1.8, 62+1.8)], fill=(229, 142, 60, 160))
    draw.ellipse([pt(195-2.0, 105-2.0), pt(195+2.0, 105+2.0)], fill=(35, 135, 115, 130))

    final = img.resize((560, 320), Image.Resampling.LANCZOS)
    final.save(f"{OUT_DIR}/role-line-guest.png", "PNG")
    print("role-line-guest.png created")

if __name__ == "__main__":
    make_resident_line()
    make_staff_line()
    make_admin_line()
    make_guest_line()
    print("All 4 role line arts created successfully!")
