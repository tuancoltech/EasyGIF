# GIF Color Filter Presets — Feature Specification

**Version:** 1.1
**Date:** 2026-05-15
**Scope:** Video → GIF and Video → Animated WebP flows
**Status:** Draft

---

## 1. Overview

Add palette-aware color filter presets to the export options. Before exporting, the user can choose from a set of named filters — each one adjusts hue, saturation, or contrast via FFmpeg `-vf` chains that are applied to raw frames before encoding, making them format-agnostic. Filters are presented in the export options dialog as a static per-filter preview thumbnail strip so the user can see the visual difference before committing to an export.

One filter characteristic is GIF-specific: the Vintage preset's palette reduction (64 colors + dithering) applies only when exporting as GIF. When exporting as Animated WebP, Vintage applies the color tone only.

---

## 2. Goals

- Let users apply one of four color filter presets (None, Vintage, Neon, Noir) before export.
- Show a static single-frame thumbnail per filter so the user sees the visual difference before exporting.
- Implement filters as FFmpeg `-vf` chains inserted into the existing encoding pipeline with minimal changes.
- Support both GIF and Animated WebP output — the `-vf` chains are format-agnostic.

## 3. Non-Goals (this iteration)

- Real-time filter preview in the editing screen.
- Custom or user-adjustable filter parameters (e.g. a saturation slider).
- Filter support in GIF Split, GIF → Video, or Motion Photo flows.
- More than four presets (None + three named filters).

---

## 4. User Stories

### US-1 — Filter Selection
> As a user, I want to choose a color filter preset from the export options, so I can give my output a specific visual style without any manual editing.

**Acceptance criteria:**
- A `chipColorFilter` chip is present in the More options chip group for both GIF and Animated WebP formats.
- Tapping the chip reveals the `llcGroupColorFilter` expanded panel; tapping it again collapses it.
- The expanded panel shows four filter options: **None**, **Vintage**, **Neon**, **Noir**.
- **None** is selected by default.
- Selecting a filter updates the selected state visually (highlighted stroke around the card).
- When a non-None filter is active, the chip label shows the selected preset name (e.g. "Vintage") so the user knows a filter is applied while the panel is collapsed. When None is selected, the chip label shows "Color Filter".

---

### US-2 — Static Preview Thumbnails
> As a user, I want to see a preview thumbnail for each filter preset, so I can compare the visual effect before exporting.

**Acceptance criteria:**
- Each filter option displays a static single-frame thumbnail showing the filter applied to a representative frame of the clip (at `clipDuration / 2`).
- Thumbnails are generated in the background when the Color Filter panel is **first expanded** (lazy — not on dialog open); a loading shimmer is shown per card while generation is in progress.
- If thumbnail generation fails for a preset, the option remains selectable but shows a placeholder image — it does not block export.
- Thumbnails are square-cropped to 72dp × 72dp.

---

### US-3 — Filter Applied to Export
> As a user, when I tap Save with a filter selected, I want the exported file to have the chosen filter applied throughout all frames.

**Acceptance criteria:**
- The selected filter's FFmpeg `-vf` chain is injected into the encoding pipeline for both GIF and WebP outputs.
- When exporting as GIF, the Vintage filter additionally reduces the palette to 64 colors and enables dithering.
- When exporting as Animated WebP, the Vintage filter applies the color tone only — palette reduction is not applicable.
- Selecting **None** produces output identical to the current no-filter export.

---

## 5. UI Changes

### 5.1 Export Options Dialog (`dialog_fragment_video_to_gif_export_options.xml`)

The dialog already follows a pattern where the **More options row** hosts a `ChipGroup` of toggleable chips, and each chip reveals a dedicated expanded section below it (currently `llcGroupColorKey` and `llcGroupFramerate`). The Color Filter feature follows this same pattern exactly.

**Change 1 — Add chip to `chipGroupMoreOptions`**

Add a new `Chip` inside the existing `chipGroupMoreOptions`:

```xml
<com.google.android.material.chip.Chip
  android:id="@+id/chipColorFilter"
  style="@style/Theme.EasyGif.Chip"
  android:text="@string/color_filter" />
```

When a non-None filter is active, the chip label updates to show the selected preset name (e.g. "Vintage") so the user knows a filter is applied while the panel is collapsed.

**Change 2 — New expanded section `llcGroupColorFilter`**

Add a new `LinearLayoutCompat` immediately after `llcGroupFramerate` and before the closing comment `<!--以上菜单按需显示-->`, following the same structure as `llcGroupFramerate`:

- `id="llcGroupColorFilter"`, initial `visibility="gone"`
- Revealed/hidden when `chipColorFilter` is tapped, same as how `chipFramerate` controls `llcGroupFramerate`
- Contains a standard label column (weight=2, text: `@string/color_filter`) and a content column (weight=5)
- Content column: horizontal `RecyclerView` (`id="rvColorFilterOptions"`) displaying four filter cards
- Bottom: `<include layout="@layout/view_divider_horizontal" />`

**Filter card anatomy** (per item in the `RecyclerView`):
- 72dp × 72dp rounded thumbnail (`ShapeableImageView`, corner radius 8dp)
- Label below the thumbnail (12sp, centered): "None" / "Vintage" / "Neon" / "Noir"
- Selected state: 2dp accent-colored stroke around the card
- Loading state: shimmer placeholder while the thumbnail is being generated

### 5.2 No changes to FileSavedActivity

Filters are a pre-export setting; the save screen is format-agnostic.

---

## 6. Data Model Changes

### 6.1 New type: `ExportColorFilter`

Named `ExportColorFilter` (not `GifColorFilter`) to reflect that it applies to both output formats.

```kotlin
enum class ExportColorFilter(val vfChain: String?) {
    NONE(vfChain = null),
    VINTAGE(vfChain = "curves=r='0/0 0.5/0.6 1/1':b='0/0 0.5/0.4 1/0.85',hue=s=0.7"),
    NEON(vfChain = "hue=s=2.5,eq=brightness=0.05:contrast=1.1"),
    NOIR(vfChain = "hue=s=0,eq=contrast=1.5"),
}
```

Vintage's palette reduction behavior (`paletteColors`, `dither`) is handled conditionally in the pipeline based on output format rather than carried on the enum, since it is not a filter property — it is a GIF encoding constraint.

### 6.2 `TaskBuilderVideoToGif` — new field

| Field | Type | Default | Description |
|---|---|---|---|
| `colorFilter` | `ExportColorFilter` | `ExportColorFilter.NONE` | Filter preset applied during encoding. Works for both GIF and WebP pipelines. |

### 6.3 Pipeline integration

The filter's `vfChain` is injected before the format-specific encoding step. Behavior differs by format for Vintage only.

#### GIF pipeline

**`getCommandCreatePalette()`** — prepend `vfChain` to the `palettegen` filter:

| Filter | Effective `-vf` argument |
|---|---|
| NONE | `palettegen` (unchanged) |
| VINTAGE | `curves=...,hue=s=0.7,palettegen=max_colors=64` |
| NEON | `hue=s=2.5,eq=...,palettegen` |
| NOIR | `hue=s=0,eq=...,palettegen` |

**`getCommandVideoToGif()`** — prepend `vfChain` before `paletteuse` in the filtergraph:

```
[input vfChain] [x]; [x][palette] paletteuse=dither=bayer
```

For VINTAGE, also pass `--colors 64 --dither` to the gifsicle post-processing step.

#### Animated WebP pipeline

**`getCommandVideoToWebp()`** — prepend `vfChain` to the `-vf` argument:

| Filter | Effective `-vf` argument |
|---|---|
| NONE | _(no `-vf`)_ (unchanged) |
| VINTAGE | `curves=...,hue=s=0.7` — color tone only; no palette reduction |
| NEON | `hue=s=2.5,eq=...` |
| NOIR | `hue=s=0,eq=...` |

---

## 7. Thumbnail Generation

Thumbnail generation runs as a background coroutine triggered **lazily** when the Color Filter panel is first expanded. This avoids FFmpeg work when the user never opens the panel. Thumbnails reflect the visual color effect only and are format-agnostic (the Vintage thumbnail does not simulate palette reduction).

**Algorithm:**
1. Extract a single representative frame at `clipDuration / 2` to a temp PNG via `getCommandExtractFrame()`.
2. For each of VINTAGE, NEON, NOIR: apply the `vfChain` to the extracted PNG via FFmpeg, scale and crop to 72×72, save to a temp JPEG.
3. NONE uses the unfiltered extracted frame directly.
4. Publish each result to the UI via `StateFlow<Map<ExportColorFilter, Bitmap?>>` as each completes.
5. All three filtered thumbnails are generated in parallel (one coroutine per preset).
6. Temp files are deleted when the dialog is dismissed.

**Constraints:**
- Generation runs on `Dispatchers.IO`.
- If any single preset fails, the others are unaffected; the failed card shows a placeholder.

---

## 8. Out-of-Scope Decisions (Deferred)

| Topic | Decision |
|---|---|
| Real-time filter preview in editing screen | Deferred — static export-dialog thumbnails are sufficient for v1 |
| Filter support in GIF Split, GIF → Video, Motion Photo flows | Deferred to a follow-up iteration |
| Custom filter parameters (e.g. saturation slider) | Deferred — named presets keep UX simple for v1 |
| Additional presets beyond the initial four | Deferred |
| Simulating palette reduction in Vintage thumbnail | Deferred — color tone preview is sufficient for v1 |

---

## 9. Resolved Decisions

| # | Question | Decision |
|---|---|---|
| OQ-1 | Export dialog vs. editing screen for filter selection? | Export dialog — keeps effort Low; static thumbnails provide sufficient visual feedback for v1 |
| OQ-2 | Should filters apply to Animated WebP? | Yes — FFmpeg `-vf` chains are format-agnostic; Vintage applies color tone only for WebP (palette reduction is GIF-specific) |
| OQ-3 | Should Vintage reduce the palette count for GIF? | Yes — palette reduction to 64 colors + dithering is the defining characteristic of the Vintage aesthetic within GIF's constraints |
| OQ-4 | Type name: `GifColorFilter` or `ExportColorFilter`? | `ExportColorFilter` — avoids implying GIF-only scope now that WebP is included |
