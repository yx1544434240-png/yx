# Vue 3 + Vite

This template should help get you started developing with Vue 3 in Vite. The template uses Vue 3 `<script setup>` SFCs, check out the [script setup docs](https://v3.vuejs.org/api/sfc-script-setup.html#sfc-script-setup) to learn more.

Learn more about IDE Support for Vue in the [Vue Docs Scaling up Guide](https://vuejs.org/guide/scaling-up/tooling.html#ide-support).

## 环境变量 / Environment

复制 `.env.example` 为 `.env.local` 按需修改（Node ≥ 22，见 `.nvmrc`）。
生产构建禁止 `VITE_SHOW_DEMO=true`，`vite build` 会直接失败；`npm run build` 之后会检查包体积门槛（`scripts/check-bundle.mjs`）。
