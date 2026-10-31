# 许可范围与第三方内容

本文说明 ProjectC — Ark of Cosmos 各类文件的来源和许可范围。根目录 `LICENSE.txt` 的 MIT 授权只适用于本项目有权授权的原创部分；文件位于同一仓库、改用 Java／GLSL、重新命名或经过格式转换，都不使第三方内容自动成为 MIT。

## Java 代码

银河中英文物品描述分别参考 Calamity Mod 的 `Localization/en-US/Mods.CalamityMod.Attunement.hjson`、`Mods.CalamityMod.Items.Weapons.Melee.hjson`，以及 CalamityCN 的 `Localization/CalamityMod/zh-Hans/` 对应文件。中文调谐名称和相关用语来自 CalamityCN 汉化作者；操作说明及版本差异按本项目实际行为调整。相关原文和译文的权利归各自作者所有，不纳入本项目 MIT 授权。

目录：`src/main/java/net/everla/projectc/arkofcosmos/`。

本项目新增的 Minecraft／Forge 注册、网络通信、输入处理、NBT 状态管理、三维攻击平面、实体插值和渲染适配等原创部分，以 MIT 授权，版权所有者为 EverlaPrismerAya。

代码按职责分包，并将可独立识别的灾厄派生实现集中到 `calamity/`。这次整理不改变原有许可，也不表示边界外的混合适配文件已完全脱离原作。

以下路径相对于上述 Java 根目录：

| 文件 | Calamity 来源／派生内容 |
| --- | --- |
| `calamity/combat/GalaxiaRules.java`、`entity/GalaxiaEntity.java`、`calamity/client/GalaxiaRenderer.java` | `FourSeasonsGalaxia`、四种 Attunement、`PhoenixsPride`、`AriesWrath`、`AriesWrathConstellation`、`PolarisGaze`、`PolarisGazeStar`、`PolarisGazeDash`、`AndromedasStride`、`AndromedasStrideBoltSpawner`、`GalaxiaBolt` 和 `GalaxiaTropicRing` 的规则、阶段、曲线、参数与原图绘制；含用户确认的三维攻击平面、地表环形扩散和玩家碰撞适配 |
| `calamity/entity/CalamityAttackEntity.java` | `ArkoftheCosmosSwungBlade`、`RendingNeedle`、`EonBolt`、`ArkoftheCosmosConstellation`、`ArkoftheCosmosParryHoldout`、`ArkoftheCosmosBlast` 的攻击阶段、运动、时序与命中规则；包含必要的 Minecraft 单位及实体接口适配 |
| `calamity/combat/ArkWeaponRules.java` | 原武器的连段模式、伤害换算、充能上限及工具提示配色；保留本项目已有的机制修改 |
| `calamity/combat/ElementsRules.java`、`entity/ElementsEntity.java`、`combat/ElementsCombat.java`、`item/ElementsItem.java` | Calamity Team 的 ArkoftheElements、SwungBlade、ParryHoldout、SnapBlast、SolarNeedle、ElementalGlassStar 的规则、曲线、倍率、时序和形态；含原创 Forge 接入、三维瞄点适配及用户指定数值，Calamity 派生部分不作 MIT 重新授权 |
| `calamity/client/ElementsRenderer.java`、`calamity/client/ElementsEffects.java`、`calamity/client/ElementsVanillaParticles.java`、`calamity/client/ElementsAudio.java` | 原刀刃／弹幕 PreDraw、BasicTint、残影、粒子及 SoundStyle；Calamity 部分为 Calamity Team ARR，Terraria Dust／Gore／ActiveSound 派生逻辑归 Re-Logic／原权利人，不作 MIT 重新授权；含原创 Minecraft 三维及接口适配 |
| `calamity/util/ArkCurves.java` | 原攻击的分段曲线、参数及相关缓动实现 |
| `calamity/client/ArkAttackRenderer.java` | 原刀刃的 PreDraw、开合、残影、smear、针弹与爆发绘制逻辑；仍混有本项目的三维厚度与轮廓适配 |
| `calamity/client/ArkEffects.java` | 原攻击的粒子生成条件、数量、参数、星链及根据 `CalamityPlayer.ModifyScreenPosition` 适配的震屏行为；仍包含客户端轨迹管理 |
| `calamity/client/ArkFxParticle.java` | `GenericSparkle`、`CritSpark`、`FlareShine`、`HeavySmokeParticle`、`SquishyLightParticle`、`PulseRing`、`StrongBloom`、`GenericBloom`、`LineVFX`、`BloomLineVFX` 的更新与绘制逻辑及插值适配 |
| `calamity/client/CalamityTrail.java` | `PrimitiveRenderer` 的拖尾采样、几何和纹理坐标逻辑及三维适配 |
| `calamity/client/GalaxiaEffects.java`、`calamity/client/GalaxiaRenderer.java` | Galaxia 四模式及派生弹幕的原始 AI 粒子生成、PreDraw、残影、锁链、星座与星环绘制；`ArkFxParticle` 同时包含 `ConstellationRingVFX` 的原始椭圆环及星芒绘制，来源 Calamity Team，ARR；含客户端子步同步、插值、攻击平面与近景淡化适配 |
| `combat/ArkCombat.java`、`item/ArkItem.java` | Forge／物品接入；仍保留原武器的连段发起、充能、倍率、冷却、描述与状态行为，不能将整个混合文件理解为无例外的 MIT 代码 |
| `combat/ArkDamage.java` | 本项目的独立同步血量、伤害结算与死亡保护；其中原版不死图腾的检查、消耗、统计／进度触发和恢复效果流程参考并适配 Minecraft `LivingEntity.checkTotemDeathProtection`，该部分不纳入本项目 MIT 授权，详见下文 Minecraft 代码说明 |
| `entity/ArkAttackEntity.java` | Minecraft 实体生命周期、同步、插值、附魔与冲刺碰撞接入；仍保留攻击寿命初始化、子步调度、派生效果触发与已有附加伤害行为，不能将整个混合文件理解为无例外的 MIT 代码 |
| `client/ArkClient.java` | 原武器状态条配色及显示参数的客户端接入；震屏算法已移至 `calamity/client/ArkEffects.java` |
| `client/render/ArkFxDraw.java` | 本项目的 OpenGL／Minecraft 绘制适配，保留 XNA 混合与绘制语义；原拖尾算法已移至 `calamity/client/CalamityTrail.java` |

`util/AttackFrame.java`、`util/ArkGeometry.java` 与 `network/ArkNetwork.java` 分别提供本项目的三维攻击平面、空间几何及 Forge 网络接入。来源分区便于维护与审阅，不应被用来将灾厄派生内容重新授权为 MIT，或把包含原创适配的派生文件整体视为第三方原文件。

Calamity 来源的代码须保留 Calamity Team 署名及原条款。本项目只对新增原创部分作 MIT 授权，不声称取得了将原代码整体转授 MIT 的权利。原许可允许以源码作为开发参考，并要求对直接采用的代码署名；这不等于原项目采用 MIT。

### Minecraft 代码与兼容性研究

`combat/ArkDamage.java` 中的不死图腾流程参考并适配 Minecraft 1.20.1 的原版实现，保留双手检查、伤害类型限制、Forge 图腾使用事件、物品消耗、统计与进度、恢复效果及实体事件。Minecraft 原始代码及其派生部分的权利归 Mojang／Microsoft 等原权利人，适用 Minecraft 自身的许可与使用条款；本项目不将其重新授权为 MIT，也不声称拥有额外再分发授权。相关条款见 <https://www.minecraft.net/eula>。

## 纹理 PNG

范围：`src/main/resources/assets/arkofcosmos/textures/` 内的物品、刀刃、弹幕和 FX 纹理。

这些纹理来自 Calamity Mod，权利归 Calamity Team／各原作者，适用 **All Rights Reserved（保留所有权利）**，不适用本项目 MIT。重命名、重复放入不同绘制目录，以及用于三维刀刃表面，均不改变其来源或许可。

具体来源、当前 SHA-256 和历史来源记录见 `licenses/RESOURCE-SOURCES.json`。不能把该清单当作资源授权书。

## 着色器

目录：`src/main/resources/assets/arkofcosmos/shaders/core/`。

| 文件 | 来源与许可范围 |
| --- | --- |
| `fx_trail.fsh` | 对 Calamity `Effects/FadedUVMapStreak.fx` 中 `TrailPass` 的 GLSL 转换；保留原公式和常量，属于 Calamity 派生内容，**不作 MIT 重新授权** |
| `galaxia_tint.fsh` | 对 Calamity `Effects/BasicTint.fx` 中 `Recolor`／`TintPass` 的 GLSL 150 转换，保留 `rbg` 顺序、插值和 Alpha 乘法；Calamity Team，ARR，不作 MIT 重新授权 |
| `galaxia_tint.json` | 本项目编写的 Minecraft shader、sampler 和 uniform 接口配置；原创适配部分适用 MIT |
| `fx_trail.vsh` | 本项目编写的 Minecraft 顶点格式／矩阵接口适配；该原创适配部分以 MIT 授权，不扩展到原拖尾算法 |
| `fx_trail.json` | 本项目编写的 Minecraft 着色器注册、采样器和 uniform 配置，以 MIT 授权 |
| `fx_sprite.vsh`、`fx_sprite.fsh`、`fx_sprite.json` | 本项目编写的通用纹理／顶点颜色绘制及 Minecraft 接口配置，以 MIT 授权；采样到的第三方纹理仍遵循各自许可 |
| `mahou_forcefield.fsh`、`mahou_forcefield.json` | Calamity `Effects/SupremeShieldShader.fx` 及 `NPCs/SupremeCalamitas/ForcefieldTexture.png`、`ExtraTextures/GreyscaleGradients/Perlin.png` 的派生 GLSL 与纹理；Calamity Team，ARR，不适用本项目 MIT，来源与 SHA-256 见 `licenses/RESOURCE-SOURCES.json` |
将 HLSL／FX 转换为 GLSL 属于实现适配，不会消除原代码的权利或署名要求。

## 音频 OGG

目录：`src/main/resources/assets/arkofcosmos/sounds/`。

| 文件 | 来源与权利归属 |
| --- | --- |
| `snap_0.ogg` | Calamity 的 `ScissorGuillotineSnap`；Calamity Team／原作者，ARR |
| `phoenix_0.ogg`～`phoenix_2.ogg` | Calamity 的 `LouderPhantomPhoenix`；Calamity Team／原作者，ARR |
| `meaty_0.ogg` | Calamity 的 `MeatySlash`；Calamity Team／原作者，ARR |
| `galaxia_beacon_0.ogg`、`galaxia_lightning_0.ogg` | Calamity 的 AstralBeaconUse、LightningStrike 原始OGG；Calamity Team／原作者，ARR |
| `galaxia_item*.ogg`、`galaxia_hit*.ogg`、`galaxia_book_*.ogg`、`galaxia_impact_*.ogg`、`galaxia_eruption_*.ogg` | Terraria 原生 SoundID 音频转换；Re-Logic／原权利人所有，不适用本项目 MIT |
| `elements_item9_0.ogg` | Terraria `Content/Sounds/Item_9.xnb` 原音频转码；Re-Logic／原权利人所有，不适用 MIT |
| `item*.ogg`、`crystal_*.ogg`、`needle_death_*.ogg` | Terraria 原生 SoundID 音频转换；Re-Logic／原权利人所有，不适用本项目 MIT |
| `calamitas_phase1.ogg`、`calamitas_phase2.ogg`、`calamitas_phase3.ogg` | Calamity Mod Music 的 `CalamitasPhase1.ogg`、`CalamitasPhase2.ogg`、`CalamitasPhase3.ogg`；DM DOKURO《Stained, Brutal Calamity》及相关权利人，不适用本项目 MIT，来源与逐文件 SHA-256 见 `licenses/RESOURCE-SOURCES.json` |
原音频转码为 OGG、按 Minecraft 事件重新映射，均不构成新的音频授权。当前未在本项目中提供 Re-Logic 对这些音频的单独再分发授权。

## 语言、模型与配置

本项目原创的 Minecraft 模型定义、标签、音效事件映射及集成配置适用 MIT。中英文语言文件包含原作品名称、描述与翻译；来自原作品的文字仍保留原作者权利，新增的操作绑定说明等原创文字适用 MIT。资源 JSON 对第三方 PNG／OGG 的引用不使这些资源成为 MIT。

## Forge 与构建工具

- 项目基于 Minecraft Forge MDK。原 MDK 许可文本保留在 `licenses/FORGE-LICENSE.txt`，原有 `CREDITS.txt` 和文件头不因本项目采用 MIT 而失效。
- Gradle Wrapper 脚本保留原有版权与 Apache License 2.0 文件头；Gradle 相关条款见 <https://gradle.org/legal/>。
- Minecraft／Forge 等构建依赖按各自条款提供，不属于本项目原创内容。

## Calamity 原条款

本地随原作品取得的许可原文保存在 `licenses/CALAMITY-LICENSE.md`。其中包括：

> The Calamity Mod and all associated source code, visual assets and audio assets, is all rights reserved (ARR).

> Code lifted from the Calamity Mod repository must be credited to the Calamity Team.

> The Calamity Mod cannot be redistributed in any form or in any location. The Steam Workshop is the only officially hosted download of the Calamity Mod.

本项目的 MIT 文本不覆盖或修改上述条款，也不构成 Calamity Team／Re-Logic 对本仓库包含的第三方素材的额外公开发布许可。当前目录中未提供另外取得的素材再分发授权文件。

## Odamane Halo 来源

二阶段 Boss 的 Odamane Halo 几何、UV、动画与渲染行为参照Goety Revelation的 OdamaneHaloModel／OdamaneHaloLayer，在本模组命名空间内独立重建，不调用或依赖 Goety Revelation、RevelationFix、Ending Library 或 Curios 的 Java 类。贴图同样原样取自此模组。odamane／rewind GLSL 原样保留，相关后处理 JSON 仅替换资源命名空间。

参考 JAR 的 mods.toml 声明 `ARR`；这些来源素材、着色器及派生模型不属于本项目原创 MIT 内容，权利归原作者／原权利人。逐项来源和 SHA-256 见 `reference/mahou/odamane-halo.json` 与 `licenses/RESOURCE-SOURCES.json`。

## 上游与署名

- Calamity Mod／Calamity Team：<https://github.com/CalamityTeam/CalamityModPublic>
- Terraria／Re-Logic：<https://terraria.org/>
- Minecraft Forge：<https://minecraftforge.net/>
- Gradle：<https://gradle.org/>

Java 移植、Minecraft 三维适配及项目维护：EverlaPrismerAya。


