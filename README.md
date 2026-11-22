# Create: Hive Mind

Create: Mobile Packages（CMP）的附属模组，为运输蜂（Robo Bee）增加**同维度远距离折跃**与**跨维度投递**。

- 形态：CMP 附属（addon），不向上游提 PR
- modid：`create_hivemind`；显示名：`Create: Hive Mind`
- 许可：MIT
- 目标环境：Minecraft 1.21.1 / NeoForge 21.1.219+ / Create 6.0.10+ / CMP 0.7.8

> 当前状态：**M1 已实现并出包**（同维度远距离折跃 + 折跃感知 ETA）。跨维度投递（M2）尚未开始。
> 构建产物：`build/libs/create_hivemind-1.21.1-0.0.2.jar`。尚未做游戏内运行时验证，见
> [docs/ROADMAP.md](docs/ROADMAP.md) 与 [docs/SESSION-REFERENCE.md](docs/SESSION-REFERENCE.md)。

## 功能

运输蜂在**同一维度**内远距离投递时，朝目标飞出一段后沿连线折跃到目标附近，再飞完最后一段并正常降落交付。

### 折跃逻辑（0.0.3 起：锚点式）

起飞时把当前位置记为**出发点（anchor）**，之后按 `checkIntervalTicks` 的间隔探测。
以下两条**同时成立**时折跃：

1. 当前位置 → **出发点** 的距离 > `minFoldDistance`
2. 当前位置 → **目标点** 的距离 > `maxArrivalDistance`

折跃落点：在「当前位置 → 目标点」这条连线上，取距目标正好 `maxArrivalDistance` 的那个点。
**落点随即成为新的出发点**，所以一次航程可以连续折跃多次，直到离目标足够近。

因为折跃后「离目标 = `maxArrivalDistance`」，第 2 条不再成立，循环会自然收敛，不需要次数上限。
若有人把 `maxArrivalDistance` 配成 ≥ `minFoldDistance`，折跃会原地自激，此时模组**自动禁用折跃**。

配置写在 `config/create_hivemind-server.toml`（服务器配置，改完重启生效）：

```toml
[fold]
  enable = true
  minFoldDistance = 100
  maxArrivalDistance = 64
  checkIntervalTicks = 5
  particles = true
  sound = true
```

> 0.0.2 的 `minDistance` / `minTravelTicks` / `arrivalHeight` / `arrivalSpread` /
> `maxFoldsPerTrip` / `foldCooldownTicks` 已全部删除。旧 toml 里残留的键会被 NeoForge 忽略，
> 删掉文件重新生成即可。

实现方式：`RoboBeeBehaviorMixin` 在 CMP 的 `RoboBeeBehaviorController#handleNavigateToTarget` 末尾注入，
只调用 `VirtualRobo#setPos` 把逻辑蜂挪到落点，不改状态机、不加方块、不加物品、不改存档格式。

**落点不做地形检测**：逻辑蜂没有碰撞，卡在山体里只是一瞬间的视觉瑕疵，之后会自己飞出来。

## 剩余时间（ETA）怎么算

玩家在 Toast 里看到的剩余时间**不是**「剩余直线距离 ÷ 速度」，而是**实际预期还要飞多久**：

```
剩余时间 = 总路程时间 − 折跃路段按正常速度折算的时间
```

锚点式折跃让这个式子很干净。设 `dT` = 到目标距离、`dA` = 到出发点距离：

```
还要先飞     = max(0, minFoldDistance − dA)
折跃点离目标 = dT − 还要先飞
若 折跃点离目标 > maxArrivalDistance → 剩余 = 还要先飞 + maxArrivalDistance
否则                                → 剩余 = dT
```

折跃前（`dA` 接近 `minFoldDistance`）剩下 `还要先飞 + 64`；折跃后 `dA` 归零、`dT` 变成 64，
此时 `dT − 还要先飞` 为负 → 走「不折跃」分支 → 剩余仍是 `dT`。所以**倒计时连续、单调下降、不跳变**。

示例（`beeSpeed = 6`，5000 格投递，默认配置 `minFoldDistance = 100`、`maxArrivalDistance = 64`）：

| 阶段 | 上游算法 | 本模组 |
|---|---|---|
| 起飞瞬间 | 834 秒 | 28 秒 |
| 飞了 8 秒（`dA = 48`） | 786 秒 | 20 秒 |
| 折跃前一刻（`dA = 95`） | 784 秒 | 12 秒 |
| 折跃后（`dT = 64`） | 11 秒 | 11 秒 |

注入点：`VirtualRoboEtaMixin` 覆盖 `updateEta()` 的最终值（影响所有读取 ETA 的地方），
`PlayerTargetEtaMixin` 在 `updateEtaToast` 开头先覆写再让 Toast 读取
——因为 Toast 在 `handleNavigateToTarget` 里是「先 setETA 再立刻读」，早于 `updateEta()`。

## 为什么是附属而不是改上游

CMP 0.7.8 中：

- 服务端配置只有 6 项（速度、旋转速度、端口互发、最大距离、名牌、副手包裹），**没有任何传送或维度开关**；
- 导航是纯直线插值，**没有折跃逻辑**；
- 跨维度被显式禁止（`CommonEvents#onEntityTravelToDimension` 对 `RoboEntity` 一律 `setCanceled(true)`，自 v0.6.0 起）。

所以"改成配置就能用"这条路不存在，必须写代码。上游 issue #347 与本需求一致，但 PR #334（Portal Port）自 2026-04 起仍未合并，且它要求实体传送门方块，与"自动折跃"语义不同。

## 目录结构

```
create_hivemind/
├── build.gradle                  # ModDevGradle 2.0.148，Java 21 toolchain
├── gradle.properties             # 版本号与 mod 元信息，全部在此集中管理
├── settings.gradle
├── gradle/wrapper/               # Gradle 8.14.2（固定版本）
├── LICENSE                       # MIT
├── docs/
│   ├── ROADMAP.md                # 需求、路线、验收标准、风险
│   └── SESSION-REFERENCE.md      # 调研来源、构建验证与已知环境问题
├── src/main/templates/META-INF/neoforge.mods.toml   # 占位符模板，构建时展开
├── src/main/resources/
│   ├── create_hivemind.mixins.json                  # 3 个 mixin 的注册
│   └── assets/create_hivemind/lang/{en_us,zh_cn}.json
└── src/main/java/de/hivemind/create_hivemind/
    ├── CreateHiveMind.java                          # @Mod 入口
    ├── index/CHMConfigs.java                        # 配置注册
    ├── config/CHMServer.java                        # [fold] 九个配置键
    ├── robo/fold/FoldController.java                # 折跃判定与执行
    ├── robo/fold/FoldLanding.java                   # 落点选择
    ├── robo/fold/FoldState.java                     # 单蜂折跃状态
    ├── robo/fold/FoldStateStore.java                # 弱引用状态表
    ├── robo/eta/EtaModel.java                       # 折跃感知的 ETA
    ├── mixin/RoboBeeBehaviorMixin.java              # 注入 handleNavigateToTarget
    ├── mixin/VirtualRoboEtaMixin.java               # 注入 updateEta
    └── mixin/PlayerTargetEtaMixin.java              # 注入 updateEtaToast
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
