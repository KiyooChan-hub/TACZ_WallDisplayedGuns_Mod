# 0.6.12 融合地形加载与装饰枪预热

保留原 ReceivingLevelScreen，onClose 在预热未完成时暂缓关闭；Opening 事件处理其他直接关闭路径。沿用原界面背景及本地化，不再切换到独立 WarmupScreen。资源重载或自定义加载界面下继续按预算处理，不强行替换界面。保留30秒超时及增量加载退路。

验证：test build通过。隔离百枪压力存档完成进入、重新进入、资源重载三个周期；预热完成后首次转向零额外模型烘焙/上传，100枪可见，新区域增量加载通过。逐帧记录无WarmupScreen，加载截图为原Loading terrain界面。Forge使用正式重映射JAR验证。测试音乐0、主音量0.5。实际跨维度切换未单独实机测试，复用相同ReceivingLevelScreen关闭路径。

部署本机1.21.1，旧JAR备份至Codex_Output/wallgun-integrated-loading-backup。仅部署正式模组，不改玩家音量、不部署测试制品。
