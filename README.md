# Create: Hive Mind

Create: Mobile Packages（CMP）的附属模组，为运输蜂（Robo Bee）增加**同维度远距离折跃**与**跨维度投递**。

- 形态：CMP 附属（addon），不向上游提 PR
- modid：`create_hivemind`；显示名：`Create: Hive Mind`
- 许可：MIT
- 目标环境：Minecraft 1.21.1 / NeoForge 21.1.219+ / Create 6.0.10+ / CMP 0.7.8

> 当前状态：**M1 已实现并出包**（同维度远距离折跃）。跨维度投递（M2）尚未开始。
> 构建产物：`build/libs/create_hivemind-1.21.1-0.0.1.jar`。尚未做游戏内运行时验证，见
> [docs/ROADMAP.md](docs/ROADMAP.md) 与 [docs/SESSION-REFERENCE.md](docs/SESSION-REFERENCE.md)。

## 功能

运输蜂在**同一维度**内远距离投递时，先正常飞一小段，然后直接折跃到目标附近，再飞完最后一段并正常降落交付。

触发条件（全部满足）：

1. `[fold] enable = true`
2. 当前与目标的直线距离 ≥ `minDistance`（默认 512 格）
3. 已经历 ≥ `minTravelTicks` 个巡航 tick（默认 60，即 3 秒）
4. 本次航程折跃次数 < `maxFoldsPerTrip`（默认 2）
5. 距上次折跃 ≥ `foldCooldownTicks`（默认 40）

落点：目标点上方 `arrivalHeight`（默认 32 格），再加半径 `arrivalSpread`（默认 12 格）的随机水平偏移，
然后向下扫描最多 64 格，取第一个「本格与上方一格都是空气」的位置；区块未加载时直接使用高空落点。

配置写在 `config/create_hivemind-server.toml`，服务器配置（改完重启生效）：

```toml
[fold]
  enable = true
  minDistance = 512
  minTravelTicks = 60
  arrivalHeight = 32
  arrivalSpread = 12
  maxFoldsPerTrip = 2
  foldCooldownTicks = 40
  particles = true
  sound = true
```

实现方式：`RoboBeeBehaviorMixin` 在 CMP 的 `RoboBeeBehaviorController#handleNavigateToTarget` 末尾注入，
只调用 `VirtualRobo#setPos` 把逻辑蜂挪到落点，不改状态机、不加方块、不加物品、不改存档格式。

## 为什么是附属而不是改上游

CMP 0.7.8 中：

- 服务端配置只有 6 项（速度、旋转速度、端口互发、最大距离、名牌、副手包裹），**没有任何传送或维度开关**；
- 导航是纯直线插值，**没有折跃逻辑**；
- 跨维度被显式禁止（`CommonEvents#onEntityTravelToDimension` 对 `RoboEntity` 一律 `setCanceled(true)`，自 v0.6.0 起）。

所以"改成配置就能用"这条路不存在，必须写代码。上游 issue #347 与本需求一致，但 PR #334（Portal Port）自 2026-04 起仍未合并，且它要求实体传送门方块，与"自动折跃"语义不同。

## 目录结构

```
create_hivemind/
├── build.gradle                  # ModDevGradle 2.0.78，Java 21 toolchain
├── gradle.properties             # 版本号与 mod 元信息，全部在此集中管理
├── settings.gradle
├── gradle/wrapper/               # Gradle 8.14.2（固定版本）
├── LICENSE                       # MIT
├── docs/
│   ├── ROADMAP.md                # 需求、路线、验收标准、风险
│   └── SESSION-REFERENCE.md      # 调研来源与结论摘要
├── src/main/templates/META-INF/neoforge.mods.toml   # 占位符模板，构建时展开
├── src/main/resources/
│   └── create_hivemind.mixins.json                  # 空 mixin 配置（M0）
└── src/main/java/de/hivemind/create_hivemind/
    └── CreateHiveMind.java                          # @Mod 空壳
```

M1 起新增的代码位置（已在 ROADMAP 规划，尚未创建）：

```
src/main/java/de/hivemind/create_hivemind/
├── index/CHMConfigs.java
├── config/CHMServer.java
├── robo/fold/{FoldController,FoldLanding,FoldStateStore}.java
└── mixin/{RoboBehaviorMixin,VirtualRoboAccessor,PlayerTargetMixin}.java
```

## 构建

前置：**JDK 21 必须本机预装**（`java -version` 显示 21.x）。本仓库没有引入 `foojay-resolver` 插件，
Gradle 不会自动下载 JDK，也没有写死 `org.gradle.java.home`，用系统默认 JDK。

首次构建需要联网拉取 NeoForge、Create、CMP 依赖。

```bash
# Windows
.\gradlew.bat build

# 只编译，最快验证工程是否可用
.\gradlew.bat compileJava

# 启动客户端（需已能解析依赖）
.\gradlew.bat runClient
```

### 依赖来源

| 依赖 | 坐标 |
|---|---|
| Create | `com.simibubi.create:create-1.21.1:6.0.10-280:slim`（maven.createmod.net） |
| Ponder | `net.createmod.ponder:ponder-neoforge:1.0.82+mc1.21.1` |
| Flywheel | `dev.engine-room.flywheel:flywheel-neoforge(-api)-1.21.1:1.0.6` |
| Registrate | `com.tterrag.registrate:Registrate:MC1.21-1.3.0+67` |
| **CMP** | `maven.modrinth:create-mobile-packages:xSPt2JXH`（versionId，即 1.21.1-0.7.8） |

CMP 不作为源码依赖、不复制其代码；只通过二进制依赖接入。需要阅读上游源码时，clone 到 `reference/`（已在 `.gitignore` 中），不参与构建。

离线兜底：把 jar 放进 `libs/`，`build.gradle` 里已有 `flatDir { dirs "libs" }`。

## 安装（开发用）

`runClient` 只需要 CMP 依赖被解析；正式测试时把 CMP 0.7.8 的 jar 与 Create 6.0.10 放进 `run/mods/`。

## 计划命名空间

当前包名为占位值 `de.hivemind.create_hivemind`。若要在发布前更换，只需改三处：

1. `gradle.properties` 的 `mod_group_id`
2. `src/main/java/` 下的包目录
3. `src/main/resources/create_hivemind.mixins.json` 的 `package` 字段

modid `create_hivemind` 一旦发布不应再改（会牵动 mixin refmap 与存档标识）。

## 上游与来源

- CMP 仓库：<https://github.com/timplay33/Create-Mobile-Packages>
- 构建脚手架参考上游（MIT），并参考了 `Create: Additional Logistics` 的 NeoForge 1.21.1 MDG 布局。
- 调研会话与结论：见 [docs/SESSION-REFERENCE.md](docs/SESSION-REFERENCE.md)。
