# 0.6.10 首次持枪动画保护

仅更新 Minecraft 1.21.1 / NeoForge。中键选枪后的 TACZ kept-item 过渡帧可能与主手快照不一致；原生渲染事件在该情况下不初始化动画，却继续绘制。

第一人称渲染入口仅在 needReInit 为真时介入：若渲染快照与当前主手不匹配，则跳过未初始化的过渡帧；匹配时调用 TACZ 原生 tryInit，再沿用原生渲染更新。已初始化动画和 put-away 等退出过渡保持原流程。未改变静态展示批处理、枪械数据或背包同步。

验证：test build --offline 通过；隔离客户端 placementSmoke 通过；firstPersonSmoke 实测未初始化且不匹配的帧在绘制前退出，匹配的首帧初始化并完成 TACZ 渲染。测试音乐 0、主音量 0.5。偶发用户原情景尚未稳定重现，需正式整合包重载后验收，不能宣称已穷尽第三方兼容问题。

正式部署到本机 1.21.1 mods，旧 0.6.9 备份在 Codex_Output/wallgun-first-person-0.6.10-backup。未更新 1.20.1。
