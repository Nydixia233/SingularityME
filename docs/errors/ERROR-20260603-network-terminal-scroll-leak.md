# Network Terminal 内容区滚动条泄漏

## 错误现象

连接页面在内容不足以滚动时仍出现右侧滚动条；拖动该滚动条后，切换到主页等本应无滚动条的页面，内容也会沿用之前的滚动偏移。

## 触发场景

Network Terminal 的连接页会在主内容区 `contentViewport` 内再创建一个内部 `ListWidget`。当连接页内部列表高度直接使用外层 `layout.contentH` 时，外层内容区的上下 padding 会让实际子内容高度超过可视高度。

## 根本原因

`NetworkTerminalUI` 中 `contentViewport` 是所有页面共用的 `ListWidget`，并带有 `Palette.CONTENT_VIEWPORT_PAD` 内边距。连接页内部列表原本设置为 `layout.contentH`，但它被放入带上下 padding 的外层滚动视口后，外层计算出的内容高度至少比可视区多 `CONTENT_VIEWPORT_PAD * 2`，因此出现假滚动条。

同一个外层 `ListWidget` 在页面切换和网络切换时被复用，若旧页面产生过滚动偏移，`ScrollData` 的 scroll 值不会因为 `contentArea.removeAll()` 自动归零，导致滚动状态泄漏到下一页。

相关源码位置：

- `src/main/java/com/github/singularityme/client/ui/NetworkTerminalUI.java`：`contentViewport` 创建、页面切换渲染、连接页列表高度。
- `src/main/java/com/github/singularityme/client/ui/NetworkUiKit.java`：内容视口内高与滚动重置 helper。

## 修复方案

- 新增 `terminalContentInnerHeight(int)`，高度计算统一扣除内容视口上下 padding。
- 新增 `connectionListHeight(int)`，连接页内部列表使用外层内容视口的实际内高。
- 新增 `resetListScroll(ListWidget<?, ?>)`，通过 `ScrollData.scrollTo(scrollArea, 0)` 清除复用列表的旧滚动偏移。
- 在切换页面、切换网络、选中网络被数据刷新替换时标记下一次渲染需要重置内容区滚动；普通状态包刷新不重置，避免用户在同一页面浏览列表时被打断。

## 预防措施

- 任何放入带 padding 的 `ListWidget` / 视口中的内部固定高度列表，都必须按父容器内高计算，不得直接使用父容器外框高度。
- 复用滚动容器渲染不同语义页面时，必须显式重置滚动状态。
- 为内容视口内高和滚动归零行为保留单元测试，避免后续 UI 调整重新引入假滚动条和跨页偏移泄漏。
