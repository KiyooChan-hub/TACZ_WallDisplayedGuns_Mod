# 0.6.21 跨维度自动退出摆放模式同步（2026-09-29）

## 实现

- 从 1.21.1 同步服务端权威关闭、加载会话标记以及客户端放行后提示逻辑，使用 Forge 1.20.1 对应事件和网络实现。
- 服务端成功维度变更后关闭模式；客户端立即解除状态和禁火拦截，并在加载界面消失后复用手动关闭的聊天与 `MODE_CLOSE` 音效。
- 不依赖加载期被拦截的普通模式包，登录、同维度重生、已关闭状态及取消传送不会重复触发。

## 验证

- `gradlew.bat --offline test build -Psmoke compileSmokeJava` 成功；30 项 JUnit，零失败、零错误、零跳过；Forge 正式 JAR 完成重混淆。
- 隔离单机客户端以真实 P 键路径开启摆放模式，确认 TACZ 输入被抑制；随后由整合服务器传送至下界。
- 下界加载放行后，服务端与客户端模式均关闭，TACZ `InputExtraCheck.isInGame()` 恢复，验收文件通过。
- 截图确认玩家恢复控制后聊天栏显示 `Gun placement mode: Off`，摆放 HUD 已消失。
- 测试实例音乐 0、主音量 0.5、其余声音分类 1.0；未修改正式实例个人设置。
- 正式 JAR 不含 smoke 测试类。

## 制品与部署

- `tacz-wall-display-1.20.1-forge-0.6.21.jar`，176,971 字节。
- SHA-256：`077644AC18320CD3C1FE90EEF485886E8208937A1DE0E180194B28197BB6CE4A`。
- 已部署至 `E:/GAME/Minecraft/Minecraft 1.20.1/.minecraft/mods/`，该目录只保留 0.6.21；部署包与构建包哈希一致。
- 0.6.20 备份及 0.6.21 归档位于 `E:/GAME/Minecraft/Codex_Output/wall-display-0.6.21-20260929-132914/`。
