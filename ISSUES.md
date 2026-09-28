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
| O4 | Engine alert text (stored with each day) still says "exceeds ceiling" | Output topics use their own alerts (v29); the stored text is not shown on Output any more. |
| O5 | Water use is not logged | All water figures are projected. |
| O6 | Real Google Sheets sync needs a proper OAuth access token and a two-account test | See README "Google OAuth". |
| O7 | Humidity-compensation coefficients on the IB controller not verified | A test was given; confirm on the controller. |

## Fixed
| Version | Issue | Cause | Fix |
|---|---|---|---|
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
- Build with an isolated `GRADLE_USER_HOME` (`~/.gradle-flockit`); the shared scoop Gradle home
  breaks the Kotlin settings script.
- Bash heredocs containing Python triple quotes break in the agent shell — write scripts to a file.
- Compose tests with infinite animations: set `mainClock.autoAdvance = false`, then advance time.
