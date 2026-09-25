# TACZ Wall Display

Build a large armory with decorative TACZ guns, including their equipped attachments. Static model rendering reduces the recurring rendering work of displaying many guns in item frames. Actual performance depends on your hardware, gun models, and display size.

## Requirements

**Install TACZ Wall Display and the matching TACZ version on both the client and the server.** For single-player, install both in your game instance.

| Minecraft | Mod loader / recommended version | Required TACZ version | Java |
| --- | --- | --- | --- |
| 1.21.1 | NeoForge; recommended: **21.1.248** | [UNOFFICIAL TaCZ NeoForge Port — 1.1.8-hotfix-r6](https://www.curseforge.com/minecraft/mc-mods/tacz-1-21-1/files/8547439) | 21 |
| 1.20.1 | Forge; recommended: **47.4.21** | [TaCZ: Timeless and Classics Zero Guns — 1.1.8-hotfix](https://www.curseforge.com/minecraft/mc-mods/timeless-and-classics-zero/files/8141310) | 17 or a compatible newer runtime |

These requirements apply to the matching Minecraft builds. **The Forge and NeoForge versions above are recommendations, not minimum requirements. This addon does not impose a platform version gate.** You do not need to update your loader just to match the recommended number if your current setup is compatible. TACZ and other installed mods retain their own requirements. **The listed TACZ versions are exact requirements, not minimum versions.** The mod declares mandatory dependencies and rejects missing or mismatched TACZ versions. Static model capture integrates with TACZ's internal renderer, so other TACZ versions need separate compatibility testing.

Older-loader verification: Forge **47.3.5** and NeoForge **21.1.1** passed an isolated in-game test covering equipped model display, configuration, six-face rotation/flipping, Creative flight Shift input, synchronization, and sounds. This does not guarantee every historical loader version or mod combination.

Choose the download that matches your Minecraft version and mod loader. The 1.20.1 build uses Forge; the 1.21.1 build uses the unofficial NeoForge port of TACZ. Install only the matching TACZ project.

**No separate GeckoLib, SimpleBedrockModel, or Cloth Config installation is required by this addon.** The required TACZ downloads include their model library and other bundled runtime libraries. Rendering and monitoring mods such as Sodium, Embeddium, Iris, and RuOK are not prerequisites. Packet Fixer is not a prerequisite of this addon either; large gun-pack collections may have their own networking requirements.

Create is optional. On Minecraft 1.21.1, Create 6.0.10 was tested for super-glued decorative guns on rotating contraptions; no Create installation is needed for stationary displays. This compatibility is not included in the 1.20.1 build.

Additional gun packs are optional. To display a gun from an additional pack, keep that pack and any dependencies required by the pack installed according to its instructions.

## Features

- Display large collections of guns using cached static models and batched rendering.
- Preserve the original gun and its equipped attachments when placing it as a decorative block.
- Reuse the gun's detailed model, textures, and inventory icon directly from TACZ and installed gun packs.
- Place displays on all six block faces and remove their supports afterward.
- On 1.21.1, rotate a display with right-click and flip it with Left Alt + scroll in Free Gun Placement Mode.
- Prepare nearby displays while entering the world to reduce first-view loading stutter.
- On the 1.21.1 build, super-glue displays to Create moving structures; their static meshes follow rotation and retain the original gun when disassembled.

Gun-pack and attachment compatibility depends on the source models. Unusual custom rendering may need additional support.

## How to Use

**Minecraft 1.21.1:** Press **P** to enter Free Gun Placement Mode; rebind it in the TACZ section of Controls. Hold a TACZ gun and right-click a block to display it on that face. Left-click retains normal block breaking, and decorative guns break with one empty-hand click in Survival. Right-click an existing display with any item or an empty hand to rotate it by 22.5 degrees. Hold **Left Alt** and scroll over a display to flip it. Hold a real gun and **Left Alt + right-click** a display to place another gun in the clicked face's neighboring cell, including unsupported space. Gun actions are suspended in this mode. Press the mode key again to leave. Crafting conversion is no longer available. Existing decorative gun items restore their original guns when loaded; they cannot be placed directly. Creative pick-block returns the original gun.

**Minecraft 1.20.1:** Put one TACZ gun in a crafting grid to obtain its decorative block, and craft the block alone to restore the gun. This version retains its previous crafting workflow until it is explicitly updated.

For either version, break a display in Survival to recover the original usable gun with its saved attachments and other data. Creative-mode breaking produces no drop. On **1.20.1 only**, hold a stick or the configured adjustment item and right-click to rotate by 22.5 degrees; Shift + right-click flips it.

## Adjustment Item (1.20.1 Only)

Set `adjustmentItem` in `tacz-wall-display-server.toml` to an item ID. The default is:

```toml
adjustmentItem = "minecraft:stick"
```

The server controls this setting in multiplayer. On Forge 1.20.1, edit the file in the world's `serverconfig` folder; `defaultconfigs` supplies the template for worlds without a saved configuration. The 1.21.1 build no longer has an adjustment-item configuration.

The selected item's own right-click behavior is not specially handled. Choose an appropriate item for your setup.
