# ProjectC — Ark of Cosmos · 鸿蒙方舟

将 Terraria 灾厄模组（Calamity Mod）的鸿蒙方舟带入 Minecraft 的 Forge 模组。包含五段连击、投掷剪合、星链、针弹、追踪弹、招架充能与时空撕裂，并将对应的二维特效适配到 Minecraft 三维场景。

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
```

## 操作与战斗

| 操作 | 效果                                   |
| --- |--------------------------------------|
| 持续按住攻击键，默认左键 | 五段连斩，斩击最后一段扔出剪刀                      |
| 投掷时保持攻击键 | 剪刀追随视线方向及选中的目标                       |
| 投掷进度 25%～75% 时松开攻击键 | 触发剪合；刀刃成功命中后获得 5 层充能，每次剪合仅获得一次       |
| 按下使用键，默认右键 | 向前剪出并招架；刀刃直接命中，或发动后短暂无敌期间首次受击，均可获得 16 层充能 |
| 有充能时，按住冲刺键并按下使用键 | 消耗全部充能施放时空撕裂                         |
| 本次消耗至少 5 层充能，并持续按住上述两键 | 剪合后触发向剪刀的冲刺                          |

冲刺键默认 **左 Alt**，可在「选项 → 控制 → 按键绑定 → 鸿蒙方舟」修改，也支持鼠标按键。工具提示实时显示当前冲刺键和使用键。

- 默认投掷目标位于玩家前方 **20 方块**；剪刀本体最远距玩家 **47.5 方块**。
- 自动索敌优先选择准星 12° 范围内的敌对生物及正在攻击玩家的中立生物。实际攻击也可以命中被动生物；玩家与盟友被排除。
- 充能上限为 **16 层**，普通攻击会消耗充能。物品上方彩虹条显示能量，下方橙色条显示招架冷却。
- 招架发动后提供约 **0.35 秒**无敌窗口，冷却约 **5.67 秒**。
- 武器基础攻击伤害为 **16 点**，无限耐久，支持剑类附魔及相应命中效果。
- 冲刺终点可以容纳玩家时，允许穿过途中方块；终点被方块占据时仍处理途中方块碰撞；潜行和实体碰撞不会阻止冲刺。

现有物品说明保留了部分原作品文字；上表中的 16 层充能和招架机制描述对应本模组的实际逻辑。

## 三维适配与特效

每次发动攻击时，按照玩家视线和左右方向锁定攻击平面。投掷目标可以随视角移动，但刀刃的旋转平面保持固定。

刀刃使用原纹理表面与透明轮廓侧面形成厚度。挥斩残影、回旋和投掷 smear、针弹与追踪弹拖尾、星链、光环、烟雾、星芒及撕裂缝合效果分别接入对应攻击阶段。附着刀刃和弹幕的平面效果随各自视觉平面绘制，独立粒子保持面向摄像机。

第一人称下，武器主人看到的星链、弹幕、拖尾和粒子在视点附近会平滑淡化：0.5 方块以内保留 12% 不透明度，3 方块外恢复原样；其他玩家及第三人称视角不受影响。

拖尾着色器由 Calamity 的 `FadedUVMapStreak.fx` 转换为 GLSL 150。保留原宽度、颜色和衰减函数，以及预乘透明、非预乘透明和加法混合语义。特效接受方块遮挡，保留自身发光，不向场景添加彩色动态光。

攻击使用原有 60／120 Hz 逻辑子步；客户端根据同步轨迹进行渲染插值，拖尾保留跨 tick 历史。粒子更新使用 60 Hz，在二维转三维时不复制额外方向层，也不按渲染帧率增加生成数量。

## 架构

服务端负责攻击状态、目标选择、命中、附魔效果、能量与位移。客户端负责输入采集、实体渲染、粒子模拟、着色器和物品状态显示。

```text
客户端输入 ArkClient
    │ 输入状态包
    ▼
ArkNetwork → ArkCombat → ArkAttackEntity
                            │ 服务端攻击状态与运动子步快照
                            ▼
                    客户端轨迹插值
                       ├─ ArkAttackRenderer：刀刃、弹幕、附着平面效果
                       └─ ArkEffects → ArkFxParticle → ArkFxDraw：粒子与 GLSL 绘制
```

攻击实体通过 Forge 实体同步传递种类、拥有者、阶段、剩余寿命、平面、位置、旋转、缩放与运动子步；命中、死亡和招架等一次性效果通过独立效果包发送。客户端按逻辑步去重，避免同一快照在多个渲染帧重复生成粒子。

### 代码目录

Java 包：`net.everla.projectc.arkofcosmos`。

按职责分类，灾厄派生实现额外放入 `calamity` 来源分区：

```text
arkofcosmos/
├─ ArkOfCosmos.java          模组入口与注册
├─ item/                    武器物品、NBT 与工具提示接入
├─ entity/                  实体生命周期、同步、插值、附魔和冲刺碰撞
├─ combat/                  Forge 战斗事件、玩家输入状态与目标选择
├─ network/                 输入与特效数据包
├─ client/                  按键、显示事件及客户端注册
│  └─ render/               Minecraft / OpenGL 纹理与着色器绘制接口
├─ util/                    三维攻击平面与几何工具
└─ calamity/                Calamity 派生规则及其适配实现
   ├─ combat/               武器连段规则、伤害换算、充能上限与提示配色
   ├─ entity/               攻击阶段、运动、时序与命中规则
   ├─ util/                 原攻击分段缓动曲线
   └─ client/               刀刃绘制、粒子、震屏与拖尾算法
```

`entity.ArkAttackEntity` 继承 `calamity.entity.CalamityAttackEntity`：后者保存攻击模拟状态与派生算法，前者实现实体同步、实际伤害／附魔、特效发包和 Minecraft 冲刺碰撞。攻击仍按原有顺序执行子步、命中、寿命递减与快照采集。

`client.render.ArkFxDraw` 提供纹理、混合状态和着色器绘制接口，灾厄拖尾采样与几何生成由 `calamity.client.CalamityTrail` 调用它完成。`client.ArkClient` 接收输入和 Forge 客户端事件，并调用灾厄效果实现。

包名表示职责和主要来源，不是逐行许可声明。`calamity` 内仍有必要的 Minecraft 适配；`item`、`combat`、`entity` 等适配类仍保留部分派生参数和接入逻辑，不能据此将整个文件认定为纯原创 MIT。具体范围见 [许可范围说明](THIRD_PARTY_NOTICES.md)。注册 ID、NBT 字段、网络协议和资源路径不因 Java 包调整而改变。

现有检查随包结构迁移至 `src/test/java/` 下对应的 `combat`、`calamity/util` 和 `calamity/client` 目录。

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
| `src/main/resources/data/` | 剑与工具分类标签 |
| `META-INF/accesstransformer.cfg` | 摄像机平移震屏所需的访问配置 |

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

## 验证范围与已知差异

当前构建、曲线／粒子数值检查及综合服务端 GameTest 已通过。GameTest 覆盖连段、投掷距离、锁敌、剪合、派生弹幕、招架、伤害与冲刺等行为；这些检查不能替代客户端逐段视觉对照和真实多人联机验证。

特效黑色不透明外框已在代码中修正着色器混合状态应用顺序，修正版仍需客户端复核。着色器包与第三方渲染优化模组的兼容性尚未完成验证。

本项目保留原实现的部分特殊行为，包括投掷曲线在特定阶段的跳变，以及剪合阶段未归一化向量造成的超长伤害线。因此，47.5 方块仅是剪刀本体的距离上限。Terraria 的暴击、NPC 防御和击退公式未完整移植。

## 许可与致谢

**MIT 仅覆盖本项目有权授权的原创部分，不覆盖整个资源目录，也不重新授权 Calamity 的移植代码。**

- 原创 Minecraft／Forge 适配代码、原创配置和文档：见 [MIT License](LICENSE.txt)。
- Calamity 派生的 Java 逻辑及拖尾片元着色器：保留 Calamity 原作品权利与署名，见 [许可范围说明](THIRD_PARTY_NOTICES.md)。
- Calamity 纹理与音效：Calamity Team／原作者所有，原许可为 All Rights Reserved。
- Terraria 音频：Re-Logic 所有；转码为 OGG 不改变权利归属。
- Forge MDK 与 Gradle Wrapper：保留各自原有许可与文件头。

完整的按文件／类别许可划分见 [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md)，原条款保存在 [`licenses/`](licenses/)。
