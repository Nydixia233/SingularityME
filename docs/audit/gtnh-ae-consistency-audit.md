# Singularity ME GTNH AE Consistency Audit

Date: 2026-05-26

Baseline: `com.github.GTNewHorizons:Applied-Energistics-2-Unofficial:rv3-beta-944-GTNH:dev` from `dependencies.gradle`.

Scope: code review only. No source code fixes were made.

## Sources And Baseline

- Local dependency baseline: `dependencies.gradle:2` pins AE2 to `rv3-beta-944-GTNH`.
- AE2 baseline source: downloaded from GTNH Nexus as `Applied-Energistics-2-Unofficial-rv3-beta-944-GTNH-sources.jar`.
- Public references:
  - https://github.com/GTNewHorizons/Applied-Energistics-2-Unofficial/releases
  - https://github.com/GTNewHorizons/GT-New-Horizons-Modpack
  - https://github.com/GTNewHorizons/AE2FluidCraft-Rework
  - https://www.curseforge.com/minecraft/modpacks/gt-new-horizons

Version note: the public CurseForge GTNH page observed during this audit lists a 2.8.0 modlist with AE2 `rv3-beta-690-GTNH`, while this project builds against `rv3-beta-944-GTNH`. The audit uses the current project dependency as requested, and treats GTNH pack/modlist differences as version skew rather than direct defects.

## Verification Results

All required non-interactive checks currently pass:

- `.\gradlew.bat build`: passed. Output still warns that the project is not a tagged Git checkout or is relying on `NO-GIT-TAG-SET`.
- `.\gradlew.bat compileJava compileMixinJava -x spotlessJavaCheck`: passed.
- `.\gradlew.bat spotlessJavaCheck`: passed.

Not run: manual in-client scenarios such as placement, chunk unload, cross-dimension merge, AE2FC fluid interaction, and wireless terminal access. These require an interactive Minecraft client/server run and should be done after the P0/P1 issues below are fixed.

## Findings

### P0 - Export Bus with Crafting Card can crash due to missing `CRAFT_ONLY` config

Local code reads `Settings.CRAFT_ONLY` when the Export Bus has a crafting card:

- `src/main/java/com/github/singularityme/gui/ContainerSingularityExportBus.java:53-54`

But the tile never registers the setting:

- `src/main/java/com/github/singularityme/tile/TileSingularityExportBus.java:88-92`

GTNH AE2 registers `Settings.CRAFT_ONLY` in the export bus base class:

- AE2 baseline `appeng/parts/automation/PartBaseExportBus.java:53`

Impact: `ConfigManager#getSetting(Settings.CRAFT_ONLY)` throws for unregistered settings, so opening/syncing the GUI after installing a crafting card can crash instead of behaving like GTNH AE.

Recommendation: register `Settings.CRAFT_ONLY` with `YesNo.NO`, persist it through the existing config manager, and make export/crafting logic respect craft-only mode exactly like `PartBaseExportBus`.

### P1 - Storage Bus priority is displayed but not applied to the network handler

Local Storage Bus stores and displays `priority`, but the constructed handler never receives it and `setPriority` does not rebuild the cached handler:

- `src/main/java/com/github/singularityme/tile/TileSingularityStorageBus.java:159-166`
- `src/main/java/com/github/singularityme/tile/TileSingularityStorageBus.java:319-330`

GTNH AE2 applies priority to the handler and resets cache on priority changes:

- AE2 baseline `appeng/parts/misc/PartStorageBus.java:625`
- AE2 baseline `appeng/parts/misc/PartStorageBus.java:743-746`

Impact: storage priority appears configurable but does not affect insertion/extraction ordering, which is a core AE behavior and not part of the Singularity feature exemption.

Recommendation: set handler priority when building the storage handler and invalidate/rebuild the handler when priority changes.

### P1 - Drive priority is not propagated to storage cell handlers

Local Drive returns and persists a priority value, but `getCellArray` returns raw cell inventories from the cell handler:

- `src/main/java/com/github/singularityme/tile/TileSingularityDrive.java:112-128`
- `src/main/java/com/github/singularityme/tile/TileSingularityDrive.java:131-145`

GTNH AE2 wraps each cell inventory in a `DriveWatcher`/`MEInventoryHandler` and applies `ih.setPriority(this.priority)`:

- AE2 baseline `appeng/tile/storage/TileDrive.java:325-328`

Impact: Drive GUI priority can be changed, but cells will not participate in AE storage priority ordering like GTNH AE drives do.

Recommendation: mirror AE2 `TileDrive#updateState` behavior: cache wrapped handlers per stack type, set priority on each wrapper, and rebuild on inventory/priority changes.

### P1 - Storage Bus bypasses GTNH external storage and AE2FC compatibility path

Local Storage Bus only wraps adjacent `IInventory`/`ISidedInventory` through `MEMonitorIInventory(new AdaptorIInventory(inv))`:

- `src/main/java/com/github/singularityme/tile/TileSingularityStorageBus.java:319-320`
- `src/main/java/com/github/singularityme/tile/TileSingularityStorageBus.java:395-402`

GTNH AE2 resolves adjacent inventories through the external storage registry and includes AE2FC fluid packet conversion:

- AE2 baseline `appeng/parts/misc/PartStorageBus.java:564-567`
- AE2 baseline `appeng/parts/misc/PartStorageBus.java:598-617`
- AE2 baseline `appeng/parts/misc/PartStorageBus.java:646-649`

Impact: many GTNH AE integrations that expose storage through `IExternalStorageHandler`, AE2FC fluid packet handling, or non-vanilla handlers will not behave like GTNH AE. This is a full AE ecosystem compatibility gap.

Recommendation: use AE2's external storage registry and stack-type-aware handler path rather than hard-wiring `IInventory`.

### P1 - Storage Bus missing GTNH partition/extract/sticky semantics

Local Storage Bus sets only `handler.setPartitionList(...)`; it does not set an extract partition list and does not implement sticky behavior:

- `src/main/java/com/github/singularityme/tile/TileSingularityStorageBus.java:359-366`
- `src/main/java/com/github/singularityme/proxy/CommonProxy.java:114-116`

GTNH AE2 sets both insert and extract partition lists, supports sticky cards, and registers sticky/ore filter upgrades for storage buses:

- AE2 baseline `appeng/parts/misc/PartStorageBus.java:635-670`
- AE2 baseline `appeng/core/Registration.java:758-762`

Impact: filtered extraction, sticky storage behavior, and ore-filtered storage bus behavior diverge from GTNH AE.

Recommendation: add the missing upgrade registrations and port AE2 `PartStorageBus#getInternalHandler` partition handling, including `setExtractPartitionList` and sticky mode.

### P1 - Import/Export Bus missing GTNH speed tiers and ore filter behavior

Local upgrade registration covers only `CAPACITY`, `SPEED`, `FUZZY`, `REDSTONE`, and Export `CRAFTING`:

- `src/main/java/com/github/singularityme/proxy/CommonProxy.java:101-111`

GTNH AE2 also registers `SUPERSPEED`, `SUPERLUMINALSPEED`, and `ORE_FILTER` for import/export buses:

- AE2 baseline `appeng/core/Registration.java:642-646`
- AE2 baseline `appeng/core/Registration.java:651-656`

Local item throughput only implements basic SPEED cards:

- `src/main/java/com/github/singularityme/tile/TileSingularityImportBus.java:303-316`
- `src/main/java/com/github/singularityme/tile/TileSingularityExportBus.java:395-408`

GTNH AE2 includes additional throughput from super and superluminal cards:

- AE2 baseline `appeng/parts/automation/PartImportBus.java:84-106`
- AE2 baseline `appeng/parts/automation/PartExportBus.java:45-67`

Impact: high-tier GTNH automation throughput and ore-dictionary filtering are not one-to-one.

Recommendation: register and implement `SUPERSPEED`, `SUPERLUMINALSPEED`, and `ORE_FILTER`, including the ore-filter string/list behavior.

### P1 - Import/Export Bus bypasses AE energy-gated transfer helpers

Local Import Bus directly simulates/injects into the item monitor:

- `src/main/java/com/github/singularityme/tile/TileSingularityImportBus.java:262-279`

Local Export Bus directly extracts from the item monitor and inserts into the adjacent target:

- `src/main/java/com/github/singularityme/tile/TileSingularityExportBus.java:364-379`

GTNH AE2 gates transfer amount through the energy grid and uses powered insert/extract helpers:

- AE2 baseline `appeng/parts/automation/PartBaseImportBus.java:121-126`
- AE2 baseline `appeng/parts/automation/PartImportBus.java:136-146`
- AE2 baseline `appeng/parts/automation/PartBaseExportBus.java:225-239`
- AE2 baseline `appeng/parts/automation/PartBaseExportBus.java:272-290`

Impact: bus operation ignores the normal AE energy budget. The global network feature can change connectivity, but energy semantics were explicitly in scope for GTNH consistency.

Recommendation: delegate transfer through AE2 energy-aware helpers or reproduce their energy checks before mutating storage/adjacent inventories.

### P1 - Interface targets all directions instead of AE's configured target face

Local Interface sets every side valid and returns every side as a crafting target:

- `src/main/java/com/github/singularityme/tile/TileSingularityInterface.java:65`
- `src/main/java/com/github/singularityme/tile/TileSingularityInterface.java:167-168`

GTNH AE2 block Interface uses its facing/point-at side for targets, and part Interface targets its part side:

- AE2 baseline `appeng/tile/misc/TileInterface.java:138`
- AE2 baseline `appeng/tile/misc/TileInterface.java:243-247`
- AE2 baseline `appeng/parts/misc/PartInterface.java:367-368`

Impact: pattern push, blocking checks, inventory insertion, and machine routing can hit multiple adjacent inventories instead of the intended side. That is a behavior difference outside the global-network exemption.

Recommendation: store/derive an explicit target side and pass only that side to `DualityInterface`, while leaving network connectivity wireless/global.

### P2 - AE2FC/full ecosystem is not part of compile-time dependencies

Current dependencies include AE2, GT5U, GTNHLib, NEI, and Waila only:

- `dependencies.gradle:2-6`

The audit scope includes AE2FC, wireless terminals, ThaumicEnergistics, ExtraCells history, and other AE ecosystem mods. None are compile-time dependencies here, and `run/client/mods` did not contain those jars during the audit.

Impact: the project can compile while still breaking addon API expectations. Examples already visible from AE2 baseline are AE2FC fluid packet conversions and fluid monitor paths.

Recommendation: add a dedicated compatibility test profile or source set that brings in the GTNH AE ecosystem dependencies, then compile and run smoke tests against them.

### P2 - GTNH Git/versioning setup is still not release-clean

The project directory is not a Git worktree from this environment, and Gradle emits the GTNH convention warning. The current build succeeds because version override is set to `NO-GIT-TAG-SET`.

Impact: local development builds work, but release metadata is not trustworthy and does not match GTNH convention expectations.

Recommendation: use a real Git checkout with at least one tag, or explicitly configure non-Git versioning with a valid `modVersion`.

## Component Matrix

| Singularity component | GTNH AE baseline | Current status |
|---|---|---|
| Storage Bus | `PartStorageBus`, `ContainerStorageBus`, `GuiStorageBus` | Compiles, but priority, external storage, sticky, ore filter, extract partition, AE2FC paths diverge. |
| Import Bus | `PartImportBus`, `PartBaseImportBus` | Basic item import exists; missing ore filter, super speed tiers, energy-gated transfer. |
| Export Bus | `PartExportBus`, `PartBaseExportBus` | Basic export/scheduling/crafting exists; missing `CRAFT_ONLY`, ore filter, super speed tiers, energy-gated transfer. |
| Interface | `TileInterface`, `PartInterface`, `DualityInterface` | Delegates most logic to `DualityInterface`; target side behavior diverges. |
| Drive | `TileDrive` | Cell slots work structurally; priority/cache/idle drain wrapper behavior diverges. |
| Terminal | `ITerminalHost`, AE2 terminal containers | Basic storage monitor access exists; addon/security/manual behavior still needs in-client testing. |
| Global grid | `Grid`, `GridNode`, `PathGridCache` | Intentional Singularity feature. Needs stress testing for lifecycle, chunk unload, and addon assumptions. |

## Manual Test Scenarios Still Required

- Place, break, and chunk-unload every Singularity tile; confirm no duplicate/dangling GridNode and no stale cache entries.
- Cross-dimension same-player storage merge with Drive + Terminal + Storage Bus.
- Storage Bus priority ordering against a normal AE2 drive and multiple external handlers.
- Import/Export Bus with redstone, fuzzy, ore filter, super/superluminal speed, crafting, and craft-only mode.
- Interface pattern push into a single machine side and into multi-adjacent-machine setups.
- AE2FC fluid storage/interface/packet scenarios.
- Wireless terminal access/security with this global grid.
- ThaumicEnergistics/ExtraCells-style storage monitor access if those mods are present in the target pack.

## Investigation Conclusion

Current source code now passes the build, compile, and Spotless gates. It is not yet behaviorally identical to GTNH AE outside the explicit global-network feature. The highest-risk issue is the missing Export Bus `CRAFT_ONLY` setting, followed by storage priority not taking effect and several GTNH-specific upgrade/AE2FC compatibility paths not being copied.
