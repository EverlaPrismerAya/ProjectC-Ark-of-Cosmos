# 怒气与肾上腺素移植

来源：工作区相邻的 Revengeance-1.20.1。源码、HUD 图、图标、音效和 GPLv3 授权与 SHA-256 来源记录随 JAR 放在 META-INF/licenses/revengeance/。

默认怒气 V、肾上腺素 B。在控制设置中允许绑定相同键盘键或鼠标按钮；一次按下分别匹配两个绑定，服务器分别检查充满条件。不满足条件的一方不会阻止另一方。长按不重复激活，界面打开时不触发。

怒气现按 CalamityModPublic `1a8cebd27ec5615316b78f71973446b5528d2b78` 的规则移植（`CalPlayer/CalamityPlayerMiscEffects.cs:UpdateRippers`、`CalamityPlayerOnHit.cs:OnHitAnything`、`CalamityPlayerHitHurt.cs`、`Balancing/BalancingConstants.cs`）：距离取敌人碰撞箱中心距离减最长半轴，16 像素映射 1 格并扩展至三维；10 格内满收益，50 格外无收益，使用原非线性曲线；普通目标不叠加，取最高收益，Boss 最高三倍；近普通敌人 45 秒、近 Boss 15 秒充满。造成/受到伤害延迟衰减 10 秒，邻近本身仅短暂维持战斗，脱战后 30 秒耗尽。默认永久强化 3 次，基础 9 秒加 3 秒，激活持续 12 秒、伤害乘 1.35。饰品被动怒气、破碎社区受伤转怒气、盖尔大剑特殊怒气机制未引入本项目。

配置统一位于 projectc-common.toml；详情见根目录 COMBAT_CONFIGURATION.md，旧独立 TOML 不再读取。旧扫描与距离配置已被新的邻近配置替代。

肾上腺素保留原项目 128 格 Boss 检测、约 30 秒充满、无 Boss 时 1 秒耗尽、满值抗性 II、受伤暂停充能 20 tick 及原有损失阈值。激活持续 100 tick，攻击伤害乘 3.1。两个效果叠加时乘 4.185。增伤为原项目的攻击伤害属性修饰器。

肾上腺素不再读取 Boss 标签：反射读取实体及父类中的非静态 BossEvent 字段，实际持有 ServerBossEvent（含自定义子类）才算 Boss；字段信息按实体类缓存，实例值每次检查。末影龙单独检查龙战管理器。死亡实体、空字段、静态字段不算。未改怒气充能规则。纯客户端独立绘制、集合/外部管理器中的自定义血条不保证自动识别，需要对应模组的服务端关联接口。充能、损失和激活由服务端执行，同步属性供客户端 HUD 显示。

Forge 1.20.1 没有 incompatible 依赖类型，使用可选依赖 [0] 排除已发布的 Revengeance 版本，并在构造器中再次拒绝任何 revengeance 版本。模组描述中明确显示冲突说明。

验证命令（测试类不进入正式 JAR）：

```powershell
.\gradlew.bat -I build-tools/revengeance-tests.gradle runGameTestServer --offline --console=plain
.\gradlew.bat -I build-tools/revengeance-client.gradle runClient --offline --console=plain
.\gradlew.bat build --offline --console=plain
```

2026-11-08 验证：服务端组合 GameTest 通过；真实客户端默认键位、同键盘键、同鼠标键及 C2S 双激活通过；build、7 项 Mixin 成员映射检查通过；发布 JAR 资源和测试类隔离检查通过。在隔离目录加入 modid=revengeance、版本 1.0.0 的加载夹具，Forge 依赖解析确认拒绝（expected [0,0], actual 1.0.0）。未进行真实双客户端联机或光影验收。

后续 Boss 血条判定验证：服务端 GameTest 通过，包括凋灵字段识别、父类私有 BossEvent 字段持有自定义 ServerBossEvent 子类时充能、同一实体类空字段不充能、静态字段不计入、普通活体不计入、死亡 Boss 不计入，以及原有怒气与肾上腺素规则回归。末影龙使用其公开龙战管理器关联；未对每一种第三方自定义血条实现逐一验收。

直接扣血兼容修复：ArkDamage 的公共生命值结算在玩家实际掉血后调用肾上腺素受伤处理，补齐至尊灾厄等绕过 LivingHurtEvent 的伤害路径；被冷却拒绝或未掉血的攻击不触发。原生受伤事件继续使用同一处理，不额外广播模拟事件。

怒气规则验证：3 项服务端 GameTest 通过，覆盖多目标不叠加、Boss 三倍收益、原非线性曲线、伤害双向战斗宽限、10 秒脱战衰减、默认三强化 12 秒持续时间，以及既有肾上腺素/配置回归。
