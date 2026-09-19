# 0.4.7 验证与部署

- 默认面右键逆时针旋转 22.5°；翻面后顺时针，再翻回恢复逆时针。保留按玩家观察侧补偿、六面放置、Shift 翻面、单次音效与已有保存姿态。
- 更新中英文物品提示、README 与 CurseForge 使用说明。
- `gradlew --offline test build smokeJar -Psmoke`：构建及 13 项单元测试通过。
- 真实 NeoForge 21.1.248 / TACZ 1.1.8-hotfix-r6 隔离客户端：六面各完成默认面逆时针 16 步及翻面顺时针 16 步，背侧默认面方向、翻面恒等、保存/同步及生存/创造掉落回归通过。
- 实际客户端使用物品包：六面均完成默认面旋转、Shift 翻面、翻面后旋转、再次翻回；服务端与客户端状态一致，24 次操作恰好 24 次展示框音效。截图已保存并检查南面姿态。
- 100 支三配件枪的静态压力回归：180 tick 无上传，32 次姿态编辑无 TACZ 重烘焙，单次上传不超过 4 支枪。
- 测试在可见交互桌面启动，启动前音乐设为 0、主音量 0.5、其余分类 1.0；不修改玩家正式实例音量。
- 证据：`E:/GAME/Minecraft/Codex_Output/tacz-wall-display-0.4.7/production-test/pose-verification`。
- 已部署 `E:/GAME/Minecraft/Minecraft 1.21.1/.minecraft/mods/tacz-wall-display-1.21.1-0.4.7.jar`，0.4.6 备份至输出目录 backup；正式实例仅一份产品 JAR，无测试 JAR。
- 构建、实机测试及部署制品 SHA-256 一致：`5b76b45fdc49ec1a908cb42763d3c9b965933850f69a054da3bd85785739bcb7`。
- 1.20.1 未更新，未进行远端推送。
