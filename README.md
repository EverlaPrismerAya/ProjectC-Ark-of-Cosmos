# ProjectC — Ark of Cosmos · 鸿蒙方舟

Minecraft Forge 1.20.1 模组，将 Terraria 灾厄系列武器与Boss适配到三维战斗，包含方舟与环境刃系列、银河、恣睢、怨戾、异端僭越，以及魔法使、第四天灾、至尊唤魔者和硫火女巫。

## 安装

| 项目 | 要求 |
| --- | --- |
| Minecraft | 1.20.1 |
| Forge | 47系列，开发使用47.4.21 |
| Java | 17 |
| 安装端 | 客户端与服务端 |
| 模组ID | `arkofcosmos` |

将构建JAR放入实例的 `mods/`。Goety启用至尊灾厄、女巫炼注和相关武器／仪式；Goety Revelation、RevelationFix与Curios提供相应可选联动。Oculus／Embeddium为可选光影兼容。

已内置怒气与肾上腺素，默认V／B激活，可改键；不能同时安装独立Revengeance模组。

## 功能导航

| 功能 | 说明 |
| --- | --- |
| 方舟系列 | [鸿蒙方舟](docs/ark-of-cosmos.md)、[旧神方舟](docs/ark-of-the-old-one.md)、[破碎方舟](docs/fractured-ark.md)、[远古方舟](docs/ark-of-the-ancients.md)、[元素方舟](docs/elements.md) |
| 环境刃与银河 | [残缺环境刃](docs/broken-biome-blade.md)、[环境之刃](docs/biome-blade.md)、[真·环境之刃](docs/true-biome-blade.md)、[银河](docs/galaxia.md) |
| 灾厄武器与炼注 | [恣睢](docs/violence.md)、[死灵炼注／怨戾／异端僭越](docs/necromantic-infusion.md)、[硫火女巫与普通炼注](docs/brimstone-witch.md) |
| Boss与召唤 | [魔法使](docs/mahou.md)、[第四天灾](docs/fourth-calamity.md)、[至尊唤魔者，灾厄](docs/supreme-calamitas.md)、[天灾变体与信标召唤](docs/calamity-summoning.md) |
| 原版战斗套件 | [七材质长矛、重锤与玩家风弹](docs/vanilla-combat.md) |
| 配置与开发 | [公共配置与战斗增益](docs/COMBAT_CONFIGURATION.md)、[项目架构](docs/architecture.md)、[渲染与特效](docs/rendering.md) |

武器获取、配方与操作见对应页面。公共配置为 `config/projectc-common.toml`，支持热重载与同步；客户端选项位于 `config/projectc-client.toml`。管理命令 `/projectc` 要求权限等级2，详细行为见架构和Boss说明。

`docs/` 与 `AGENT.md` 按项目设置仅在本地维护，不随Git发布；本地完整开发入口见 [AGENT.md](AGENT.md)。

## 从源码构建

安装JDK 17，在项目根目录运行：

```powershell
.\gradlew.bat build
```

Linux／macOS使用 `bash ./gradlew build`。产物位于 `build/libs/`；依赖由Gradle Wrapper获取。开发客户端使用 `runClient`。

`build` 通过 `check` 执行 `verifyMixinSrg` 与 `verifyMixinRefmap`。GameTest需按本地开发规则临时接入，日常构建不提供历史验证脚本中的任务。构建成功不代表游戏内视觉、音效、多人和整合包兼容已验收。

## 许可与致谢

**`main` 的原创部分采用 GNU GPL v3（`GPL-3.0-only`），不覆盖整个资源目录，也不重新授权第三方移植代码。**

| 分支 | 许可范围 |
| --- | --- |
| `main` | 本项目有权授权的原创部分采用 GPL-3.0-only |
| `1.20-legacy` | 固定于 `c5508f85c40755f5d6cff1208a151e0c2445cba9`，原创部分保留 MIT，第三方内容沿用各自条款 |

分发 GPL 覆盖的二进制或修改版本时，须保留版权及许可声明、标明修改，并按 GPL v3 要求提供对应版本的源码和必要构建脚本。此前 MIT 授权不被撤销，原声明见 [MIT 授权文本](licenses/MIT-legacy.txt)。第三方派生代码与 GPL 的组合分发仍须另外核对许可兼容性。

- 原创 Minecraft／Forge 适配代码、原创配置和文档：见 [GNU GPL v3](LICENSE.txt)。
- Calamity 派生的 Java 逻辑及拖尾片元着色器：保留 Calamity 原作品权利与署名，见 [许可范围说明](THIRD_PARTY_NOTICES.md)。
- Calamity 纹理与音效：Calamity Team／原作者所有，原许可为 All Rights Reserved。
- Terraria 音频：Re-Logic 所有；转码为 OGG 不改变权利归属。
- Forge MDK 与 Gradle Wrapper：保留各自原有许可与文件头。

完整的按文件／类别许可划分见 [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md)，原条款保存在 [`licenses/`](licenses/)。
