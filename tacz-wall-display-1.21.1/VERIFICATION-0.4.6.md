# 0.4.6 验证与部署

- 需求：WTHIT 指向装饰枪时显示原枪本名，不显示装饰枪前缀。
- 通过可选客户端插件替换 OBJECT_NAME_TAG，保留名称格式、图标及来源行。采用原枪 Item.getName，不采用铁砧自定义名称。不修改枪械数据或渲染逻辑。
- `gradlew --offline test build smokeJar -Psmoke`：构建及 13 项单元测试通过。
- 真实 NeoForge 21.1.248 客户端 + TACZ 1.1.8-hotfix-r6 + WTHIT 12.10.2 + Bad Packets 0.8.2：自动创建隔离存档，准星实际提示分别为 `Noveske N4 突击步枪` 与 `AKM 突击步枪`，来源行保留；截图已检查。测试检查 WTHIT 实际已生成的 TooltipRenderer 内容，未直接调用兼容提供器。
- 卸载隔离实例的 WTHIT/Bad Packets 后，正式 JAR 正常启动、进档；三种高模瞄具在 0/8/9999 距离设置下的静态捕获回归通过。
- 早期测试发现插件 side 大小写错误并修正为 client；测试引导界面和读取提示阶段也已修正，最终重新运行通过。用户要求可见窗口后，测试均以交互桌面权限启动。
- 用户追加音量规范后，无 WTHIT 回归在音乐 0、主音量 0.5 下重新运行。已写入全局与本仓库 AGENTS.md，玩家实例音量不改动。
- 测试证据：`E:/GAME/Minecraft/Codex_Output/tacz-wall-display-0.4.6/production-test/wthit-verification` 和 `lod-verification`。
- 部署：`E:/GAME/Minecraft/Minecraft 1.21.1/.minecraft/mods/tacz-wall-display-1.21.1-0.4.6.jar`；0.4.5 备份于输出目录 backup。正式实例无测试 JAR，1.20.1 未改动。
- 最终构建与部署 SHA-256：`6bf94519617d9797490e75d9577d493ed0ed8e60aadf502bc424e33855772fda`。最后仅删除插件 JSON 末尾空行；与实机测试制品逐个 ZIP 条目比对，其他内容完全相同、JSON 解析结果一致，重新构建与单元测试通过。
- 未执行远端推送。
