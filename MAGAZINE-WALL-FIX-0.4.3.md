# 1.12.0 / Wall Display 0.4.3

用户要求：FDE ELCAN +10%，修正聚合物弹匣416D挂墙前突，执行部署但不测试。

源码排查：三种Spearhead CAG主弹匣magazine的pivot为[0,5,2.2]，备用节点additional_magazine仍为[0,5,2]。TACZ的renderAdditionalMagazine功能渲染器会在备用节点坐标处重复绘制主弹匣几何；Blockbench中的这个辅助节点本身无方块，不运行TACZ功能渲染，因此不显示该副本。独立挂墙模型未经过第一人称动画监听器的备用弹匣隐藏过程，旧节点位置使副本前突0.2。

修正位于独立仓库GunOrientation.prepareModel：仅在spearhead:hk416d_cag_display、hk416d_cag_fde_display、hk416d_cag_two_tone_display的detached模型中隐藏additional_magazine。没有移动主弹匣或改变其几何；不改手持模型、换弹动画或其他枪包。经典金属弹匣版本及TACZ原版416A5不属于该错位节点名单。

枪包：ELCAN颜色贴图目标FDE壳体乘1.10，同步bbmodel与物品栏图标。

构建部署：挂墙模组使用Gradle --offline jar -x test完成必要编译；枪包直接ZIP打包。未运行单元测试、自动验证、游戏或渲染验收。部署枪包1.12.0及1.21.1挂墙模组0.4.3，旧版均备份。1.20.1未修改。部署路径与备份记录见枪包deployment.json。
