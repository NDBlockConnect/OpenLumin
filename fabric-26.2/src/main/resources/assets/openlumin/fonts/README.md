# OpenLumin bundled fonts

This directory ships the fonts used by OpenLumin's TTF text renderer. Keep this file in
sync whenever a font is added, replaced or removed — the license terms below are a
distribution requirement, not documentation decoration.

| File | Family / source | License | Notes |
|---|---|---|---|
| `font.ttf` | **Jura** (Google Fonts, upstream copyright 2012 Google Inc.) | SIL OFL 1.1 (see `OFL.txt`) | Default face used by `StaticFontLoader.DEFAULT_FONT_ID`. Variable font: carries the full Light–Bold weight range (`fvar`/`gvar`/`STAT` tables present), not a single static cut. |
| `jura-light.ttf` | same as `font.ttf` | SIL OFL 1.1 (see `OFL.txt`) | Retained as the historical path expected by `StaticFontLoader.JURA_LIGHT`. Byte-identical to `font.ttf` (verified by SHA-256). |

## Silently-missing faces

`StaticFontLoader` previously referenced `fonts/icons.ttf` and `fonts/osakachips.ttf`.
Neither file has ever been committed to this repository, and no call site dereferenced
them, so those constants were removed. If a future face is added, place the file here and
extend the table above **in the same change**.

`StaticFontLoader.safeBuiltin` degrades a missing or unreadable face to `null` and logs one
error, and `TextRenderer` treats a `null` face as "text disabled" (no draw, zero width).
That keeps a missing asset from crashing the game, but it also hides packaging mistakes:
when adding fonts, confirm the resource actually resolves rather than relying on the
fallback.

## License obligations

`OFL.txt` contains the SIL Open Font License 1.1 text, copied verbatim from the upstream
distribution. Redistributing these font files without this license text, or selling the
fonts by themselves, is not permitted by the OFL. Font files must not be renamed in a way
that drops the upstream copyright notice (this directory's file names are internal asset
paths, not font family names, which is why they may differ from `Jura`).

GitHub@NDBlockConnect | BlockConnect@StarsailsClover
