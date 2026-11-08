# 恣睢移植验收记录

日期：2026-11-08。行为基准：Calamity `1a8cebd27ec5615316b78f71973446b5528d2b78`。

后续内置附魔/追踪调整：恣睢以现有 `InnateEnchantment` 战斗视图提供破妄 X，并显示说明；命中使用攻击武器快照，实际附魔等级可叠加。共用自动目标筛选排除 `CalamitasSepulcherEntity` 全部五种部位；银河与环境之刃光束等缓存目标路径同样检查追踪资格。没有将灾坟魔物加入直接命中的全局免疫。新增两项 GameTest 验证内置等级、NBT 不变、实际等级叠加/快照，以及所有部位不可追踪但可直接命中；连同已有项目共 12 项全部通过（`reference/violence/innate-tracking-gametest.log`）。本次未重跑客户端视觉与联机验收。

后续准星锁定调整：接入银河共用的 12° 目标选择，恣睢限定 256 方块；左键追随目标中心，右键按锁定方向发射。新增远距离 GameTest，验证 200 方块偏轴目标可选、260 方块轴线目标被排除、投掷方向和无目标回退；连同原 9 项恣睢测试共 10 项全部通过（`reference/violence/aim-gametest3.log`）。完整构建通过（`reference/violence/aim-build.log`）。以下客户端截图与联机验收来自此次锁定调整之前，此次调整未重跑客户端验收。

## 自动与实际运行结果

| 检查 | 结果与证据 |
|---|---|
| 9 项 Violence GameTest + 23 项既有巫咒回归 | 全部 32 项通过；`reference/violence/gametest-final.log` |
| 正式构建、Mixin/SRG/refmap 审计、`checkCalamitasCore` | 通过；`reference/violence/release-final.log` |
| Oculus/Embeddium，光影关闭，两个独立客户端 | 通过；`reference/violence/client-plain2.log`，`client-1794069571616/peer.log` |
| Oculus/Embeddium，BSL 8.4.01.2 | 通过，运行时断言确认光影启用；`reference/violence/client-bsl2.log` |
| 原生渲染，排除 Oculus/Embeddium，两个独立客户端 | 通过；`reference/violence/client-native.log`，`client-1794069897356/peer.log` |
| JSON、资源 SHA-256、正式 JAR 测试隔离 | 通过；正式包无测试类、测试结构或仅测试用 Pig 回退标签 |

GameTest 覆盖：面板伤害/颜色/剑标签，全部 12 种炼注中恰好六种合格；连续 20 次 Boss 战利品生成每次一把；源帧节奏和旋转、子步扫掠、15 倍投掷、单实体、局部命中冷却、条件衰减/停顿、持续无害回收；实际 ArkDamage 权威血量、护甲与 35% 保底、无敌帧、图腾、死亡掉落与特殊标签回退；魔焱投掷和原有效果、四秒巫咒、30% 减益、两倍 DOT、普通/强化重叠与降级、火抗、免疫、药水外壳移除、死亡重生清理；切换武器后的来源/炼注/抢夺快照。

旧回归夹具只在测试生成副本中适配当前 Boss 首刻初始化，以及隔离连续测试累积的动态减伤。没有为通过测试改动 Boss 生产逻辑。

客户端使用实际网络输入，在独立平坦世界测试：左键持续操控并自然命中牛、松开回收、向上三维操控、右键穿过石墙自然命中、回收后无残留。另一独立 JVM 通过 loopback TCP 加入，观察两种模式、回收及清理。原生和 BSL 的 `aim3d` 截图已查看，可见原始模型、旋转残影和轨迹；截图位于各自控制目录。

- 原生：`reference/violence/client-1794069897356/aim3d-plain.png`
- BSL：`reference/violence/client-1794069806872/aim3d-bsl.png`

## 资源与适配边界

原始资源出处及 SHA-256 在 `licenses/RESOURCE-SOURCES.json`。本地原始 PNG 与固定提交 PNG 的所有非透明像素相同，差异仅存在于 alpha=0 的 RGB；SylvestaffStreak 完全一致。音效由本地原始 XNB 的 PCM 转为 Vorbis q8，保留源文件、PCM 和输出哈希。StarProj、Item1 和 TrailStreak 着色器复用已有同源资源。中文核对本地 CalamityCN，并保留其文件哈希。

二维鼠标操控映射为三维视线；有机/无机命中表现按 Minecraft 生物类别适配。三像素厚度、粒子、轨迹已实际渲染。回收火焰复用 Minecraft 火焰粒子；没有实现 Terraria 的彩色场景动态光照。资源加载及音效触发通过，但未人工试听音量/听感。未完成所有镜头、夜间/水下、其他光影包、长时间联机压力和专用服务器跨维度场景的人工验收；GameTest 生命周期检查不能替代这些验收。

正式产物位于 `reference/violence/release-build/libs/`。所有验证产物与运行目录均与共享工作区默认 `build/`、`run/` 分离。
