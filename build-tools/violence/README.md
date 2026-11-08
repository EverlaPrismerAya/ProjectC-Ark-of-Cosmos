# 恣睢（Violence）

`arkofcosmos:violence`：剑，面板基础伤害 24.25，名称颜色 `#6C2DC7`。正常击败至尊灾厄时，战利品表固定额外掉落一把。

自带破妄 X，复用现有战斗附魔视图，与实际附魔等级相加，不写入物品附魔 NBT。需要安装提供破妄的 Goety Revelation。模组自动锁定与追踪统一排除灾坟魔物的全部部位，直接碰撞命中仍按原有伤害流程处理。

按住左键操控旋转武器，松开或切换武器后回收；右键投掷，初伤为基础攻击的 15 倍，速度为左键初速的 1.6666 倍。沿用银河的 12° 准星敌对实体锁定，锁定距离 256 方块：左键跟随目标中心，右键朝锁定目标投掷；无目标时沿视线操控。每名玩家最多存在一个攻击实体，不结算普通剑挥砍伤害。投掷穿墙、穿透目标，同一目标只命中一次；有效命中按原作条件停顿并将后续伤害乘 90%，回收阶段停止投掷伤害。

右键命中施加 4 秒孱弱巫咒。魔焱炼注令这次巫咒强度翻倍：持续伤害翻倍，护甲与对外伤害减益从 15% 增至 30%，持续时间不变。普通与强化巫咒独立计时，重叠取较强者，强化到期后恢复尚未到期的普通效果。与魔焱自身持续伤害重叠时取较高值，不重复扣血。沿用现有巫咒免疫、药水外壳清除和死亡重生规则，不添加禁疗。

可用普通炼注只有：魔焱、须臾、凋零、憎懣、嗜血、淫欲。攻击生成时保存武器与炼注快照；投掷后换武器不改变已经发出的攻击。

行为依据 Calamity `1a8cebd27ec5615316b78f71973446b5528d2b78` 的 `Violence`、`ViolenceThrownProjectile`。移植按每格 16 源像素、每游戏刻 3 源帧换算，保留左键 2、投掷 5、回收 4 次子更新，逐子步进行扫掠碰撞。二维鼠标位置映射为完整三维视线命中点。所有直接伤害调用 `ArkDamage.hurt`；归属、炼注、附魔与抢夺使用攻击快照。

模型使用原始贴图沿透明轮廓挤出三源像素，轨迹复用已有同源着色器；本地原始资源、音效转换和 SHA-256 记录见 `licenses/RESOURCE-SOURCES.json`。血液与火花按原作粒子更新，实体“有机”分类映射到 Minecraft 生物类型。回收火焰使用 Minecraft 火焰粒子；没有新增动态光照依赖。

开发验证入口（测试代码不进入正式 JAR）：

```powershell
.\gradlew.bat -I build-tools/violence-tests.gradle -PwitchRegression runGameTestServer --offline --console=plain
.\gradlew.bat -I build-tools/violence-client.gradle runClient --offline --console=plain
.\gradlew.bat '-Dviolence.bsl=true' -I build-tools/violence-client.gradle runClient --offline --console=plain
.\gradlew.bat '-Dviolence.native=true' -I build-tools/violence-client.gradle runClient --offline --console=plain
```

实际验收结果与限制记录在 `reference/violence/validation.md`；构建成功不代表所有客户端视觉、听感和联机环境均已验收。
