# 公共配置与战斗增益

配置文件为 `config/projectc-common.toml`，支持热重载并同步客户端。以下数值为代码默认值，实际游戏以服务端配置为准。

客户端显示配置位于 `config/projectc-client.toml`。

## Boss 与武器

`boss.mahou` 提供生命、总技能倍率与各技能倍率。默认生命 1440。Infernum 阶段门槛固定为 75%、45%、25%，不再使用旧阶段及式神门槛。

`boss.mahou.skill_damage_multiplier` 是全部攻击的总倍率。`boss.mahou.attacks` 提供新弹幕的独立倍率，`boss.mahou.contact` 提供接触伤害的独立倍率。默认均为 1，范围为 0–100，0 禁用对应来源的伤害。各倍率在命中时读取，热重载影响已经生成的弹幕。派生弹幕只应用自身类型的倍率，不继承父弹已经计算的倍率。

| 配置组 | 独立配置项 |
| --- | --- |
| `attacks` 普通与尾杀弹幕 | `dart`、`final_dart`、`dark_soul`、`redirect_soul`、`arrow`、`hellblast`、`flame` |
| `attacks` 爆弹、火柱与激光 | `giga`、`explosion`、`pillar`、`jewel_beam`、`overload_beam` |
| `attacks` 召唤物弹幕 | `bone`、`soul_bomb`、`shadow_blast`、`shadow_giga`、`shadow_bolt`、`shadow_spark`、`fist`、`slash`、`lost_soul`、`redirect_hellfire` |
| `contact` 本体与召唤物 | `boss`、`worm`、`segment`、`cataclysm`、`catastrophe`、`seeker`、`shadow`、`bomber` |

例如，`boss.mahou.attacks.pillar=0.5` 将尾杀火柱伤害减半，`boss.mahou.contact.boss=0` 禁用本体冲锋接触伤害。实际基础伤害仍由招式提供，配置倍率与魔法使既有狂暴倍率相乘；预警、装饰及零伤害爆炸不会因此变成有伤害的攻击。

旧 `boss.mahou.skills` 整组已移除，包括旧火柱、领域、武器及 `other` 等配置。Forge 加载或热重载时会清理旧组并生成新组。生命和总倍率保留；旧技能倍率不自动映射到新攻击。

`boss.fourth_calamity` 提供 `health_bars`、`bar_health`、总攻击倍率与各攻击倍率。默认44管、每管20生命，共880。

`boss.calamitas` 提供 `health_multiplier=0.002`、`damage_multiplier=0.24`、`armor_multiplier=0.1`、`movement_multiplier=1`、`pixels_per_block=16` 和各技能倍率。

像素换算在创建战斗时捕获。普通炼注费用由 `enchant_cost=32`、`disenchant_cost=8` 控制。

`apollyon_replacement_chance=0.01` 控制下界新生成 Apollyon 的替换概率，0禁用、1必定替换。

### 冴月麟配置

`boss.satsuki_rin` 独立提供冴月麟的 Apollyon／RevelationFix 战斗配置，不修改上游全局值或普通 Apollyon。生命热重载保留当前生命比例。

数值随公共配置同步客户端。默认为6666生命、至尊灾厄动态减伤，无额外固定减伤或可选单次限伤。

| 配置项 | 默认值与作用 |
| --- | --- |
| `health` | 6666，最大生命 |
| `hurt_limit` | 0，关闭可选单次受伤上限。正值启用 |
| `permanent_damage_reduction` | 0，额外固定减伤百分比 |
| `dynamic_damage_reduction` | true，使用至尊灾厄动态减伤 |
| `invulnerable_time` | 10 游戏刻，非玩家伤害冷却。保留玩家伤害规则与非玩家单次50保护 |
| `hellfire_damage_amount` / `hellcloud_damage_amount` | 0.0444 / 0.001，所属原生技能按受害者最大生命计算的伤害比例 |
| `barrier_preparation` | 3，原生亡语结界展开秒数 |
| `shooting_cooldown` | 1600 游戏刻，原生死亡箭技能冷却。末日减半 |
| `second_phase_damage_multiplier` / `genesis_damage_multiplier` | 2 / 4，原生二阶段与创世纪额外伤害倍率 |
| `fractal_sigil_damage` | 16，二阶段分形法印每道第一分形剑影的伤害 |
| `armor_piercing` / `enchantment_piercing` | 30 / 30，原生穿透属性百分比 |
| `modpack_mode` | false，开启后采用下列自定义回复比例与末日生命比例 |
| `self_healing` | 0.002，每次原生自然回复触发时的最大生命比例 |
| `arrow_healing` / `second_phase_arrow_healing` | 0.005 / 0.01，一／二阶段死亡箭命中回复比例 |
| `doom_percent` | 1/14，末日阶段最大生命比例 |
| `barrier_killing_mobs_healing` | true，原生亡语结界击杀后的复苏开关 |
| `powerful_servants` | false，安装版本支持时开启原生随从强化。关闭不撤销已发放护甲或永久属性 |

随从分为 `servants_non_nether` 与 `servants_nether` 两个子节，按随从所在维度选择。

| 随从配置项 | 非下界默认值 | 下界默认值 |
| --- | ---: | ---: |
| `hurt_limit_ratio` | 0.75 | 1/3 |
| `protection_level` | 2 | 4 |
| `extra_armor` | 10 | 20 |
| `extra_toughness` | 5 | 10 |
| `attack_multiplier` | 2.5 | 5 |
| `health_multiplier` | 2.5 | 5 |
| `knockback_multiplier` | 2.5 | 5 |
| `speed_multiplier` | 1.5 | 1.5 |

强化参数在原生强化时应用。上游技能和随从强化参数作用于已安装版本提供的功能。

基础生命、受伤保护和至尊灾厄动态减伤由本模组处理。

装备没收、属性重置、面向玩家光环的配置和原生 Apollyon 仪式抽取概率不属于冴月麟战斗项。客户端 `projectc-client.toml` 的 `satsukiRinBossBar=true` 可独立切换血条。

简易血条等级每20 游戏刻在 ℵ₀、ℵ₁、ℵ₂、ℶ₀、ℶ₁、ω、ε₀、Γ₀ 中按随机打乱的顺序循环。

### 武器属性

`weapons.<物品ID>` 提供 `base_damage` 与 `attack_speed`。攻击速度配置控制攻击速度属性，不重定特殊施法或投射物的内部时序。

| 物品ID | 默认基础伤害 |
| --- | --- |
| `fractured_ark` | 4 |
| `ark_of_the_ancients` | 6 |
| `ark_of_the_elements` | 7 |
| `ark_of_cosmos` | 16 |
| `ark_of_the_old_one` | 20 |
| `broken_biome_blade` | 5 |
| `biome_blade` | 7 |
| `true_biome_blade` | 9.5 |
| `galaxia` | 16 |
| `violence` | 24.25 |
| `rancor` | 18 |
| `gruesome_eminence` | 36 |
| `first_fractal` | 16 |

`bejeweled_staff` 默认基础伤害8，`weapons.bejeweled_staff.soul_cost=15` 为每轮三发的基础灵魂费用。见[璀璨权杖](bejeweled-staff.md)。

各武器默认攻击速度属性为4。`weapons.rancor.soul_cost=25`、`weapons.gruesome_eminence.soul_cost=30` 是附魔和装备折扣前的灵魂费用，结算见[死灵炼注](necromantic-infusion.md)。

### 光环联动配置

`integration.end_halo_trip_chance=0.05` 控制佩戴终焉之环受到甜浆果丛伤害时的摔倒概率。

## 怒气与肾上腺素

内置怒气与肾上腺素，不能同时安装独立 Revengeance 模组。默认 V 激活怒气、B 激活肾上腺素，可在按键设置中修改。

`combat_boost.rage` 和 `combat_boost.adrenaline` 的 `damage_multiplier` 分别默认为1.35与3.45。它们通过攻击伤害属性的 `MULTIPLY_BASE` 加成，超出1的部分与其他同类加成相加。

默认两者同时生效且无其他加成时为3.8倍。是否作用于某次攻击取决于该攻击是否读取攻击伤害属性。

| 配置项 | 怒气 | 肾上腺素 |
| --- | --- | --- |
| `duration_ticks` | 180，另加永久强化时长 | 100 |
| `required_charge` | 100，允许1–100 | 10000，允许1–10000 |
| `charge_ticks` | 900 | 600 |
| `decay_ticks` | 600 | 20 |

20 游戏刻为1秒。时长修改在下次激活时采用，充能门槛同步到 HUD。

怒气 `upgrade_count=3` 表示三种永久强化全部使用，每次强化增加20 游戏刻持续时间，因此默认持续12秒。

距离从敌人碰撞箱计算，采用三维非线性邻近收益：`proximity_radius=50`、`full_charge_radius=10`，普通敌人最大收益时45秒充满，`boss_charge_multiplier=3` 时Boss收益下15秒充满。多目标取最大收益，不叠加。

`combat_delay_ticks=200` 表示造成或受到伤害后10秒不衰减。仅邻近敌人不授予该宽限。

脱战后默认30秒流失满槽。实体类型标签 `arkofcosmos:not_trigger_rage` 排除邻近收益，`arkofcosmos:no_boss_rage_bonus` 排除Boss额外收益但保留普通收益。

肾上腺素的 `detection_radius=128` 为Boss检测盒半边长。受伤后的充能暂停由 `hurt_pause_ticks=20` 控制。
