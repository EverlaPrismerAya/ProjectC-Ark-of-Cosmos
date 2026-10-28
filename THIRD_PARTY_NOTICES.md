# 许可范围与第三方内容

本文说明 ProjectC — Ark of Cosmos 各类文件的来源和许可范围。根目录 `LICENSE.txt` 的 MIT 授权只适用于本项目有权授权的原创部分；文件位于同一仓库、改用 Java／GLSL、重新命名或经过格式转换，都不使第三方内容自动成为 MIT。

## Java 代码

目录：`src/main/java/net/everla/projectc/arkofcosmos/`。

本项目新增的 Minecraft／Forge 注册、网络通信、输入处理、NBT 状态管理、三维攻击平面、实体插值和渲染适配等原创部分，以 MIT 授权，版权所有者为 EverlaPrismerAya。

以下文件含有根据 Calamity 原实现移植或改写的逻辑，因此不能将这些混合文件整体理解为无例外的 MIT 代码：

| 文件 | Calamity 来源／派生内容 |
| --- | --- |
| `ArkAttackEntity.java` | `ArkoftheCosmosSwungBlade`、`RendingNeedle`、`EonBolt`、`ArkoftheCosmosConstellation`、`ArkoftheCosmosParryHoldout`、`ArkoftheCosmosBlast` 的攻击阶段、运动、时序与判定逻辑 |
| `ArkCombat.java`、`ArkItem.java` | `ArkoftheCosmos` 等原武器的连段、充能、倍率及描述行为；同时含本项目的 Minecraft 适配与机制修改 |
| `ArkCurves.java` | 原攻击的分段曲线、参数及相关缓动实现 |
| `ArkAttackRenderer.java` | 原刀刃的 PreDraw、开合、残影、smear、针弹与爆发绘制逻辑 |
| `ArkEffects.java` | 原攻击的粒子生成条件、数量、参数与星链生成逻辑 |
| `ArkFxParticle.java` | `GenericSparkle`、`CritSpark`、`FlareShine`、`HeavySmokeParticle`、`SquishyLightParticle`、`PulseRing`、`StrongBloom`、`GenericBloom`、`LineVFX`、`BloomLineVFX` 的更新与绘制逻辑 |
| `ArkFxDraw.java` | `PrimitiveRenderer` 的拖尾采样、几何和纹理坐标逻辑；同时含本项目的 OpenGL／Minecraft 适配 |
| `ArkClient.java` | 包含根据 `CalamityPlayer.ModifyScreenPosition` 适配的震屏行为及原武器的状态配色；其他内容包括本项目的客户端集成 |

Calamity 来源的代码须保留 Calamity Team 署名及原条款。本项目只对新增原创部分作 MIT 授权，不声称取得了将原代码整体转授 MIT 的权利。原许可允许以源码作为开发参考，并要求对直接采用的代码署名；这不等于原项目采用 MIT。

## 纹理 PNG

范围：`src/main/resources/assets/arkofcosmos/textures/` 内的物品、刀刃、弹幕和 FX 纹理。

这些纹理来自 Calamity Mod，权利归 Calamity Team／各原作者，适用 **All Rights Reserved（保留所有权利）**，不适用本项目 MIT。重命名、重复放入不同绘制目录，以及用于三维刀刃表面，均不改变其来源或许可。

具体来源、当前 SHA-256 和历史来源记录见 `licenses/RESOURCE-SOURCES.json`。不能把该清单当作资源授权书。

## 着色器

目录：`src/main/resources/assets/arkofcosmos/shaders/core/`。

| 文件 | 来源与许可范围 |
| --- | --- |
| `fx_trail.fsh` | 对 Calamity `Effects/FadedUVMapStreak.fx` 中 `TrailPass` 的 GLSL 转换；保留原公式和常量，属于 Calamity 派生内容，**不作 MIT 重新授权** |
| `fx_trail.vsh` | 本项目编写的 Minecraft 顶点格式／矩阵接口适配；该原创适配部分以 MIT 授权，不扩展到原拖尾算法 |
| `fx_trail.json` | 本项目编写的 Minecraft 着色器注册、采样器和 uniform 配置，以 MIT 授权 |
| `fx_sprite.vsh`、`fx_sprite.fsh`、`fx_sprite.json` | 本项目编写的通用纹理／顶点颜色绘制及 Minecraft 接口配置，以 MIT 授权；采样到的第三方纹理仍遵循各自许可 |

将 HLSL／FX 转换为 GLSL 属于实现适配，不会消除原代码的权利或署名要求。

## 音频 OGG

目录：`src/main/resources/assets/arkofcosmos/sounds/`。

| 文件 | 来源与权利归属 |
| --- | --- |
| `snap.ogg`、`snap_0.ogg` | Calamity 的 `ScissorGuillotineSnap`；Calamity Team／原作者，ARR |
| `phoenix_0.ogg`～`phoenix_2.ogg` | Calamity 的 `LouderPhantomPhoenix`；Calamity Team／原作者，ARR |
| `meaty_0.ogg` | Calamity 的 `MeatySlash`；Calamity Team／原作者，ARR |
| `item*.ogg`、`crystal_*.ogg`、`needle_death_*.ogg` | Terraria 原生 SoundID 音频转换；Re-Logic／原权利人所有，不适用本项目 MIT |

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

## 上游与署名

- Calamity Mod／Calamity Team：<https://github.com/CalamityTeam/CalamityModPublic>
- Terraria／Re-Logic：<https://terraria.org/>
- Minecraft Forge：<https://minecraftforge.net/>
- Gradle：<https://gradle.org/>

Java 移植、Minecraft 三维适配及项目维护：EverlaPrismerAya。
