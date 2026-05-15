# GIF Color Filter Presets — Task Breakdown

**Spec:** [spec.md](spec.md)
**Last updated:** 2026-05-15

---

## How to use this file

- Check off each task `[x]` as it is completed.
- When **every task** in a User Story section is checked, update that story's **Status** badge to `✅ Complete`.
- Status values: `⬜ Not Started` · `🔄 In Progress` · `✅ Complete`

---

## US-1 — Filter Selection

**Spec ref:** [spec.md § US-1](spec.md#us-1--filter-selection)
**Status:** ✅ Complete

### Data Model

- [x] **T1.1** Define `ExportColorFilter` enum with `val vfChain: String?` and four values: `NONE(null)`, `VINTAGE(...)`, `NEON(...)`, `NOIR(...)` — full `vfChain` strings per spec §6.1.
- [x] **T1.2** Add `colorFilter: ExportColorFilter` field to `TaskBuilderVideoToGif` with default value `ExportColorFilter.NONE`.

### UI Layout

- [x] **T1.3** Add `chipColorFilter` chip (`style="@style/Theme.EasyGif.Chip"`, `text="@string/color_filter"`) to `chipGroupMoreOptions` inside `dialog_fragment_video_to_gif_export_options.xml`.
- [x] **T1.4** Create `item_color_filter_card.xml`: a vertical `LinearLayout` containing a 72dp × 72dp `ShapeableImageView` (corner radius 8dp, selected stroke 2dp accent) and a 12sp centered label below it.
- [x] **T1.5** Add `llcGroupColorFilter` expanded section to `dialog_fragment_video_to_gif_export_options.xml` immediately after `llcGroupFramerate` and before `<!--以上菜单按需显示-->`: standard label column (weight=2, `text="@string/color_filter"`) + content column (weight=5) containing `RecyclerView` (`id="rvColorFilterOptions"`, horizontal layout), followed by `<include layout="@layout/view_divider_horizontal" />`. Initial `visibility="gone"`.

### Logic & Wiring

- [x] **T1.6** Add `colorFilter: ExportColorFilter` to the export options ViewModel state (default: `ExportColorFilter.NONE`).
- [x] **T1.7** Wire `chipColorFilter` tap to toggle `llcGroupColorFilter` visibility (gone ↔ visible), matching the pattern used by `chipFramerate` / `llcGroupFramerate`.
- [x] **T1.8** Implement `ColorFilterAdapter` (RecyclerView adapter): binds `ExportColorFilter` items to `item_color_filter_card.xml`; exposes an `onFilterSelected: (ExportColorFilter) -> Unit` callback; applies selected stroke to the active card.
- [x] **T1.9** Wire `ColorFilterAdapter.onFilterSelected` to update `colorFilter` in the ViewModel.
- [x] **T1.10** Observe `colorFilter` in the dialog fragment: when non-NONE, update `chipColorFilter` text to the preset's display name (e.g. "Vintage"); when NONE, restore text to `@string/color_filter`.

### Tests

- [ ] **T1.11** Unit test — `colorFilter` defaults to `ExportColorFilter.NONE` on ViewModel creation.
- [ ] **T1.12** Unit test — `ExportColorFilter.NONE.vfChain` is `null`.
- [ ] **T1.13** Unit test — each non-None preset has a non-null, non-blank `vfChain`.
- [ ] **T1.14** Unit test — ViewModel chip label state: `NONE` → "Color Filter"; `VINTAGE` → "Vintage"; `NEON` → "Neon"; `NOIR` → "Noir".

---

## US-2 — Static Preview Thumbnails

**Spec ref:** [spec.md § US-2](spec.md#us-2--static-preview-thumbnails)
**Status:** ⬜ Not Started

### Core Logic

- [ ] **T2.1** Implement `ColorFilterThumbnailGenerator`: extracts a representative frame at `clipDuration / 2` as a temp PNG via `getCommandExtractFrame()`; for VINTAGE, NEON, NOIR applies the preset's `vfChain` + scale-crop to 72×72 via FFmpeg and saves as a temp JPEG; for NONE, uses the unfiltered frame directly.
- [ ] **T2.2** Run VINTAGE, NEON, and NOIR thumbnail generation in parallel (one `Dispatchers.IO` coroutine per preset); NONE resolves immediately from the already-extracted base frame.
- [ ] **T2.3** Delete all temp files (base frame + filtered JPEGs) when generation completes or is cancelled.

### ViewModel

- [ ] **T2.4** Add `filterThumbnails: StateFlow<Map<ExportColorFilter, Bitmap?>>` to the ViewModel (initial value: all four keys mapped to `null` = loading state).
- [ ] **T2.5** Trigger `ColorFilterThumbnailGenerator` lazily — only when `chipColorFilter` is tapped for the first time; do not re-generate if thumbnails are already loaded.
- [ ] **T2.6** Publish each thumbnail result to `filterThumbnails` individually as each parallel job completes (not as a single batch).
- [ ] **T2.7** Cancel all pending thumbnail jobs and clean up temp files when the dialog is dismissed.

### UI Wiring

- [ ] **T2.8** Observe `filterThumbnails` in `ColorFilterAdapter`: while a preset's value is `null`, show shimmer placeholder; when a `Bitmap` arrives, load it into the card's `ShapeableImageView`; on failure (explicit error marker), show a static placeholder drawable — do not hide or disable the card.

### Tests

- [ ] **T2.9** Unit test — `ColorFilterThumbnailGenerator` applies the correct `-vf` argument per preset.
- [ ] **T2.10** Unit test — NONE preset does not invoke the FFmpeg filter step (uses the base frame directly).
- [ ] **T2.11** Unit test — a failure generating one preset's thumbnail does not cancel or affect the other presets' coroutines.
- [ ] **T2.12** Unit test — temp files are deleted after generation completes (success path).
- [ ] **T2.13** Unit test — temp files are deleted when generation is cancelled (dialog dismissed mid-generation).
- [ ] **T2.14** Unit test — ViewModel does not re-trigger generation if `filterThumbnails` already contains loaded bitmaps (idempotent lazy init).

---

## US-3 — Filter Applied to Export

**Spec ref:** [spec.md § US-3](spec.md#us-3--filter-applied-to-export)
**Status:** ⬜ Not Started

### GIF Pipeline

- [ ] **T3.1** Update `getCommandCreatePalette()` in `TaskBuilderVideoToGif`: prepend `colorFilter.vfChain` to the `palettegen` filter when non-null; use `palettegen=max_colors=64` when `colorFilter == VINTAGE`.
- [ ] **T3.2** Update `getCommandVideoToGif()` in `TaskBuilderVideoToGif`: prepend `colorFilter.vfChain` before `paletteuse` in the two-input filtergraph when non-null.
- [ ] **T3.3** When `colorFilter == VINTAGE` and output is GIF, pass `--colors 64 --dither` to the gifsicle post-processing step.

### WebP Pipeline

- [ ] **T3.4** Update `getCommandVideoToWebp()` in `TaskBuilderVideoToGif`: prepend `colorFilter.vfChain` to the `-vf` argument when non-null. For VINTAGE, use only the color tone chain — do not apply palette reduction flags.

### Regression Guard

- [ ] **T3.5** Verify that `colorFilter == NONE` produces commands byte-identical to the current no-filter output (no `-vf` prefix, no `max_colors`, no `--dither`).

### Tests

- [ ] **T3.6** Unit test — `getCommandCreatePalette()` with NONE: output contains `palettegen` with no filter prefix and no `max_colors`.
- [ ] **T3.7** Unit test — `getCommandCreatePalette()` with VINTAGE: output contains the curves+hue chain and `palettegen=max_colors=64`.
- [ ] **T3.8** Unit test — `getCommandCreatePalette()` with NEON: output contains `hue=s=2.5,eq=...` immediately before `palettegen`.
- [ ] **T3.9** Unit test — `getCommandCreatePalette()` with NOIR: output contains `hue=s=0,eq=...` immediately before `palettegen`.
- [ ] **T3.10** Unit test — `getCommandVideoToGif()` with VINTAGE: filtergraph contains the curves+hue chain before `paletteuse`.
- [ ] **T3.11** Unit test — VINTAGE + GIF: gifsicle command includes `--colors 64` and `--dither`.
- [ ] **T3.12** Unit test — VINTAGE + GIF: gifsicle command does **not** include palette flags when filter is NONE.
- [ ] **T3.13** Unit test — `getCommandVideoToWebp()` with VINTAGE: output contains the color tone chain but does **not** contain `max_colors` or `--dither`.
- [ ] **T3.14** Unit test — `getCommandVideoToWebp()` with NEON: output contains `hue=s=2.5,eq=...`.
- [ ] **T3.15** Unit test — `getCommandVideoToWebp()` with NONE: output contains no `-vf` argument.
