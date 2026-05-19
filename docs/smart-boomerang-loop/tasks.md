# Smart Loop Modes — Task Breakdown

**Spec:** [spec.md](spec.md)
**Last updated:** 2026-05-18

---

## How to use this file

- Check off each task `[x]` as it is completed.
- When **every task** in a User Story section is checked, update that story's **Status** badge to `✅ Complete`.
- Status values: `⬜ Not Started` · `🔄 In Progress` · `✅ Complete`

---

## US-1 — Loop Mode Selection

**Spec ref:** [spec.md § US-1](spec.md#us-1--loop-mode-selection)
**Status:** ✅ Complete

### Data Model

- [x] **T1.1** Define `ExportLoopMode` enum with three values: `FORWARD`, `REVERSE`, `BOOMERANG`.
- [x] **T1.2** In `TaskBuilderVideoToGif`: remove `val reverse: Boolean`; add `val loopMode: ExportLoopMode = ExportLoopMode.FORWARD`; migrate all call sites that passed `reverse = true/false` to `loopMode = ExportLoopMode.REVERSE/FORWARD`.
- [x] **T1.3** Add `smartTrim: Boolean` field to `TaskBuilderVideoToGif` with default value `false`.

### UI Layout

- [x] **T1.4** Add `chipLoopMode` chip (`style="@style/Theme.EasyGif.Chip"`, `text="@string/loop_mode"`) to `chipGroupMoreOptions` inside `dialog_fragment_video_to_gif_export_options.xml`.
- [x] **T1.5** Create `item_loop_mode_card.xml`: a vertical `LinearLayout` containing a 32dp vector icon and a 12sp centered label below it, with selected stroke state (2dp accent).
- [x] **T1.6** Add `llcGroupLoopMode` expanded section to `dialog_fragment_video_to_gif_export_options.xml` after `llcGroupColorFilter` (before `<!--以上菜单按需显示-->`): standard label column (weight=2, `text="@string/loop_mode"`) + content column (weight=5) containing (1) horizontal `RecyclerView` (`id="rvLoopModeOptions"`), (2) info `TextView` (`id="tvBoomerangSizeWarning"`), (3) Smart Trim toggle row (`id="rowSmartTrim"`, `SwitchCompat` `id="switchSmartTrim"`), followed by `<include layout="@layout/view_divider_horizontal" />`. Initial `visibility="gone"`.
- [x] **T1.7** Add vector drawables for Forward (→), Reverse (←), and Boomerang (↔) loop mode icons.
- [x] **T1.8** Add string resources: `loop_mode`, `loop_mode_forward`, `loop_mode_reverse`, `loop_mode_boomerang`, `loop_mode_smart_trim`, `loop_mode_boomerang_size_warning`.

### Logic & Wiring

- [x] **T1.9** Add `loopMode: ExportLoopMode` and `smartTrimEnabled: Boolean` to the export options ViewModel state (defaults: `FORWARD`, `false`).
- [x] **T1.10** Wire `chipLoopMode` tap to toggle `llcGroupLoopMode` visibility, matching the pattern used by `chipColorFilter` / `llcGroupColorFilter`.
- [x] **T1.11** Implement `LoopModeAdapter` (RecyclerView adapter): binds `ExportLoopMode` items to `item_loop_mode_card.xml`; exposes `onModeSelected: (ExportLoopMode) -> Unit` callback; applies selected stroke to the active card.
- [x] **T1.12** Wire `LoopModeAdapter.onModeSelected` to update `loopMode` in the ViewModel.
- [x] **T1.13** Observe `loopMode`: show `tvBoomerangSizeWarning` only when BOOMERANG is active. Show `rowSmartTrim` only when REVERSE or BOOMERANG is active; hide for FORWARD. Wire `switchSmartTrim` state to `smartTrimEnabled` in the ViewModel.
- [x] **T1.14** Observe `loopMode` for chip label: non-FORWARD → mode display name (e.g. "Boomerang"); FORWARD → `@string/loop_mode`.

### Tests

- [x] **T1.15** Unit test — `loopMode` defaults to `ExportLoopMode.FORWARD` on ViewModel creation.
- [x] **T1.16** Unit test — `smartTrimEnabled` defaults to `false` on ViewModel creation.
- [x] **T1.17** Unit test — chip label state: `FORWARD` → "Loop Mode"; `REVERSE` → "Reverse"; `BOOMERANG` → "Boomerang".
- [x] **T1.18** Unit test — `tvBoomerangSizeWarning` visibility: visible for BOOMERANG only.
- [x] **T1.19** Unit test — `rowSmartTrim` visibility: hidden for FORWARD, visible for REVERSE and BOOMERANG.

---

## US-2 — Loop Mode Applied to Export

**Spec ref:** [spec.md § US-2](spec.md#us-2--loop-mode-applied-to-export)
**Status:** ✅ Complete

### Frame Extraction Pipeline

All loop mode logic lives in `getCommandExtractFrame()`. `getCommandCreatePalette()`, `getCommandVideoToGif()`, and `getCommandVideoToWebp()` require no changes.

- [x] **T2.1** Update `getCommandExtractFrame()` for REVERSE: replace `(",reverse").toEmptyStringIf { !reverse }` with `(",reverse").toEmptyStringIf { loopMode != ExportLoopMode.REVERSE }`.
- [x] **T2.2** Update `getCommandExtractFrame()` for BOOMERANG: when `loopMode == BOOMERANG`, replace the simple `[out]` label at the end of the `-filter_complex` chain with the split→reverse→concat segment: `...[base]; [base]split[v1][v2]; [v2]reverse[v2r]; [v1][v2r]concat=n=2:v=1:a=0[out]`, mapping `[out]` to the BMP output.

### Regression Guard

- [x] **T2.3** Verify `loopMode == FORWARD` produces a `getCommandExtractFrame()` output byte-identical to the former `reverse = false` output.

### Tests

- [x] **T2.4** Unit test — `getCommandExtractFrame()` with FORWARD: no `reverse` and no concat filter present.
- [x] **T2.5** Unit test — `getCommandExtractFrame()` with REVERSE: output contains `,reverse` in the filter chain.
- [x] **T2.6** Unit test — `getCommandExtractFrame()` with BOOMERANG: output contains the split→reverse→concat segment and maps `[out]` to the BMP output.
- [x] **T2.7** Unit test — `getCommandCreatePalette()` is identical across all three loop modes (loop mode does not affect palette command).
- [x] **T2.8** Unit test — `getCommandVideoToGif()` is identical across all three loop modes.
- [x] **T2.9** Unit test — `getCommandVideoToWebp()` is identical across all three loop modes.

---

## US-3 — Smart Trim Detection

**Spec ref:** [spec.md § US-3](spec.md#us-3--smart-trim-detection)
**Status:** ⬜ Not Started

### Core Logic

- [ ] **T3.1** Implement `SmartTrimDetector` / `SmartTrimDetectorImpl`: extracts downscaled (64×64 grayscale) thumbnails at sampled frame rate (clips ≤10s → `fps=10`; clips >10s → `fps=5`) via FFmpeg on `Dispatchers.IO`.
- [ ] **T3.2** Compute normalized histogram correlation between the first thumbnail and each subsequent thumbnail using `withContext(Dispatchers.Default)`; return the timestamp (ms) of the highest-scoring frame if above threshold (0.85), or `null` if none qualify.
- [ ] **T3.3** Delete all extracted temp thumbnails after detection completes, returns null, or throws.

### Export Flow

- [ ] **T3.4** When the user taps "Save" with `smartTrimEnabled == true` and `loopMode != FORWARD`, run `SmartTrimDetector` before launching the encoding pipeline.
- [ ] **T3.5** If detection returns a candidate timestamp, show the Smart Trim Confirmation Dialog (spec §5.2). On **Use Smart Trim**, update `TaskBuilderVideoToGif.endMs` with the detected value. On **Use My Trim**, leave `endMs` unchanged.
- [ ] **T3.6** If detection returns `null` or throws, proceed with the user's original `endMs` silently — no dialog, no error shown.

### Tests

- [ ] **T3.7** Unit test — `SmartTrimDetector` returns `null` when no frame exceeds the similarity threshold.
- [ ] **T3.8** Unit test — `SmartTrimDetector` returns the correct timestamp when a high-similarity frame is present.
- [ ] **T3.9** Unit test — histogram correlation runs on `Dispatchers.Default`, not `Dispatchers.IO`.
- [ ] **T3.10** Unit test — temp thumbnails are deleted after detection completes (success path).
- [ ] **T3.11** Unit test — temp thumbnails are deleted when detection throws.
- [ ] **T3.12** Unit test — when `smartTrimEnabled == false`, `SmartTrimDetector` is never invoked.
- [ ] **T3.13** Unit test — when detection returns `null`, `TaskBuilderVideoToGif.endMs` is unchanged.
- [ ] **T3.14** Unit test — when the user selects "Use My Trim" in the dialog, `TaskBuilderVideoToGif.endMs` is unchanged.
- [ ] **T3.15** Unit test — when the user selects "Use Smart Trim" in the dialog, `TaskBuilderVideoToGif.endMs` equals the detected timestamp.
