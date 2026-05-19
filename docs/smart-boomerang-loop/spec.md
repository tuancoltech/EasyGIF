# Smart Loop Modes — Feature Specification

**Version:** 1.0
**Date:** 2026-05-18
**Scope:** Video → GIF and Video → Animated WebP flows
**Status:** Draft

---

## 1. Overview

Add loop mode options to the export settings: **Forward** (default, current behavior), **Reverse** (all frames played backwards), and **Boomerang** (frames played forward then immediately backward in a seamless ping-pong loop). A **Smart Trim** toggle detects the optimal cut point in the trimmed clip to minimize the visual jump at the loop boundary.

---

## 2. Goals

- Let users choose one of three loop modes: Forward, Reverse, Boomerang.
- Provide a Smart Trim option that automatically finds the best loop cut point within the trimmed clip.
- Implement loop modes via FFmpeg filter chains with no additional library dependencies.
- Support both GIF and Animated WebP output.

## 3. Non-Goals (this iteration)

- Real-time loop preview in the editing screen.
- Loop mode support in GIF Split, GIF → Video, or Motion Photo flows.
- Custom loop count or direction configuration beyond the three named modes.
- Ping-Pong as a separate mode (Boomerang covers this use case).
- Smart Trim with a manual fine-tuning UI.

---

## 4. User Stories

### US-1 — Loop Mode Selection
> As a user, I want to choose a loop mode (Forward, Reverse, Boomerang) from the export options, so my GIF/WebP loops in the style I want.

**Acceptance criteria:**
- A `chipLoopMode` chip is present in the More options chip group for both GIF and Animated WebP formats.
- Tapping the chip reveals the `llcGroupLoopMode` expanded panel; tapping it again collapses it.
- The panel shows three options: **Forward**, **Reverse**, **Boomerang**, displayed as selectable cards with an icon and label.
- **Forward** is selected by default.
- Selecting a mode updates the selected state visually (highlighted stroke around the card).
- When a non-Forward mode is active, the chip label shows the selected mode name (e.g. "Boomerang"). When Forward is selected, the chip label shows "Loop Mode".
- When **Boomerang** is selected, a small info line is shown below the mode cards: *"Output duration will be ~2× longer."*

---

### US-2 — Loop Mode Applied to Export
> As a user, when I tap Save with a non-Forward loop mode selected, I want the exported file to reflect the chosen loop behavior throughout all frames.

**Acceptance criteria:**
- **Reverse**: all frames are played in reverse order. Output duration equals the input trimmed clip duration.
- **Boomerang**: frames play forward then backward in a single seamlessly concatenated output. Output duration is approximately 2× the trimmed clip duration.
- **Forward**: output is identical to the current default behavior — no FFmpeg change applied.
- Loop mode applies to both GIF and Animated WebP pipelines.

---

### US-3 — Smart Trim Detection
> As a user, I want the app to automatically find the best trim point so that my Boomerang (or Reverse) loop feels seamless with no visible jump.

**Acceptance criteria:**
- A "Smart Trim" toggle (`switchSmartTrim`) is visible inside the Loop Mode panel when Reverse or Boomerang is selected; it is hidden when Forward is selected.
- Smart Trim is **off by default**.
- When Smart Trim is enabled and the user taps Save, the app runs detection before encoding. If a better end point is found, a confirmation dialog is shown:
  *"Smart Trim adjusted your end point from [X.Xs] to [Y.Ys] for a smoother loop."* with two actions: **Use Smart Trim** (proceed with detected end point) and **Use My Trim** (proceed with the user's original end point).
- If analysis finds no frame above the similarity threshold, encoding proceeds with the user's original end point silently — no dialog is shown.
- If analysis fails (error), encoding proceeds with the user's original end point silently — no error is shown.

---

## 5. UI Changes

### 5.1 Export Options Dialog (`dialog_fragment_video_to_gif_export_options.xml`)

The dialog follows the same chip + expanded-section pattern used by Color Filter.

**Change 1 — Add chip to `chipGroupMoreOptions`**

```xml
<com.google.android.material.chip.Chip
  android:id="@+id/chipLoopMode"
  style="@style/Theme.EasyGif.Chip"
  android:text="@string/loop_mode" />
```

When a non-Forward mode is active, the chip label updates to show the selected mode name.

**Change 2 — New expanded section `llcGroupLoopMode`**

Add after `llcGroupColorFilter` (before `<!--以上菜单按需显示-->`), following the same structure as `llcGroupColorFilter`:

- `id="llcGroupLoopMode"`, initial `visibility="gone"`
- Standard label column (weight=2, text: `@string/loop_mode`) + content column (weight=5)
- Content column:
  1. Horizontal `RecyclerView` (`id="rvLoopModeOptions"`) with three mode cards
  2. An info `TextView` (`id="tvBoomerangSizeWarning"`, text: `@string/loop_mode_boomerang_size_warning`) — visible only when Boomerang is selected
  3. A Smart Trim toggle row (`id="rowSmartTrim"`) containing a label and `SwitchCompat` (`id="switchSmartTrim"`) — visible only when Reverse or Boomerang is selected
- Bottom: `<include layout="@layout/view_divider_horizontal" />`

**Loop mode card anatomy** (per item in the RecyclerView):
- A 32dp vector icon representing the loop direction (→ Forward, ← Reverse, ↔ Boomerang)
- Label below the icon (12sp, centered): "Forward" / "Reverse" / "Boomerang"
- Selected state: 2dp accent-colored stroke around the card

### 5.2 Smart Trim Confirmation Dialog

A standard `MaterialAlertDialog` shown at export time when Smart Trim detects a better end point:

- Title: `@string/smart_trim_dialog_title` — *"Adjust trim point?"*
- Message: `@string/smart_trim_dialog_message` — *"Smart Trim found a smoother loop point at [Y.Ys] (your trim: [X.Xs])."*
- Positive button: `@string/smart_trim_use_smart` — *"Use Smart Trim"*
- Negative button: `@string/smart_trim_use_mine` — *"Use My Trim"*

### 5.3 No changes to FileSavedActivity

Loop mode is a pre-export encoding setting; the save screen is format-agnostic.

---

## 6. Data Model Changes

### 6.1 New type: `ExportLoopMode`

```kotlin
enum class ExportLoopMode {
    FORWARD,
    REVERSE,
    BOOMERANG,
}
```

### 6.2 `TaskBuilderVideoToGif` — field changes

The existing `val reverse: Boolean` field is **removed** and replaced by `loopMode`. All call sites that previously passed `reverse = true` must be updated to `loopMode = ExportLoopMode.REVERSE`.

| Change | Field | Type | Default | Description |
|---|---|---|---|---|
| Remove | `reverse` | `Boolean` | `false` | Replaced by `loopMode`. |
| Add | `loopMode` | `ExportLoopMode` | `ExportLoopMode.FORWARD` | Loop mode applied during frame extraction. |
| Add | `smartTrim` | `Boolean` | `false` | When true, run Smart Trim detection before encoding and replace the end trim point if a better cut point is found and the user confirms. |

### 6.3 Pipeline integration

All loop mode logic is applied exclusively in `getCommandExtractFrame()`. The palette generation (`getCommandCreatePalette()`), GIF encoding (`getCommandVideoToGif()`), and WebP encoding (`getCommandVideoToWebp()`) steps operate on the extracted BMP frame files and require **no changes** — they are unaware of loop mode.

#### REVERSE

Replace the existing `(",reverse").toEmptyStringIf { !reverse }` expression with:

```kotlin
(",reverse").toEmptyStringIf { loopMode != ExportLoopMode.REVERSE }
```

#### BOOMERANG

Append a split→reverse→concat segment to the existing `-filter_complex` chain, and map the `[out]` label to the BMP output:

```
...[base]; [base]split[v1][v2]; [v2]reverse[v2r]; [v1][v2r]concat=n=2:v=1:a=0[out]
```

The resulting BMP files contain the full forward+reverse sequence (approximately 2× the frame count). All subsequent pipeline steps (palette, GIF, WebP) see the doubled frame set and require no changes.

#### FORWARD

No change to the existing pipeline. Equivalent to the former `reverse = false` behavior.

---

## 7. Smart Trim Detection

Smart Trim finds the frame in the trimmed clip most similar to the first frame and proposes it as the new end trim time. The user confirms or rejects the change via a dialog before encoding begins.

**Algorithm:**
1. Extract downscaled (64×64 grayscale) thumbnails at a sampled frame rate (clips ≤10s → `fps=10`; clips >10s → `fps=5`) via FFmpeg into a temp directory. Runs on `Dispatchers.IO` (blocking subprocess + file writes).
2. Compute normalized histogram correlation between the first thumbnail and each subsequent thumbnail. Runs on `Dispatchers.Default` via `withContext` (CPU-bound in-memory work).
3. The frame with the highest score above the minimum threshold (0.85) is the candidate loop cut point.
4. If no frame exceeds the threshold, skip the dialog and proceed with the user's original `endMs`.
5. If a candidate is found, show the Smart Trim Confirmation Dialog (§5.2). On **Use Smart Trim**, update `TaskBuilderVideoToGif.endMs` with the detected value. On **Use My Trim**, leave `endMs` unchanged.
6. All extracted temp thumbnails are deleted after detection completes or is cancelled.

**Constraints:**
- Triggered only when the user taps "Save" with Smart Trim enabled — not on panel open.
- If detection throws an exception, silently fall back to the user's original `endMs`.

---

## 8. Out-of-Scope Decisions (Deferred)

| Topic | Decision |
|---|---|
| Real-time loop preview in editing screen | Deferred — static mode cards sufficient for v1 |
| Loop mode in GIF Split, GIF → Video, Motion Photo flows | Deferred |
| Manual fine-tuning of the Smart Trim cut point | Deferred — confirmation dialog is sufficient for v1 |
| Ping-Pong as a named separate option | Deferred — identical to Boomerang |
| Loop count / repeat configuration | Deferred |

---

## 9. Resolved Decisions

| # | Question | Decision |
|---|---|---|
| RD-1 | Dispatcher for `SmartTrimDetector`? | Mixed — `Dispatchers.IO` for FFmpeg subprocess/file extraction; `withContext(Dispatchers.Default)` for in-memory histogram correlation |
| RD-2 | Should the UI warn that Boomerang doubles file size? | Yes — small info line below mode cards when Boomerang is selected |
| RD-3 | Should Smart Trim be on or off by default? | Off by default — Smart Trim silently overrides the user's explicit trim choice; opt-in is safer |
| RD-4 | How to handle Smart Trim changing the user's end point? | Pre-export confirmation dialog showing old vs. new end time; user chooses "Use Smart Trim" or "Use My Trim" |
| RD-5 | `reverse: Boolean` vs `loopMode: ExportLoopMode` in `TaskBuilderVideoToGif`? | Replace `reverse: Boolean` with `loopMode: ExportLoopMode` (Option A) — eliminates duplication; call sites migrated from `reverse = true` to `loopMode = REVERSE` |
