# TACZ Wall Display — Forge 1.20.1

与 1.21.1 / NeoForge 0.4.1 完整功能对齐的独立版本。目标为 Minecraft 1.20.1、Forge 47.4.21、TACZ 1.1.8-hotfix，产物为 Java 17 字节码。

## 前置与发布说明

唯一需要另行安装的模组前置是对应版本的 TACZ；本模组和 TACZ 都需要装在客户端及服务端。Forge／NeoForge 的版本号只作推荐，本附属模组不再按平台版本号阻止加载。

硬性限制：Minecraft `[1.20.1]`、TACZ `[1.1.8-hotfix]`，必须使用 Forge。Forge **47.4.21 仅为推荐版本**，不是最低版本。Java 最低 17，使用兼容的运行时。TACZ 使用官方 Forge 版。

平台依赖与 `loaderVersion` 使用 `[0,)`，取消本模组施加的平台版本门槛；TACZ 或整合包内其他模组的要求仍由它们自己决定。低版本验证边界见下方依赖评估，不宣称所有历史版本都兼容。

TACZ 的版本是精确限制：静态捕获 Mixin 调用了其内部渲染接口，未验证其他版本前不放宽。TACZ 自带 SimpleBedrockModel 等库，无需单独安装 GeckoLib。Cloth Config、Sodium／Embeddium、Iris、RuOK、Packet Fixer 均不是本附属模组的硬性前置；枪包自身的依赖由枪包决定。

可直接复制的英文页面见 [CurseForge 说明](../tacz-wall-display-1.21.1/CURSEFORGE.md)，依赖审查依据及分文件发布关系见 [依赖评估](../tacz-wall-display-1.21.1/DEPENDENCIES.md)。

## 使用

- 将一支 TACZ 真枪单独放进背包或工作台合成栏，得到装饰枪；装饰枪单独放进合成栏可还原真枪。
- 保存整支枪的 ItemStack NBT，包括配件、弹药、名称、自定义标签和可序列化 Forge capabilities。每次转换只处理一支枪。
- 装饰枪支持六面放置，无需依附支撑方块。默认枪托在左、枪口在右；沿用已有枪包朝向校准。
- 手持配置的调整工具（默认木棍）右键：顺时针旋转 22.5°。按住 Shift 右键：沿贴面竖直轴翻面；支持创造飞行。每次有效操作播放一次展示框放入物品音效。
- 硬度 0.5，无需工具。生存破坏直接掉落原枪；创造破坏按默认逻辑不返还。
- 物品图标、模型和贴图直接复用已安装 TACZ / 枪包资源。只注册一个通用方块，不生成枪包预设目录。

## 调整工具配置（0.4.1）

采用 Forge SERVER 配置，由服务端同步给客户端。进入存档后，实际配置文件为 `saves/<存档名>/serverconfig/tacz-wall-display-server.toml`；独立服务器对应 `<世界目录>/serverconfig/tacz-wall-display-server.toml`。

```toml
adjustmentItem = "minecraft:stick"
```

改成 `minecraft:feather` 后，羽毛负责旋转和翻面，木棍不再调整；物品提示同步显示工具名称。只匹配物品 ID，格式合法但未注册的 ID 不匹配任何物品，空手不能调整。不适配所选物品自身的右键行为。

`defaultconfigs/tacz-wall-display-server.toml` 是默认模板：当存档尚无该配置时，Forge 用它初始化配置。已有存档配置请直接修改该存档的 `serverconfig` 文件。建议退出存档后修改，再重新进入。这与 1.21.1 NeoForge 默认使用实例 `config` 目录的行为不同。

## 静态渲染和进入世界预加载

每种实际枪械快照只捕获一次静态几何，包含瞄具等可见配件；不在逐帧绘制时运行骨骼动画。模型保留任意角度，以静态 VBO 按小区域合批。局部编辑最多重建一个 2×2×2 单元，正常挂墙情况下该单元最多四支枪。

进入世界时，在加载界面中扫描客户端已经接收到的附近区块，提前处理模型捕获、上传和首次材质绘制，包括摄像机背后的装饰枪。资源重载后重新预热。正常游玩时新出现的区域分帧处理，每帧最多捕获一个模型、上传两个批次、首次绘制两个批次；单个不可拆分任务仍可能超过时间预算。加载界面最长等待 30 秒，不强制加载远处区块。

## 构建与验证

在本机已安装的 JDK 下执行 `gradlew.bat build`，编译目标固定为 Java 17；发布 JAR 会执行 Forge 重混淆。通过 `-PminecraftInstance=...` 指定包含 TACZ 的 1.20.1 实例。渲染测试复用本机 Embeddium、Cloth Config、RuOK、Packet Fixer；这些不是本模组新增的硬依赖。

- `gradlew.bat test`：几何、贴面变换、批次分区和任务预算单元测试。
- `gradlew.bat -Psmoke runClient`：隔离客户端合成、破坏、瞄具参考渲染、100 枪压力与资源重载测试。
- `gradlew.bat -Psmoke -Ppose runClient`：实际客户端六面交互、飞行 Shift、音效和姿态同步。
- `gradlew.bat -PserverSmoke runServer`：专用服务端数据往返、Forge capability、100 原型配装和掉落测试；需要在隔离目录提供已接受的 EULA 及枪包。
- `gradlew.bat -Psmoke -PwarmupSmoke runClient`：隔离目录 `saves/warmup-stress` 的 100 原型存档，验证冷启动、重新进入、资源重载和新区域预加载。
- `gradlew.bat -Psmoke smokeJar`：仅供生产加载器回归的测试 JAR，不安装到用户实例。

测试源码不进入发布 JAR。`run-client`、`run-server` 和构建缓存均不提交。测试所需枪包不随源码或发布包分发。

本版本适配 1.20.1 自身的物品数据与存档格式，不提供 1.21.1 世界降级转换。

调整工具专项验证：在隔离实例设置默认配置模板后，使用 `gradlew.bat -Psmoke -PtoolItem=minecraft:stick runClient`，或将模板和参数均改为 `minecraft:feather`。验证配置读取、提示文本、旧工具失效、六面实际交互和音效。
