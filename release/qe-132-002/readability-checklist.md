# QE-132-002 standalone report readability checklist

The release owner must open both retained single-file reports offline and record the result before promotion.

- TestNG and Cucumber reports contain only their intended technical or business audience.
- Every expected passed and failed entry is navigable and has the exact expected status.
- REST attachments are nested under method/route steps and expose sanitized request, response, status, timing, and classified failure details.
- Attachment names, content types, ownership, and diagnostic text are readable; no attachment is missing or late.
- Parallel/attempt identity is unambiguous and each event appears exactly once.
- No secret sentinel appears in labels, details, attachments, raw results, or HTML.
- Raw results, expectation matrix, validator JSON, report, source identity, and hashes describe the same candidate.

Machine validation is required before this checklist, but does not replace this owner review.
