# 14. What stage 2 leaves to the reader

Date: 2026-08-20
Status: accepted

## Context

§5.5 and §5.6 describe stage 2 per surface: the paintable ratio comes from
`surface_finding.coating`, filler and skim come from each surface's own findings, and openings are
counted rather than estimated. §5.4, though, produces one wall area per *room*, and three questions
sit in the gap between the two.

## Decision

**A room's wall area is split equally across its analysed surfaces.** A room with four wall photos
gets four equal quarters; a corner-shot kitchen with one `ROOM_GENERAL` surface gets all of it. The
alternative — asking the model to estimate each wall's share — asks it for a measurement, and the
core rule of this system is that vision produces observations and never measurements. Equal split is
wrong in every individual room and unbiased across a job, which is the right kind of wrong here.

The split happens *after* the opening deduction and the 60% floor, so a room does not lose more wall
to its doors than §5.5 allows and then lose it again per surface.

**A surface whose coating is not `PAINTED` is dropped before the split, not discounted after it.** Its
area leaves the job entirely, as §5.5 says: a tiled kitchen wall is not a cheaper wall. Filler, skim,
primer and wallpaper stripping are then per painted surface, so a tiled wall cannot contribute
preparation work either.

**`DARK_TO_LIGHT` applies in proportion to the dark share of the wall area.** §5.7 gives the modifier
a factor and a condition ("tone is `DARK`") but a job has many surfaces and they are not all dark. One
dark accent wall in a flat would otherwise multiply the whole wall line by 1.50 — a 50% surcharge on
walls that need one coat. Half the wall area dark therefore prices at 1.25, and
`PricingEngineTest#darkToLightScalesWithTheDarkShareOfTheWalls` pins it. Doors keep the full factor:
`door_colour_change` is one declaration about all of them.

## Consequences

The engine's stage 2 path prices every item code §5.6 lists. Two things are worth stating plainly
because a reader will look for them:

**Ceiling findings are not priced.** `room_analysis` carries `ceiling_filler` and `ceiling_staining`,
and §5.6's quantity table uses neither — `PATCH_FILLING` and `STAIN_BLOCK_PRIMER` are both `Σ
wallNet`. So a stained ceiling today produces no stain-block primer, which is the opposite of what a
decorator would do: a ceiling stain is usually the reason to prime at all. The engine follows the
spec rather than inventing a quantity source, and this is the open question to put to the business
before launch. It is a probable underquote on exactly the jobs where a leak is involved.

**Stage 1 and stage 2 remain one path** everywhere else: allocation, modifiers, duration, minimum,
margin, VAT and rounding are shared code, and the only branches are the two §5.5 and §5.9 name. A
divergence in what the two stages charge for the same home would be a bug in the engine rather than a
feature of the stage.
