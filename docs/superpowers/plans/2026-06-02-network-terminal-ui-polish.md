# Network Terminal UI Polish Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Improve Singularity ME Network Terminal visual density, alignment, and interaction clarity while keeping the HTML reference and ModularUI2 implementation visually aligned.

**Architecture:** Keep the existing client-only ModularUI2 screen architecture. Add small reusable helpers to `NetworkUiKit`, then use them from `NetworkTerminalUI` without changing packet contracts or top-level panel structure.

**Tech Stack:** Minecraft 1.7.10 / GTNH, ModularUI2 2.3.63, Java 17 syntax in current sources, JUnit 4 tests, HTML reference files.

---

## Scope

- Include: home/info compact rows, fixed label alignment, quieter list rows, member row inline player id, form row sizing, security segmented controls, clearer selected color swatches, delete confirmation copy, HTML reference sync.
- Exclude: the green external HUD energy bar, because the user confirmed it belongs to another mod.
- Exclude for this pass: merging the 8 top-level terminal tabs or changing network packet/data contracts.

## Files

- Modify: `src/main/java/com/github/singularityme/client/ui/NetworkUiKit.java`
  - Add stable pure helpers and small MUI2 component factories.
- Modify: `src/main/java/com/github/singularityme/client/ui/NetworkTerminalUI.java`
  - Use compact home layout, segmented security controls, direct color selection, and clearer member rows.
- Modify: `src/test/java/com/github/singularityme/client/ui/NetworkUiKitTest.java`
  - Add regression tests for selected row color, color formatting, security cycling/removal behavior where pure and stable.
- Modify: `docs/html-reference/network-terminal.html`
  - Sync the visual reference with game implementation.
- Modify: `docs/html-reference/shared-palette.css`
  - Sync palette comments/classes where needed.
- Modify: `src/main/resources/assets/singularityme/lang/zh_CN.lang`
  - Add concise strings for default badge, empty member state, security choice labels, and delete warning.
- Modify: `src/main/resources/assets/singularityme/lang/en_US.lang`
  - Add English equivalents.

## Tasks

### Task 1: Tests For Stable UI Helpers

- [ ] Add tests in `NetworkUiKitTest` for:
  - selected row background is a quiet accent derived from the network color, not the raw saturated color.
  - hex color labels are uppercase and masked to RGB.
  - default badge text uses a localized full label helper rather than hardcoded `D`.
- [ ] Run `./gradlew.bat test --tests com.github.singularityme.client.ui.NetworkUiKitTest -x spotlessJavaCheck`.
- [ ] Confirm the new tests fail because helper methods are missing or still hardcoded.

### Task 2: NetworkUiKit Helpers

- [ ] Implement pure helpers:
  - `selectedRowColor(int color)` returns `darken(color, 0.32f)`.
  - `rgbHex(int color)` returns six uppercase RGB hex digits.
  - `defaultBadgeText()` returns localized default label.
- [ ] Implement MUI2 factories:
  - `statusDot(int color)` as a compact color indicator.
  - `securitySegment(SecurityLevel selected, SecurityLevel option, Runnable action)`.
  - `securitySegmentRow(SecurityLevel selected, Consumer<SecurityLevel> onSelect)`.
  - `colorReadonly(int color)`.
- [ ] Run the targeted test and confirm it passes.

### Task 3: ModularUI2 Terminal Layout

- [ ] Update `updateNetworkBar()` to use a status dot and hide meaningless trailing dash for unassigned/null values.
- [ ] Update `renderHome()` to group properties into compact rows and avoid excessive vertical whitespace.
- [ ] Update `buildSelectionRow()` to use quiet default rows and selected accent color.
- [ ] Update `renderMembers()` and `buildMemberRow()` so player id stays inside the row, right aligned with `idPill`.
- [ ] Add empty member state text when only owner exists.
- [ ] Update settings/create panels:
  - use segmented security controls instead of cycle-security buttons.
  - remove cycle-color buttons because swatches directly select colors.
  - keep Apply/Create as the single submission action.
  - keep delete behind existing confirmation and make warning text explicit.

### Task 4: HTML Reference Sync

- [ ] Update `network-terminal.html` to mirror the MUI2 structure:
  - no emoji icons in nav.
  - status dot in network bar.
  - compact home grid.
  - quiet selected rows.
  - segmented security controls.
  - obvious selected color swatch.
  - member IDs inside rows.
- [ ] Update `shared-palette.css` only for classes/variables that now map to Java helpers.

### Task 5: Localization

- [ ] Add zh_CN/en_US keys:
  - `gui.singularityme.network_terminal.badge.default`
  - `gui.singularityme.network_terminal.members.empty`
  - `gui.singularityme.network_terminal.security.public`
  - `gui.singularityme.network_terminal.security.encrypted`
  - `gui.singularityme.network_terminal.security.private`
  - `gui.singularityme.network_terminal.confirm.delete_warning`
- [ ] Replace hardcoded UI labels in Java with these keys where applicable.

### Task 6: Verification And Commit

- [ ] Run `./gradlew.bat test --tests com.github.singularityme.client.ui.NetworkUiKitTest -x spotlessJavaCheck`.
- [ ] Run `$env:GRADLE_USER_HOME="$env:USERPROFILE\.gradle"; ./gradlew.bat compileJava compileMixinJava -x spotlessJavaCheck`.
- [ ] Inspect `git diff` for unintended changes and confirm no `.superpowers/` files are tracked.
- [ ] Commit with title `[Style]: 优化网络终端界面密度与交互状态`.

