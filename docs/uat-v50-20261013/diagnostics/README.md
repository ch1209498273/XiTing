# v5.0 诊断模块验证（非最终集成）

- 定向验证：WakeLockMetricsTest 4条、SessionDiagnosticsTest 2条全部通过；同时通过Kotlin编译与八语言资源编译。
- 红阶段：缺失WakeLockMetrics编译失败；同时存在复制当时SessionConsoleUi的Context类型错误。该阶段不是纯净的行为断言红测，不能宣称完整TDD闭环。
- 独立ASCII镜像全量测试193条：191通过、2失败，均为当时的SessionOutcomeStoreTest（pause feedback refines one id without changing end or regressing confirmation；far future rows cannot occupy retained history）。主Agent后续集成验证为最终依据。
- 唤醒锁统计仅当前进程的成功acquire/release钩子累计，重复调用幂等，线程同步。heldMs为当前持有区间，totalMs为本进程所有已完成和当前区间合计。不是电池电量/耗电/系统级统计。
- 导出文本仅白名单16个key，预览和分享使用同一次捕获文本。分享仅预览页点击后发ACTION_SEND系统chooser，无自动分享、网络或新依赖。
- 未验证：Android上实际点击导出、取消、刷新、分享chooser，以及大字体/窄屏/八语言视觉效果；真机唤醒锁/耗电与厂商后台行为。
