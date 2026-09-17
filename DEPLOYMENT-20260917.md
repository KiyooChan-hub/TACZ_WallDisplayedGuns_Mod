# 新仓库双版本构建、部署与验证

日期：2026-09-17。

源码仓库：`D:/FORK/TACZ_WallDisplayedGuns_Mod`。两版功能版本均保持 0.4.2，本轮仅重新构建与部署。

## 构建与单元测试

两版分别执行 `gradlew.bat --offline clean test build -Psmoke smokeJar`，均 BUILD SUCCESSFUL。1.20.1 使用 Forge 47.4.21（Java 17 字节码），1.21.1 使用 NeoForge 21.1.248；本轮构建及客户端运行使用本机 Java 21。

- 1.21.1：13 项单元测试，零失败、零跳过。
- 1.20.1：13 项单元测试，零失败、零跳过。

## 正式 JAR 客户端验证

使用新构建的正式发布 JAR、独立 test-only JAR、本机对应 TACZ 和正式游戏库，分别启动隔离客户端并自动新建测试存档。两版均完成进档、回归和正常退出，生成本轮 SUCCESS.txt；截图已人工视觉核对。

- EXP3、ACOG TA31、Elcan 4× 三种瞄具，在 TACZ LOD 距离 0、8、9999 下的静态顶点与材质完全一致，未捕获低模贴图。
- 验证静态网格缓存复用，且高模覆盖不影响普通真枪的远距离判断。
- 三支带配件的装饰枪在平滑石墙上成功渲染，瞄具可见。

验证边界：本轮是隔离正式客户端回归，未重新运行全整合包、所有附属枪包、百枪性能基准或外置多人服务器；未修改玩家已有存档。

## 部署结果

### 1.21.1

- 安装：`E:/GAME/Minecraft/Minecraft 1.21.1/.minecraft/mods/tacz-wall-display-1.21.1-0.4.2.jar`。
- SHA-256：`7675b352944d21e4415caf4b39f38aa4a30e38a5b2e12d852cc4bff22e0a3d9f`。
- 旧包备份：`E:/GAME/Minecraft/Codex_Output/wall-display-new-repo-deploy-20260917/backup/1.21.1/`。

### 1.20.1

- 安装：`E:/GAME/Minecraft/Minecraft 1.20.1/.minecraft/mods/tacz-wall-display-1.20.1-forge-0.4.2.jar`。
- SHA-256：`c6181744891663ab6cbdd09764373261197ed08f51dd92cba1c025680c981c84`。
- 旧包备份：`E:/GAME/Minecraft/Codex_Output/wall-display-new-repo-deploy-20260917/backup/1.20.1/`。

部署时两套玩家客户端均未运行。安装文件与已通过测试的构建文件 SHA-256 一致，玩家实例各仅保留一个正式装饰枪 JAR，不部署测试 JAR。

本轮日志、成功报告、截图及备份：`E:/GAME/Minecraft/Codex_Output/wall-display-new-repo-deploy-20260917`。构建日志位于同级 `wall-display-repository-migration-20260917/deployment-build*.log`。

未推送远端。
