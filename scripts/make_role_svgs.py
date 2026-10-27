"""
Generate vector SVG line arts for each role.
"""

def generate_svgs():
    # 1. RESIDENT
    resident_svg = '''<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 280 160" fill="none">
  <defs>
    <linearGradient id="resVineGrad" x1="0%" y1="0%" x2="100%" y2="0%">
      <stop offset="0%" stop-color="#176b5d" stop-opacity="0.05" />
      <stop offset="35%" stop-color="#176b5d" stop-opacity="0.55" />
      <stop offset="100%" stop-color="#176b5d" stop-opacity="0.8" />
    </linearGradient>
    <linearGradient id="roofGrad" x1="0%" y1="0%" x2="100%" y2="0%">
      <stop offset="0%" stop-color="#176b5d" stop-opacity="0.1" />
      <stop offset="40%" stop-color="#176b5d" stop-opacity="0.75" />
      <stop offset="100%" stop-color="#176b5d" stop-opacity="0.85" />
    </linearGradient>
  </defs>

  <!-- 屋檐双弧 (Warm Home Roof Arcs) -->
  <path d="M 100 68 C 150 42, 185 42, 245 78" stroke="url(#roofGrad)" stroke-width="1.8" stroke-linecap="round" />
  <path d="M 120 78 C 155 56, 185 56, 235 88" stroke="#237d6c" stroke-width="1.2" stroke-opacity="0.45" stroke-linecap="round" />

  <!-- 暖茶香韵微波 (Warm Steams) -->
  <path d="M 170 40 C 166 26, 176 16, 170 6" stroke="#d97736" stroke-width="1.2" stroke-linecap="round" stroke-opacity="0.75" />
  <path d="M 182 38 C 178 28, 186 20, 182 10" stroke="#d97736" stroke-width="1.0" stroke-linecap="round" stroke-opacity="0.5" />

  <!-- 主生机花藤 (Main Botanical Vine) -->
  <path d="M 40 132 C 90 145, 145 100, 210 122 C 235 130, 255 120, 272 110" stroke="url(#resVineGrad)" stroke-width="1.8" stroke-linecap="round" />
  <path d="M 75 135 C 120 110, 160 135, 225 115" stroke="#237d6c" stroke-width="1.1" stroke-opacity="0.4" stroke-linecap="round" />

  <!-- 银杏主叶 (Ginkgo Biloba Leaf at 195,88) -->
  <path d="M 185 110 C 188 102, 192 95, 195 88" stroke="#176b5d" stroke-width="1.4" stroke-linecap="round" />
  <!-- 扇冠与叶脉 -->
  <path d="M 195 88 L 175 70 C 185 58, 202 54, 212 60 C 215 62, 216 66, 219 63 C 228 58, 240 66, 242 78 Z" fill="#176b5d" fill-opacity="0.16" stroke="#176b5d" stroke-width="1.3" stroke-linejoin="round" />
  <path d="M 195 88 L 186 64 M 195 88 L 200 60 M 195 88 L 214 62 M 195 88 L 226 67 M 195 88 L 235 74" stroke="#237d6c" stroke-width="0.8" stroke-opacity="0.45" />

  <!-- 伴生小银杏叶 at 228,105 -->
  <path d="M 222 120 C 225 114, 227 108, 228 105" stroke="#176b5d" stroke-width="1.1" />
  <path d="M 228 105 L 216 93 C 224 86, 238 88, 244 98 Z" fill="#176b5d" fill-opacity="0.14" stroke="#176b5d" stroke-width="1.0" />

  <!-- 绿叶簇 (Smooth Leaves) -->
  <path d="M 115 130 C 105 120, 110 112, 122 116 C 126 122, 122 128, 115 130 Z" fill="#176b5d" fill-opacity="0.32" stroke="#176b5d" stroke-width="1.0" />
  <path d="M 140 108 C 148 98, 156 102, 152 114 C 146 116, 142 112, 140 108 Z" fill="#176b5d" fill-opacity="0.32" stroke="#176b5d" stroke-width="1.0" />
  <path d="M 168 122 C 160 114, 166 108, 175 112 C 177 118, 172 122, 168 122 Z" fill="#176b5d" fill-opacity="0.3" stroke="#176b5d" stroke-width="0.9" />

  <!-- 小花微绽 (Florets) -->
  <g transform="translate(126, 116)">
    <circle cx="0" cy="-3.5" r="2.4" fill="#176b5d" fill-opacity="0.35" />
    <circle cx="3.3" cy="-1.1" r="2.4" fill="#176b5d" fill-opacity="0.35" />
    <circle cx="2.1" cy="2.8" r="2.4" fill="#176b5d" fill-opacity="0.35" />
    <circle cx="-2.1" cy="2.8" r="2.4" fill="#176b5d" fill-opacity="0.35" />
    <circle cx="-3.3" cy="-1.1" r="2.4" fill="#176b5d" fill-opacity="0.35" />
    <circle cx="0" cy="0" r="1.8" fill="#d97736" />
  </g>

  <g transform="translate(215, 128)">
    <circle cx="0" cy="-2.8" r="2.0" fill="#176b5d" fill-opacity="0.35" />
    <circle cx="2.7" cy="-0.9" r="2.0" fill="#176b5d" fill-opacity="0.35" />
    <circle cx="1.7" cy="2.3" r="2.0" fill="#176b5d" fill-opacity="0.35" />
    <circle cx="-1.7" cy="2.3" r="2.0" fill="#176b5d" fill-opacity="0.35" />
    <circle cx="-2.7" cy="-0.9" r="2.0" fill="#176b5d" fill-opacity="0.35" />
    <circle cx="0" cy="0" r="1.5" fill="#d97736" />
  </g>

  <!-- 晨露微点 -->
  <circle cx="92" cy="142" r="2" fill="#176b5d" fill-opacity="0.45" />
  <circle cx="155" cy="96" r="1.6" fill="#d97736" fill-opacity="0.65" />
  <circle cx="265" cy="112" r="1.5" fill="#176b5d" fill-opacity="0.35" />
</svg>'''

    # 2. STAFF
    staff_svg = '''<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 280 160" fill="none">
  <defs>
    <linearGradient id="staffPusleGrad" x1="0%" y1="0%" x2="100%" y2="0%">
      <stop offset="0%" stop-color="#167a68" stop-opacity="0.08" />
      <stop offset="30%" stop-color="#167a68" stop-opacity="0.75" />
      <stop offset="100%" stop-color="#167a68" stop-opacity="0.85" />
    </linearGradient>
    <linearGradient id="staffPalmGrad" x1="0%" y1="0%" x2="100%" y2="0%">
      <stop offset="0%" stop-color="#167a68" stop-opacity="0.1" />
      <stop offset="40%" stop-color="#167a68" stop-opacity="0.8" />
      <stop offset="100%" stop-color="#167a68" stop-opacity="0.85" />
    </linearGradient>
  </defs>

  <!-- 守护之手托举双弧 (Caring Palms Arcs) -->
  <path d="M 110 145 C 145 152, 185 142, 240 118" stroke="url(#staffPalmGrad)" stroke-width="2.0" stroke-linecap="round" />
  <path d="M 135 155 C 170 158, 205 148, 255 128" stroke="#238270" stroke-width="1.3" stroke-opacity="0.45" stroke-linecap="round" />

  <!-- 生机健康脉搏波 (Vitality ECG Pulse Line) -->
  <path d="M 35 82 L 85 82 Q 95 75 103 82 L 110 82 L 115 89 L 124 38 L 133 96 L 142 82 Q 154 72 166 82 L 268 82" stroke="url(#staffPusleGrad)" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round" />
  <circle cx="124" cy="38" r="2.8" fill="#d97736" />
  <circle cx="124" cy="38" r="5" stroke="#d97736" stroke-width="1.0" stroke-opacity="0.4" />

  <!-- 守护爱心徽章 (Heart of Compassion at 175, 52) -->
  <path d="M 175 44 C 170 36, 160 38, 160 48 C 160 58, 175 66, 175 66 C 175 66, 190 58, 190 48 C 190 38, 180 36, 175 44 Z" fill="#d97736" fill-opacity="0.18" stroke="#d97736" stroke-width="1.5" stroke-linejoin="round" />
  <circle cx="175" cy="50" r="2.2" fill="#d97736" />

  <!-- 敏捷上门微风弧翼 (Swift Service Response Wind Arcs) -->
  <path d="M 195 24 C 220 20, 242 28, 268 38" stroke="#238270" stroke-width="1.4" stroke-opacity="0.6" stroke-linecap="round" />
  <path d="M 212 38 C 234 36, 252 45, 272 54" stroke="#238270" stroke-width="1.2" stroke-opacity="0.45" stroke-linecap="round" />
  <path d="M 226 54 C 244 54, 258 62, 275 70" stroke="#238270" stroke-width="1.0" stroke-opacity="0.35" stroke-linecap="round" />

  <!-- 照护嫩芽 (Sprout of Care) -->
  <path d="M 238 118 C 232 108, 238 102, 246 106 C 248 112, 244 116, 238 118 Z" fill="#167a68" fill-opacity="0.32" stroke="#167a68" stroke-width="1.0" />
  <path d="M 250 112 C 246 104, 252 98, 258 102 C 260 108, 256 112, 250 112 Z" fill="#167a68" fill-opacity="0.28" stroke="#167a68" stroke-width="0.9" />

  <circle cx="154" cy="72" r="1.8" fill="#167a68" fill-opacity="0.55" />
  <circle cx="202" cy="48" r="1.5" fill="#d97736" fill-opacity="0.65" />
</svg>'''

    # 3. ADMIN
    admin_svg = '''<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 280 160" fill="none">
  <defs>
    <linearGradient id="adminGridGrad" x1="0%" y1="0%" x2="100%" y2="0%">
      <stop offset="0%" stop-color="#105246" stop-opacity="0.1" />
      <stop offset="50%" stop-color="#105246" stop-opacity="0.7" />
      <stop offset="100%" stop-color="#105246" stop-opacity="0.85" />
    </linearGradient>
  </defs>

  <!-- 六边形数智护盾中枢 (Hexagonal Shield Matrix at 195, 78) -->
  <!-- Outer Hexagon: r=32 -->
  <polygon points="222.7,94 195,110 167.3,94 167.3,62 195,46 222.7,62" fill="#105246" fill-opacity="0.14" stroke="#105246" stroke-width="1.8" stroke-linejoin="round" />
  <!-- Inner Hexagon: r=18 -->
  <polygon points="210.6,87 195,96 179.4,87 179.4,69 195,60 210.6,69" fill="none" stroke="#237d6c" stroke-width="1.1" stroke-opacity="0.5" stroke-linejoin="round" />
  <!-- Spokes -->
  <line x1="195" y1="78" x2="222.7" y2="94" stroke="#237d6c" stroke-width="0.9" stroke-opacity="0.4" />
  <line x1="195" y1="78" x2="195" y2="110" stroke="#237d6c" stroke-width="0.9" stroke-opacity="0.4" />
  <line x1="195" y1="78" x2="167.3" y2="94" stroke="#237d6c" stroke-width="0.9" stroke-opacity="0.4" />
  <line x1="195" y1="78" x2="167.3" y2="62" stroke="#237d6c" stroke-width="0.9" stroke-opacity="0.4" />
  <line x1="195" y1="78" x2="195" y2="46" stroke="#237d6c" stroke-width="0.9" stroke-opacity="0.4" />
  <line x1="195" y1="78" x2="222.7" y2="62" stroke="#237d6c" stroke-width="0.9" stroke-opacity="0.4" />

  <!-- 中枢金橘核心点 -->
  <circle cx="195" cy="78" r="4.2" fill="#e67e22" />
  <circle cx="195" cy="78" r="7.0" stroke="#e67e22" stroke-width="1.0" stroke-opacity="0.5" />

  <!-- 网格化互联链路与微节点 (Grid Links & Nodes) -->
  <line x1="167.3" y1="62" x2="105" y2="45" stroke="#176b5d" stroke-width="1.3" stroke-opacity="0.55" />
  <circle cx="105" cy="45" r="3.2" fill="#e67e22" stroke="#176b5d" stroke-width="1.0" />

  <line x1="167.3" y1="94" x2="85" y2="118" stroke="#176b5d" stroke-width="1.3" stroke-opacity="0.55" />
  <circle cx="85" cy="118" r="3.8" fill="#208b77" stroke="#176b5d" stroke-width="1.0" />

  <line x1="85" y1="118" x2="45" y2="95" stroke="#176b5d" stroke-width="1.1" stroke-opacity="0.4" />
  <circle cx="45" cy="95" r="2.8" fill="#176b5d" fill-opacity="0.5" />

  <line x1="195" y1="46" x2="185" y2="16" stroke="#176b5d" stroke-width="1.2" stroke-opacity="0.5" />
  <circle cx="185" cy="16" r="3.0" fill="#208b77" stroke="#176b5d" stroke-width="1.0" />

  <line x1="222.7" y1="94" x2="258" y2="125" stroke="#176b5d" stroke-width="1.3" stroke-opacity="0.55" />
  <circle cx="258" cy="125" r="3.5" fill="#105246" stroke="#176b5d" stroke-width="1.0" />

  <line x1="222.7" y1="62" x2="265" y2="42" stroke="#176b5d" stroke-width="1.2" stroke-opacity="0.5" />
  <circle cx="265" cy="42" r="3.2" fill="#208b77" stroke="#176b5d" stroke-width="1.0" />

  <!-- 同心调度轨道弧 (Orbital Arcs) -->
  <path d="M 130 48 C 160 22, 230 22, 260 56" stroke="#208b77" stroke-width="1.2" stroke-opacity="0.4" stroke-linecap="round" />
  <path d="M 120 118 C 155 148, 235 148, 268 112" stroke="#208b77" stroke-width="1.1" stroke-opacity="0.35" stroke-linecap="round" />
</svg>'''

    # 4. GUEST
    guest_svg = '''<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 280 160" fill="none">
  <defs>
    <linearGradient id="guestSunGrad" x1="0%" y1="0%" x2="0%" y2="100%">
      <stop offset="0%" stop-color="#e58e3c" stop-opacity="0.9" />
      <stop offset="100%" stop-color="#e58e3c" stop-opacity="0.6" />
    </linearGradient>
  </defs>

  <!-- 双重迎宾拱门 (Welcoming Portal Arches) -->
  <path d="M 155 145 C 155 52, 235 52, 235 145" stroke="#1b7364" stroke-width="1.9" stroke-linecap="round" />
  <path d="M 168 145 C 168 70, 222 70, 222 145" stroke="#278574" stroke-width="1.3" stroke-opacity="0.5" stroke-linecap="round" />
  <line x1="140" y1="145" x2="250" y2="145" stroke="#1b7364" stroke-width="1.6" stroke-linecap="round" />

  <!-- 初升晨阳与放射光芒 (Sunrise & Rays at 195, 52) -->
  <circle cx="195" cy="52" r="13" fill="url(#guestSunGrad)" stroke="#d97736" stroke-width="1.1" />
  <line x1="181.2" y1="40.4" x2="172.8" y2="33.4" stroke="#e58e3c" stroke-width="1.2" stroke-linecap="round" stroke-opacity="0.8" />
  <line x1="187.4" y1="35.7" x2="182.7" y2="25.7" stroke="#e58e3c" stroke-width="1.2" stroke-linecap="round" stroke-opacity="0.8" />
  <line x1="195.0" y1="34.0" x2="195.0" y2="23.0" stroke="#e58e3c" stroke-width="1.2" stroke-linecap="round" stroke-opacity="0.8" />
  <line x1="202.6" y1="35.7" x2="207.3" y2="25.7" stroke="#e58e3c" stroke-width="1.2" stroke-linecap="round" stroke-opacity="0.8" />
  <line x1="208.8" y1="40.4" x2="217.2" y2="33.4" stroke="#e58e3c" stroke-width="1.2" stroke-linecap="round" stroke-opacity="0.8" />

  <!-- 温润拂面清风双波 (Gentle Breeze Waves) -->
  <path d="M 35 132 C 80 120, 120 138, 150 132" stroke="#237d6c" stroke-width="1.5" stroke-linecap="round" stroke-opacity="0.55" />
  <path d="M 65 144 C 105 136, 135 148, 158 144" stroke="#237d6c" stroke-width="1.1" stroke-linecap="round" stroke-opacity="0.4" />

  <!-- 地面破土新苗 (Fresh Sprouts) -->
  <path d="M 115 130 C 108 120, 114 114, 122 118 C 124 124, 120 128, 115 130 Z" fill="#1b7364" fill-opacity="0.32" stroke="#1b7364" stroke-width="1.0" />
  <path d="M 126 127 C 122 118, 127 114, 134 117 C 135 122, 131 125, 126 127 Z" fill="#1b7364" fill-opacity="0.28" stroke="#1b7364" stroke-width="0.9" />

  <circle cx="140" cy="68" r="1.6" fill="#e58e3c" fill-opacity="0.6" />
  <circle cx="250" cy="62" r="1.8" fill="#e58e3c" fill-opacity="0.65" />
  <circle cx="195" cy="105" r="2.0" fill="#237d6c" fill-opacity="0.5" />
</svg>'''

    import os
    out_dir = "miniprogram/assets/icons"
    os.makedirs(out_dir, exist_ok=True)
    with open(f"{out_dir}/role-line-resident.svg", "w", encoding="utf-8") as f:
        f.write(resident_svg)
    with open(f"{out_dir}/role-line-staff.svg", "w", encoding="utf-8") as f:
        f.write(staff_svg)
    with open(f"{out_dir}/role-line-admin.svg", "w", encoding="utf-8") as f:
        f.write(admin_svg)
    with open(f"{out_dir}/role-line-guest.svg", "w", encoding="utf-8") as f:
        f.write(guest_svg)
    print("All 4 role SVGs created successfully!")

if __name__ == "__main__":
    generate_svgs()
