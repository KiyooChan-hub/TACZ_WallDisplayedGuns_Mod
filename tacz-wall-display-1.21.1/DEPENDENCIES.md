# 两版本前置评估与 CurseForge 发布关系

审查对象：TACZ Wall Display 0.4.1 的 1.21.1 NeoForge 与 1.20.1 Forge 源码，以及本机对应 TACZ JAR。此次只修改加载元数据与文档，不打包、部署或上传。

## 硬性加载约束

| 项目 | 1.21.1 | 1.20.1 |
| --- | --- | --- |
| 元数据 | `META-INF/neoforge.mods.toml` | `META-INF/mods.toml` |
| Minecraft | `[1.21.1]` | `[1.20.1]` |
| 平台 | NeoForge `[21.1.248,21.2)` | Forge `[47.4.21,48)` |
| TACZ，mod ID `tacz` | `[1.1.8-hotfix-r6]` | `[1.1.8-hotfix]` |
| 强制字段 | `type="required"` | `mandatory=true` |
| 依赖存在侧 | `side="BOTH"` | `side="BOTH"` |
| TACZ 顺序 | `ordering="AFTER"` | `ordering="AFTER"` |
| Java 支持基线 | 21 | 最低 17，使用兼容运行时 |

以上模组依赖原本已声明为硬性限制。本次保留 TACZ 精确版本和平台范围，将 1.21.1 的 Minecraft 范围统一成精确版本，补充两版模组列表说明，以及 NeoForge 的 TACZ 依赖原因提示。不是通过运行时静默禁用功能处理缺失前置。Java 要求由对应 Minecraft／加载器及字节码约束，本次未另加 Java 模组依赖。

`[版本]` 是精确匹配，不是最低版本。平台下限是当前已经验证的支持基线；现有测试不足以声明更早的平台版本兼容，也不能据此保证所有未来补丁版本。TACZ 的内部渲染 Mixin 涉及模型捕获、配件、光束隐藏、展示变换和可选加速桥接，升级 TACZ 需要重新核验这些接口。

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

## 验证范围

本轮只涉及加载元数据和说明，因此验证 TOML 解析、客户端／服务端必需字段、实际 TACZ JAR 版本、Maven 版本范围的正反例，以及 Gradle `processResources` 输出与源文件一致。不重新运行压力场景，不把元数据验证表述为新增的游戏启动实测。

## 依据

- [NeoForge 1.21.1 加载元数据](https://docs.neoforged.net/docs/1.21.1/gettingstarted/modfiles/)
- [Forge 1.20.x 加载元数据](https://docs.minecraftforge.net/en/1.20.x/gettingstarted/modfiles/)
- [CurseForge 文件标签与依赖关系说明](https://support.curseforge.com/support/solutions/articles/9000197242)
- [官方 Forge TACZ 对应文件](https://www.curseforge.com/minecraft/mc-mods/timeless-and-classics-zero/files/8141310)
- [非官方 NeoForge TACZ 对应文件](https://www.curseforge.com/minecraft/mc-mods/tacz-1-21-1/files/8547439)
