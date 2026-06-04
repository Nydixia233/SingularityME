# Network Terminal 权限页添加玩家输入框不可见

## 错误现象

Network Terminal 的“权限”页能看到网络拥有者和“暂无授权玩家”，但看不到添加玩家名称的输入框和“添加”按钮，玩家无法从界面上把其他玩家加入自己的网络。

## 触发场景

打开 Network Terminal，选择一个自己可管理权限的私有网络，切换到“权限”页。成员列表在内容区内占用过高，后续的添加玩家输入行被推到 `contentViewport` 可见区域下方。

## 根本原因

`NetworkTerminalUI.renderMembers()` 会先向 `contentArea` 追加成员 `ListWidget`，再追加添加玩家输入行。修复前：

- `src/main/java/com/github/singularityme/client/ui/NetworkTerminalUI.java:818` 使用 `NetworkUiKit.memberListHeight(layout.contentH)` 计算成员列表高度。
- `src/main/java/com/github/singularityme/client/ui/NetworkUiKit.java:419` 的旧实现只固定扣除 48px，没有完整扣除外层视口 padding、`contentArea` child gap、添加行上下 margin。
- 添加玩家行没有显式 `height(Palette.ROW_H)`，还使用过 `.padding(0, 12)`。MUI2 两参数 padding 是 `horizontal, vertical`，因此这会变成上下各 12px 的垂直 padding，进一步放大内容高度。

最终内容高度超过 `contentViewport` 内高，输入行仍存在于 widget 树中，但被滚动视口裁到下方，玩家第一屏看不到。

## 修复方案

- 在 `NetworkUiKit.Palette` 中集中定义 `TERMINAL_CONTENT_CHILD_GAP` 和 `MEMBER_ADD_ROW_MARGIN_V`，让内容区间距和成员页高度预算使用同一组常量。
- `NetworkUiKit.memberListHeight(int)` 改为基于 `terminalContentInnerHeight(contentHeight)` 计算，并完整扣除添加行高度、上下 margin 和内容区 child gap。
- `NetworkTerminalUI.renderMembers()` 中添加玩家输入行改为固定 `height(Palette.ROW_H)`，水平 padding 写成 `.padding(Palette.LIST_ROW_PADDING_H, 0)`，垂直留白放到 `.margin(0, Palette.MEMBER_ADD_ROW_MARGIN_V)`。
- 增加测试覆盖：
  - `NetworkUiKitTest.memberListLeavesRoomForAddMemberRow`
  - `NetworkTerminalUITest.renderMembersKeepsAddMemberRowVisibleBelowMemberList`

## 预防措施

- 在带 padding 的 `ListWidget` / viewport 中追加固定操作行时，内部列表高度必须按“视口内高 - 操作行高度 - 操作行 margin - child gap”计算，不得只用经验常数扣减。
- `Flow.column().coverChildrenHeight()` 的直接子项必须有明确高度，避免依赖子树反向撑高造成 MUI2 求解不稳定。
- MUI2 `padding(int, int)` / `margin(int, int)` 统一按 `horizontal, vertical` 理解；固定高度行需要垂直留白时优先用外部 margin，不给行本身加垂直 padding。
- 任何页面将“列表 + 底部输入/按钮行”放在同一个滚动视口时，都应增加预算测试或渲染结构测试，确认操作行不会被列表顶出可见区域。
