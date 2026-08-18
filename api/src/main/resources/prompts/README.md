# Vision prompts

One directory per prompt, one file per version: `room-analysis/v1.md`, `v2.md`, …

The filename **is** the value written to `room_analysis.prompt_version`. Spec §6 makes that column
mandatory: without it, results produced by different prompts become incomparable and the whole
calibration dataset is worthless.

## Rules

- **Never edit a released version in place.** A prompt is an input to persisted analysis results, in
  exactly the way a price book version is an input to persisted quotes. Editing `v1.md` after rows
  reference it silently rewrites history. Add `v2.md` instead.
- Prompts live here rather than in the database because they belong to the deployed artifact: a
  rollback of the application must roll the prompt back with it.
- The response schema the model is held to lives in `schema.json` next to the prompt. Validate
  against it before persisting anything; on validation failure retry once, then fail the job.

## Loading

`quoting/adapter/out/vision` reads the file, records the version on the result, and never
interpolates anything the model could confuse with instructions.
