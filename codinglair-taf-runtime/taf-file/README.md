# TAF structured file validation

`FileController` compares JSON, XML, CSV, Excel OOXML (`.xlsx`), PDF, and fixed-width files inside
named sandboxes. Enable the Spring starter with `taf.file.enabled=true` and configure one or more
`taf.file.sandboxes.<name>.root` values. Each sandbox defaults to a 10 MiB per-file limit, which can
be changed with `maximum-size`.

Comparisons return immutable `FileValidationResult` values. JSON/XML field paths can be ignored,
numeric values can use an explicit absolute tolerance, CSV delimiters and character sets are
explicit, and fixed-width layouts require positive column widths. PDF validation intentionally
uses the binary digest and size: document bytes and extracted text are never placed in reports.
Every evidence artifact contains only the format, outcome, sizes, SHA-256 digests, declared ignored
fields/tolerance, and bounded field-path differences.

Paths are normalized and resolved beneath the sandbox's real path. Traversal, symbolic-link escape,
missing/non-regular files, and oversized files are rejected before parsing. OOXML decompressed
worksheet content is subject to the same cumulative bound. XML external entities and DTDs are
disabled.

## Payload references

`FilePayloadResolver` streams immutable binary payloads referenced by `PayloadReference` from a
configured root. Both `file:` and Git-worktree-relative `git:` references are accepted only when
their real path remains beneath that root. The provider validates the actual file size, configured
maximum, detected media type, and SHA-256 checksum before returning any bytes.

Returned `ResolvedPayload` instances are bounded to their requested inclusive byte range and must
be closed. Evidence contains only logical ID, checksum, media type, size, and version; physical
paths and payload bytes are excluded. The payload resolver itself interprets no document content.
