# ProjectC — Ark of Cosmos · 鸿蒙方舟

将 Terraria 灾厄模组（Calamity Mod）的鸿蒙方舟带入 Minecraft 的 Forge 模组。包含五段连击、投掷剪合、星链、针弹、追踪弹、招架充能与时空撕裂，并将对应的二维特效适配到 Minecraft 三维场景。

另提供独立武器 Galaxia（银河），包含凤凰、白羊、北极星和仙女座四种攻击模式、星座选择盘及派生弹幕；战斗视觉使用原作纹理及由原 BasicTint.fx 转换的 GLSL，包含残影、smear、锁链、星座和星环。原版同阶段视觉一致性仍待验收，光影包兼容性由用户自行校验。

新增独立武器 Ark of the Elements（元素方舟），包含五段连击、投掷剪合、太阳针与元素玻璃星、招架充能、裂缝剪击及元素谐鸣状态。使用原作纹理与音频，本轮绘制使用 Minecraft 内置通道。

本项目由 EverlaPrismerAya 维护，与 Calamity Team、Re-Logic、Mojang 和 Minecraft Forge 项目没有官方隶属关系。原作品的代码、纹理和音效归各自权利人所有。

## 环境与安装

| 项目 | 版本／要求 |
| --- | --- |
| Minecraft | 1.20.1 |
| 加载器 | Forge；开发与回归使用 47.4.21 |
| Java | 17 |
| 当前模组版本 | 0.1.1 |
| 安装位置 | 客户端与服务端均需安装 |
| 模组 ID | `arkofcosmos` |

将构建得到的模组 JAR 放入对应游戏实例的 `mods` 目录。当前没有合成配方，可在创造模式的战斗物品栏获取，或使用命令：

```mcfunction
/give @s arkofcosmos:ark_of_cosmos
/give @s arkofcosmos:galaxia
/give @s arkofcosmos:ark_of_the_elements
```

### 代码目录

Java 包：`net.everla.projectc.arkofcosmos`。

武器默认通过 `WeaponDamage` 走原生伤害与死亡流程，并无视原版受击冷却；银河、后续新增武器以及方舟对 `ignore_special_damage` 标签实体的伤害沿用此入口。各攻击自身的命中间隔仍按武器规则计算。方舟的独立血量与特殊致死机制由专用的 `ArkDamage` 提供。

按职责分类，灾厄派生实现额外放入 `calamity` 来源分区：

```text
arkofcosmos/
├─ ArkOfCosmos.java          模组入口与注册
├─ item/                    武器物品、NBT 与工具提示接入
├─ entity/                  实体生命周期、同步、插值、附魔和冲刺碰撞
├─ combat/                  战斗事件、输入状态、目标选择、独立血量与死亡结算
├─ mixin/                   原版／Forge 接入与通用 ASM 血量守卫
├─ network/                 输入与特效数据包
├─ client/                  按键、显示事件及客户端注册
│  └─ render/               纹理、着色器绘制与 Oculus 光影合成后特效通道
├─ util/                    三维攻击平面与几何工具
└─ calamity/                Calamity 派生规则及其适配实现
   ├─ combat/               武器连段规则、伤害换算、充能上限与提示配色
   ├─ entity/               攻击阶段、运动、时序与命中规则
   ├─ util/                 原攻击分段缓动曲线
   └─ client/               刀刃绘制、粒子、震屏与拖尾算法
```
协议具体范围见 [许可范围说明](THIRD_PARTY_NOTICES.md)。

### 资源目录

资源命名空间为 `assets/arkofcosmos/`。

| 目录／文件 | 内容 |
| --- | --- |
| `textures/item/` | 物品主体、握柄与辉光纹理 |
| `textures/entity/` | 左右刀刃、辉光、针弹与追踪弹等实体纹理 |
| `textures/fx/` | 星芒、烟雾、光晕、连线、smear 和拖尾采样纹理；含独立绘制路径使用的刀刃纹理 |
| `shaders/core/` | Minecraft 核心着色器 JSON、GLSL 顶点／片元程序 |
| `sounds/`、`sounds.json` | 音频文件与音效事件映射 |
| `models/item/` | 物品模型定义 |
| `lang/` | 简体中文和英文名称、说明、按键名称 |
| `src/main/resources/data/` | 剑与工具分类标签、实体伤害与死亡兼容标签 |
| `META-INF/accesstransformer.cfg` | 摄像机平移与玩家击杀归属字段的访问配置 |
| `src/main/resources/arkofcosmos.mixins.json` | Mixin 配置与 ASM 启动插件入口 |

实体类型标签 `arkofcosmos:ignore_death_remove` 保留实体自身的死亡流程、死亡演出与延迟掉落，不强制移除尸体；默认包含 Bosses of Mass Destruction 的四种 Boss。
`arkofcosmos:ignore_special_damage` 使用与训练假人相同的普通物理伤害路径（保留 35% 伤害下限），不接管生命值与死亡逻辑，同时禁用方舟的强制尸体移除，无需再加入 `ignore_death_remove`；默认包含 `dummmmmmy:target_dummy` 和 `touhou_little_maid_spell:stellar_witch`。
可通过数据包中的 `data/arkofcosmos/tags/entity_types/ignore_death_remove.json` 与 `ignore_special_damage.json` 增补或覆盖，未安装的模组实体为可选项。

素材逐项来源与散列记录见 [资源清单](licenses/RESOURCE-SOURCES.json)。清单只记录来源与文件对应关系，不授予第三方素材的使用或再分发许可。

## 从源码构建

安装 JDK 17，在项目根目录运行：

```powershell
.\gradlew.bat build
```

Linux／macOS 可使用 `bash ./gradlew build`。构建产物位于 `build/libs/`。Gradle Wrapper 会下载所需依赖。

```powershell
# 开发客户端
.\gradlew.bat runClient

# 服务端攻击行为回归
.\gradlew.bat runGameTestServer

# 曲线和粒子数值检查，也会随 build 执行
.\gradlew.bat arkRegression arkFxRegression
```

## 许可与致谢

**MIT 仅覆盖本项目有权授权的原创部分，不覆盖整个资源目录，也不重新授权 Calamity 的移植代码。**

- 原创 Minecraft／Forge 适配代码、原创配置和文档：见 [MIT License](LICENSE.txt)。
- Calamity 派生的 Java 逻辑及拖尾片元着色器：保留 Calamity 原作品权利与署名，见 [许可范围说明](THIRD_PARTY_NOTICES.md)。
- Calamity 纹理与音效：Calamity Team／原作者所有，原许可为 All Rights Reserved。
- Terraria 音频：Re-Logic 所有；转码为 OGG 不改变权利归属。
- Forge MDK 与 Gradle Wrapper：保留各自原有许可与文件头。

完整的按文件／类别许可划分见 [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md)，原条款保存在 [`licenses/`](licenses/)。
