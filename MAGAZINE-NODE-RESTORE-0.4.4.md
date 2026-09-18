# Wall Display 0.4.4

移除GunOrientation.prepareModel中针对三个Spearhead CAG显示ID隐藏additional_magazine的特判。恢复使用枪模型提供的辅助节点，不在挂墙端补偿或覆盖其坐标。原有其他处理不变，仅更新1.21.1。

执行必要的Gradle jar编译打包，显式排除test；不运行单元测试或实机验证。部署0.4.4并备份0.4.3。部署路径见deployment-0.4.4.json。
