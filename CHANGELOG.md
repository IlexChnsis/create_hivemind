# Changelog

格式参考上游：每条版本以一行连字符分隔线包裹。

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
