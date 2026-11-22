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
| 构建脚手架 | ModDevGradle 2.0.78 / Java 21 toolchain / Gradle 8.14.2 wrapper |
| 依赖接入 | Create 6.0.10-280、CMP 0.7.8（`maven.modrinth:create-mobile-packages:xSPt2JXH`，Modrinth 项目 `1vXRfEHZ`） |
| 空壳加载 | `@Mod("create_hivemind")` 类 + 空 `create_hivemind.mixins.json` |
| 实际构建结果 | 见下表「构建验证」，未通过的项在此写明原因 |

### 构建验证

（写于 2026-11-22，M0 交付时填写）

- 环境：JDK 21.0.10（Zulu，`C:\Program Files\Zulu\zulu-21`）、Gradle 8.14.2、Windows 11
- 首跑约束：交付时的沙箱环境**出网被阻断**（`pwsh` 访问 GitHub / Gitee 均为 HTTP 000），
  无法下载 ModDevGradle 插件与 Create / CMP 依赖，因此 `compileJava` 不在此环境验证。

| 命令 | 结果 | 说明 |
|---|---|---|
| `.\gradlew.bat --version` | **通过** | wrapper 自举成功，使用本地已解压的 Gradle 8.14.2，未触发下载；识别 JDK 21.0.10 |
| `.\gradlew.bat compileJava` | **未验证** | 卡在插件解析：`net.neoforged.moddev:2.0.78` 未在本机缓存，且无外网 |
| `.\gradlew.bat runClient` | **未执行** | 依赖同上，需先能解析依赖 |

已知首跑风险：

1. 本机 Gradle 缓存里存在的是 ModDevGradle `2.0.91` / `2.0.107`，本仓库按调研决议锁定上游一致的 `2.0.78`。
   联网环境首跑会正常下载；若必须完全离线，临时把 `build.gradle` 的版本改成 `2.0.91` 再试。
2. `File watcher server ... Couldn't open current thread, error = 5` 是沙箱限制造成的非致命告警；
   正常桌面环境不会出现。
3. 若把 `GRADLE_USER_HOME` 指向工作区内目录（沙箱下唯一的可写缓存位置），
   该目录**不要提交**（`.gitignore` 已覆盖 `.tmpgradle`）。

联网后的验收顺序：

```bash
.\gradlew.bat --version      # 确认 Gradle 8.14.2 + JDK 21
.\gradlew.bat compileJava    # 确认插件、Create、CMP 依赖可解析，空壳类可编译
.\gradlew.bat runClient      # 确认 create_hivemind 出现在 /mods 且无 mixin 报错
```

