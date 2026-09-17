# GitHub 远端迁移

日期：2026-09-17。

- 目标：https://github.com/KiyooChan-hub/TACZ_WallDisplayedGuns_Mod
- 分支：master。
- 本地：`D:/FORK/TACZ_WallDisplayedGuns_Mod`。

用户明确授权本次 push，作为 AGENTS.md 默认禁止自动推送规则的一次性例外；未来任务仍遵循默认规则。

## 已完成

- 恢复清理后的历史（原本地分支在一次回退和拉取旧远端后重新带回了混合历史）。
- 首次推送包含 18 条完整提交，HEAD 为 `583e57e3d86ce6fef362adf1b48fcbbb369efe81`，涵盖 15 条装饰枪开发记录及后续迁移、部署、清理记录。
- 从 GitHub 独立克隆到验证目录，确认 HEAD、18 条提交数量及 Git 对象完整性一致。
- 本地 origin 的 fetch/push 地址与 master 上游均改为 GitHub；删除临时迁移分支。
- 本记录另作为迁移说明提交，不改动产品代码、构建产物或已部署游戏。

## Codeup 清理状态

本地已不再配置 Codeup 远端。Codeup 平台上的旧仓库尚待用户通过仓库设置删除：当前浏览器控制工具无法启动，现有 Git 认证无法执行平台仓库删除。不能将“移除本地远端地址”视为“已删除远端仓库”。

## 校验与备份

`E:/GAME/Minecraft/Codex_Output/wall-display-github-migration-20260917/` 保存清理后的完整历史 bundle 与 GitHub 独立克隆验证目录。已通过 bundle verify 和 git fsck 检查。
