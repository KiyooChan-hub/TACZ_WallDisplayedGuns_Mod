# 0.6.23 预加载超时配置与部署验证（2026-10-01）

## 变更

- 1.21.1 客户端配置新增 `preloadTimeoutSeconds`，默认 10 秒、允许 1–240 秒；仅限制 `LOADING` 初始场景准备，不改变独立的 300 秒激活安全截止时间。
- 超时或模型失败时仍向服务端请求跳过，等待匹配的释放确认才恢复游戏；已发送 `ready` 或 `refresh` 时不再因预加载时限误发 skip。
- 更新当前 CurseForge 文案中已过时的 `BACKGROUND` 默认模式说明。1.20.1 未改动。

## 验证

- 离线 `gradlew.bat --offline test build -PactivationSmoke compileActivationSmokeJava` 成功，30 项单元测试通过，生成 `tacz-wall-display-1.21.1-0.6.23.jar`。
- 隔离服务端/客户端 `fallback` 场景通过：首会话模拟模型失败，第二会话模拟超过默认 10 秒；服务端均记录 skip 并释放玩家，其余三次按 `LOADING` 正常准备。客户端/服务端 `SUCCESS.txt` 均记录五次会话通过，无 `FAILED.txt`，两个 Gradle 任务均 `BUILD SUCCESSFUL`。
- 服务端验收记录 344 次等待期间的伤害、选中、推动检查，并确认跨维度时摆放模式关闭。客户端测试实例音乐 0、主音量 0.5、其余分类 1.0；未修改正式实例音量。
- 未在玩家原整合包和外部服务器上复现反馈，因此本地验收不等于所有枪包组合已验证。

## 部署

- 正式 1.21.1 实例：`E:/GAME/Minecraft/Minecraft 1.21.1/.minecraft/mods/tacz-wall-display-1.21.1-0.6.23.jar`；SHA-256 `125EC0FEFC52157DA96D83EC81F0E35D1678A2E157AC61F802183E079393AC02`。
- 原 0.6.21 JAR 已备份到 `E:/GAME/Minecraft/Codex_Output/wall-display-0.6.23-20261001-081021/`；备份与原文件哈希一致。正式模组目录仅保留 0.6.23。
- 运行中的游戏需要重启才能加载新 JAR。联网服务器也必须安装匹配版本；未部署到外部服务器、未同步 1.20.1、未执行 push。
