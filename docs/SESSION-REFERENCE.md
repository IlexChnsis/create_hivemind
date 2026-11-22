# 调研来源

- 会话 ID：`session-14cb25d3-22bc-4b7d-a513-715215b867c8`
- 时间：2026-11-22
- 调研对象：`timplay33/Create-Mobile-Packages` @ `mc1.21.1/main`（tree `53ec8db`，v0.7.8，发布日 2026-10-05）
- 结论摘要：见 [ROADMAP.md](ROADMAP.md) 第 1 节
- 关键约束：
  - `VirtualRobo.serverLevel` 为 `private`，无 setter（跨维度需 `@Accessor`）
  - `RoboManager` 按维度分治（每个 `ServerLevel` 一个 `SavedData`）
  - `VirtualRobo` 的 NBT 不存维度（跨维度必做 `homeDimension` 持久化）
  - `CommonEvents` 显式取消 `RoboEntity` 的维度旅行事件（#250，v0.6.0 起）
  - 目标解析只查当前 `ServerLevel`（玩家与端口都是）
- 未合并的上游工作：PR #334「Portal Port」（迁移骨架可参考，语义不可依赖）
- 上游既有相关 issue：#347（与需求一致，open）、#94（Portal Port 设计，open）

## M0 验收

| 项 | 结果 |
|---|---|
| 仓库初始化 | 本仓库，`git init` + 首次提交 |
| 构建脚手架 | ModDevGradle 2.0.78 → 实建时为 2.0.148（见下）/ Java 21 toolchain / Gradle 8.14.2 wrapper |
| 依赖接入 | Create 6.0.10-280、CMP 0.7.8（`maven.modrinth:create-mobile-packages:xSPt2JXH`，Modrinth 项目 `1vXRfEHZ`） |
| 空壳加载 | `@Mod("create_hivemind")` 类 + 空 `create_hivemind.mixins.json` |
| 实际构建结果 | 见下表「构建验证」 |

## 构建验证

（2026-11-22 实测，M1 出包时更新）

- 环境：Windows 11、JDK 21.0.10（Zulu）、Gradle 8.14.2
- 网络：本机**直连不可用**，所有 HTTPS 均需走本地代理 `127.0.0.1:7897`；
  `maven.neoforged.net` 在此网络下直连超时，但经代理可正常拉取。
- Gradle 缓存位置：沙箱只允许写工作区，因此 `GRADLE_USER_HOME` 指向 `./.gradle-home`（已 gitignore）。

| 命令 | 结果 | 说明 |
|---|---|---|
| `.\gradlew.bat build` | **通过** | `BUILD SUCCESSFUL`，产出 `build/libs/create_hivemind-1.21.1-0.0.1.jar`（约 13.7 KB） |
| `.\gradlew.bat runClient` | **未执行** | 本机无法启动游戏客户端做运行时验证 |

产物内容已核对：`CreateHiveMind`、`CHMConfigs`、`CHMServer`、`FoldController`/`FoldLanding`/`FoldState`/`FoldStateStore`、
`RoboBeeBehaviorMixin` 共 8 个 class，加 `create_hivemind.mixins.json`、`META-INF/neoforge.mods.toml`、`assets/create_hivemind/lang/{en_us,zh_cn}.json`。

### 已知环境问题（不影响本模组产物）

1. **NeoForm 重编译在本机失败**：`createMinecraftArtifacts` 在写
   `net/minecraft/world/ticks/package-info` 时抛 `IOException: null`。已排除：
   ModDevGradle `2.0.78 / 2.0.91 / 2.0.107 / 2.0.148`、NeoFormRuntime `1.0.13 / 1.0.24`、
   JDK `Zulu 21.0.10 / Temurin 21.0.9`、locale（zh_CN / en_US）、堆大小（1G / 2G）、
   输出目录写入权限（实测可写）。
   结论：该故障属于「重编译后的 MC 源码/类」，与本模组 jar 无关。
   因此 `build.gradle` 用 `enable { disableRecompilation = true }` 绕过。
   **换到没有该故障的机器时，删掉该行即可恢复完整重编译流程。**
2. **mixin 需 `remap = false`**：NeoForge 1.21.1 运行时使用 Mojang 官方名，
   CMP 也按官方名发布，所以没有可用的混淆映射表；`mixins.json` 因此不带 `refmap`。
3. `File watcher server ... Couldn't open current thread, error = 5` 是沙箱限制造成的非致命告警。

### 联网复现步骤

```bash
# 需要能访问 maven.neoforged.net；若要走代理：
#   1) 取消 gradle.properties 里 systemProp.*.proxyHost/Port 注释，或
#   2) 在 %USERPROFILE%\.gradle\gradle.properties 配置同样的 systemProp.*
.\gradlew.bat build
```

### 运行时验收（尚未做）

```bash
# 把 CMP 0.7.8 + Create 6.0.10 放进 run/mods/ 后：
.\gradlew.bat runClient
```

游戏内检查点：

1. `/mods` 能看到 `Create: Hive Mind`
2. 首次启动生成 `config/create_hivemind-server.toml`，含 `[fold]` 八个键
3. 5000 格同维度投递：起飞约 3 秒后折跃，总耗时从约 14 分钟降到 1–2 分钟
4. 折跃瞬间有传送门粒子 + 末影人音效，随后正常降落交付
5. 短距离（< 512 格）投递不发生折跃
