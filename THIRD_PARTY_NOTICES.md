# 许可范围与第三方内容

本文说明 ProjectC — Ark of Cosmos 各类文件的来源和许可范围。根目录 `LICENSE.txt` 的 GPL-3.0-only 授权只适用于本项目有权授权的原创部分；文件位于同一仓库、改用 Java／GLSL、重新命名或经过格式转换，都不使第三方内容自动成为 GPL-3.0-only。

本项目原创部分的版权归 EverlaPrismerAya 所有（Copyright (c) 2026 EverlaPrismerAya），当前 `main` 分支采用 GNU General Public License version 3 only（SPDX：`GPL-3.0-only`）。分发受 GPL 覆盖的修改版本或二进制时，须遵守 GPL v3，包括保留版权及许可声明、标明修改并按其要求提供对应源码及必要构建脚本。

`1.20-legacy` 分支固定于提交 `c5508f85c40755f5d6cff1208a151e0c2445cba9`，原创部分保留该提交的 MIT 授权及第三方许可边界。此前已按 MIT 授予的权利不因 `main` 改用 GPL 而撤销；原版权与授权声明保存在 `licenses/MIT-legacy.txt`，该文本不构成对 `main` 新增 GPL 内容的 MIT 授权。

GPL 声明不授予第三方内容的额外权利，也不证明现有第三方派生代码与 GPL 的组合分发已经获得授权。引入 GPL 上游代码时仍须逐项核对兼容性、保留上游版权与许可并标明修改。

## Java 代码

银河中英文物品描述分别参考 Calamity Mod 的 `Localization/en-US/Mods.CalamityMod.Attunement.hjson`、`Mods.CalamityMod.Items.Weapons.Melee.hjson`，以及 CalamityCN 的 `Localization/CalamityMod/zh-Hans/` 对应文件。中文调谐名称和相关用语来自 CalamityCN 汉化作者；操作说明及版本差异按本项目实际行为调整。相关原文和译文的权利归各自作者所有，不纳入本项目 GPL-3.0-only 授权。

目录：`src/main/java/net/everla/projectc/arkofcosmos/`。

本项目新增的 Minecraft／Forge 注册、网络通信、输入处理、NBT 状态管理、三维攻击平面、实体插值和渲染适配等原创部分，以 GPL-3.0-only 授权，版权所有者为 EverlaPrismerAya。

代码按职责分包，并将可独立识别的灾厄派生实现集中到 `calamity/`。来源分区不改变第三方内容的原有许可，也不表示边界外的混合适配文件已完全脱离原作。

以下路径相对于上述 Java 根目录：

| 文件 | 来源／派生内容 |
| `textures/item/ashes_of_calamity.png` | Calamity Team：`Items/Materials/AshesofCalamity.png` 原图五帧动画；源 `AshesofCalamity.SetStaticDefaults` 每帧6 Terraria tick映射为2 Minecraft tick。ARR，非GPL授权；散列见资源清单。 |
| --- | --- |
| `combat/LegacyApollyonDamageWindow.java`、`mixin/compact/goetyAndGoetyRevelation/LegacyApollyon*.java`，以及 `ArkMixinPlugin` 的旧版冷却回调适配 | 从本机 RevelationFix 4.9 的 `ApollyonDamageWindow`、`advanced/invul/LivingEntityMixin`、`advanced/invul/goety/ApostleMixin`、`goety/ApollyonMixin` 回移伤害窗口、实际扣血结算与冷却提交逻辑；适配旧 GR 2.3／RF 4.0 的独立生命与双冷却计时器。原始 JAR `[Forge]RevelationFix-1.20.1-4.9.jar` SHA-256：`cbb8a0fbf7f9c1f9926f17616861e1bcaddb4c23d3e2b487a637eac484f9af31`。原作者保留全部权利（上游元数据许可 `ARR`），来源算法及其派生部分不作 GPL-3.0-only 重新授权；未复制或内嵌上游 JAR。 |
| `enchant/CurseCombat.java`、`CurseMath.java`、`TaintedGeometry.java`、`entity/WitchCurseEntity.java`、`client/WitchCurseRenderer.java`、硫火魔珠与炼注界面原图／音效 | Calamity Team：固定提交 `1a8cebd27ec5615316b78f71973446b5528d2b78` 的 `UI/CalamitasEnchantments`、`CalPlayer`、`CalamityGlobalItem/Projectile`、`NPCs/Other` 及相关魔法、近战、召唤、治疗弹幕源码；中文名称来自 CalamityCN `Mods.CalamityMod.UI.hjson`，说明按 Minecraft / Goety 适配行为调整。原始纹理与 `WeaponEnchant.ogg` 逐字节复制并按 SHA-256 去重，来源散列见资源清单；来源算法、资源及派生部分 ARR，不作 GPL-3.0-only 重新授权。Forge 菜单、NBT、灵魂适配、网络与三维坐标为本项目适配。 |
| `client/CalamitasVortexSound.java`、`mixin/client/calamitas/CalamitasDronePitchMixin.java` | 固定 CalamityModPublic 提交 `1a8cebd27ec5615316b78f71973446b5528d2b78` 的 `BrimstoneMonster.cs` 持续音、距离参数和退场条件；Calamity Team ARR。只读核对本机 Terraria `Utils.GetLerpValue`／`Audio.ActiveSound` 与 FNA `SoundEffectInstance.UpdatePitch` 的默认插值、2500源像素衰减和八度换算；API及原始实现归各原权利人，原条款不变，不由本项目重新授权。含本项目的三维监听距离、原生流式通道与限定声音事件的音调适配；未复制运行库。运行库散列见资源清单。 |
| `calamity/SupremeCalamitasBarrage.java`、`calamity/SupremeCalamitasEncounter.java`、`calamity/SupremeCalamitasArena.java` | Calamity Team：公开源码提交 `1a8cebd27ec5615316b78f71973446b5528d2b78` 的 `NPCs/SupremeCalamitas/SupremeCalamitas.cs` 五轮弹幕、阶段条件、施法／投降计时、战场尺寸及越界加速，和 `Systems/Mechanic/ArenaWallSystem.cs` 的边界方向语义。Java／Forge 三维坐标、存档及强制流程防跳过属于本项目适配；原算法及其派生部分遵循 Calamity 原许可，ARR，不作 GPL-3.0-only 重新授权。未引入特殊种子、Permafrost 或 Boss Rush 流程 |
| `calamity/SupremeCalamitasSparks.java`、`calamity/client/CalamitasParticles.java`、`ArkFxParticle` 的 SOURCE_SPARK／VOID_SPARK／SOURCE_ORB／SOURCE_BLOOM 与 `textures/fx/calamitas_*spark*.png`、`calamitas_glow_orb.png`、`calamitas_bloom.png` | Calamity Team，固定提交 `1a8cebd27ec5615316b78f71973446b5528d2b78` 的 `Buffs/DamageOverTime/VulnerabilityHex.cs` 玩家VoidSpark与 `CalPlayer/CalamityPlayerDrawEffects.cs` 随机视觉位置、`SupremeCalamitas.cs` 兄弟施法火花、`BrimstoneMonster.cs` 开场三层Bloom、成长GlowOrb／Dust235与命中光球、`SepulcherHead.cs` 30弹幕攻击的红白Bloom与 `Particles/DirectionalPulseRing.cs` 脉冲环（复用 `textures/fx/hollowcirclehardedge.png`）、`SCalBrimstoneFireblast.cs`／`SCalBrimstoneGigablast.cs` 飞行Spark及爆裂前Bloom、`SupremeCatastropheSlash.cs` 斩击Dust与 `SupremeCataclysmFist.cs` 拳弹尾迹／末帧双圈／退场Dust发射参数、`BrimstoneBarrage.cs` 的探魂者／灾坟魔物Dust尾迹、`BrimstoneWave.cs` 与两种Fireblast／Gigablast的Dust发射参数、`SoulSeekerSupreme.cs` 成功／失败射击Dust、预警GlowOrb及 `Particles/StaticPulseRing.cs`／`textures/fx/calamitas_static_ring.png` 静态环、`Particles/PointParticle.cs` 及 `textures/fx/calamitas_point.png` 的火球爆裂双层粒子、`Particles/CustomPulse.cs`、`Particles/GlowSparkParticle.cs`、`Particles/SparkParticle.cs`、`Particles/VoidSparkParticle.cs`、`GlowOrbParticle.cs`、`BloomParticle.cs`、`Utilities/MathematicalUtils.cs` 与 `GeneralParticleHandler.cs`。原始纹理逐字节复制，散列和路径见资源清单；来源算法、纹理及派生部分 ARR，不作 GPL-3.0-only 重新授权。球形方向扩展、源帧同步／去重及三维billboard属于Minecraft适配。 |
| `textures/fx/calamitas_dust_5_*.png`／`calamitas_dust_235_*.png`／`calamitas_dust_261_*.png`／`calamitas_dust_66_*.png`／`calamitas_dust_279_*.png`／`calamitas_dust_60_*.png`／`calamitas_dust_114_*.png`／`calamitas_dust_182.png` 与 `ArkFxParticle` 的 SOURCE_DUST_5／235／261／66／279／60／114／182 | Re-Logic，来自本地正版 tModLoader 的原始 `Content/Images/Dust.xnb` 与 `Terraria.Dust`／`Main.DrawDust` 实现；各类原始8×8帧仅裁切，182的三张相同帧复用一张文件，原始XNB、像素区域及散列见资源清单。纹理、来源算法及派生部分ARR，不作GPL-3.0-only重新授权。源坐标转换、三维方向扩展、Minecraft原生标量环境光及既有Oculus粒子通道为Minecraft适配。 |
| `calamity/CalamitasHexEffect.java`、`textures/mob_effect/vulnerability_hex.png` | Calamity Team，固定提交 `1a8cebd27ec5615316b78f71973446b5528d2b78` 的 `Buffs/DamageOverTime/VulnerabilityHex.cs`／`.png`、`CalPlayer/CalamityPlayerLifeRegen.cs`、`CalamityPlayerMiscEffects.cs` 及各射弹命中时长。图标逐字节复用，散列见资源清单；来源算法、纹理及派生部分 ARR，不作 GPL-3.0-only 重新授权。原生状态效果、配置比例及原生伤害入口为 Minecraft 适配；尚未完整实现全部源减益行为。 |
| `entity/SupremeCalamitasEntity.java`、`entity/CalamitasSummons.java` | Calamity Team，同一固定提交 `1a8cebd27ec5615316b78f71973446b5528d2b78` 的 `SupremeCalamitas.cs`：本体阶段／运动、越界防御、十颗火心布局与连接、兄弟施法位置、十名探魂者初始编队、弹幕伤害和投降流程；来源算法及派生部分 ARR，不作 GPL-3.0-only 重新授权。原生生命门槛、Forge取消生成重试、稳定UUID／NBT及战斗归属清理为Minecraft适配。 |
| `CalamitasAttackRenderer` 的低语漩涡、`calamitas_hell_ball.fsh`、`calamitas_screamy_face.png`／`calamitas_soul_vortex.png`／`calamitas_large_bloom.png` | Calamity Team，固定提交 `1a8cebd27ec5615316b78f71973446b5528d2b78` 的 `Effects/HellBall.fx` 与 `Projectiles/Boss/BrimstoneMonster.cs`：原HellBall算法、源绘制参数、十层旋转、零alpha预乘混合及黑色中心。三张纹理原样复制，源路径与散列见资源清单。来源纹理、算法及派生部分ARR，不作GPL-3.0-only重新授权；GLSL／三维billboard与Oculus深度路径为平台适配。 |
| `client/CalamitasAttackRenderer.java`、`fx_white_sprite.fsh`、`textures/fx/calamitas_slash*.png` | Calamity Team，固定提交 `1a8cebd27ec5615316b78f71973446b5528d2b78` 的 `SupremeCatastropheSlash.cs` 四帧、偏移与残影；两张原纹理逐字节复制，由运行时着色器保留alpha并改为白色。来源算法、纹理及派生部分ARR，不作GPL-3.0-only重新授权。Goety近似外观仅按资源位置引用已安装模组的 `hell_blast`／`soul_bolt`／`cursed_skull` 模型层和相关动画纹理，不复制或分发其资源／Java类。弩箭引用Minecraft原版规范纹理和箭体网格。 |
| `client/CalamitasMinionRenderer.java`、`fx_primitive.fsh`、`textures/entity/brimstone_heart.png` | 固定提交 `1a8cebd27ec5615316b78f71973446b5528d2b78` 的 `BrimstoneHeart.cs` 六帧脉动、连接宽度／颜色与 `Graphics/Primitives/PrimitiveRenderer.cs` 的40次Catmull-Rom采样，以及 `Effects/StandardPrimitiveShader.fx` 的直接顶点颜色输出；火心纹理逐字节复制，SHA-256见资源清单。来源算法／纹理及派生部分为Calamity Team ARR，不作GPL-3.0-only重新授权。恼鬼使用Minecraft现有模型／纹理与原版闲置翼动，不复制原版资源，不运行恼鬼AI。 |
| `lang/en_us.json`、`lang/zh_cn.json` 中 `dialogue.arkofcosmos.calamitas.*` | 至尊灾厄初战／再战共31条对话：英文逐字取自固定提交 `1a8cebd27ec5615316b78f71973446b5528d2b78` 的 `Localization/en-US/Mods.CalamityMod.Status.Boss.hjson`；中文逐字取自本地 CalamityCN 的对应汉化文件。Calamity Team 与汉化原作者保留各自权利，不作 GPL-3.0-only 重新授权；来源文件及文本片段SHA-256见资源清单。 |
| `calamity/SupremeCalamitasCombat.java`、`calamity/SupremeCalamitasBrothers.java`、`entity/CalamitasAttackEntity.java`、`entity/CalamitasMinionEntity.java` | Calamity Team，同一公开源码提交的 `SupremeCalamitas.cs`、`SupremeCataclysm.cs`、`SupremeCatastrophe.cs`、`SoulSeekerSupreme.cs`、`BrimstoneHeart.cs`、`Utilities/NPCUtils.cs`，以及 `Projectiles/Boss/` 下的 `BrimstoneHellblast`、`BrimstoneHellblast2`、`BrimstoneBarrage`、`SCalBrimstoneFireblast`、`SCalBrimstoneGigablast`、`BrimstoneWave`、`BrimstoneMonster`：普通施法／冲刺、兄弟攻击循环、火心及探魂者行为、射弹运动／分裂／碰撞／生命周期的 Java／三维／原生伤害适配。来源算法及派生部分遵循 Calamity 原许可，ARR，不作 GPL-3.0-only 重新授权；实际固定源文件列表见开发基线清单 |
| `entity/CalamitasBrotherEntity.java`、`entity/CalamitasAttackEntity.java` 的 FIST／SLASH 分支 | Calamity Team，固定公开源码提交 `1a8cebd27ec5615316b78f71973446b5528d2b78` 的 `SupremeCataclysm.cs`、`SupremeCatastrophe.cs`、`SupremeCataclysmFist.cs`、`SupremeCatastropheSlash.cs`：兄弟强化、一次复活、原生伤害适配、额外更新、派生射弹及寿命。来源算法及派生部分 ARR，不作 GPL-3.0-only 重新授权。掠夺者／卫道士模型和纹理引用 Minecraft 规范资源路径，不复制游戏资源。 |
| `calamity/SupremeCalamitasSepulcher.java`、`entity/CalamitasSepulcherEntity.java`、`CalamitasAttackEntity` 的 `SOUL` 分支 | Calamity Team，同一公开源码提交的 `NPCs/SupremeCalamitas/SepulcherHead.cs`、`SepulcherBody.cs`、`SepulcherBodyEnergyBall.cs`、`SepulcherTail.cs`、`SepulcherArm.cs` 及 `Projectiles/Typeless/SepulcherSoul.cs`：头部追击／发射、分节数量与间距、附肢运动、火心关联退场及无伤害灵魂。三维轨迹历史跟随、第三轴转向、原生生命与完整劫掠兽模型属于移植适配；来源算法及派生部分遵循 Calamity 原许可，ARR，不作 GPL-3.0-only 重新授权 |
| `calamity/combat/BrokenBiomeRules.java`、`entity/BrokenBiomeEntity.java`、`combat/BrokenBiomeCombat.java`、`combat/BrokenBiomeFreeze.java`、`item/BrokenBiomeBladeItem.java`、`client/BrokenBiome*.java`，以及共享 `client/BiomeEffects.java`、`client/BiomeItemEffects.java` 中相应粒子适配及 `client/TrueBiomeAudio.java` 的源音效参数 | 本机 CalamityMod.dll 的 `BrokenBiomeBlade`、五种基础 Attunement、`PurityProjection`、`AridGrandeur`、`BitingEmbrace`、`GrovetendersTouch`、`DecaysRetort`、原始 PreDraw／粒子参数与调谐逻辑；Calamity Team／原作者 ARR，不作 GPL 重新授权。Terraria AIType 156、Dust 75 与原始 SoundStyle 音频归 Re-Logic／原权利人。含用户确认的5点基础伤害、120°普通挥剑、三维位移、探索解锁轮盘及独立冻结控制；中英文用语参考 CalamityMod／CalamityCN，资源及复用音效 SHA-256 见资源清单。原版攻击与所用粒子均使用普通／加色混合，无专用着色器；独立崩解依用户确认只减少10护甲，暂不映射灾厄减伤属性 |
| `calamity/combat/TrueBiomeRules.java`、`entity/TrueBiomeEntity.java`、`combat/TrueBiomeCombat.java`、`combat/GlacialStateEffect.java`、`item/TrueBiomeBladeItem.java`、`client/TrueBiome*.java`、`calamity/client/TrueBiomeEffects.java` | 本机 CalamityMod.dll 的 `OmegaBiomeBlade`、四种 Attunement、`SwordsmithsPride`／Beam、`LamentationsOfTheChained`、`GhastlyChain`、`ChainedMeatHook`、`SanguineFury`／Wheel／Dash、`MercurialTides`／Blast／Monolith、`PurityProjectionSigil`、gState 以及对应粒子和绘制规则；Calamity Team／原作者 ARR，不作 GPL 重新授权。Terraria AIType 156、Dust 75 及 SoundStyle／原始音频归 Re-Logic／原权利人；含用户确认的攻击平面、径向地表喷发、基础伤害与 Forge 接口适配。中英文调谐用语来自 CalamityMod／CalamityCN；资源 SHA-256 见清单 |
| `lang/zh_cn.json`、`lang/en_us.json` 中 `fractured.*` | 破碎方舟描述来自 CalamityCN `zh-Hans/Mods.CalamityMod.Items.Weapons.Melee.hjson` 的完整汉化及 CalamityMod 对应英文原文；招架机制文字按本模组实际行为调整。原文及汉化归各自原作者，不作 GPL-3.0-only 重新授权 |
| `calamity/combat/AncientsRules.java`、`entity/AncientsEntity.java`、`combat/AncientsCombat.java`、`item/AncientsArkItem.java`、`client/AncientsRenderer.java`、`client/AncientsClient.java`、`calamity/client/AncientsEffects.java` | 本机 CalamityMod.dll 的 `TrueArkoftheAncients`、`TrueArkoftheAncientsSwungBlade`、`AncientStar`、`TrueAncientBeam`、`TrueAncientBlast`、`HomeInOnNPC` 的攻击、时序、追踪、碰撞、充能、衰减和原图绘制规则；Calamity Team，ARR。Forge 适配使用用户确认的锁定准星平面、6点基础伤害及使用键＋大撕裂键；格挡复用项目共通实现。纹理使用 `TrueArkoftheAncients`／Glow、`AncientStar`、原始 smear／BloomLine／BloomLineCap 与粒子；复用 Terraria 原始音频转码文件，不新增自定义 GLSL。名称与风味原文来自 CalamityMod／CalamityCN，归各自原作者所有，不作 GPL 重新授权 |
| `calamity/combat/FracturedRules.java`、`entity/FracturedEntity.java`、`combat/FracturedCombat.java`、`item/FracturedArkItem.java`、`client/FracturedRenderer.java`、`client/FracturedClient.java`、`calamity/client/FracturedEffects.java` | 本机 CalamityMod.dll 中 `FracturedArk`、`ArkoftheAncientsSwungBlade`、`AncientBeam` 及 `DrawAfterimagesCentered` 的交替挥砍、充能、剑气、碰撞、时序和原图绘制规则；Calamity Team，ARR。含原创 Forge 输入与同步接口及用户确认的准星攻击平面映射、4点基础伤害、无暴击／击退和自定义配方；格挡判定复用本项目方舟共通实现，音效采用破碎方舟原版 SkyDragonsFuryShot／CrystalImpact／Item67／Item35；刀身按用户要求采用原图轮廓三像素厚度。视觉沿用原版 CritSpark、GenericSparkle、SquishyLightParticle、招架成功辉光及 Terraria Dust 66／15／57／58，复用通用粒子实现；圆周散射按确认转换为等速三维球面散射 |
| `calamity/combat/BiomeRules.java`、`combat/BiomeCombat.java`、`entity/BiomeEntity.java`、`item/BiomeBladeItem.java`、`client/BiomeRenderer.java`、`client/BiomeEffects.java`、`client/BiomeItemEffects.java` | Calamity Team 的 `TrueBiomeBlade`（环境之刃）、八种 Attunement、BiomeBladeHoldout 及派生弹幕／粒子的攻击、时序、曲线与绘制规则；Calamity 部分 ARR，Terraria Projectile／Dust 与 SoundStyle 派生逻辑归 Re-Logic／原权利人，不作 GPL-3.0-only 重新授权；包含用户确认的三维与 Forge 适配，本武器复用共通 BasicTint 与深度 FX 绘制通道；物品栏能量来自 ChargingEnergyParticleSet／EnchantedParticle |
| `combat/OldOneCombat.java`、`combat/OldOneEffects.java`、`entity/OldOneEntity.java`、`client/OldOneRenderer.java`、`client/OldOneParticles.java` | 旧版 Calamity `ArkoftheCosmos`、`Galaxia2`、四种 `EonBeam`、`AstralStar` 以及 Terraria 1.3.5.3 `Projectile`、`Dust`、`Gore`、`Main` 及旧版 `AstralOrange` 的攻击、派生、AI、状态和绘制规则；Calamity 部分为 Calamity Team ARR，Terraria 部分归 Re-Logic／原权利人，不作 GPL-3.0-only 重新授权；包含用户确认的三维转换及 Minecraft／Forge 接口适配 |
| `calamity/combat/GalaxiaRules.java`、`entity/GalaxiaEntity.java`、`calamity/client/GalaxiaRenderer.java` | `FourSeasonsGalaxia`、四种 Attunement、`PhoenixsPride`、`AriesWrath`、`AriesWrathConstellation`、`PolarisGaze`、`PolarisGazeStar`、`PolarisGazeDash`、`AndromedasStride`、`AndromedasStrideBoltSpawner`、`GalaxiaBolt` 和 `GalaxiaTropicRing` 的规则、阶段、曲线、参数与原图绘制；含用户确认的三维攻击平面、地表环形扩散和玩家碰撞适配 |
| `calamity/entity/CalamityAttackEntity.java` | `ArkoftheCosmosSwungBlade`、`RendingNeedle`、`EonBolt`、`ArkoftheCosmosConstellation`、`ArkoftheCosmosParryHoldout`、`ArkoftheCosmosBlast` 的攻击阶段、运动、时序与命中规则；包含必要的 Minecraft 单位及实体接口适配 |
| `calamity/combat/ArkWeaponRules.java` | 原武器的连段模式、伤害换算、充能上限及工具提示配色；保留本项目已有的机制修改 |
| `calamity/combat/ElementsRules.java`、`entity/ElementsEntity.java`、`combat/ElementsCombat.java`、`item/ElementsItem.java` | Calamity Team 的 ArkoftheElements、SwungBlade、ParryHoldout、SnapBlast、SolarNeedle、ElementalGlassStar 的规则、曲线、倍率、时序和形态；含原创 Forge 接入、三维瞄点适配及用户指定数值，Calamity 派生部分不作 GPL-3.0-only 重新授权 |
| `calamity/client/ElementsRenderer.java`、`calamity/client/ElementsEffects.java`、`calamity/client/ElementsVanillaParticles.java`、`calamity/client/ElementsAudio.java` | 原刀刃／弹幕 PreDraw、BasicTint、残影、粒子及 SoundStyle；Calamity 部分为 Calamity Team ARR，Terraria Dust／Gore／ActiveSound 派生逻辑归 Re-Logic／原权利人，不作 GPL-3.0-only 重新授权；含原创 Minecraft 三维及接口适配 |
| `calamity/util/ArkCurves.java` | 原攻击的分段曲线、参数及相关缓动实现 |
| `calamity/client/ArkAttackRenderer.java` | 原刀刃的 PreDraw、开合、残影、smear、针弹与爆发绘制逻辑；仍混有本项目的三维厚度与轮廓适配 |
| `calamity/client/ArkEffects.java` | 原攻击的粒子生成条件、数量、参数、星链及根据 `CalamityPlayer.ModifyScreenPosition` 适配的震屏行为；仍包含客户端轨迹管理 |
| `calamity/client/ArkFxParticle.java` | `GenericSparkle`、`CritSpark`、`FlareShine`、`HeavySmokeParticle`、`SquishyLightParticle`、`PulseRing`、`StrongBloom`、`GenericBloom`、`LineVFX`、`BloomLineVFX` 的更新与绘制逻辑及插值适配 |
| `calamity/client/CalamityTrail.java` | `PrimitiveRenderer` 的拖尾采样、几何和纹理坐标逻辑及三维适配 |
| `calamity/client/GalaxiaEffects.java`、`calamity/client/GalaxiaRenderer.java` | Galaxia 四模式及派生弹幕的原始 AI 粒子生成、PreDraw、残影、锁链、星座与星环绘制；`ArkFxParticle` 同时包含 `ConstellationRingVFX` 的原始椭圆环及星芒绘制，来源 Calamity Team，ARR；含客户端子步同步、插值、攻击平面与近景淡化适配 |
| `combat/ArkCombat.java`、`item/ArkItem.java` | Forge／物品接入；仍保留原武器的连段发起、充能、倍率、冷却、描述与状态行为，不能将整个混合文件理解为无例外的 GPL-3.0-only 代码 |
| `combat/ArkDamage.java` | 本项目的独立同步血量、伤害结算与死亡保护；其中原版不死图腾的检查、消耗、统计／进度触发和恢复效果流程参考并适配 Minecraft `LivingEntity.checkTotemDeathProtection`，该部分不纳入本项目 GPL-3.0-only 授权，详见下文 Minecraft 代码说明 |
| `entity/ArkAttackEntity.java` | Minecraft 实体生命周期、同步、插值、附魔与冲刺碰撞接入；仍保留攻击寿命初始化、子步调度、派生效果触发与已有附加伤害行为，不能将整个混合文件理解为无例外的 GPL-3.0-only 代码 |
| `client/ArkClient.java` | 原武器状态条配色及显示参数的客户端接入；震屏算法已移至 `calamity/client/ArkEffects.java` |
| `client/render/ArkFxDraw.java` | 本项目的 OpenGL／Minecraft 绘制适配，保留 XNA 混合与绘制语义；原拖尾算法已移至 `calamity/client/CalamityTrail.java`；`shaders/core/fractured_sprite.*` 为原创 Minecraft 光照贴图与原版辉光混合适配，不是灾厄专属 .fx 的移植 |

`util/AttackFrame.java`、`util/ArkGeometry.java` 与 `network/ArkNetwork.java` 分别提供本项目的三维攻击平面、空间几何及 Forge 网络接入。来源分区便于维护与审阅，不应被用来将灾厄派生内容重新授权为 GPL-3.0-only，或把包含原创适配的派生文件整体视为第三方原文件。

Calamity 来源的代码须保留 Calamity Team 署名及原条款。本项目只对新增原创部分作 GPL-3.0-only 授权，不声称取得了将原代码整体转授 GPL-3.0-only 的权利。原许可允许以源码作为开发参考，并要求对直接采用的代码署名；这不等于原项目采用 GPL-3.0-only。

### Minecraft 代码与兼容性研究

`combat/ArkDamage.java` 中的不死图腾流程参考并适配 Minecraft 1.20.1 的原版实现，保留双手检查、伤害类型限制、Forge 图腾使用事件、物品消耗、统计与进度、恢复效果及实体事件。Minecraft 原始代码及其派生部分的权利归 Mojang／Microsoft 等原权利人，适用 Minecraft 自身的许可与使用条款；本项目不将其重新授权为 GPL-3.0-only，也不声称拥有额外再分发授权。相关条款见 <https://www.minecraft.net/eula>。


| Mojang 派生文件 | 来源与权利归属 |
| --- | --- |
| `combat/vanilla/SpearMaterial.java`、`SpearCombat.java`、`item/SpearItem.java` | Minecraft 26.1.2 `Items`、`Item.Properties.spear`、`ToolMaterial`、`KineticWeapon`、`PiercingWeapon`、`AttackRange`、`ProjectileUtil`、`LivingEntity`、`Player` 的参数、命中和相对速度规则；含 Forge 1.20.1 输入与扫掠适配 |
| `combat/vanilla/MaceCombat.java`、`ImpulseFall.java`、`item/MaceItem.java`、`mixin/VanillaPlayerAttackMixin.java`、`VanillaLivingCombatMixin.java` | Minecraft 26.1.2 `MaceItem`、`CombatRules`、`LivingEntity`、`ServerPlayer`、附魔数据的下砸、分段伤害、突破与冲量落差派生逻辑 |
| `combat/vanilla/WindBurst.java`、`entity/PlayerWindCharge.java`、`VanillaEnchantments.java` | Minecraft 26.1.2 `WindChargeItem`、`WindCharge`、`AbstractWindCharge`、`AbstractHurtingProjectile`、`Projectile`、`ServerExplosion`、`SimpleExplosionDamageCalculator`、方块爆风触发和附魔数据；含 1.20.1 原生接口适配 |
| `client/vanilla/SpearAnimations.java`、`Ease.java`、`GustParticle.java`、`mixin/client/Spear*Mixin.java` | Minecraft 26.1.2 `SpearAnimations`、`Ease`、`GustParticle`、`GustSeedParticle`、`ParticleResources`、`ItemInHandRenderer`、`HumanoidModel` 的动作曲线、持握与粒子派生实现 |

上述 Mojang 原始及派生内容归 Mojang／Microsoft 及原权利人所有，受 [Minecraft EULA](https://www.minecraft.net/eula) 与 [Minecraft Usage Guidelines](https://www.minecraft.net/usage-guidelines) 约束；不是本项目原创 GPL-3.0-only 代码，也不由本项目重新授予 Mojang 内容的再分发许可。来源为只读 Minecraft 26.1.2 实例及其官方资源索引 30，源类／资源 SHA-256 与适配文件 SHA-256 见 `licenses/RESOURCE-SOURCES.json`。

## 纹理 PNG

范围：`src/main/resources/assets/arkofcosmos/textures/` 内的物品、刀刃、弹幕和 FX 纹理。

这些纹理来自 Calamity Mod，权利归 Calamity Team／各原作者，适用 **All Rights Reserved（保留所有权利）**，不适用本项目 GPL-3.0-only。重命名、重复放入不同绘制目录，以及用于三维刀刃表面，均不改变其来源或许可。

具体来源、当前 SHA-256 和历史来源记录见 `licenses/RESOURCE-SOURCES.json`。不能把该清单当作资源授权书。

| 文件 | 来源与许可范围 |
| --- | --- |
| `item/biome_blade.png`、`fx/biome_*.png`、`sounds/biome_*.ogg` 及共用规范资源 | Calamity `TrueBiomeBlade`／MendedBiomeBlade 八调谐的原 PNG 逐字节复制；Terraria 原始 XNB PCM 按实际 SoundStyle 转码 OGG，保留变体；Calamity Team ARR／Re-Logic 原条款，逐文件来源与原始／输出 SHA-256 见资源清单 |
| `item/true_biome_blade.png`、`fx/true_biome_*.png`、`mob_effect/glacial_state.png` 及共用规范纹理 | CalamityMod `OmegaBiomeBlade`、四主调谐与派生射弹、粒子、冰冻状态图标的原 PNG，逐字节复制并按 SHA-256 去重；Calamity Team／原作者 ARR。使用原始 Calamity OGG 与 Terraria XNB PCM 转码音效，归对应原权利人，来源与输出散列逐项记入资源清单 |
| `item/ark_of_the_ancients.png`、`item/ark_of_the_ancients_glow.png`、`entity/ancient_star.png` | Calamity Mod `Items/Weapons/Melee/TrueArkoftheAncients.png`／`TrueArkoftheAncientsGlow.png`、`Projectiles/Melee/AncientStar.png` 原图逐字节复制；剑气使用同一辉光，粒子／爆发复用原始 PNG；Calamity Team／原作者 ARR，逐项散列见资源清单 |
| `item/fractured_ark.png`、`item/fractured_ark_glow.png` | Calamity Mod `Items/Weapons/Melee/FracturedArk.png`、`FracturedArkGlow.png` 原图，逐字节复制；剑气引用同一辉光纹理，Calamity Team／原作者 ARR |
| `item/ark_of_the_old_one.png`、`old_one/eon*.png`、`old_one/orb.png`、`old_one/astral_star.png`、`old_one/astral_orange.png` | 旧版 Calamity 解包原图，剑身为旧版 `Items/Weapons/Melee/ArkoftheCosmos.png`，不使用新版同名武器图；Calamity Team／原作者 ARR，不适用 GPL-3.0-only |
| `old_one/projectile_*.png`、`old_one/dust.png`、`old_one/glow_190.png`、`old_one/extra_75.png`、`old_one/gore_*.png` | 本机 Terraria 1.4.5 XNB 的 Texture2D 原像素转换，保留预乘 RGBA；用户明确允许替代缺失的 1.3.5.3 原生素材，不能称为旧版原图；Re-Logic／原权利人所有，不适用 GPL-3.0-only |
| `mob_effect/old_one_*.png` | 灾厄专属状态为旧版 Calamity Buffs 原图（Calamity Team ARR）；原生状态为用户允许的 Terraria 1.4.5 Buff XNB 转换（Re-Logic／原权利人）；逐文件来源及散列见资源清单，均不适用 GPL-3.0-only |
| `fx/calamitas_shield_top.png`、`fx/calamitas_shield_bottom.png`、`fx/calamitas_central_gold.png`、`fx/calamitas_immune_smear.png`、`fx/calamitas_forcefield.png` | 固定 Calamity 提交 `1a8cebd` 的护盾、CentralGold、Immune及Forcefield原始PNG；Calamity Team／原权利人所有，ARR，不适用GPL-3.0-only；逐文件路径与SHA-256见资源清单 |
| `fx/terraria_perlin.png` | Terraria `Content/Images/Misc/Perlin.xnb` 的Texture2D原始RGBA无损导出；Re-Logic／原权利人所有，不适用GPL-3.0-only；原XNB与导出资源散列见资源清单 |
| `src/main/resources/assets/arkofcosmos/textures/item/auric_bar.png` | Calamity Mod `Items/Materials/AuricBar.png`；第三方素材，不适用本项目 GPL-3.0-only，来源与 SHA-256 见 `licenses/RESOURCE-SOURCES.json` |

## 着色器

目录：`src/main/resources/assets/arkofcosmos/shaders/core/`。

| 文件 | 来源与许可范围 |
| --- | --- |
| `fx_trail.fsh` | 对 Calamity `Effects/FadedUVMapStreak.fx` 中 `TrailPass` 的 GLSL 转换；保留原公式和常量，属于 Calamity 派生内容，**不作 GPL-3.0-only 重新授权** |
| `calamitas_arena_metaball.fsh`、`client/CalamitasArenaMaterial.java`、`CalamitasArenaRenderer.java`、`calamity/client/CalamitasArenaVisual.java` | 固定Calamity公开提交 `1a8cebd27ec5615316b78f71973446b5528d2b78` 的 `Effects/Metaballs/MetaballEdgeShader.fx`、`Graphics/Metaballs/ScalArenaMetaball.cs`、`Metaball.cs`、`SupremeCalamitas.cs` 与 `ArenaWallSystem.cs` 的GLSL／Minecraft适配：四向2像素掩模边缘、双层纹理、滚动、波纹RGBA修正、源颗粒／消散及三维墙面投影。Calamity Team，ARR，不作GPL-3.0-only重新授权；顶点／JSON接口与深度可见性掩模为本项目原创适配。`textures/fx/calamitas_arena_smoke.png`、`calamitas_arena_wave.png` 与 `calamitas_arena_circle.png` 为同一提交原始PNG（最后一项源为 `ExtraTextures/BasicCircle.png`），来源和SHA-256见资源清单。 |
| `galaxia_tint.fsh` | 对 Calamity `Effects/BasicTint.fx` 中 `Recolor`／`TintPass` 的 GLSL 150 转换，保留 `rbg` 顺序、插值和 Alpha 乘法；Calamity Team，ARR，不作 GPL-3.0-only 重新授权 |
| `galaxia_tint.json` | 本项目编写的 Minecraft shader、sampler 和 uniform 接口配置；原创适配部分适用 GPL-3.0-only |
| `fx_sprite.vsh`（`fx_trail` 共用） | 本项目编写的 Minecraft 顶点格式／矩阵接口适配；该原创适配部分以 GPL-3.0-only 授权，不扩展到原拖尾算法 |
| `fx_trail.json` | 本项目编写的 Minecraft 着色器注册、采样器和 uniform 配置，以 GPL-3.0-only 授权 |
| `fx_sprite.vsh`、`fx_sprite.fsh`、`fx_sprite.json` | 本项目编写的通用纹理／顶点颜色绘制及 Minecraft 接口配置，以 GPL-3.0-only 授权；采样到的第三方纹理仍遵循各自许可 |
| `mahou_forcefield.fsh`、`mahou_forcefield.json` | Calamity `Effects/SupremeShieldShader.fx` 及 `NPCs/SupremeCalamitas/ForcefieldTexture.png`、`ExtraTextures/GreyscaleGradients/Perlin.png` 的派生 GLSL 与纹理；Calamity Team，ARR，不适用本项目 GPL-3.0-only，来源与 SHA-256 见 `licenses/RESOURCE-SOURCES.json` |
将 HLSL／FX 转换为 GLSL 属于实现适配，不会消除原代码的权利或署名要求。

## 音频 OGG

目录：`src/main/resources/assets/arkofcosmos/sounds/`。

| 文件 | 来源与权利归属 |
| --- | --- |
| `sounds/item/spear/**/*.ogg`、`sounds/item/mace/*.ogg`、`sounds/entity/wind_charge/*.ogg` | Minecraft 26.1.2 官方资源索引 30 的原始 OGG，逐字节复制；Mojang／Microsoft 及音频原权利人，非 GPL-3.0-only；投掷声引用 Minecraft 既有 `random/bow`，不重复复制 |
| `textures/item/*_spear*.png`、`textures/item/mace.png`、`textures/block/heavy_core.png`、`textures/particle/gust_*.png` | Minecraft 26.1.2 原始 PNG，逐字节复制；Mojang／Microsoft，非 GPL-3.0-only；逐文件来源与 SHA-256 见资源清单 |
| `fractured_parry_start_0.ogg`～`fractured_parry_start_2.ogg`、`item67_0.ogg` | Terraria 原始 `dd2_sky_dragons_fury_shot_*` 和 `Item_67` XNB 的 SoundEffect PCM 提取及 Vorbis 转码，保持原采样率与声道；Re-Logic／原权利人所有，不适用 GPL-3.0-only |
| `fractured_swing_0.ogg`～`fractured_swing_3.ogg`、`fractured_beam_death_0.ogg`～`fractured_beam_death_2.ogg` | Terraria 原始 XNB `dd2_monk_staff_swing_*`、`dd2_wither_beast_death_*` 的 SoundEffect PCM 提取及 Vorbis 转码，保持原采样率与声道；Re-Logic／原权利人所有，不适用 GPL-3.0-only |
| `snap_0.ogg` | Calamity 的 `ScissorGuillotineSnap`；Calamity Team／原作者，ARR |
| `phoenix_0.ogg`～`phoenix_2.ogg` | Calamity 的 `LouderPhantomPhoenix`；Calamity Team／原作者，ARR |
| `meaty_0.ogg` | Calamity 的 `MeatySlash`；Calamity Team／原作者，ARR |
| `galaxia_beacon_0.ogg`、`galaxia_lightning_0.ogg` | Calamity 的 AstralBeaconUse、LightningStrike 原始OGG；Calamity Team／原作者，ARR |
| `galaxia_item*.ogg`、`galaxia_hit*.ogg`、`galaxia_book_*.ogg`、`galaxia_impact_*.ogg`、`galaxia_eruption_*.ogg` | Terraria 原生 SoundID 音频转换；Re-Logic／原权利人所有，不适用本项目 GPL-3.0-only |
| `elements_item9_0.ogg` | Terraria `Content/Sounds/Item_9.xnb` 原音频转码；Re-Logic／原权利人所有，不适用 GPL-3.0-only |
| `item*.ogg`、`crystal_*.ogg`、`needle_death_*.ogg` | Terraria 原生 SoundID 音频转换；Re-Logic／原权利人所有，不适用本项目 GPL-3.0-only |
| `sounds/music/calamitas_phase_1.ogg`、`sounds/music/calamitas_phase_2.ogg`、`sounds/music/calamitas_phase_3.ogg` | Calamity Mod Music 的 `CalamitasPhase1.ogg`、`CalamitasPhase2.ogg`、`CalamitasPhase3.ogg`；DM DOKURO《Stained, Brutal Calamity》及相关权利人；魔法使与至尊唤魔者共享同一原始文件，后者按源50%／30%生命边界切换流式循环，不适用本项目 GPL-3.0-only，来源与逐文件 SHA-256 见 `licenses/RESOURCE-SOURCES.json` |
| `brimstone_monster_drone.ogg` | Calamity Mod `Sounds/Custom/SCalSounds/BrimstoneMonsterDrone.ogg`，至尊灾厄低语漩涡持续音效；Calamity Team／原作者，ARR，不适用 GPL-3.0-only；原样复制，SHA-256：`8bc7e11a72e8038b39690a724e963f2f5aba741f5f32141916336cfd27ebfe7d`。魔法使与至尊唤魔者的独立声音事件共享该规范文件；已核对固定公开源码提交 `1a8cebd27ec5615316b78f71973446b5528d2b78`，后者使用原始距离音量／音调参数及原生流式播放。 |
| `calamitas_shot.ogg`、`calamitas_big_shot.ogg`、`calamitas_hellblast.ogg`、`calamitas_dash.ogg`、`calamitas_fireblast_impact.ogg`、`calamitas_gigablast_impact.ogg`、`calamitas_vortex_spawn.ogg`、`calamitas_sepulcher_spawn.ogg`、`calamitas_barrage_end.ogg` | 固定 CalamityModPublic 提交 `1a8cebd27ec5615316b78f71973446b5528d2b78` 的 `Sounds/Custom/SCalSounds/` 原始 OGG，逐字节复制并先按 SHA-256 去重；用于至尊唤魔者，灾厄的施法、冲刺、爆裂、漩涡生成、灾坟魔物召唤与弹幕结束。Calamity Team／原作者 ARR，不适用 GPL-3.0-only；逐文件源路径及原始／输出 SHA-256 见资源清单。 |
| `calamitas_brother_hurt1.ogg`、`calamitas_brother_hurt2.ogg`、`calamitas_brother_death1.ogg`、`calamitas_brother_death2.ogg` | 同一固定 CalamityModPublic 提交的 `Sounds/Custom/SCalSounds/BrothersHurt{1,2}.ogg` 与 `BrothersDeath{1,2}.ogg`，按 SHA-256 去重后保留原始文件；由双随从原生受击／死亡入口选择变体。Calamity Team／原作者 ARR，不适用 GPL-3.0-only；逐文件来源与散列见资源清单。 |
| `calamitas_shield_hurt1.ogg`、`calamitas_shield_hurt2.ogg`、`calamitas_shield_hurt3.ogg` | 同一固定 CalamityModPublic 提交的 `Sounds/NPCHit/ShieldHit{1,2,3}.ogg`，按 SHA-256 去重后保留原始字节；用于至尊唤魔者，灾厄的护盾受击声及其5–7源帧冷却。Calamity Team／原作者 ARR，不适用 GPL-3.0-only；逐文件来源与散列见资源清单。 |
| `calamitas_transition_burst.ogg`、`calamitas_cataclysm_spawn.ogg`、`calamitas_catastrophe_spawn.ogg` | 同一固定 CalamityModPublic 提交的 `Sounds/Custom/SupremeCalamitasSpawn.ogg`、`Sounds/NPCKilled/RavagerDeath1.ogg` 与 `RavagerDeath2.ogg`，逐字节复用并按 SHA-256 去重；用于二阶段转换第100源帧及双随从施法完成时的锁定生成位置。Calamity Team／原作者 ARR，不适用 GPL-3.0-only；逐文件来源与散列见资源清单。 |
| 双随从攻击音效：`calamitas_brother_roar`、`charge`、`echo`、`finisher1/2/3`、`resonance`、`swing`、`dash`、`revive` 原始 OGG | 同一固定 CalamityModPublic 提交的 `RavagerLimbLoss2/3`、`RavagerPillarSummon`、`ThanatosHitOpen1`、`ScorchedEarthShot1/2/3`、`CatastropheResonanceSlash`、`MurasamaBigSwing`、`ExobladeBeamSlash`。按 SHA-256 去重并复用原始字节；触发条件、音量及八度音高来自 `SupremeCataclysm.cs` / `SupremeCatastrophe.cs`。Calamity Team／原作者 ARR，不适用 GPL-3.0-only；完整路径与散列见资源清单。 |
| `calamitas_barrage_rumble.ogg` | 固定 CalamityModPublic 提交的 `Sounds/Custom/SCalSounds/SCalRumble.ogg`，SHA-256 去重后保留原始字节；五轮弹幕第540源帧触发一次并跟随战斗目标，使用原生流式声道。Calamity Team／原作者 ARR，不适用 GPL-3.0-only；来源与散列见资源清单。 |
| `calamitas_sepulcher_burst.ogg`、`calamitas_sepulcher_death.ogg` | 同一固定提交的 `Sounds/Item/DeadSunRicochet.ogg` 与 `Sounds/NPCKilled/SepulcherDeath.ogg`，按 SHA-256 去重后逐字节复用；用于灾坟魔物环形弹幕的一次发射声及火心控制退场时目标中心的一次死亡声。Calamity Team／原作者 ARR，不适用 GPL-3.0-only；来源与散列见资源清单。 |
| `calamitas_vortex_warning.ogg` | 固定提交 `1a8cebd` 的 `Sounds/Custom/SCalAltarSummon.ogg`，SHA-256去重后逐字节复用；普通世界第四轮弹幕第180源帧在目标中心播放，音高增加0.3八度。Calamity Team／原作者 ARR，不适用 GPL-3.0-only；来源与散列见资源清单。 |
| `calamitas_vortex_burn.ogg` | 同一固定提交的 `Sounds/Item/WeldingBurn.ogg`，按 SHA-256 去重后逐字节复用；低语漩涡在第六源帧节奏、原生伤害成功时于受击者中心播放灼烧提示。Calamity Team／原作者 ARR，不适用 GPL-3.0-only；来源与散列见资源清单。 |
| `calamitas_heart_hurt.ogg`、`calamitas_heart_death.ogg`、`calamitas_seeker_death0/1/2.ogg` | 本机 Terraria 原版 `NPC_Hit_13.xnb`、`NPC_Killed_1.xnb` 与 `Custom/dd2_skeleton_death_0/1/2.xnb` 的原始 PCM，按固定灾厄源码指定的 SoundID 使用。为 Minecraft 转为 Ogg Vorbis q10，保持采样率与声道，无增益、裁剪或混音；这是有损格式转换，不是原文件逐字节复用，编解码帧差见清单。Re-Logic／原作者保留所有权利，不适用 GPL-3.0-only；原 XNB、PCM 与输出文件散列及转换参数见资源清单。 |
| `calamitas_flame.ogg`、`calamitas_seeker_cast.ogg`、`calamitas_heart_cast0/1/2.ogg`、`calamitas_seeker_impact0/1/2.ogg` | 本机 Terraria 原版 `Item_20.xnb`、`Item_74.xnb`、`Custom/dd2_dark_mage_cast_heal_0/1/2.xnb` 与 `Custom/dd2_dark_mage_heal_impact_0/1/2.xnb` 的 PCM，按固定灾厄源码调用点接入火焰射弹及火心／探魂者施法提示。Minecraft 所需 Ogg Vorbis q10 有损转换保留原采样率与声道，无增益、裁剪或混音；并非原文件逐字节复用。Re-Logic／原作者保留所有权利，不适用 GPL-3.0-only；原 XNB／PCM／WAV／输出散列、帧差与转换参数见资源清单。 |
| `sounds/music/calamitas_defeat.ogg` | Calamity Mod Music 原始 `Sounds/Music/CalamitasDefeat.ogg`，逐字节复用；至尊唤魔者投降阶段的场景音乐，与每世界一次的 LongFade 事件区分。DM DOKURO／原权利人保留权利，不适用 GPL-3.0-only；散列见资源清单。 |
| `calamitas_defeat_long_fade.ogg` | Calamity Mod Music `Sounds/Music/CalamitasDefeat_LongFade.ogg`；第三方素材，不适用本项目 GPL-3.0-only，来源与 SHA-256 见 `licenses/RESOURCE-SOURCES.json` |
原音频转码为 OGG、按 Minecraft 事件重新映射，均不构成新的音频授权。当前未在本项目中提供 Re-Logic 对这些音频的单独再分发授权。

## 语言、模型与配置

| Mojang 派生资源 | 来源与权利归属 |
| --- | --- |
| `models/item/*_spear*.json`、`spear_in_hand.json`、`mace.json`、`handheld_mace.json`、`heavy_core.json` | Minecraft 26.1.2 原模型、纹理引用和显示变换；为 1.20.1 调整命名空间、纹理变量及 Forge 显示上下文，模型形状与变换仍归 Mojang／Microsoft，非 GPL-3.0-only |
| 七材质长矛及重锤配方、gust 粒子定义、新增物品／附魔／字幕语言条目 | Minecraft 26.1.2 原配方、粒子资源和语言文字的接口适配；Mojang／Microsoft 及原翻译权利人，非 GPL-3.0-only。重锤配方与修理将旋风棒替换为烈焰棒 |

本项目原创的 Minecraft 模型定义、标签、音效事件映射及集成配置适用 GPL-3.0-only。中英文语言文件包含原作品名称、描述与翻译；来自原作品的文字仍保留原作者权利，新增的操作绑定说明等原创文字适用 GPL-3.0-only。资源 JSON 对第三方 PNG／OGG 的引用不使这些资源成为 GPL-3.0-only。

## Forge 与构建工具

- 项目基于 Minecraft Forge MDK。原 MDK 许可文本保留在 `licenses/FORGE-LICENSE.txt`，原有 `CREDITS.txt` 和文件头不因本项目采用 GPL-3.0-only 而失效。
- Gradle Wrapper 脚本保留原有版权与 Apache License 2.0 文件头；Gradle 相关条款见 <https://gradle.org/legal/>。
- Minecraft／Forge 等构建依赖按各自条款提供，不属于本项目原创内容。

## Calamity 原条款

本地随原作品取得的许可原文保存在 `licenses/CALAMITY-LICENSE.md`。其中包括：

> The Calamity Mod and all associated source code, visual assets and audio assets, is all rights reserved (ARR).

> Code lifted from the Calamity Mod repository must be credited to the Calamity Team.

> The Calamity Mod cannot be redistributed in any form or in any location. The Steam Workshop is the only officially hosted download of the Calamity Mod.

本项目的 GPL-3.0-only 文本不覆盖或修改上述条款，也不构成 Calamity Team／Re-Logic 对本仓库包含的第三方素材的额外公开发布许可。当前目录中未提供另外取得的素材再分发授权文件。

## Odamane Halo 来源

二阶段 Boss 的 Odamane Halo 几何、UV、动画与渲染行为参照Goety Revelation的 OdamaneHaloModel／OdamaneHaloLayer，在本模组命名空间内独立重建，不调用或依赖 Goety Revelation、RevelationFix、Ending Library 或 Curios 的 Java 类。贴图同样原样取自此模组。odamane／rewind GLSL 原样保留，相关后处理 JSON 仅替换资源命名空间。

此模组的 mods.toml 声明 `ARR`；这些来源素材、着色器及派生模型不属于本项目原创 GPL-3.0-only 内容，权利归原作者／原权利人。逐项来源和 SHA-256 见 `reference/mahou/odamane-halo.json` 与 `licenses/RESOURCE-SOURCES.json`。

## 上游与署名

- Calamity Mod／Calamity Team：<https://github.com/CalamityTeam/CalamityModPublic>
- Terraria／Re-Logic：<https://terraria.org/>
- Minecraft Forge：<https://minecraftforge.net/>
- Gradle：<https://gradle.org/>

Java 移植、Minecraft 三维适配及项目维护：EverlaPrismerAya。

## 第四天灾玩家皮肤

第四天灾战斗音乐 `assets/arkofcosmos/sounds/music/masquerade_waltz.ogg` 由用户提供，原文件未转码，来源路径与 SHA-256 见资源清单。作者及许可未随文件提供，权利归原作者／原权利人，不纳入本项目 GPL-3.0-only 授权。

| 资源 | 来源与权利说明 |
| --- | --- |
| 怨戾／异端僭越、DeathAshParticle模型表面灰烬的派生源码、PNG、OGG、原版音效及Dust裁片 | Calamity Mod提交`1a8cebd27ec5615316b78f71973446b5528d2b78`的Rancor、GruesomeEminence及派生射弹、粒子、融合材质和着色器；原始纹理／Calamity声音逐字节复用。Terraria音效从Re-Logic原始XNB PCM解码后转为Vorbis q8，属于有损编码；Dust为原始RGBA图集8×8无重采样裁片。源码与资源遵循各自上游许可／保留权利声明，不将美术音频纳入本项目GPL许可；详见本文件Calamity／Terraria既有声明及`licenses/RESOURCE-SOURCES.json`。 |
| `assets/arkofcosmos/sounds/fourth_calamity_death.ogg` | 用户提供的第四天灾消失音效，原文件未转码；来源路径与 SHA-256 见资源清单。作者及许可未随文件提供，权利归原作者／原权利人，不纳入 GPL-3.0-only 授权。 |
| `assets/arkofcosmos/textures/entity/kira_official.png` | 正版玩家 `Kira_Official`，UUID `d150668b708846f8aa9d367bbd962d85` 的 slim 皮肤快照；来源为 Mojang Session Server 返回的 Minecraft Textures 地址，URL 与 SHA-256 见资源清单。皮肤权利归原作者／原权利人，不纳入 GPL-3.0-only 授权。 |

第四天灾战术设计参考本地 sample 中 ProviPvP 与 Meteor Client 的操作节奏、触及、归属、自伤约束和水晶选点评分思路；不引入其客户端框架或训练假人的模拟受伤实现。真实交互使用 Minecraft／Forge 自身入口。

第四天灾服务端 `FourthCalamityCrystalAura` 的选目标、破晶／放置流水线、支撑、面部放置、重试与预测伤害适配自 Meteor Development 的 Meteor Client `CrystalAura.java` 与 `DamageUtils.java`（本地版本提交 `2385f7b4bb9de1a698b4503254bd32f30fd7a87d`，上游 https://github.com/MeteorDevelopment/meteor-client ）。原代码版权归 Meteor Development，以 GNU GPL v3 发布；本项目适配代码遵循 GPL-3.0，许可证全文见 `LICENSE.txt`。客户端设置界面、按键、HUD 和网络发包由 Boss 服务端设置、原生实体同步及交互入口替代。

第四天灾服务端自卫分支参考同一 Meteor Client 提交的 `KillAura.java`：复用命中箱最近点范围判定与一次时序门控后的多目标攻击遍历，以 Boss 原生攻击和既有伤害权威替代客户端发包。自卫配置为6格范围、不限制目标数量、每tick尝试攻击，保留既有目标10tick受伤冷却。

## 至尊灾厄用户提供皮肤

| 文件 | 来源与许可 |
| --- | --- |
| `assets/arkofcosmos/textures/entity/supreme_calamitas.png` | 用户提供的 `sample/至尊女巫-灾厄.png`，64×64原图逐字节复制，SHA-256 `e4ff470dfa03bc89ea537dbab027f7be4796e5fdd4542718ab4da2aa7fd630f3`；供至尊灾厄的Slim模型使用。原作者／原权利人保留权利，提供者未另行指定许可，不纳入GPL-3.0-only。 |

怨戾与异端僭越物品Tooltip逐字保留Calamity固定提交的`Localization/en-US/Mods.CalamityMod.Items.Weapons.Magic.hjson`英文原文；中文优先采用本地CalamityCN同名Magic条目，与固定英文对照后保留汉化文本。原文快照、中文源文件散列及选择说明见`reference/necromantic-infusion/localization-sources.json`；相关文本权利归Calamity Team／汉化作者，未重新按GPL授权。
