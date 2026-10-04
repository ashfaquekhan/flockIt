# FlockIt issue log

Every problem found so far — what went wrong, why, and how it was fixed — so the same mistake
is not made twice. Newest first. Open items at the top. Update together with
[CHANGELOG.md](CHANGELOG.md) on every release.

## Open / to watch
| # | Issue | Notes |
|---|---|---|
| O1 | Heater and alarm settings for the controller not planned yet | Need the controller's Heater Setting and Alarm Setting screens. |
| O2 | Task-planner reorganisation, water-fill range reminder | Deferred since v24. |
| O3 | Task alarms are not re-scheduled after the phone restarts | Alarms refresh only when the app is opened. |
| O4 | Engine alert text (stored with each day) still says "exceeds ceiling" | Output topics use their own alerts; the stored text is not shown on Output. |
| O5 | Water use is not logged | All water figures are projected. |
| O6 | Real Google Sheets sync needs a proper OAuth access token and a two-account test | See README "Google OAuth". |
| O7 | Humidity-compensation coefficients on the IB controller not verified | A test was given; confirm on the controller. |
| O8 | No Entry fields for house readings (house temp/RH, litter, NH₃, CO₂, body/foot temperature…) | Removed from Entry in v16. Readings typed into the sheet's Measured* columns now show as Present. Add an optional readings section if wanted. |
| O9 | Foot temperature has no official target | Range shown is from the cheek test and thermal-camera studies; treat as a guide. |
| O10 | Breast height vs pan lip is an estimate | ≈4.0 cm at 40 g, scaling with weight^⅓; pan lip height is a farm setting. |
| O11 | Feeding times and "hot day" (≥ 33 °C) are rules of thumb | Based on farm practice + heat-stress research (no feed ~5 h before peak heat). |
| O12 | Feedings logged on the Output page are kept on the phone only | They drive the coop animation; the day's feed used is still entered on the Entry tab. Sync to the sheet if wanted. |
| O13 | Pan plan uses the farm's "bags to fill 1 line" as a straight pans-per-bag rate | Pan spacing and line gap are farm settings (default 2.5 ft, width ÷ lines); first pan 5 ft from the front wall; 3 % slack on the fill; birds-per-pan limit scaled by weight^⅓ below 2 kg (Ross gives 45–80 for grown birds only). |
| O14 | Lighting schedule assumes the dark period ends at 05:00 | Make it a farm setting if the farm's dark period differs. |
| O15 | Coop environment values are ideals | No house sensors; birds behave as in comfortable air. |
| O16 | Cooling grid and fan finder start from the season's typical temperatures | Weather is not used for inside values until house sensors exist. |
| O17 | Farm settings still hold the old default of 60 pans per line unless changed | Set feed pans (e.g. 112), sensor pans (2) and bags to fill a line (e.g. 3.3) in Farm settings for the plan to match the shed. |
| O18 | Pour per line can be a fraction of a bag (e.g. 2.38 = 118.8 kg) | Shown in bags and kg; the day's total is always whole bags. |
| O19 | Day clock times are rules, not settings | Dark ends at 05:00 (O14); the hot window comes from the forecast or the season's typical day. Say if the farm needs a different dark period or feeding hours. |
| O20 | Farm window draws up to 1,800 birds at once | A width that would hold more at today's density steps down to half or quarter width. |
| O23 | True CV needs birds weighed one by one | Bulk (bucket) weighing can't give the bird-to-bird CV; enter 10+ single weights (50–100 is better) on sample days. |
| O24 | Trend tolerance is ±3 % against the commercial standard (or ideal) | A guide; tell me if some KPIs need a tighter or wider band. |
| O22 | Sheet upgrade and repair are checked by unit tests, not yet against a live Google account | The layout logic is tested; the Drive/Sheets calls need a real run on the farm's sheet. |
| O25 | Feed correction is advice only | Capped at +5 % / −3 %, none in week 1; birds eat to appetite, so check pans are cleared before adding feed. |
| O26 | Breaths a minute in the animation box are typical resting rates, not measured | 30–50 in week 1, 20–40 later; panting over 60. |
| O27 | Access token cached in plain preferences and app backup allowed | See ARCHITECTURE.md §10; fix with the P6 sign-in change. |
| O28 | Larger refactor (use cases, DI, Gradle modules) is planned, not done | Steps listed in ARCHITECTURE.md §9; done gradually so each release installs over a live flock. |
| O30 | Stock page does not plan orders | By request: received, used and in store only. Needed / to order / days left can come back later. |
| O31 | Which chart day is which flock day is an assumption | Taken as: the chart's day 1 is the placement day (the app's day 0). If the company counts the day AFTER placement as day 1, feed and commercial mortality should move back one row — say so and it is a one-line change (`CompanyStandard.duringDay`). |
| O32 | Curves past day 49 (breed) and day 55 (company) are modelled, not from a table | Gompertz weight, maintenance + growth feed. Replace with the published rows if the farm keeps flocks that long. |
| O33 | Feeding suggestion and growth forecast need data | 3 days of feed entries / at least one weighing; before that they say so. Their noise settings (3 % a weighing, 0.6 % drift a day) are sensible defaults, not fitted to this farm yet. |
| O34 | Sheet schema 6 upgrade is tested on data, not against a live Google account | As O22: the layout logic is unit-tested; the Drive / Sheets calls need a real run. A snapshot of the old sheet is made first. |
| O21 | Output review checklist (keep / change / drop) is waiting for the farm's choices | https://claude.ai/artifact/VDtyZmDKtLiJBu5Nxq6B3g |

## Fixed
| Version | Issue | Cause | Fix |
|---|---|---|---|
| v42 | App used the phone's default type, which differs between phones | No bundled font | Rubik and JetBrains Mono bundled (option A) |
| v41 | Feed and commercial mortality one day behind the company chart | The chart starts at day 1 (placement day), the app at day 0; "during the day" figures used the same row number as the morning weight | During flock day N = chart row N + 1 (`CompanyStandard.duringDay`); ration read one chart day after the flock's weight |
| v41 | Feed used by variety hard to find in the sheet | The per-variety columns were added at the far right; no running totals anywhere | Columns beside the total; FeedLedger and DailySummary tabs |
| v41 | Flock stopped at the harvest day | Day rows, the day bar and the curves all ended at harvestAge (curves at day 49 / 55) | Rows added as the flock runs on, up to day 70; curves carried on |
| v41 | Weight shown green before it was measured (animation box, named birds); FCR / mortality green before the day's entries | Fixed colours instead of the value's kind | Kinds for weight, mortality and FCR drive the colours everywhere |
| v41 | Only five sample locations | Five fixed columns | Any number from 1 to 12 (extra columns in DB and sheet) |
| v41 | Feeding logged for the animation always at "now" | No time field | Time picker beside the bags |
| v40 | App icon and loading animation looked off | Drawn without options to choose from (v38) | Four sheets of options (design/icon-options*.png); option 45 chosen; loader assembles the same mark from its parts |
| v40 | Start-up screen flashed the phone's default background | Theme had no window background | Black window and splash background |
| v39 | Words broken mid-word although there was room (Temperature, Humidity, Static pressure…) | The name shared its width with the long line of references under the value, leaving it a few characters | Name gets the whole first line beside the value; references on their own line |
| v39 | Chip rows ended with one chip alone on a second line | Rows were filled three at a time | Rows are split evenly (2 + 2) |
| v39 | "Add task" button text invisible | Button content colour was the brand colour, which is black | White text and icon |
| v39 | Stock and tasks were hard to reach inside Output | v38 put them in an Output tab | Own tabs in the bottom bar |
| v39 | Next task in the middle of the clock crowded the dial | Text drawn in the centre | Shown under the dial; the centre shows the time now |
| v38 | Ideal and commercial highlights on the bars overlapped | Both bands drawn on the centre line | Ideal above the line, commercial below |
| v38 | Ventilation ranges had no dot and units sat by the name | Range bars drew a dot only for a reading; unit placed after the label | Ring dot at the ideal when there is no reading; unit after the value |
| v38 | Orange dash for "on par" read as a warning | Orange is the warning colour | "=" in grey (green when good) |
| v38 | Feed clock could be dragged to any hour | The ring was a manual setting | Times worked out from light hours, heat and the feeding count; clock is display only |
| v38 | Loader showed a small dark square over the page | Material's pull-to-refresh indicator draws a filled disc | Custom loader with no background |
| v38 | Uniformity said little | Only CV and a location spread | Histogram, light / even / heavy shares, weights |
| v37 | "CV" was far too low | Computed from the spread of the 5 bucket averages, which averages away the bird-to-bird spread | True CV from birds weighed one by one; bucket spread shown as "spread between locations" |
| v37 | Feed used by variety wasn't kept apart | Recalculation added all of a day's bags to its first type and used one bag weight | Per-type split and each type's bag weight used everywhere; one sheet column per variety |
| v37 | Numbers in the sheet showed a leading ' | Every cell was sent as text | Numbers and true/false sent as real values |
| v37 | Entered values disappeared / couldn't be corrected one by one | Revert wiped the whole day; locked fields drawn grey on black | Unlock keeps values; Clear all is separate; locked values drawn readable |
| v37 | Commercial / ideal missing in places and the KPI blocks took too much room | Three chips per scope row | One compact line per KPI with com / ideal in small type and a slim bar |
| v36 | Back up & repair failed or scrambled data on sheets from older versions | Every tab was read by column position; older layouts put values in the wrong fields, and repair wrote that back | Tables read by header name with older names accepted; layout rules in SheetSchema |
| v36 | Repair failed on sheets missing a tab (_Config, _FeedTypes, Tasks, ActivityLog); pull failed entirely | Writes and reads assumed every tab existed | Missing tabs are added before writing; reads use only the tabs present |
| v36 | Old rows came back after a repair | The rewrite wrote from A1 but never cleared rows or columns beyond the new data | Leftovers beyond each block are cleared after the write |
| v36 | Rows recovered from backups were lost again during repair | The live pull ("sheet wins") ran after merging the backups | One merge across live sheet, phone and backups, newest row winning |
| v36 | Repair could wipe feed types / thresholds | Rewritten from the phone only, which may not have them | Read from the sheet and merged; never written empty over existing ones |
| v36 | Backups stayed in the old layout; backup _Meta lost its schema version | Backups were only copied or trashed; the copy's _Meta was partly overwritten | Newest backup rewritten and verified in the current layout; full _Meta written and trimmed |
| v36 | New farm sheets had no Tasks header | Tasks tab created 10 columns wide for a 15-column header | Tabs created 20 columns wide |
| v35 | Scrolling to another day moved the page to a different section | Content above changed height between days; the scroll state was lost on days with no data | Scroll state kept for every day; the card at the top is remembered and put back after the day changes |
| v35 | Holding a button showed nothing | The progress fill used the brand colour, which became black in v32 (black on black) | White fill with a bright edge, press-in, haptics, "Done" flash |
| v35 | Blocks with several cards overlapped after adding scroll anchors | The anchor wrapper was a Box, which stacks its children | Anchor wrapper is a Column with the page's spacing |
| v35 | Crowd piled onto the few pans in view | Every hungry bird went to the nearest pan with no limit | Each pan takes only as many birds as fit its rim; birds start at mixed fullness and digest slower |
| v35 | 2.5 ft and 9.8 ft labels in the pan drawing sat in the wrong places | Labels placed at the band edges, not on dimension lines | Dimension lines with end ticks on one highlighted cell; labels on dark backing |
| v34 | Plan could use more bags than the day needs (e.g. 20 for 18.28) | Half-bag rounding per line × 4 lines × feedings | Day fed in whole bags (ceil), split evenly; series chosen to fit the pour |
| v34 | "Bags to fill a line" couldn't be 3.3; sensor pans, pan spacing and line gap were fixed | Integer column; constants in code | New farm columns (DB v14 migration), used by the plan and drawings |
| v34 | "ft² per pan" didn't match the drawn cell | Chip spread the floor beyond the line ends over the pans | Cell (repeat geometry) and load (birds ÷ open pans) shown separately |
| v34 | Coop was view-only; names overlapped | No gestures; names drawn at the head | Turn / tilt / zoom / taps; names stacked without overlap |
| v33 | Birds in the coop didn't go to eat after "Feed" | The animation loop kept the first feeder state it saw, so new feedings never reached it; and a bird could stop 2×10⁻¹⁷ m short of its feeder spot (step = distance − stop rounded to 0) and never start eating | Loop reads the latest input (`rememberUpdatedState`); arrival allows a 0.1 mm slack and steps go the full distance; a new feeding sends every awake bird to the feeder |
| v33 | Feeding plan said 3 feedings for 16 bags on day 11 (farm feeds 2 × 8) and showed fractions of bags per feeding | Bags split evenly by the age's feeding count, no rounding to what can be poured | Whole / half bags per line; fewest total bags, 2 feedings minimum; numbered steps in order |
| v33 | Value chips and bars didn't line up down a card | Rows without a label started at the edge; the comparison bar used two offset lanes | Label column always kept; one-track bar under the chips |
| v32 | Pan plan said "fill the lines once with 12 bags, then pour 5 whenever needed" | Modelled a separate tube charge; pattern not tied to each feeding | Pattern chosen so one feeding fills every open pan; feedings reduced if no safe pattern fits |
| v32 | Inside conditions still estimated from the weather (birds feel, house RH, fan finder start) | Weather used as outside input to the house model | Removed; season's typical day used for what-if tools |
| v32 | Grey panels, coloured fills and cramped rows on small phones / large text | Tonal elevation and coloured containers; fixed chip counts | Pure black + white outlines; wrapping chip rows; 2-column tiles on narrow screens |
| v31 | Reception deaths counted as mortality on Output; mortality % and livability divided by birds placed | Output added reception to deaths; engine used placed as denominator | Reception only reduces the entry flock; both use placed − reception |
| v31 | Feeder lines assumed to be as long as the house | Line length taken from house length | Line length = pans × 0.76 m; drawn from the hopper |
| v31 | Lock, unlock and close never reached the sheet — a refresh could undo them | Only saved locally | Flock row upserted in the sheet (upsertFlock) |
| v31 | Text drawn black on the black glass panels | Panels had no content colour | Glass panels provide a light content colour |
| v31 | Feeder showed 199.9 % and "empty in 29.9 h" at night | Over-fill not capped; hours to empty ignored the lighting programme | Fill capped at the pans (rest waits in the hopper); hours to empty stepped through the light schedule |
| v30 | Values edited directly in the Google Sheet were sometimes ignored | Merge kept the phone's row when its timestamp was newer; cells were read as displayed text, so "1,200" or formatted numbers failed to parse | Sheet row always wins unless a local save hasn't reached the sheet ("dirty" flag, re-sent on refresh); cells read as raw values with a tolerant number parser |
| v30 | Hand-edited FeedBagsUsed in the sheet didn't change feed used | Per-type breakdown string still held the old total | Breakdown rescaled to the new total on read |
| v30 | Ideal range not marked on the comparison bars | Only the commercial band was drawn | Both bands drawn (commercial upper lane, ideal lower lane) |
| v30 | Inside conditions shown from outside weather | "Birds feel now / house now" estimated from weather on Environment | Environment shows inside targets only; weather labelled "outside" |
| v30 | Same parameter shown in several places; stock bags mixed across types | Hero tiles + KPI + tables overlapped; totals only | One place per parameter; per-type stock rows plus totals |
| v30 | Feeding 4 bags at a time reached only a third of the pans (farm, day 9–10) | Pans fill in order from the hopper; small feedings into empty pans stop short | Feeding plan shows the bags needed to charge the lines, reach into empty pans, and to feed again before pans run empty |
| v30 | Migration test first failed on a missing index | Test's copy of the v12 database skipped index definitions | Test copies indexes too (the migration itself was fine) |
| v29 | Output was number-heavy, small text, details folded away, no value tags | Layout grew as tables | Five topic tiles, tagged values, larger text, animations, no folded pane |
| v29 | Numbers shown as whole numbers ("3", "87", "4,419 L") | `Fmt` dropped trailing zeros | Fixed decimals everywhere (`Fmt.n` always ≥ 1 place) |
| v29 | Mortality flagged "above ceiling" while still under the company standard | Industry curve is lower than the company standard | Industry curve shown as the Ideal; alerts compare against the company standard |
| v29 | "Days of feed left" could show a negative value | Negative stock divided by the ration | Hidden when stock ≤ 0; negative stock is its own alert |
| v28 | Feed to give ~20 % too low | Used the Ross objective intake | Company feed curve at the flock's weight (`CompanyStandard.feedForWeight`) |
| v28 | Brooding set too cold (29.5 °C at day 1) | Curve anchored to the 70 % RH end of the Aviagen table | Curve re-anchored to ~50 % RH (33.0 °C at 42 g) |
| v28 | Fan bank lit fans 1, 2, 3… | Ignored the switch-on order | Lit in ladder order (5, 3, 7, 1 …) |
| v28 | Chart capture squashed in tests | Test screen height too short | Tall test screen |
| v28 | Air speed over-estimated at bird level | Sensors / birds ~1 ft above litter in slower air | Floor air factor 0.80 |
| v28 | SAFE level guessed as half the fans | Wrong assumption | SAFE = MIN, as on the farm's own sheet |
| v27 | Duplicate farms in the list | Drive scan imported backup copies as farms | Backups tagged and skipped; duplicates removed on sync |
| v27 | Repair failed on new sheets | Grid too small (Tasks 10 columns, DailyData 200 rows) | `ensureGrid` before rewriting |
| v27 | Wet-bulb temperature wrong | Degrees passed where radians were needed | Converted to radians |
| v27 | Unsaved entry text lost when leaving the app | No drafts | Drafts saved per field |
| v27 | Whole day locked after the first save | One lock flag for all fields | Per-field locks (SavedFields) |
| v25 | Minimum ventilation ~35–40 % low from day 21 | Age-keyed curve | Keyed on body weight (Ross table × 1.3) |
| v25 | Min-vent timer replaced by "Continuous" on warm days | Weather overwrote the stored timer | Timer always stored; weather only sets the expected mode |
| v24 | Fan timer wrong | Engine used hard-coded defaults, not the sheet's _Config | Config values passed through |
| v22 | FCR spikes on the first days | Tiny weights early on | FCR / cFCR charts start at day 5 |
| v21 | Metric text overlapping | Long labels and chips on one line | Chips moved, labels ellipsised |
| v18 | Cut-off lock not applied after settings change | Lock status not re-evaluated | Re-run on farm change and after save |
| v15 | Google Sheet stayed empty | One oversized write range made the whole batch fail silently | Writes anchored at A1, every write checked |
| v14 | Shared / other-device farms not found | `drive.file` scope cannot list them | Farm index in appDataFolder |

## Build / tooling notes
- Never edit source files with PowerShell `Get-Content`/`Set-Content` without `-Encoding utf8`: it re-encodes
  UTF-8 as ANSI and mangles °, –, · (happened once in v31; repaired by reversing the cp1252 round-trip).
- Build with an isolated `GRADLE_USER_HOME` (`~/.gradle-flockit`); the shared scoop Gradle home
  breaks the Kotlin settings script.
- Bash heredocs containing Python triple quotes break in the agent shell — write scripts to a file.
- Compose tests with infinite animations: set `mainClock.autoAdvance = false`, then advance time.
