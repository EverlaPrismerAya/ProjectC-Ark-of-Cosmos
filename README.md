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

## 许可与致谢

**MIT 仅覆盖本项目有权授权的原创部分，不覆盖整个资源目录，也不重新授权 Calamity 的移植代码。**

- 原创 Minecraft／Forge 适配代码、原创配置和文档：见 [MIT License](LICENSE.txt)。
- Calamity 派生的 Java 逻辑及拖尾片元着色器：保留 Calamity 原作品权利与署名，见 [许可范围说明](THIRD_PARTY_NOTICES.md)。
- Calamity 纹理与音效：Calamity Team／原作者所有，原许可为 All Rights Reserved。
- Terraria 音频：Re-Logic 所有；转码为 OGG 不改变权利归属。
- Forge MDK 与 Gradle Wrapper：保留各自原有许可与文件头。

完整的按文件／类别许可划分见 [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md)，原条款保存在 [`licenses/`](licenses/)。
