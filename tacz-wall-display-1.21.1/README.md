# TACZ Wall Display 0.4.5

Minecraft 1.21.1 / NeoForge 21.1.248 / TACZ 1.1.8-hotfix-r6 / Java 21。

## 0.4.5：静态装饰只显示当前安装的弹匣

修复新建独立枪械模型时未执行 TACZ 动画状态清理，导致换弹用 `additional_magazine` 与正常弹匣同时被捕获的问题。静态捕获前仅对独立模型恢复正常状态，再应用已有的枪包显隐修正；标准与扩容弹匣继续由 TACZ 按保存的原枪配件选择。不会启动逐帧动画，也不改变真枪数据或玩家手持模型。

重启游戏后，已有装饰枪会重新生成正确缓存，无需拆除或重新合成。仅更新 1.21.1；1.20.1 保持原版本。

N4 专项测试：`gradlew --offline test build -Psmoke smokeJar`，独立测试实例使用 `-Dwallgun.magazineSmoke=true`。`-Dwallgun.magazineSeedOld=true` 配合旧版正式 JAR 复现重叠并保存测试存档，替换为新版后使用 `-Dwallgun.magazineReopen=true` 验证旧存档升级和重新进档。需要本机 Suffuse 与 Spearhead 枪包；只在隔离测试实例中运行。

## 0.4.2：装饰枪始终捕获高精度模型

修复部分瞄具在首次静态捕获时选到 TACZ 低模、随后被缓存的问题。仅在 `MeshCapture` 生效期间覆盖 TACZ 的高模距离判断；由 TACZ 同时选择高模及对应贴图。继续使用静态 `FIXED` 捕获，不运行第一人称瞄准渲染，不改写 TACZ 配置。正常真枪渲染仍遵循 TACZ 的原有距离判断。

升级后重启游戏即可让已有装饰枪重新生成模型缓存，无需重新合成、破坏重放或迁移存档数据。

本轮验证：13 项单元测试通过；使用实际发布 JAR，在独立正式客户端中测试 EXP3、ACOG TA31、Elcan 4× 三种瞄具。按 TACZ 距离设置 0 → 8 → 9999 分别清空缓存后捕获，三组模型的顶点、材质、贴图及全部顶点属性完全一致，均无 LOD 贴图。另验证捕获前后普通 TACZ 远距离判断不变、静态缓存复用，以及设置为 0 时三支已装配装饰枪正常摆放渲染。此次未重做大规模帧率基准测试。

复现回归：`gradlew --offline test build -Psmoke smokeJar` 生成正式 JAR 及独立测试 JAR；在隔离实例中搭配对应 TACZ，以 JVM 参数 `-Dwallgun.lodSmoke=true` 启动，结果写入 `lod-verification/SUCCESS.txt` 和截图。测试 JAR 仅用于隔离实例，不能随正式模组部署。开发环境也支持 `gradlew --offline -Psmoke -PlodSmoke runClient`。

## 前置与发布说明

唯一需要另行安装的模组前置是对应版本的 TACZ；本模组和 TACZ 都需要装在客户端及服务端。Forge／NeoForge 的版本号只作推荐，本附属模组不再按平台版本号阻止加载。

硬性限制：Minecraft `[1.21.1]`、TACZ `[1.1.8-hotfix-r6]`，必须使用 NeoForge。NeoForge **21.1.248 仅为推荐版本**，不是最低版本。使用 Java 21。TACZ 必须来自 **[UNOFFICIAL] TaCZ NeoForge Port**，不是官方 Forge 版。

平台依赖与 `loaderVersion` 使用 `[0,)`，取消本模组施加的平台版本门槛；TACZ 或整合包内其他模组的要求仍由它们自己决定。低版本验证边界见下方依赖评估，不宣称所有历史版本都兼容。

TACZ 的版本是精确限制：静态捕获 Mixin 调用了其内部渲染接口，未验证其他版本前不放宽。TACZ 自带 SimpleBedrockModel 等库，无需单独安装 GeckoLib。Cloth Config、Sodium／Embeddium、Iris、RuOK、Packet Fixer 均不是本附属模组的硬性前置；枪包自身的依赖由枪包决定。

可直接复制的英文页面见 [CurseForge 说明](CURSEFORGE.md)，依赖审查依据及分文件发布关系见 [依赖评估](DEPENDENCIES.md)。

## 使用

- 一把真枪单独放入背包或工作台合成栏，得到保存完整原枪数据的装饰枪；同样可逆向合成。
- 支持东、西、南、北、上、下六面放置。可以借助临时方块定位，再拆除支撑，装饰枪会保留。
- 徒手可拆，硬度 0.5。生存模式拆除直接掉落一把原枪，保留配件、弹药、名字和其他原有组件；创造模式沿用默认无掉落行为。
- 手持配置的调整工具（默认木棍）右键：从操作的一侧看，顺时针旋转 22.5°，16 次回到一圈。
- 手持调整工具按住 Shift 右键：绕安装平面内的竖轴翻转 180°，显示枪的另一侧；两次翻面复原。检测 Shift 输入，不要求蹲姿，创造飞行可用。
- 每次成功调整播放一次展示框放入物品音效。地面／顶面的初始方向随放置者朝向确定；后续翻面保持这个基准。
- 摆放方向保存在方块实体中并同步给客户端；拿回真枪后再次转换、放置，重新使用默认方向。
- 默认视觉方向为尾部左、枪口右。现有 0.2.x 通用装饰枪方块重启后使用新默认外观，原枪数据不受影响。

## 调整工具配置

在本机实例的 `config/tacz-wall-display-server.toml` 中填写物品 ID：

```toml
adjustmentItem = "minecraft:stick"
```

例如改为 `minecraft:feather` 后，羽毛负责旋转／翻面，木棍不再负责调整。物品提示会显示当前工具名称。原工具与新工具不并存，也不按物品标签匹配；物品上的名称、附魔等数据不影响 ID 匹配。

采用 NeoForge SERVER 配置，联机以服务端配置为准并同步客户端。本机 NeoForge 的实际生成位置为实例 `config` 目录；如自行启用了存档级服务端配置覆盖，则编辑该存档覆盖文件。建议退出存档后编辑并重新进入。

只检查物品 ID，不适配所选物品自身的右键功能。格式合法但未注册的 ID 不匹配任何物品；空手不能调整。默认值始终为 `minecraft:stick`。

## 外观与性能

直接读取 TACZ 和枪包原有贴图、模型、配件外观。模型仅首次捕获为静态网格，之后按材质及固定空间小单元批量绘制。转动与翻面只改变摆放变换，不重新捕获枪械模型。

进入世界时会显示“正在准备附近的装饰枪”。扫描客户端已有区块内、距玩家约 112 格的实际装饰枪（受渲染距离限制），不依赖视线朝向，不额外加载服务器区块。模型捕获、GPU 缓冲和首次材质绘制准备完成后自动进入游戏。资源重载后也会重新准备，退出存档不会触发新的加载界面。

加载界面期间客户端、区块接收与世界绘制继续推进。收集新增装饰枪后等待 0.75 秒安静期；附近区块未收齐时先给数据到达保留最多 2.5 秒的初始等待，之后仅以已到达的数据判断。预加载界面最长保持 30 秒，超时后继续分帧处理，并记录日志。异常模型使用现有缺失标记，不丢弃原枪。

进入新区域后，每帧最多开始一次未缓存外观捕获、两次批次重建和两次首次绘制；捕获与重建共享约 2 毫秒的启动预算，首次绘制另有约 1 毫秒预算。单次 TACZ 捕获或 GPU 操作不可中断，因此这些是任务启动预算，不能保证每帧绝不超时。加载界面内提高处理配额。新出现的枪可能短暂分批显示。

`/wallgun_stats` 包含 `pendingModels`、`pendingBatches`、`warming` 等诊断字段。日志记录每次预加载耗时与模型／上传数量。

随版本提供本机 560 个已加载条目的朝向审查结果，其中 220 个枪械条目需要转换默认展示侧。558 个有效外观 ID 的校准信息位于 `assets/tacz_wall_display/orientation.json`；这是方向元数据，不是预设方块注册表。新增外观优先以枪口定位节点作自动判断，也可通过资源包覆盖校准文件。

静态装饰保留激光器本体，跳过 TACZ 实时瞄准射线；另对 `ccrp:hk416_sopmod` 模型中名为 `laser_illuminated` 的静态长射线辅助骨骼作局部隐藏，不修改枪包或真实持枪渲染。

审查边界：`bf1:handgun` 是源枪包固定展示缩放为零、包含手部模型的特殊条目，没有可审查的枪械展示几何；保留缺失标记和原枪数据。`bf1:lunge_mine`、`bf1:syringe`、`ccrp:shield_ots33` 为突刺地雷、注射器和盾牌类特殊外观，不适用枪托／枪口规则，保留源展示侧。未来新增或重做的枪包仍可能需要视觉校准。

## 开发验证

`gradlew test build`：产品构建与单元测试。

`gradlew -Psmoke runClient`：隔离客户端完整合成、快照、配件、资源重载和压力测试。

`gradlew -Psmoke -Ppose runClient`：六面摆放基准、实际客户端 Shift 飞行交互、音效、连续旋转与翻面测试。

`gradlew -Psmoke -PwarmupSmoke runClient`：在事先复制至 `run-client/saves/warmup-stress` 的百枪测试存档副本中验证冷启动、重新进档、资源重载、背后新区域的分帧准备及首次转身。采样包含加载结束后的最初帧，不排除“预热帧”；测试过程中会在副本内创建另一个区域。

`gradlew -Psmoke -Paudit runClient`：生成全部已加载枪械的编号预览和清单。`-PauditOnly=namespace:id,...` 可定向复核。

测试源集不进入发布 JAR。各测试只使用 `run-client` 内的新存档或独立副本；不编辑玩家存档。

调整工具专项回归：将隔离实例的配置设为目标 ID 后，运行 `gradlew -Psmoke -PtoolItem=minecraft:stick runClient`；可用 `minecraft:feather` 验证改配、旧工具失效、提示文本和六面实际客户端交互。

装饰枪兼容性专项回归：独立测试 JAR 配合 JVM 参数 `-Dwallgun.compatibilitySmoke=true`，默认以 TACZ 自带 HK416 与 SCAR-L 检查四档弹匣静态模型、双向合成、原枪掉落和挂墙渲染。可用 `-Dwallgun.compatibilityGuns=namespace:first,namespace:second` 指定两个支持测试配件的原枪 ID。结果写入 `display-compatibility-verification`；不验证真枪射击、换弹或枪包配方。历史清理说明见根目录 `migration/HISTORY-CLEANUP.md`。
