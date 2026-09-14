# 0.2.1 瞄具静态捕获修复 — 2026-09-15

## 根因与修复

只读检查用户存档确认三把 N4 装饰枪都保留了 AttachmentSCOPE：`tacz:sight_exp3`、`mk16:553_g43`、`tacz:scope_elcan_4x`。缺失发生在渲染阶段，没有证据表明合成丢失配件。

TACZ 的 BedrockAttachmentModel 在 FIXED 上下文中通过私有 renderTempPart 独立绘制 scopeBodyPath 和 ocularRingPath，再进入通用模型通道。0.2.0 只拦截通用通道，独立部件直接写到实时缓冲后被隐藏，没有进入装饰枪的永久静态模型。

新增 AttachmentCaptureMixin：只在本模组捕获窗口内且上下文为 FIXED 时，按原父节点路径变换，暂时显示并捕获目标部件，finally 恢复隐藏状态和矩阵栈。随后取消该次实时缓冲提交。捕获窗口外不干预 TACZ；不恢复每帧瞄具动画、模板缓冲或实时视野计算。

注册 ID、原枪快照、配方与掉落机制不变。0.2.0 已摆放方块可继续使用，完全重启后重新捕获即可，无需重做转换。

## 修正验收盲点

0.2.0 的展示框“参考数据”与产品共用了捕获钩子，可能同时漏掉同一部件。此前仅靠两边相等判定“完整配件外观通过”并不充分；其中 ACOG 枪实际只有 11,228 个被捕获顶点。

本轮新增仅在 smoke 源集中存在的 ReferenceBufferMixin，在 Minecraft 实际 getBuffer 提交层记录 TACZ 完整绘制。在采集参考值时，MeshCapture.active 为 null，所有产品捕获钩子均不工作。因此外壳／目镜环走原始路径，参考记录不会沿用本轮产品的路径拦截逻辑。参考测试补丁和测试模组不进入发布 JAR。

同时生成正常渲染的真实 ItemFrame 与装饰枪并排截图，供可见外观核对。三把 N4 按用户实际配件 ID 组合重建，包含原消音器、握把和枪托。

## 独立几何对照

比较材质／纹理集合、有效顶点数、每个顶点的相对坐标、UV、法线以及贴墙间隙，结果如下：

```
tacz:scar_l: 10596 vertices; maximum relative position error=1.7881393432617188E-7; visible wall gap=0.0010000001639127731
tacz:ak47: 8772 vertices; maximum relative position error=1.1920928955078125E-7; visible wall gap=0.0010000001639127731
mk16:m4urgi10: 15244 vertices; maximum relative position error=2.086162567138672E-7; visible wall gap=0.0010000020265579224
equipped: 13468 vertices; maximum relative position error=1.1920928955078125E-7; visible wall gap=0.0010000001639127731
scope/tacz:sight_exp3: 18080 vertices; maximum relative position error=1.6391277313232422E-7; visible wall gap=0.0010000020265579224
scope/mk16:553_g43: 21752 vertices; maximum relative position error=1.7881393432617188E-7; visible wall gap=0.0010000020265579224
scope/tacz:scope_elcan_4x: 19140 vertices; maximum relative position error=1.7881393432617188E-7; visible wall gap=0.0010000020265579224
```

ACOG SCAR-L 现在为 13,468 个有效顶点，相比旧版补回 2,240 个顶点。三种 N4 瞄具组合均为五种材质，保持原始任意角度及枪包纹理。最大相对位置误差小于 2.1e-7 格，最近有效面距墙约 0.001 格。

## 运行验收和性能

8 项单元测试通过；完整隔离客户端运行通过，SUCCESS 记录包括两种合成菜单、持久／网络编解码、完整原枪数据往返、四方向放置和掉落、资源重载及缓存稳定性。三种实际瞄具并排截图已经人工核对，均显示完整外壳，参考是真实 ItemFrame 的正常渲染。

测试沿用复制的枪包、Sodium、Iris、Complementary v4.7.2、RuOK 和相关配置，60 FPS 上限。统计排除各阶段最初 20 tick；截图在采样窗口外执行。

| 场景 | 统计帧数 | P99 | 最大耗时 | 超过 40 ms |
|---|---:|---:|---:|---:|
| 100 把裸枪近墙运动 | 1014 | 18.37 ms | 18.94 ms | 0 |
| 100 把裸枪 19 次增删 | 1192 | 18.83 ms | 32.55 ms | 0 |
| 100 把完整三配件枪近墙运动 | 1015 | 18.44 ms | 18.88 ms | 0 |

裸枪墙和完整三配件墙 360 tick 近墙运动均零 GPU 重建；19 次增删仍只执行 19 次局部上传，最多 42,384 个顶点（4 把裸枪）。完整 ACOG 瞄具增加了真实几何，100 把带配件枪仍保持 120 个固定材质批次，未恢复逐帧骨骼渲染。

测试使用独立新存档与内置服务器，没有修改用户已有世界。外置多人服务器和其他任意枪包组合不在本次实机覆盖范围内；第一次看到未缓存外观仍有一次捕获成本。

发布包不包含测试模组、参考捕获钩子、截图或枪包。部署清单记录 SHA-256 和旧 JAR 备份。
