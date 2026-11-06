# 交付入口

- 主资源：`web/src/assets/imgs/hexiaozhi-digital-human.png`
- 运行时：`web/src/views/campusStudent/assistant/index.vue`
- 眼睛：运行时内的 SVG 方框、内侧光核和状态表情；眨眼、四向视线和倾听光核由 CSS 驱动。
- 时间轴：`../build/timeline.json`
- 背景：CSS 光晕、漂移光带和微弱粒子，按时间轴 9.6 秒循环做低幅度呼吸。
- 静态降级：同一 PNG 与 `idle` 面屏；`prefers-reduced-motion` 下关闭全部动画。
