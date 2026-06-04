# 错误记录索引

> 本文件作为"指针文档"：每条错误指向 `docs/errors/` 下的具体错误分析文件。
> 目的是帮助后续开发者快速查阅历史问题、快速排错、避免同一错误反复找原因。

---

## 记录格式模板

新增错误记录文件命名：`ERROR-[YYYYMMDD]-[简述].md`（如 `ERROR-20260418-git-commit.md`）。

每条错误记录至少包含以下五要素：

```markdown
# [错误简述]

## 错误现象
（一句话描述用户/开发者看到什么症状）

## 触发场景
（在什么操作下、什么环境/配置下出现）

## 根本原因
（分析为什么会发生，引用源码位置）

## 修复方案
（做了什么修改，引用 commit hash 或代码 diff）

## 预防措施
（如何避免同类问题再次发生——加测试？加校验？改流程？）
```

---

## 记录列表

<!-- 格式：[日期] [简述] → [文件] -->

| 日期 | 简述 | 文件 |
|------|------|------|
| 2026-06-01 | PowerShell Set-Content 损坏 UTF-8 中文注释编码 | `ERROR-20260601-powershell-encoding-corruption.md` |
| 2026-06-01 | MUI2 TextWidget SIZING padding 溢出（非致命） | `ERROR-20260601-mui2-sizing-padding-overflow.md` |
| 2026-06-02 | MUI2 widthRel 子项横向溢出 | `ERROR-20260602-mui2-widthrel-row-overflow.md` |
| 2026-06-02 | Network Terminal 只适配单一 guiScale | `ERROR-20260602-network-terminal-gui-scale.md` |
| 2026-06-02 | Network Terminal 背景 hover 闪烁与主页内宽裁切 | `ERROR-20260602-network-terminal-hover-overflow.md` |
| 2026-06-03 | Network Terminal 切换网络时 loading 中间帧闪烁 | `ERROR-20260603-network-terminal-status-switch-flicker.md` |
| 2026-06-03 | MUI2 padding 参数顺序误判 | `ERROR-20260603-mui2-padding-argument-order.md` |
| 2026-06-03 | Network Terminal 内容区滚动条泄漏 | `ERROR-20260603-network-terminal-scroll-leak.md` |
| 2026-06-03 | MUI2 色块视觉子节点抢占点击目标 | `ERROR-20260603-mui2-color-swatch-click-target.md` |
| 2026-06-03 | Network Terminal 默认按钮切换闪烁 | `ERROR-20260603-network-terminal-rail-default-button-flicker.md` |
| 2026-06-03 | MUI2 文档误导项审计 | `ERROR-20260603-mui2-doc-audit.md` |
| 2026-06-03 | Network Tab 密码行切换触发 MUI2 resize 失败 | `ERROR-20260603-network-tab-password-resize.md` |
| 2026-06-03 | 部署旧 Mod Jar 被运行中的客户端或服务端锁定 | `ERROR-20260603-deploy-locked-mod-jar.md` |
| 2026-06-04 | Network Terminal 权限页添加玩家输入框不可见 | `ERROR-20260604-network-terminal-member-input-hidden.md` |
| 2026-06-04 | Network Terminal 权限行不可编辑与授权后目标玩家未刷新 | `ERROR-20260604-network-terminal-inline-permission-refresh.md` |
| 2026-06-04 | 无权限破坏被拒绝后客户端残留透明假方块 | `ERROR-20260604-denied-break-client-ghost-block.md` |
| 2026-06-04 | 终端 Monitor 包装遗漏默认方法委托导致断线 | `ERROR-20260604-terminal-monitor-default-recursion.md` |
| 2026-06-04 | 存储 Handler 继承入口绕过 Guard | `ERROR-20260604-storage-handler-internal-guard.md` |
| 2026-06-04 | NEI 精确 GUI 类匹配导致奇点终端集成失效 | `ERROR-20260604-nei-exact-gui-class.md` |
| 2026-06-05 | AE2 子 GUI 返回路径缺少奇点 PrimaryGui | `ERROR-20260605-ae2-primary-gui-return.md` |
| 2026-06-05 | AE2 总线调度与矿辞状态细节未对齐 | `ERROR-20260605-ae2-bus-parity.md` |
