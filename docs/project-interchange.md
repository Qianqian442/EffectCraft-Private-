# Project interchange and canonical IR

## Core rule

The project file is data; plug-in binaries are executable code. A project parser should not need to execute the plug-ins referenced by the project.

## Import pipeline

1. Receive a content URI, local file, or share intent.
2. Preserve URI permissions and copy/stream the source into app-private import staging.
3. Detect the actual file signature and supported project format; do not trust the extension alone.
4. Parse into a source-format document model.
5. Convert known constructs into canonical IR.
6. Store unknown source fields and unsupported blocks as opaque, versioned payloads.
7. Resolve media links with a user-facing relink screen when desktop paths are missing.
8. Build a compatibility report before preview or rendering.

## Minimum preserved data

- project identity, name, dimensions, pixel aspect, frame rate, duration and color settings;
- composition IDs and nested composition relationships;
- layer IDs, order, source, in/out points, start offsets, stretch, transform, opacity and blending;
- keyframe times, values, interpolation, temporal/spatial handles and easing;
- masks, mattes, parent links, markers, audio, effects and parameters;
- external assets, original paths, hashes where available, and relink state;
- unrecognized source fields and source-format payloads.

Time should be stored in rational or integer tick form where possible. Avoid converting everything to floating-point seconds during import/export.

## Desktop AEP considerations

- Do not assume binary .aep files are ZIP or XML containers.
- Do not rename .aep or .aepx files to another extension to claim compatibility.
- .aepx is XML-based, but still requires a version-aware parser and tests.
- Desktop paths are not valid Android paths. Use SAF document URIs and explicit relinking.
- Expressions may reference desktop-only APIs or scripts. Preserve source text and mark runtime compatibility.
- A project may open correctly while rendering differently because an effect or font is absent.

## QQ and Android share intent

The importer must support Android content URIs supplied through ACTION_VIEW and ACTION_SEND, including:
- content:// URIs;
- temporary read grants;
- display names that differ from file paths;
- stream-only providers where no filesystem path is exposed;
- large files copied or streamed without loading the entire project into memory.

The receiver must keep the existing app-start auto-import flow. Opening a file from QQ should lead to import, not merely to the home screen.
