# 0.1.0 验证记录 — 2026-09-14

构建：NeoForge 21.1.248 / Java 21 / TACZ 1.1.8-hotfix-r6。

`build` 通过，3 项单元测试通过：任意角度、UV、法线、发光信息保留；贴墙定位；不完整面片拒绝和捕获上下文清理。

独立客户端使用本地 9 个外置枪包的副本和 TACZ 默认包。运行时公共枪械索引为 **560** 项，创造标签 ID 集合与其完全相等。磁盘索引文件数量不是有效枪型数量；枪包自身加载失败项不由本模组伪造补入。

实际运行验证：

- 12 个装饰枪方块，10 个不同枪型，零捕获失败。
- 包含默认包、mk16、suffuse、ghost 的模型，正面和斜侧面确认有厚度、贴墙、纹理正确。
- 12 次枪型 NBT 保存/读取及中键复制检查通过。
- 四个水平墙面方向的放置状态、支撑墙移除检查通过。
- F3+T 等价资源重载后重新烘焙，枪械继续显示。
- 同一区段 100 把 AK47：100 个可见实例，1 次材质批次绘制。
- 两个静止阶段分别连续 180 个客户端 tick 没有 GPU 上传计数增长。
- 12 个物品图标使用原枪包 slot 贴图，截图检查通过。

最终运行组合：Sodium 0.8.13-beta.2+mc1.21.1、Iris 1.8.14-beta.1+mc1.21.1、ComplementaryShaders_v4.7.2，光影设置来自用户实例的副本。

最终自动检查输出：

```text
Catalog=560; bakes=20, failures=0, uploads=22, draws=11, visible=12, cachedBatches=11
Reload, static cache stability, NBT identity, wall support and pick block verified.
100 same-material guns, one section: bakes=20, failures=0, uploads=24, draws=1, visible=100, cachedBatches=1
No uploads during 180 stable ticks. Four placement orientations and support removal verified.
12 source-pack item icons rendered.
```

累计烘焙次数 20 包含资源重载前后两轮；累计上传次数包含场景变化。`draws=1` 仅描述该同材质单区段测试，不代表混合枪型场景。

早期测试曾遇到失焦暂停菜单遮住截图，以及初次进入世界时过早修改方块造成半面测试墙未刷新。测试程序改为等待初始区块加载后摆放，并禁用测试客户端的失焦暂停；最终运行全部检查通过。这些改动仅在开发测试模组中，不改变正式实例设置。

没有进行与原展示框的受控 FPS 对比，没有验证全部 560 个模型或整个整合包的所有兼容组合。首次加载新枪型仍可能发生一次性卡顿。未覆盖自由配件组合、皮肤选择和专用服务器启动。

发布 JAR 检查：只包含原型代码及模型/语言元数据，没有枪包 PNG、TACZ JAR 或开发测试模组。
