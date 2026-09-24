# 0.5.0：真枪自由摆放

仅更新 Minecraft 1.21.1 / NeoForge；1.20.1 仍保留原有合成方式。

- 取消双向合成，保留旧装饰枪物品注册用于存档迁移，并阻止其直接放置。创造中键选取现有装饰枪返回完整原枪。
- 默认 P 键切换模式；客户端配置 `toggleKey` 给出初始键位，控制设置可覆盖。模式内手持真枪右键走服务端校验后放置，左键保留原版空手破坏路径；TACZ 输入暂停，退出后恢复。
- HUD 由代码绘制橙色边缘提示和右上角黑色说明框；当前语言及实际按键决定文本宽度，右端固定，文字不换行，过长时整体缩小。
- `ItemStack.parseOptional` 读取旧物品时还原真枪；登录玩家背包和末影箱、已加载物品实体与展示框，以及打开的容器另作补充迁移。第三方非标准存储需要分别验证。

验证：`gradlew test compileSmokeJava` 通过。隔离测试客户端执行 `gradlew -Psmoke -PplacementSmoke runClient`，报告 `verification/placement.txt` 和 `verification/placement-client.txt` 均为 PASS：合成关闭、旧物品 NBT 还原、旧物品禁止放置、六面真枪放置、原枪数据、创造中键、生存消耗、掉落、关闭模式拒绝放置、P 键、TACZ 输入屏蔽／恢复和实际客户端右键网络放置。测试客户端音乐关闭，主音量 50%。
