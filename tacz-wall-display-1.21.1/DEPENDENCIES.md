# 两版本前置评估与 CurseForge 发布关系

审查对象：TACZ Wall Display 0.4.1 的 1.21.1 NeoForge 与 1.20.1 Forge 源码，以及本机对应 TACZ JAR。此次只修改加载元数据与文档，不制作发布包、部署或上传。隔离运行测试使用既有二进制的元数据修改副本及测试专用程序，不安装到玩家实例。

## 硬性加载约束

| 项目 | 1.21.1 | 1.20.1 |
| --- | --- | --- |
| 元数据 | `META-INF/neoforge.mods.toml` | `META-INF/mods.toml` |
| Minecraft | `[1.21.1]` | `[1.20.1]` |
| 平台存在要求 | NeoForge，版本范围 `[0,)` | Forge，版本范围 `[0,)` |
| 仅推荐的平台版本 | 21.1.248 | 47.4.21 |
| javafml 语言加载器范围 | `[0,)` | `[0,)` |
| TACZ，mod ID `tacz` | `[1.1.8-hotfix-r6]` | `[1.1.8-hotfix]` |
| 强制字段 | `type="required"` | `mandatory=true` |
| 依赖存在侧 | `side="BOTH"` | `side="BOTH"` |
| TACZ 顺序 | `ordering="AFTER"` | `ordering="AFTER"` |
| Java 支持基线 | 21 | 最低 17，使用兼容运行时 |

按用户要求取消平台和语言加载器的版本号门槛，保留“必须安装正确平台”、Minecraft 与 TACZ 的硬性约束。不是把 Forge／NeoForge 改成可选，也不是让 Forge 和 NeoForge 的 JAR 混用。

平台与 `loaderVersion` 的 `[0,)` 接受正常的历史及新版版本号。不要改成空字符串：隔离 Forge 47.3.5 的实际测试中，`versionRange=""` 被判为不满足依赖，改为 `[0,)` 后正常加载。虽然文档描述空串可匹配任意版本，实际兼容旧加载器时应以验证结果为准。

47.4.21 和 21.1.248 保留为构建及推荐测试版本，不能再称作最低版本，也不能把它们写成 CurseForge 强制更新条件。TACZ 自身及其他整合包模组的限制不由本模组覆盖；移除门槛不等于保证所有历史平台、平台补丁或第三方组合都兼容。

`[TACZ版本]` 仍是精确匹配。静态捕获 Mixin 涉及 TACZ 内部模型、配件、光束隐藏、展示变换和可选加速桥接；本轮没有修改 TACZ 版本约束。

## 为什么只需要另装 TACZ

审查生产源码的第三方引用与 Mixin 目标后，除 Minecraft／加载器供应的基础库外，直接模组引用均来自 `com.tacz.guns`。`ARCompat` 是 TACZ 自己的类，不代表 Accelerated Rendering 必装。

检查两个 TACZ JAR 的加载元数据、Manifest 和 `META-INF/jarjar/metadata.json`：

- Forge TACZ 的真实版本来自 Manifest `Implementation-Version: 1.1.8-hotfix`；NeoForge TACZ 在自身元数据中声明 `1.1.8-hotfix-r6`，不能只凭文件名判断。
- 两个 TACZ JAR 都自带 SimpleBedrockModel、LuaJ、BCEL、Commons Math 等运行库；Forge TACZ 另含 MixinExtras。用户无需重复安装这些内嵌依赖。
- 本模组捕获的是 TACZ 自带 Bedrock 渲染类，没有独立 GeckoLib API 依赖。
- Cloth Config 是 TACZ 的可选配置界面支持，不是本附属模组配置功能的依赖；本模组使用 Forge／NeoForge 原生配置。
- Sodium／Embeddium、Iris、RuOK 是测试实例的渲染或监控模组，不是必装前置。
- Packet Fixer 用于本机大型枪包集合的包大小问题，不是所有装饰枪用户都必须安装的库。
- 额外枪包和枪包要求的扩展模组，只在使用相应内容时按枪包说明安装；不会强制所有用户安装本机的全部枪包。

本审查没有新增兼容性承诺，也没有把“不是必装”写成“与任意版本均兼容”。

## CurseForge 发布关系

完整可复制英文页面：同目录 `CURSEFORGE.md`。下列是作者发布操作说明，不属于面向玩家的页面正文。

| 发布文件 | 游戏／加载器标签 | Required Dependency 项目 | 当前对应 TACZ 文件 |
| --- | --- | --- | --- |
| 1.21.1 NeoForge 装饰枪 | 1.21.1、NeoForge | `[UNOFFICIAL] TaCZ NeoForge Port`，项目 ID **1353462**，slug `tacz-1-21-1` | 文件 **8547439**，`1.1.8-hotfix-r6` |
| 1.20.1 Forge 装饰枪 | 1.20.1、Forge | `[TaCZ] Timeless and Classics Zero Guns`，项目 ID **1028108**，slug `timeless-and-classics-zero` | 文件 **8141310**，`1.1.8-hotfix` |

在每个文件的 Related Projects 中分别设为 **Required Dependency**。同一装饰枪项目同时发布两版时，不要把两个 TACZ 项目同时放进适用于全部文件的 Default Relations；两者的 mod ID 相同，玩家只安装对应一个。加载器用文件标签区分，不以普通模组前置项目代替。

CurseForge 的项目依赖关系与本地 TOML 的精确版本检查是两套机制。发布前检查自动选择的 TACZ 文件是否符合上表；关系声明不能代替版本锁定。不要把 GeckoLib、SimpleBedrockModel、Cloth Config 或测试辅助模组标为本模组 Required Dependency，也不要把 TACZ 内嵌库标成本模组直接嵌入的库。

本轮未登录或修改 CurseForge 后台；发布时由作者应用上述分文件关系。

## 低版本兼容性验证

- 从官方发布库获取 NeoForge 21.1.1 的源码、universal JAR、FML 4.0.24、事件总线 8.0.1。装饰枪引用的 40 个平台字段／方法全部存在；TACZ 及内嵌库引用的 215 个平台字段／方法也未发现缺失。
- 使用 Forge 47.3.5 官方安装描述和本机该版本运行库检查，装饰枪 42 个平台字段／方法全部存在。TACZ 及内嵌库检查 274 个引用，未发现缺失；其中 2 个继承自原版或 Java 的引用未用平台库单独验证。
- Forge 47.3.5 实际隔离测试通过：仅 TACZ、装饰枪与测试程序，自动创建新存档，确认模型捕获显示、服务端配置、六面真实客户端交互、创造飞行 Shift、姿态同步；18 次调整对应 18 次音效。
- NeoForge 21.1.1 / FML 4.0.24 的同项实际测试也通过。使用官方 21.1.1 二进制补丁及对应运行库、当前 TACZ 1.1.8-hotfix-r6 和当前工具专项测试程序；只安装 TACZ、装饰枪和测试程序。
- 两版测试截图均确认 SCAR-L 的瞄具、握把和消音器可见。验证的是本轮相关的基础显示和交互，不是全部枪包压力场景或所有合成路径的再次全量回归。
- 两版离线 `processResources` 成功；35 个 Maven 版本范围正反例通过，旧平台版本可接受，错误 Minecraft／TACZ 版本仍被拒绝。

二进制检查不能代替全部运行路径验证，未承诺所有旧平台或第三方模组组合兼容。隔离测试日志和截图保存在 `E:/GAME/Minecraft/Codex_Output/tacz-wall-display-platform-compat/`。所有玩家实例、现有存档和已部署 JAR 保持不变。

## 依据

- [NeoForge 1.21.1 加载元数据](https://docs.neoforged.net/docs/1.21.1/gettingstarted/modfiles/)
- [Forge 1.20.x 加载元数据](https://docs.minecraftforge.net/en/1.20.x/gettingstarted/modfiles/)
- [CurseForge 文件标签与依赖关系说明](https://support.curseforge.com/support/solutions/articles/9000197242)
- [官方 Forge TACZ 对应文件](https://www.curseforge.com/minecraft/mc-mods/timeless-and-classics-zero/files/8141310)
- [非官方 NeoForge TACZ 对应文件](https://www.curseforge.com/minecraft/mc-mods/tacz-1-21-1/files/8547439)
