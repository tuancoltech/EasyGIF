# Video-to-GIF Export Preview — API

## `PreviewController`

```kotlin
class PreviewController(frame: Bitmap, cropParams: CropParams)
```

| Member | Description |
|---|---|
| `suspend fun render(taskBuilder: TaskBuilderVideoToGifForPreview): Bitmap?` | Renders the preview for `taskBuilder` on a single background thread. |
| `suspend fun clear()` | Clears both caches and wipes the preview cache directory. |

### `render` results

| Returns | Condition |
|---|---|
| `Bitmap` | Pipeline succeeded; the bitmap is cached for identical `taskBuilder` values. |
| `null` | A stage output was missing/unreadable (decode failed). Nothing is cached and the next call re-runs every stage. |

Throws `CancellationException` if the calling coroutine is canceled (e.g., superseded by a newer render).
