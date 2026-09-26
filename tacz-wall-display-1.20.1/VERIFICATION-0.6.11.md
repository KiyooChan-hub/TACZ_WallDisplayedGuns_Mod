# 0.6.11 同步首帧动画修复

同步 1.21.1 当前首帧动画保护到 Forge 1.20.1。当前两版的本地化资源一致，1.20.1 保留此前生产环境 refmap 与重映射修复。

平台差异：Forge TACZ GunItemRendererWrapper 重写 renderFirstPerson，不调用父类对应入口。保护必须注入该枪械入口；通过父类 Invoker 调用原生 needReInit / tryInit。不匹配且需初始化的旧渲染快照在绘制前取消，匹配时初始化后继续正常渲染。已初始化和退出动画沿用原流程。未更改静态批处理或枪械快照。

验证：test build smokeJar --offline 通过；重映射后正式 JAR 在隔离 Forge 47.4.21 客户端进入全新世界，首帧初始化、六面摆放、旧物品迁移、选取、掉落、翻面、旋转、模式键、HUD、禁火提示检查通过。音乐0、主音量0.5，其余分类1.0。用户反馈的偶发闪烁仍需完整整合包重载后验收。

部署至本机 Minecraft 1.20.1 mods，旧0.6.10备份至 Codex_Output/wallgun-forge-first-person-0.6.11/backup。正式实例不部署测试JAR、不修改音量。
