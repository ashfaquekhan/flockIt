# FlockIt changelog

All app changes, newest first. Update this file (and [ISSUES.md](ISSUES.md)) in the same commit
as every release, so earlier changes and fixes are never forgotten or repeated.

Install note: unless a version says **DB bump**, it installs over a live flock without clearing
anything. A DB bump clears the phone's local cache and re-pulls everything from the Google Sheet.

---

## v36 — 2026-09-30 · Backup & repair handles every older sheet, and updates the backup too
- **Sheets are read by column name, not position** (`SheetSchema`): Flocks, DailyData, Tasks and
  _FeedTypes are matched by header, ignoring case, spaces and underscores, and older names are accepted
  (e.g. Deaths → Mortality, Feed Used → FeedBagsUsed). Columns moved, renamed, missing, or a tab with no
  header row all read correctly. Farm and threshold keys match in any case.
- **Missing tabs don't break anything**: the pull reads only the tabs a file has (before, one missing tab
  made the whole farm fail to load).
- **Older sheets are upgraded automatically**, once, the first time the farm is opened or written: the
  sheet is rewritten in the current layout (schema 4) from its own data. Columns the app doesn't know are
  kept at the right under their own names. A snapshot copy of the old sheet is kept in Drive, and this
  farm's backups are upgraded too. It only runs for people who can edit the sheet.
- **Back up & repair rebuilt**:
  1. reads the live sheet, every backup of the farm and the phone's copy (any layout) and merges them, the
     newest row winning (rows only a backup or the phone has are brought back; a task deleted from the
     sheet is not brought back);
  2. copies the live sheet as a safety snapshot;
  3. rewrites the live sheet: missing tabs added, grids grown, all tabs written in one go, then any old
     rows and columns beyond the new data cleared (no tab is ever left empty), and reads it back to check
     every flock, day row and task is there;
  4. rewrites the newest backup the same way and checks it (or makes a new one) — the backup is in the
     current layout, named with the repair time;
  5. only then moves the other backups and the snapshot to the Drive trash.
  If a check fails, nothing is deleted. Feed types and thresholds are read from the sheet and never wiped
  when the phone doesn't have them. The phone then reloads and recomputes from the repaired sheet.
- New farm sheets: schema 4, Flocks and Tasks tabs wide enough for their headers (the Tasks header used
  not to fit and was never written), flocks keep deletedAt.
- Test: `SheetSchemaTest` (older layouts, headerless tab, old keys, merge rules, write-and-read-back).

## v35 — 2026-09-30 · Feed and water clocks, farm window, steady scrolling, clearer plan
- **Feeding clock** (plan step 6) and **water clock** (Water card): 24-hour dials, midnight at the top,
  with the day's feeding or refill times on a ring. Drag the ring round and every time moves together
  (the gaps stay the same, 15-minute steps; kept on the phone per farm). A green pointer on the rim is the
  time now, a grey band is the lights-off period, the middle shows the next time and hours to it.
- **Water refills × (1–4)**: refills = tanks the day's water needs × the multiplier, spread evenly; set it
  with the chips under the water clock (saved to the farm, synced).
- **Farm window** (the animation): a 3, 5 or 10 ft square of the real house with the real feeder lines,
  pans on and off by the day's pattern, sensor pans, drinker lines with nipples every 0.82 ft, the
  barricade and house walls where the window reaches them. Birds at the flock's real density (131 in
  10 × 10 ft at 1.31 birds/ft²); sizes that would hold more than 320 birds aren't offered. Four birds are
  named and followed in a table (weight, fullness, what they're doing); the rest are drawn simply. Pans
  fill along the line from the hopper after a feeding; each pan takes only as many birds as fit its rim.
  The map under it is the whole house at its real shape: drag or tap it to move the window. The view
  size and bird count are written in the corner. Turn, tilt, zoom and taps as before.
- **Scrolling through days keeps your place**: the card at the top of the screen stays there when the
  day changes, even when cards above it change height (and after days with no data).
- **Hold buttons show progress**: a white fill sweeps across with a bright edge and a %, the button
  presses in, the phone ticks at the start and buzzes when done, and it flashes "Done". The task check
  circle fills round in white.
- **Feeding plan, plainer**: no feed-change note in the title; steps are Feed needed today → Bags to give
  (full bags, above need, times a day 2 / 3 with Best / Also OK / Not safe) → Each feeding (bags, bags per
  line, kg per line) → Pans on and off (pattern, on / in area / feed pans, pans filled %, left in hopper)
  → One pan covers (birds vs max, floor ft², walk vs max) → Feeding clock.
- **One pan covers drawing** redone: one highlighted cell with its birds as dots, its length marked on top,
  the line gap on the right, the pan spacing under two pans (labels on dark backing), the furthest walk
  and the 2 m ring.
- Text one step larger throughout (Material sizes + 1 sp, value chips 15 sp).
- Tests: `ScreensRenderTest.dayChangeKeepsSection` (the plan title stays put across day changes),
  `CoopFeedTest` (window over a feeder line: pans fill to their rims), `CoopInteractionTest` (density,
  map drag).

## v34 — 2026-09-30 · Pan series from the farm's own layout, per-pan coverage, interactive coop · **DB v14 (non-destructive)**
- **Feeder layout settings** (Farm settings → Drinkers & Feeders): bags to fill 1 line now takes decimals
  (e.g. 3.3), feed pans per line (sensor pans not counted), sensor pans per line, pan spacing (ft),
  feeder line gap (ft, 0 = width ÷ lines). Line length = (feed pans + sensor pans) × spacing. Synced to the
  sheet's _Farm tab. Migration keeps every farm, flock and day (the old whole-number bags value is copied).
- **Pan on/off series for any farm**: the day is fed in whole bags, never more (15.27 → 16). Each feeding
  pours the same on every line; the plan picks the most open safe series of consecutive pans (all on,
  4·1, 3·1, 2·1, 3·2, 1·1, 2·3, 1·2, 1·3) whose open pans that pour fills all the way to the last one
  (3 % slack for the 3–3.3 bag spread), repeated from the hopper to the barricade. Safe = furthest walk
  to an open pan ≤ 2 m (half the line gap across, half the gap between open pans along) and birds per
  pan ≤ the size-scaled limit. Both 2 and 3 feedings are worked out; the best is marked and the other is
  a tap away. Example shed (unit-tested): 112 + 2 pans, 3.3 bags a line, 16 bags → 2 × 8.00, 2.00 bags a
  line, **3 on · 2 off, 68 of 112 pans**, 56.3 birds a pan, 1.87 m walk; 3 feedings → 1 on · 2 off, 38 pans.
- **One pan covers**: the floor each open pan serves in the repeat (line gap × repeat ÷ pans on) with its
  birds at today's density, the load (all birds ÷ open pans) against the max, and the walk against 2 m —
  with a true-scale drawing of the series, each pan's cell with its ft² and birds, pan spacing, line gap,
  the furthest walk and the 2 m ring. The top view shows the sensor pans at the far end.
- Plan steps: 1 Required → 2 Day in whole bags + feedings (2 / 3) → 3 Each feeding (bags, per line, kg
  per line) → 4 Pan series (series, on / in area / feed pans, filled %, hopper bags, top view) → 5 One pan
  covers → 6 Times.
- **Coop is interactive**: drag sideways to turn it (it keeps spinning a little), two fingers to tilt and
  zoom, double-tap to square it up; the far walls follow the angle. Tap a bird (it flaps and calls; its
  weight, % from the flock mean and fullness show), tap the floor (grains land; nearby birds come to peck,
  the curious one from furthest), tap the feeder (it rattles, birds come), tap the drinker (ripples, the
  nearest bird drinks). Feed runs down the tube after a feeding and the pan fills smoothly; crumbs fly
  when birds eat, dust when they scratch; resting birds breathe; birds keep apart; names never overlap.
- Tests: `CoopInteractionTest` (projection round-trip, grains, swipe/tap/double-tap renders), `FeedPlanTest`
  (example shed, never more than the day's whole bags), `MigrationTest` (v12 → v13 → v14).

## v33 — 2026-09-30 · Whole-bag feeding plan, birds eat when fed, rows aligned
- **Feeding plan in whole bags, in order**: 1 Required today (projected / commercial / ideal bags and
  g/bird) → 2 Plan (bags rounded up, feedings, extra) → 3 Each feeding (bags, bags per line, pans per
  bag) → 4 Pans on each line (pattern, on / in area / on line, top view) → 5 Check (walk and birds per pan
  against their max) → 6 Times. Bags are poured per line in whole or half bags; the plan takes the
  fewest bags that cover the day (2 feedings minimum, up to the age's number), then the most open safe
  pattern those bags fill. Example (unit-tested): day 11, 15.27 bags needed → 16.00 bags = 2 × 8.00,
  2.00 bags a line, 2 on · 1 off, 40 of 60 pans. All values still shown with decimals.
- Overview "Feed bags" tile and "Given today … of …" show the planned (rounded) bags.
- **Coop birds eat when feed is logged**: every awake bird heads to the feeder and fills up there;
  fullness only rises while eating and drops as they digest.
- **Aligned rows**: every value row keeps the label column, so chips line up down each card; the
  comparison bar is one track (commercial ±5 % band, commercial and ideal ticks, the flock's dot) under
  the chips.
- Test: `CoopFeedTest` (birds go to the feeder and eat after a feeding).

## v32 — 2026-09-30 · Black and white UI, pan plan rebuilt, no weather inside
- **Pan on/off plan rebuilt** (numbers and the top view only, no instructions):
  one bag fills (pans per line ÷ bags per line) pans — 20 on a 60-pan line that takes 3 bags. Each
  feeding = day's bags ÷ feedings for the age (5 / 4 / 3 / 2). The plan picks the most open pattern
  (all on, 3·1, 2·1, 3·2, 1·1, 2·3, 1·2, 1·3) that one feeding fills completely, keeps every bird within
  2 m of an open pan, and stays under the birds-per-pan limit (Ross 45–80 for grown birds, scaled up
  for small birds by weight^⅓). If nothing fits, it tries one feeding fewer. Example (unit-tested):
  day 11, 16 bags → 2 feedings × 8.00 bags, 2 on · 1 off, 40 of 60 pans a line. The old "charge the
  lines" step is gone.
- **No weather for anything inside the house**: no "birds feel now", no house humidity from weather,
  fan finder and cooling grid start from the season's typical day, feedings follow age only.
- **Coop**: Wake / Sleep button while the lights are off; box size and text follow the screen width;
  grey labels, coloured numbers; feeder and drinker drawn as white outlines.
- **Pure black UI with white outlines**: every card, button, chip, tab and the top bar; colour only on
  numbers and on the Present / Projected / Ideal / Commercial / Min / Max markers. Emoji-free.
- **Fits small phones and large text**: value chips wrap to fit (3 per row at 360 dp), tiles drop to 2
  per row on narrow screens or ≥ 110 % text, legend in two rows, chart and coop text sized for the screen.
- **Less text, fewer parameters**: explanatory notes removed from Output; sections trimmed.
- Render tests for every screen at 360 dp and 412 dp and at 130 % text (`ScreensRenderTest`);
  feeding-plan unit tests (`FeedPlanTest`).

## v31 — 2026-09-30 · One-page Output, coop animation, pan patterns
- **One page, no topic tabs**: Overview (coop animation, feed entry, alerts, key numbers, growth),
  then Feed, Ventilation, Mortality and Environment as you scroll. Close batch at the bottom.
- **Coop animation** (small 3-plane box on black, only the floor and birds move): 3–4 birds with their
  own character (curious, big eater, lazy, chatty). Their weights are spread by the flock's CV around the
  live average weight; age changes size, colour, comb and behaviour (chicks busy, big broilers rest).
  Lights follow the lighting programme by the clock (dark 23:00–05:00 at 18 h), and the birds sleep in the
  dark. Floor shows a 0.5 m grid and the 2 m reach ring. In-box text: birds, weight, CV, air, RH, feels-like,
  wind chill, static pressure, litter temperature and moisture, body temperature, water temperature and pH
  (ideal values where there is no sensor), feeder level.
- **Feeding log**: type the bags poured and press Enter. The one feeder in the animation fills and empties
  at the flock's real intake rate (company curve at the flock's weight, eaten during the light hours);
  when it runs dry the birds get hungry step by step — crowding the feeder, calling, then slumping.
- **Farm top view**: every feeder line (hopper → pans → motor) and drinker line at the farm's aspect ratio,
  the brooding barricade, and which pans are on or off today. Scrolls sideways for long houses.
- **Feeder line length = pans × 0.76 m (2.5 ft)**, not the house length; lines spread evenly across the
  width with drinkers between feeders.
- **Pan on/off patterns**: all open, 2 on · 1 off, 1 on · 1 off, 1 on · 2 off — the app picks the most open
  pattern that keeps every bird within 2 m of feed and ≤ 80 birds per pan (45 above 3.5 kg), then checks the
  feed needed to reach the last open pan (auger tube + pans) against one feeding, else the day's ration;
  if nothing fits it says to pour the far pans by hand.
- **Reception deaths fixed**: they only reduce the entry flock. Mortality % and livability now use the
  entry flock (placed − reception), and the Output no longer adds reception deaths to mortality.
- **Close batch**: saves every unsent day to the sheet, then marks the batch closed and read-only locally
  and in the sheet. Lock / unlock now also reach the sheet (before, a refresh could undo them).
- **Flock list**: running batches first, newest on top (batch numbers stay in creation order).
- **Day bar**: < and > buttons at the ends of the day slider.
- **Matte black glass UI**: black background, transparent panels with thin white outlines, light value
  colours; emojis removed throughout.

## v30 — 2026-09-29 · Clearer topics, feeding plan, sheet-first refresh — **DB v13 (no wipe)**
- **Sheet edits are never skipped**: on every refresh the Google Sheet's day rows replace the phone's
  copy (except a day saved on the phone that hasn't reached the sheet yet — it is kept and re-sent).
  Cells are read as real values, so numbers typed or formatted in the sheet ("1,200", "12.5 kg")
  read correctly. A hand-edited FeedBagsUsed rescales the per-type breakdown to match.
- **Database v13 upgrades in place** (new columns only) — the flock is kept, nothing is re-pulled.
  A unit test builds a v12 database with data, upgrades it and checks the rows survive.
- **Six colour-coded value kinds everywhere**: Present (green), Projected (amber), Ideal (blue),
  Commercial (violet), Min (cyan), Max (rose). Every number sits in a chip that names its kind.
- **Macro + micro in one place**: per bird and whole flock/farm/house on the same row; no parameter is
  shown twice across a topic. Hero tiles removed (the topic tile carries the headline).
- **Comparison bars show both bands**: commercial ±5 % (violet) and ideal ±5 % (blue).
- **Range bars** for min / ideal / max parameters (temperatures, humidity, air quality, water, space).
- New emojis: 🌀 Ventilation, 🥣 Feed & Water.
- **Ventilation**: minimum-ventilation card (needed vs delivered air per bird and whole house, timer,
  air changes, draught limit); **fan finder** with two rotary dials (outside temperature and humidity)
  showing fans to run, air per bird / house, air speed, house air vs min/ideal/max, felt temperature,
  heaters and pads; **3 × 3 cooling grid** (3 temperatures × 3 humidities, tap a cell to load it into
  the dials); controller & fan capacity card. The messy three-scenario block is gone.
- **Environment**: inside targets only — the house is no longer estimated from the weather (outside
  air is shown labelled "outside"). New: litter/floor temperature and moisture, body (vent)
  temperature and foot temperature ranges by age, light intensity, air-quality range bars.
- **Birds**: KPIs with per-bird and whole-flock rows (weight, gain, FCR, cFCR, EPEF, mortality);
  birds-today card without repeats; growth detail; space as range bars.
- **Feed & Water — feeding plan**: feed today (to give vs commercial vs ideal, per bird / farm kg /
  bags), feedings by age and heat with times, bags per feeding and per line, bags to charge the open
  part of the lines, how far a feeding reaches into empty pans (pans fill in order from the hopper),
  pans open, birds per pan vs Ross 45–80, daily clean-out advice from day 10.
- **First week card**: feeder trays (Ross 1 per 100 chicks) and manual drinkers (12 per 1,000) to keep
  each day with a removal schedule, feed paper area (≥ 70 % of the brooding area, out by day 4), and
  when the birds' breast reaches the pan lip.
- **Water**: per bird / farm / tank fills for today, hot and cool days; birds per nipple; Ross nipple
  flow by age; water temperature 18–21 °C (Ross).
- **Stock**: each feed type separately (received, used, in store, kg, needed to lifting by phase,
  to order, days left) plus all types; godown capacity bar with free space.
- **Farm settings**: godown capacity (bags), manual feeders (trays), manual drinkers, nipples per
  drinker line, pan lip height (cm).

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
