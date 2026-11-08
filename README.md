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
