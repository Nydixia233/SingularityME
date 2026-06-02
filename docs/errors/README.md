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
