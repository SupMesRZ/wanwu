# Oil Motion 验收

- 意图：单个河小智，页面背景，1:1 局部组件，自主闭环；通过。
- 素材：384×384 PNG，含 `tRNS` 透明通道；DPR 2 所需尺寸为 352×352；通过。
- 预算：单帧 Alpha 图集，理论解码内存 0.5 MiB，无阻断项；通过。
- 运行时：无 `pointermove`；背景光晕、光带与角色呼吸共享 9.6 秒时间轴；通过。
- 状态：待机、思考、回答、聆听、疑问、成功和未识别均保留；通过。
- 降级：`prefers-reduced-motion` 下显示静态 idle；通过。
- 眼睛完善（2026-09-16）：按新规范使用 SVG 外框与内侧光核。本地 Chrome 无头模式、DPR 2，192／140／104／72 CSS 像素下共 71 项检查通过；包含光核、唯一滤镜 ID、四向视线、眨眼间隔与中心、闭环衔接及全部状态形状。截图：`eyes-preview.png`。
- 回归命令：`node web/src/views/campusStudent/assistant/motion/qa/check-motion.mjs`；Vue 模板、SCSS 编译及眼睛形状约束通过。
- 构建：Vue 生产构建完成；仓库原有 CSS 顺序和包体积警告不阻断交付。
- 页面更新：此前 Nginx 提供的构建仍含 `digital-idle-pupil` 旧样式；已重新构建，容器入口已引用新的资源。
