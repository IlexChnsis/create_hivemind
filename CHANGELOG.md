# Changelog

格式参考上游：每条版本以一行连字符分隔线包裹。

## 0.0.4 — 按目标类型分别开关

区分「发往玩家」与「发往另一个港口」的航班，分别控制是否折跃：

| 配置项 | 作用 |
|---|---|
| `enableForPlayers` | 目标解析到玩家时是否折跃（默认 true） |
| `enableForPorts` | 目标解析到另一个 Robo Bee Port 时是否折跃（默认 true） |

- 新增 `robo/fold/FoldGate`：`kindOf()` 判定目标类型，`allows()` 按类型取对应开关
- `FoldController` 与 `EtaModel` 共用同一判定，避免 ETA 与实际折跃行为不一致
- 原 `enable` 总开关被两个分类型开关取代；两者都 false 时行为与未安装模组一致
- 未知目标类型一律不折跃（保守策略，防止上游将来新增目标类型时被默认开启）

**规避的坑**：上游 `BeePortBlockEntityTarget.asBeePortBlockEntity()` 在目标区块未加载时返回
`null`。类型判定因此改用 `instanceof`，不依赖区块加载状态——否则远距离港口投递
（正是最需要折跃的场景）会被误判为未知类型而永不折跃。

配置项数：6 → 7（`enable` 拆成两项）。

------------------------------------------------------

## 0.0.3 — 锚点式折跃

折跃逻辑重写为「出发点」模型：

- 起飞时把当前位置记为**出发点（anchor）**；每次折跃成功后，落点成为新的出发点
- 触发条件（同时成立）：离出发点 > `minFoldDistance` 且离目标 > `maxArrivalDistance`
- 落点：在「当前位置 → 目标点」连线上，取距目标正好 `maxArrivalDistance` 的点
- 按 `checkIntervalTicks` 的间隔探测，可与距离计算共用同一间隔
- 折跃后「离目标 = `maxArrivalDistance`」，条件自然不再成立 → 循环收敛，无需次数上限与冷却

> 「离出发点的距离」指**当前点到出发点的直线距离**，不是沿飞行轨迹的路径积分。

默认值：`minFoldDistance = 100`、`maxArrivalDistance = 64`、`checkIntervalTicks = 5`。
折跃需要初始距离 > `minFoldDistance + maxArrivalDistance` = 164 格，低于此值不折跃。
远期投递总耗时 ≈ 27 秒，且与距离基本无关（5000 格与 200 格相同）。

配置项收敛到 6 个，删除 0.0.2 的六项：

| 删除 | 原因 |
|---|---|
| `minDistance` | 被 `minFoldDistance`（相对出发点）取代 |
| `minTravelTicks` | 改由距离驱动，不再按时间 |
| `arrivalHeight` | 落点改为连线上的几何点，不再抬到目标上方 |
| `arrivalSpread` | 同上，取消随机水平偏移 |
| `maxFoldsPerTrip` | 条件自收敛，不再需要次数上限 |
| `foldCooldownTicks` | 由 `checkIntervalTicks` 承担节流 |

新增：

- `CHMServer.isUsable()`：`maxArrivalDistance >= minFoldDistance` 时自动禁用折跃，避免原地自激
- ETA 模型同步重写为 `dT` / `dA` 双变量式，折跃前后连续，倒计时不再跳变
- 落点不再做地形检测（逻辑蜂无碰撞，卡方块只是一瞬视觉瑕疵）

行为变化：远期投递（5000 格）由 0.0.2 的约 9 秒变为约 27 秒（首次折跃需先飞满 `minFoldDistance`）。

> 修订：0.0.3 首发时 `minFoldDistance` 默认值为 512（远期投递 ≈ 96 秒），
> 随后按需求改为 100，版本号不变。

------------------------------------------------------

## 0.0.2 — 折跃感知的 ETA

- 修正玩家侧 Toast 与旅行显示源的剩余时间：改为「实际预期飞行时间」，
  即总路程时间减去折跃路段按正常速度折算的时间
- 新增 `robo/eta/EtaModel`：按与 `FoldController` 一致的判定规则预测折跃，
  预测公式为 `(补足 minTravelTicks 期间飞行距离 + 折跃后落点距离) / beeSpeed + 1`
- 新增 `VirtualRoboEtaMixin`：覆盖 `updateEta()` 的最终值，影响所有读取 ETA 的地方
- 新增 `PlayerTargetEtaMixin`：在 `updateEtaToast` 开头覆写 ETA
  （Toast 在 `handleNavigateToTarget` 里先 setETA 后立刻读取，早于 `updateEta()`）
- 折跃未发生时不改动任何数值；折跃后自动退回上游公式，数值单调下降不跳变
- 配置未加载时（专用客户端）直接退回上游公式，不访问 config
- 产物：`build/libs/create_hivemind-1.21.1-0.0.2.jar`

示例（`beeSpeed=6`、`minDistance=512`、`minTravelTicks=60`、5000 格投递）：
折跃前 834 秒 → 起飞后 9 秒 → 折跃后 6 秒。

------------------------------------------------------

## 0.0.1 — M1 同维度远距离折跃

- 新增 `[fold]` 服务端配置（`config/create_hivemind-server.toml`，9 个键）
- 新增折跃判定与执行：距离阈值 + 最小飞行时间 + 单程次数上限 + 冷却
- 新增落点选择：目标上方定高 + 水平随机散布 + 向下扫描空气位，未加载区块不强制加载
- 新增粒子与音效反馈（可分别关闭）
- 通过 `RoboBeeBehaviorMixin` 注入 CMP 的 `handleNavigateToTarget` 末尾，不新增注册内容
- 折跃状态用 `WeakHashMap` 外部持有，不污染 `VirtualRobo` 的 NBT
- 构建：ModDevGradle 2.0.148，`enable { disableRecompilation = true }`（本机 NeoForm 重编译故障的 workaround）
- 产物：`build/libs/create_hivemind-1.21.1-0.0.1.jar`

------------------------------------------------------

## 未发布

- M2 跨维度投递（仅玩家目标）
- M3 健壮性（悬停超时回收、调试命令、Sable 子世界坐标）

## 0.0.1 — M0 脚手架

- 建立仓库骨架：ModDevGradle、Java 21 toolchain、Gradle 8.14.2 wrapper
- 接入 Create 6.0.10-280 与 Create: Mobile Packages 0.7.8 二进制依赖
- 空的 `@Mod` 类与空 mixin 配置，用于验证加载链路
- 落盘调研文档：`docs/ROADMAP.md`、`docs/SESSION-REFERENCE.md`
