# 颐邻品牌图标与登录页素材

## 品牌图标

使用内置 image_gen 工具生成专属位图图标，没有使用 SVG 或将旧房屋图标转换为 PNG。

- `yilin-icon-master.png`：原始生成稿，保留完整分辨率。
- `yilin-icon-512.png`：512×512 PNG，可用于后续应用图标配置。
- `../../miniprogram/assets/brand/yilin-icon.png`：256×256 部署版本，用于登录页。

设计元素为照护者与长者的拥抱轮廓、家与暖色窗户，使用深玉绿色、鼠尾草绿和暖米白。缩小版本只做尺寸与编码优化，没有改变图案内容。

最终生成提示词：

> Use case: logo-brand. Create ONE finished raster PNG app icon for an actual Chinese community home eldercare service named 颐邻 (Yilin). No lettering or words inside the icon. Square app icon, full bleed warm ivory background with soft rounded-square green main emblem centered, occupying 80% of canvas. The emblem combines two gently embracing human figures (one elder, one caregiver) forming a sheltered home / roof / heart through distinctive negative space. Mature, warm, trustworthy premium community service identity. Deep jade #176B5D and light sage with one tiny muted warm ochre accent. Clean carefully proportioned bold silhouette that stays recognizable at 48 pixels. Flat polished brand design, generous internal breathing room, no generic stock house icon, no medical cross, no thin outlines, no gradients, no 3D, no shadows, no mockup, no presentation board, no border, no watermark. Output a single production-ready 1024 square PNG icon.

## 登录页照片

使用网上搜索并下载的 Pexels 实景照片。作者、原页面、下载地址与授权入口记录在 `../../miniprogram/assets/photos/README.md`。

## 页面实现

登录页使用实景照片全屏覆盖，保留品牌 PNG 与底部半透明微信快捷登录按钮。移除账号密码、注册、演示切换及游客入口，保留协议勾选与阅读弹层。自定义导航让图片延伸至状态栏区域，小屏压缩留白，图片失败时保留深绿底色与登录操作。认证仅使用微信 Code 登录，保留请求防重、会话校验与登录后的跳转规则。
