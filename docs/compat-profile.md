# Singularity ME Compat Profile

This profile is for optional AE ecosystem smoke testing. It must not make the
production jar depend on optional addons.

## AE2FC

GTNH Maven coordinate:

```text
com.github.GTNewHorizons:AE2FluidCraft-Rework:1.5.85-gtnh:dev
```

Enable it only for local compatibility runs:

```powershell
.\gradlew.bat runClient '-Psingularityme.compat.ae2fc=true' '-Psingularityme.compat.ae2fcVersion=1.5.85-gtnh'
```

The quoted `-P` arguments are required in PowerShell so Gradle receives the
properties instead of interpreting fragments as task names.

The normal build leaves this dependency disabled:

```powershell
.\gradlew.bat build
```

## Wireless Crafting Terminal

Local Gradle cache contains the GTNH coordinate:

```text
com.github.GTNewHorizons:WirelessCraftingTerminal:1.12.13:dev
```

Enable it only for local compatibility runs:

```powershell
.\gradlew.bat runClient '-Psingularityme.compat.wct=true' '-Psingularityme.compat.wctVersion=1.12.13'
```

AE2FC and WCT can be enabled together:

```powershell
.\gradlew.bat runClient '-Psingularityme.compat.ae2fc=true' '-Psingularityme.compat.ae2fcVersion=1.5.85-gtnh' '-Psingularityme.compat.wct=true' '-Psingularityme.compat.wctVersion=1.12.13'
```

## Addons Without A Local Coordinate

The current workstation cache did not contain resolvable Maven metadata for
ThaumicEnergistics, AE2Stuff / AE2 Things, or ExtraCells. Keep those jars out
of the production dependency set. Add them to a local `runClient` mods folder or
extend this file with exact coordinates once the target GTNH pack version is
selected.

## Smoke Checklist

- AE2FC fluid storage is visible through a Singularity Storage Bus.
- AE2FC fluid packet filters convert to fluid stacks for Storage Bus filters.
- Singularity Interface item behavior still delegates to AE2 DualityInterface.
- Singularity Terminal item monitor still opens.
- WCT can bind/open/access a Singularity-backed terminal path, or fails with a
  specific terminal/security contract gap.
- ThaumicEnergistics essentia storage/interface behavior is recorded against
  exact jars when available.
- AE2Stuff / AE2 Things and ExtraCells startup/API findings are tied to exact
  jars when available.

## Current Status

### 已集成（dependencies.gradle 中有 runtimeOnly 声明）

| Addon | Status | Last Tested | Notes |
|-------|--------|-------------|-------|
| AE2FC | 🔴 未测试 | — | fluid storage/filter/interface smoke |
| WCT | 🔴 未测试 | — | terminal bind/open/access via Singularity path |

### 远期目标（无代码集成，仅记录可行性）

| Addon | Status | Notes |
|-------|--------|-------|
| ThaumicEnergistics | ⚫ 未集成 | essentia storage/interface; jars not in local cache |
| AE2Stuff / AE2 Things | ⚫ 未集成 | startup/API; jars not in local cache |
| ExtraCells | ⚫ 未集成 | historical API smoke; jars not in local cache |

**Status legend**: 🔴 未测试 | 🟡 部分通过 | 🟢 全部通过 | ⚫ 未集成
