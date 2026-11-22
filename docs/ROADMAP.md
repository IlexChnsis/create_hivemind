# Create: Hive Mind — 需求与开发路线

调研基准：`timplay33/Create-Mobile-Packages`，分支 `mc1.21.1/main`，tree `53ec8db`，v0.7.8（2026-10-05）。
本文档由 2026-11-22 的调研会话产出（会话 ID 见 [SESSION-REFERENCE.md](SESSION-REFERENCE.md)），用于本仓库 bootstrap。

## 0. 元信息

| 字段 | 值 |
|---|---|
| modid | `create_hivemind` |
| 显示名 | Create: Hive Mind（发布名建议 `Create: Hive Mind (CMP Addon)`） |
| 包名 | `de.hivemind.create_hivemind`（占位，见 README 改名说明） |
| 许可 | MIT |
| MC / 加载器 | 1.21.1 / NeoForge 21.1.219+ |
| 关键依赖 | Create 6.0.10+、Create: Mobile Packages 0.7.8 |
| 构建 | NeoForge ModDevGradle 2.0.78，Java 21 toolchain，Gradle 8.14.2 |
| 形态 | CMP 的附属（addon），不提 PR |

注意：上游 `build.gradle` 用 Java 21 toolchain，而 `create_mobile_packages.mixins.json` 的 `compatibilityLevel` 才是 `JAVA_17`。本仓库照抄：Java 21 toolchain + mixin 配置写 `JAVA_17`。

## 1. 两个需求

### R1（本期交付）：运输蜂远距离折跃 + 跨维度投递

期望：

1. 远距离时飞一段后直接传送到目的地附近，再飞完最后一段并正常降落交付；
2. 跨维度投递（玩家在下界/末地也能收到）；
3. 优先配置项，其次附属实现。

CMP 现状（已核实）：

- 无传送/维度配置：`CMPServer` 仅 `beeSpeed`、`beeRotationSpeed`、`portToPort`、`beeMaxDistance`、`displayNametag`、`allowRoboBeeSpawnPackageTransport` 六项。
- 导航是直线插值：`RoboBeeBehaviorController.handleNavigateToTarget()` → `moveTo(robo, target, speed)`；状态机 `IDLE → TAKEOFF → NAVIGATE_TO_TARGET → ALIGN_FOR_DELIVERY → LAND → DELIVER_PACKAGE → SHUTDOWN`。
- 跨维度被显式禁止：`CommonEvents.onEntityTravelToDimension()` 对 `RoboEntity` 一律 `setCanceled(true)`（v0.6.0 起，#250）。
- 目标解析严格同层：`PlayerTarget.fromAddress` 用 `level.getPlayers()`；`CMPHelper.getClosestBeePort` 只查当前 `ServerLevel` 的 `DronePortTracker`。
- 逻辑蜂按维度分治：`RoboManager extends SavedData`（每 `ServerLevel` 一个）；`VirtualRobo` 持有 `private ServerLevel serverLevel`，NBT 不存维度。
- 端口索引不跨维度：`DronePortTracker.load()` 返回空，靠 `onLoad()` 现场注册；`LogisticsNetwork` 不存端口坐标。
- 痛点量化：`beeSpeed` 默认 6 格/秒 → 5000 格约 833 秒（约 14 分钟）。
- 隐含缺陷：`isWithinRange` 只比坐标不比维度；`@玩家` 地址在玩家异维度时 `target=null` 并永久悬停（静态推断，需实测）。
- 上游动态：issue #347 与需求一致（open）；PR #334「Portal Port」已实现跨维度迁移骨架但未合并。

可复用迁移骨架（PR #334 `executePortalTransfer`）：

```java
RoboManager.get(serverLevel).remove(id);
despawnEntity();
this.serverLevel = destinationLevel;      // 需 @Accessor
this.currentPos = <目标维度落点>;
RoboManager.get(destinationLevel).add(this);
// 两边 setDirty()
```

### R2（本期仅摘要，见附录 A）：Portable Stock Ticker 多网络绑定

期望：一个 ticker 绑定多个网络，GUI 切换，从多个独立仓库取物。

CMP 现状（已核实）：

- 网络唯一来源 `LogisticallyLinkedItem.networkFromStack(stack)` 读 `CMP_FREQ`（单个 Freq UUID）。
- `PortableStockTickerMenu(id, inv)` 不接收网络参数，靠 `PortableStockTicker.find(inv)` 再找 stack。
- `RequestStockUpdate` 是无字段 unit 包；`SendPackage(order, address)` 不带网络；服务端一律从 stack 读 Freq。
- `ClientScreenStorage.stacks` 是静态单列表，100 tick 刷新，无网络维度。
- `ADDRESS_TAG` / `CATEGORIES` 不按网络分桶；`HIDDEN_CATEGORIES` 按玩家不按网络 → 切网络会串味。
- 已有基础设施：`ClientNetworkDataStorage` + `RequestPlayerNetworksPacket` 能拿到玩家所属网络的 name/owner/locked/players。
- 先例：`yision1/CreatePhantom` 的 Tunable Portable Stock Ticker（6 张频道卡 + 选频道 + 切频道关屏重开）。

结论：可行（5/5），但工作量约 R1 的 2 倍、兼容面 3 倍，主要成本在 60 KB 的 `PortableStockTickerScreen` 与 JEI/EMI/CFL 兼容。排在 R1 之后。

## 2. 仓库策略

推荐：自建新仓库；不 fork；不 clone 当工作仓。

- 附属形态，正确依赖方式是二进制依赖（Modrinth maven / CurseMaven）。
- MIT 允许只依赖不复制；不要把它源码抄进自己仓库。
- 需要读源码时 clone 到 `reference/`（git ignored，不参与构建）。
- `build.gradle` / gradle wrapper / `gradle.properties` 骨架可参考上游，README 已署名来源。

## 3. 开发路线（R1 优先）

原则：先同维、再跨维；先玩家目标、再端口目标；每步可独立运行、独立回滚。

### M0 脚手架（0.5 天）— 本次已完成

- [x] 自建仓库，落构建骨架，改 modid / 包名 / 作者
- [x] git init + 首次提交；LICENSE 放 MIT
- [x] 引入 CMP + Create 依赖
- [x] 空 mixin 配置
- 验收：`./gradlew build` 通过；`/mods` 里能看到 create_hivemind；`runClient` 无 mixin 报错

> M0 的验收记录见 `docs/SESSION-REFERENCE.md` 的「M0 验收」一节。

## M1 同维度远距离折跃（已实现，三个小版本迭代）

> 实现状态：代码已完成并出包（`BUILD SUCCESSFUL`），**尚未做游戏内运行时验证**。
> 演进：0.0.1 初版（时间驱动）→ 0.0.2 折跃感知 ETA → **0.0.3 锚点式（当前生效逻辑）**。
> 下面 M1a 保留作历史记录，现行语义以 M1c 为准。
>
> 代码位置：
> - `robo/fold/FoldController.java` — 判定与执行
> - `robo/fold/FoldLanding.java` — 连线落点（0.0.3 起为纯几何）
> - `robo/fold/FoldState.java` / `FoldStateStore.java` — 单蜂折跃状态（含 anchor）
> - `robo/eta/EtaModel.java` — 折跃感知的剩余时间
> - `mixin/RoboBeeBehaviorMixin.java` — 注入 `handleNavigateToTarget` 的 TAIL
> - `mixin/VirtualRoboEtaMixin.java` — 注入 `updateEta` 的 TAIL
> - `mixin/PlayerTargetEtaMixin.java` — 注入 `updateEtaToast` 的 HEAD
> - `config/CHMServer.java` / `index/CHMConfigs.java` — 配置

### M1c 锚点式折跃（0.0.3，当前逻辑）★

需求原文（用户）：

> 从出发后，记录当前出发点并始终朝向目标追踪；检测到「当前位置距出发点 > 最小跃迁距离」
> 且「距目标 > 最大到达距离」则跃迁；沿连线跳到距目标为最大到达距离的点；完成后出发点更新为该落点。

实现：

- `FoldState` 持有 `anchor`；`beginTrip()` 在起飞/换目标/超时后把当前位置记为 anchor
- `FoldController.tryFold()` 按 `checkIntervalTicks` 节流（默认 5 tick）后才检查条件
- 判定：`here.distanceTo(anchor) > minFoldDistance` 且 `here.distanceTo(target) > maxArrivalDistance`
- 落点：`FoldLanding.pointTowardTarget(here, target, maxArrivalDistance)`，即 `target + (here-target).normalize() * arrival`
- 折跃后 `state.markFold(now, landing, target)` → **anchor = 落点**，下一轮以新基准继续
- 收敛性：折跃后 `车 → 目标 == maxArrivalDistance`，第二条条件不再成立 → 循环自然终止，无需次数上限
- 自激保护：`maxArrivalDistance >= minFoldDistance` 时 `CHMServer.isUsable()` 返回 false，直接禁用折跃
- 落点不做地形检测：逻辑蜂无碰撞、表现实体每 tick 被 `syncFromVirtual` 强同步，卡方块只是一瞬视觉瑕疵

配置（`config/create_hivemind-server.toml`，7 项）：

```toml
[fold]
  enableForPlayers = true    # 发往玩家时是否折跃（0.0.4）
  enableForPorts = true      # 发往另一个港口时是否折跃（0.0.4）
  minFoldDistance = 100      # 与出发点的直线距离超过这个值才折跃
  maxArrivalDistance = 64    # 离目标小于这个距离就不折跃；折跃落点距目标正好这么多
  checkIntervalTicks = 5     # 探测间隔，20 tick = 1 秒
  particles = true
  sound = true
```

> 「与出发点的距离」指**当前点到出发点的直线距离**（`here.distanceTo(anchor)`），
> 不是沿飞行轨迹的路径积分。运输蜂基本沿直线朝目标飞，所以这个值从 0 单调增长。

### M1d 按目标类型分开关（0.0.4）

需求：区分发往玩家与发往另一个港口的无人机，分别控制是否折跃。

实现：`robo/fold/FoldGate`

- `kindOf(target)` 用 `instanceof` 判定 `PlayerTarget` / `BeePortBlockEntityTarget`
- `allows(target)` 按类型取 `ENABLE_FOR_PLAYERS` / `ENABLE_FOR_PORTS`，未知类型一律 false
- `FoldController` 与 `EtaModel` 共用此判定，避免「显示按会折跃算、实际不折跃」
- 原 `enable` 总开关被两个分类型开关取代（两者都 false 等价于关闭）

**坑（已规避）**：上游 `BeePortBlockEntityTarget.asBeePortBlockEntity()` 在目标区块未加载时返回
`null`。若用它判类型，远距离港口目标会被误判为「未知类型」而永不折跃——正好废掉主场景。
因此类型判定只看对象类型，不看区块加载状态。

删除的旧配置及原因：

| 删除项 | 原因 |
|---|---|
| `enable` | 拆成 `enableForPlayers` / `enableForPorts`（0.0.4） |
| `minDistance` | 改为相对出发点的 `minFoldDistance` |
| `minTravelTicks` | 改由距离驱动，不按时间 |
| `arrivalHeight` / `arrivalSpread` | 落点改为连线上的几何点，不再抬升/散布 |
| `maxFoldsPerTrip` | 条件自收敛，不需要上限 |
| `foldCooldownTicks` | 由 `checkIntervalTicks` 承担节流 |

代价与调参（模拟数据，`beeSpeed = 6`，`maxArrivalDistance = 64`）：

锚点模型的特性是**总耗时与距离基本无关**——远期目标会被一次折跃拉近到 64 格，
所以 5000 格和 200 格花的时间一样。

| 起点到目标 | 折跃次数 | 总耗时 | 不折跃 |
|---|---|---|---|
| 5000 | 1 | ≈ 27.4 s | 833 s |
| 1000 | 1 | ≈ 27.4 s | 167 s |
| 500 | 1 | ≈ 27.4 s | 83 s |
| 200 | 1 | ≈ 27.4 s | 33 s |
| 165 | 1 | ≈ 27.4 s | 28 s |
| 150 | 0 | 25.1 s | 25 s |
| 100 | 0 | 16.7 s | 17 s |

折跃需要的初始距离约为 `minFoldDistance + maxArrivalDistance` = 164 格，低于此值不会折跃。
`minFoldDistance` 越小越快：

| `minFoldDistance` | 折跃触发距离 | 远期投递总耗时 |
|---|---|---|
| 64 | ≈ 128 格 | ≈ 21 s |
| **100（默认）** | **≈ 164 格** | **≈ 27 s** |
| 256 | ≈ 320 格 | ≈ 53 s |
| 512 | ≈ 576 格 | ≈ 96 s |

### M1b 折跃感知的 ETA（0.0.2，0.0.3 同步重写）

问题：上游 `CMPHelper.calcETA` 只算 `剩余直线距离 / beeSpeed`。折跃前严重高估（5000 格 ≈ 834 秒），
折跃后突然归零，玩家看到的「剩余时间」与实际体验不符。

口径（用户指定）：

```
剩余时间 = 总路程时间 − 折跃路段按正常速度折算的时间
        = 这段路实际预期要飞多久
```

0.0.3 的实现（两变量式，已按锚点逻辑重写）：

```
还要先飞     = max(0, minFoldDistance − dA)        # dA = 到出发点距离
折跃点离目标 = dT − 还要先飞                       # dT = 到目标距离
若 折跃点离目标 > maxArrivalDistance → 剩余 = 还要先飞 + maxArrivalDistance
否则                                → 剩余 = dT
```

连续性：折跃后 `dA` 归零、`dT` 变为 `maxArrivalDistance`，`dT − 还要先飞` 为负 → 走「不折跃」分支 → 剩余仍是 `dT`。
因此倒计时单调下降、不跳变（已用模拟验证：折跃前 11 s，折跃后 11 s）。

其他要点：

- 两个注入点：`updateEta()` 的 TAIL 覆盖最终值；`updateEtaToast` 的 HEAD 保证 Toast 读到新值
  （Toast 在 `handleNavigateToTarget` 里「先 setETA 再立刻读」，早于 `updateEta()`）
- `CHMServer.SPEC.isLoaded()` 守卫，专用客户端不访问 config
- 状态尚未建立时（首 tick 的 Toast），把当前位置当 anchor，`还要先飞 = minFoldDistance`，语义仍然正确

验收（待游戏内确认）：5000 格投递时 Toast 从 ≈ 28 秒平滑降到 ≈ 11 秒，折跃瞬间不跳变；
短距离（< 164 格，即 `minFoldDistance + maxArrivalDistance`）显示值与上游一致。

### M1a 初版逻辑（0.0.1，已被 0.0.3 取代，保留作记录）

行为：起飞 → 飞 `minTravelTicks` → 距离 > `minDistance` 且未超次数 → 瞬移到目标上方定高 + 随机散布 →
继续飞 → 正常降落。配置为 `minDistance` / `minTravelTicks` / `arrivalHeight` / `arrivalSpread` /
`maxFoldsPerTrip` / `foldCooldownTicks` / `particles`（0.0.3 已全部删除或替换）。

注入点（侵入性从低到高）：

1. `RoboBeeBehaviorController#handleNavigateToTarget` 的 HEAD/TAIL（推荐，最精确）
2. `VirtualRobo#tick(ServerLevel)` 的 TAIL（拿不到状态机 state，用「有 target 且距离超阈值」代理）
3. 备选：mixin `VirtualRobo#getTargetPos` 旁路判断

核心逻辑：

```java
boolean shouldFold(VirtualRobo robo) {
    if (!cfg.fold.enable.get()) return false;
    Vec3 t = robo.getTargetPosition();
    if (t == null) return false;
    double d = robo.getCurrentPos().distanceTo(t);
    return d >= cfg.fold.minDistance.get()
        && traveledTicks() >= cfg.fold.minTravelTicks.get()
        && folds() < cfg.fold.maxFoldsPerTrip.get()
        && cooldownElapsed();
}

Vec3 pickArrival(ServerLevel level, Vec3 target, int height, int spread) {
    double a = level.random.nextDouble() * Math.PI * 2;
    Vec3 p = target.add(0, height, 0)
                   .add(Math.cos(a) * spread, 0, Math.sin(a) * spread);
    BlockPos base = BlockPos.containing(p);
    for (int dy = 0; dy < 64; dy++) {
        BlockPos c = base.below(dy);
        if (level.getBlockState(c).isAir() && level.getBlockState(c.above()).isAir())
            return Vec3.atBottomCenterOf(c.above(2));
    }
    return p;
}
```

注意：

- 真身每 tick 被 `syncFromVirtual()` 强制同步位置，`setPos` 即表现为瞬移，无需额外发包
- 逻辑蜂不依赖区块加载（未加载只 `despawnEntity()`），落点区块未加载不会中断
- `isAtTarget` 判据是 `dist < speed`，落点别太贴近目标点
- Sable/Aeronautics 子世界坐标先 `projectOutOfSubLevel` 再算
- 折跃后 `setTargetVelocity(Vec3.ZERO)`；交付成功 / `clearRequest()` / 目标变更时重置计数

验收：5k 格同维投递从 ~14 分钟降到 ~1–2 分钟；不重复折跃；正常降落；未加载区块不崩。

### M2 跨维度（玩家目标）（2–3 天）

行为：蜂在 A 维飞向 B 维玩家 → 飞 N tick 后迁移到 B 维并落在玩家附近 → 继续飞完并交付。

必须解决：

1. `VirtualRobo.serverLevel` 是 `private` 且无 setter → `@Mixin @Accessor("serverLevel")`（非 final，可写）
2. 目标解析只查本层 → 自己遍历 `server.getAllLevels()` → `getPlayers(...)`，或 mixin `PlayerTarget.fromAddress`
3. 端口跨维度不可枚举 → 本期只支持玩家目标；端口留后续（或等上游 Portal Port）
4. 返回模式 `homePortPos` 在源维度 → 持久化 `homeDimension`（mixin `serializeNBT`/`deserializeNBT` 加 `ResourceKey`）
5. 拾取请求（Trash Store）误判 → `hasActiveTrashRequest` 在源层找不到蜂会误判 `CANCELLED`；迁移时一并带 `RoboRequest`，或让拾取任务不走跨维
6. 存档一致性 → 迁移前后两个 manager 都 `setDirty()`；`serializeNBT` 补维度字段

流程：

```
A 维飞向 B 维玩家
 ├ 已飞 N tick 且玩家不在本层
 ├ 解析 B 维 + 玩家位置 P
 ├ 取 P 附近安全落点 Q（同 M1 的 pickArrival）
 ├ 源 manager.remove(id) + despawnEntity()
 ├ @Accessor 设 serverLevel = B；setPos(Q)
 ├ 目标 manager.add(this)；双方 setDirty()
 └ 状态机继续 NAVIGATE_TO_TARGET → 交付
```

平衡性：绕开了上游 Portal Port 的门方块，建议加成本限制（消耗物品 / 仅「末影升级」可用 / 每次计费），否则会被质疑过强（上游 #94 明确写了「门必须难做」）。

验收：主世界→下界/末地投递成功；玩家中途换维度能重解析；重进存档蜂仍在正确维度；返回模式能回家；拾取任务不误杀。

### M3 健壮性（2 天）

- [ ] `@玩家` 地址在玩家离线/异维度时的悬停超时回收（顺手修 CMP 隐含缺陷）
- [ ] 与 `portToPort=false`、`beeMaxDistance=256` 组合测试
- [ ] 与 Sable / Create Aeronautics 子世界同装测试
- [ ] 多人：同网络两玩家、地址歧义、并发折跃
- [ ] 调试命令 `/hivemind fold <id>`、`/hivemind debug`
- [ ] 日志分级（默认 INFO，调试开 DEBUG）

### M4（可选）R2 多网络 ticker

见附录 A，排在 R1 稳定之后。

## 4. 测试清单（R1）

1. 同维 5k 格：正常折跃、不重复、正常降落
2. 同维但落点在未加载区块：不崩，进区后正常显示
3. 玩家在另一维度：送达、返回模式回家
4. 送达途中玩家换维度：目标重解析
5. 拾取任务跨维度
6. 保存退出重进：蜂仍在正确维度
7. 多人：两名玩家同网络、地址歧义
8. 与 `portToPort=false` / `beeMaxDistance=256` 组合
9. 与 Sable/Aeronautics 同装
10. `@玩家` 地址在玩家离线且异维度的行为

## 5. 风险登记

| 风险 | 等级 | 缓解 |
|---|---|---|
| CMP 小版本更新导致 mixin 目标移位（尤其 60 KB 的 Screen） | 中 | R1 只挂稳定方法，避免挂 `renderBg` 内部表达式 |
| 跨维度后 NBT 丢维度 → 蜂回错层 | 高 | 必做 `homeDimension` 持久化 + 重进测试 |
| 拾取请求跨层误判 `CANCELLED` | 中 | 迁移时带 request，或限制拾取不走跨维 |
| 与上游 Portal Port（PR #334）语义冲突 | 低 | 只做自动折跃，不新增方块；上游合并后给配置项关闭以共存 |
| 折跃被质疑过强 | 中 | 配置化阈值 + 可选成本 |
| 附属与 CMP 版本强耦合 | 中 | `versionRange` 收紧到 `[0.7.0,0.8.0)`，README 写兼容矩阵 |

## 附录 A — R2 多网络 ticker 摘要（暂不实施）

推荐路线 M1（改动最小）：

- 保留 `CMP_FREQ` = 当前活动网络
- 新增组件 `BOUND_NETWORKS : List<UUID>`
- 切换：`SetActiveNetworkPacket` → 服务端校验 bound ∈ 已绑 且 `Create.LOGISTICS.mayInteract(net, player)` → 覆盖 `CMP_FREQ` → 客户端清空 `ClientScreenStorage.stacks` + `RequestStockUpdate`
- UI：mixin `PortableStockTickerScreen#init`，在 `trashMenuButton` 旁加按钮，列表读 `ClientNetworkDataStorage.getNetworks()`
- 必做：`ADDRESS_TAG` / `CATEGORIES` / `HIDDEN_CATEGORIES` 按网络分桶，否则切换后串味
- 工作量约 450–700 行；不改菜单类型 → JEI 兼容零成本

不推荐：新建物品 + 频道卡 + 自有屏幕（1200–2000 行，需复刻 60 KB 屏幕或大量 `@WrapOperation`）。中间态（120–200 行）：潜行右键轮转 `CMP_FREQ`，聊天栏提示网络名，用于先验证链路。

## 附录 B — 参考链接

- CMP：<https://github.com/timplay33/Create-Mobile-Packages>
- 跨维度需求帖：<https://github.com/timplay33/Create-Mobile-Packages/issues/347>
- Portal Port 设计：<https://github.com/timplay33/Create-Mobile-Packages/issues/94>
- Portal Port PR（未合并，迁移骨架参考）：<https://github.com/timplay33/Create-Mobile-Packages/pull/334>
- 多网络先例：<https://github.com/yision1/CreatePhantom>
