# FlockIt changelog

All app changes, newest first. Update this file (and [ISSUES.md](ISSUES.md)) in the same commit
as every release, so earlier changes and fixes are never forgotten or repeated.

Install note: unless a version says **DB bump**, it installs over a live flock without clearing
anything. A DB bump clears the phone's local cache and re-pulls everything from the Google Sheet.

---

## v29 — 2026-09-28 · Output by topic
- **Output rebuilt as five topic tiles** — 🌬️ Ventilation, 🌡️ Environment, 🐔 Birds,
  🌾 Feed & Water, 📦 Stock. Each tile shows an emoji, one headline value and an alert badge
  (✓ / count, amber = watch, red = act). Tap a tile to see that whole topic.
- **Every topic shows macro and micro**: per bird, whole farm/house, today, till date and %,
  wherever it applies. Every parameter from the old tables is kept, and nothing is hidden in
  a folded pane (the "Detailed tables" section is gone).
- **Value tags with colour codes** on every number: Present (green), Projected (amber),
  Ideal · Ross (blue), Commercial · company (violet). Legend at the top.
- **Both curves kept everywhere**: Ideal (Ross 308) and Commercial (company chart) side by side
  in KPI bars, tables and charts. New chart: cumulative feed per bird.
- **Exact decimals everywhere**: values always show fixed decimals (e.g. 24.0, 1,298.8, 0.63),
  never rounded to whole numbers. Only head counts of birds stay whole.
- **Ventilation animations**: top view of the house with spinning fans, a blinking timer fan
  (sped-up ON/OFF) and air streaks that move at the air speed at bird height. Shown for right
  now, minimum ventilation, and the coolest / middle / hottest hour of the day (forecast, or a
  typical day for the farm's season), each with fans to run, air speed, house temperature and
  humidity, what birds feel, heaters and pads.
- **Stock drawn as sacks** (filled = in store, faded = used) per feed type, with days left for
  the current phase, totals, still to order, and diesel.
- **Alerts per topic**: mortality vs commercial, weight/FCR vs commercial, uniformity, space,
  feed eaten vs commercial, missing feed entry, water pH/temperature, negative stock, days of
  feed left, felt temperature, heater capacity, air speed, NH₃, CO₂, O₂, static pressure, humidity.
- Larger text for labels and values (no more 9–10 sp text on Output).
- Mortality benchmark: the industry curve sits *below* the company standard, so it is shown as
  the Ideal, and alerts now compare against the company standard.
- Render test `OutputVisualsScreenshotTest` shoots every topic.

## v28 — 2026-09-28 · Company standard, corrected curves
- `CompanyStandard`: the company's all-branches chart, days 1–55 (BW, gain, FCR, cFCR,
  feed/day, cumulative feed, mortality, feed phase, ±5 % tolerance from day 28).
- **Feed to give** now follows the company feed curve at the flock's weight (Ross intake ran
  ~20 % below what commercial flocks eat).
- Brooding temperature curve corrected: 33.0 °C for a 42 g chick; lighter chicks brooded warmer.
- Controller engine: 20-level ladder, floor air-speed factor 0.80 (sensors ~1 ft above litter),
  SAFE = MIN, MAX = age cap + 2 fans.
- Output: KPI scorecard (Actual vs Company vs Ross), tap-to-inspect charts, simple ventilation
  card; level-programming cards removed.
- Miscellaneous notes are bullet points: duplicates blocked, reuse chips from the last 14 days.

## v27 — 2026-09-27 · Level controller, per-field locks — **DB bump (v12)**
- IB-style level controller model (`IbController`): ladder, per-day SET/HEAT/MIN/MAX/SAFE,
  felt temperature, heat + moisture balance of the house.
- Per-field entry locks (weights / mortality / feed lock separately; SavedFields column 50).
- Unsaved entry text survives leaving the app (drafts); counts carry forward.
- Output ledgers (Today / Till date / Plan), exact decimals (`Fmt`), feed stock colours,
  ventilation-now card with hourly forecast, humidity next to temperature in the top bar.
- Duplicate farms fixed (backup copies were imported as farms).
- Back up & repair: merges newer rows from backups, grows the sheet grid, verifies, trashes old
  backups.
- Fix: wet-bulb formula used degrees instead of radians.

## v26 — 2026-09-25
- EPEF, water temperature and litter moisture.

## v25 — 2026-09-25 · Minimum ventilation fixed
- Min-vent keyed on body weight (Ross cfm/bird × 1.3 × calibration); was age-keyed and ~35–40 % low.
- The day's min-vent timer is always stored (warm days no longer replace it with "Continuous").
- ON-time floor 50 s; OFF stretched instead (cycle cap 600/500/450 s).
- First controller-ladder model; fan switch-on order 5,3,7,1,9,2,4,6,8,10.
- Air-quality ideals (CO₂, NH₃, CO, dust), feed phase, 7-day weight multiple.

## v24 — 2026-09-22
- Day slider cleanup, "Today" pill.
- Ventilation used hard-coded defaults instead of the sheet's _Config — fixed.
- Tunable water-refill and min-vent calibration factors (**DB bump v11**).
- Back up & repair sheet (Farms ⋮ menu).

## v23 — 2026-09-22 · Task planner — **DB bump (v10)**
- Task planner with day ranges, recurrence and OS notifications (snooze / complete).

## v22 — v20 — 2026-09-22
- v22 smoother performance graphs, no early FCR spikes.
- v21 fixed metric layout collisions; FCR / mortality "settling" before day 7.
- v20 smaller word values in metrics.

## v19 — 2026-09-22
- Topic-wise dashboard, thermodynamic parameters, entry hold-buttons (disable cut-off lock,
  revert day), 14-day weather forecast.

## v18 — 2026-09-22
- Lock-out fix, hold-to-save, kg/ft² density, moving brooding barricade.

## v17 — 2026-09-21
- Present / Predicted / Ideal colours for values.

## v16 — 2026-09-21 — **DB bump (v9)**
- Pull-to-refresh, entry lock after save, multiple feed types used per day, 3 task blocks,
  bird-size chart, cleaner dark theme.

## v15 — v14 — 2026-09-20/21
- Google Sheets is the authoritative workspace: verified writes, every write checked.
- Cross-device farm index in Drive appDataFolder.

## Before v14 — 2026-09-19/20
- Sign-in, Farms → Flocks → Flock navigation, dark theme, placement time and transit
  mortality, farm hardware settings, measured daily inputs, Output gist card, feed plan,
  delete / recycle bin, date & time pickers, committed debug keystore.
