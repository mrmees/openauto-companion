# OpenAuto Prodigy External API v1.1 Protos

These files are vendored copies of the External API v1 contract from:

`../openauto-prodigy/proto/api/`

Treat this directory as generated-input source. Do not edit these proto files
in the companion repo. Contract changes must land in the Prodigy repo first and
remain additive-only.

This copy includes the deployed v1.1 additive fields:

- `ServerHello.server_id`
- `TimeReport.timezone_id`
- `SystemStatus.display_width`
- `SystemStatus.display_height`

Feature detection in Companion code must use proto3 optional field presence,
not minor-version comparisons.
