# FlockIt changelog

All app changes, newest first. Update this file (and [ISSUES.md](ISSUES.md)) in the same commit
as every release, so earlier changes and fixes are never forgotten or repeated.

Install note: unless a version says **DB bump**, it installs over a live flock without clearing
anything. A DB bump clears the phone's local cache and re-pulls everything from the Google Sheet.

---

## v44 — 2026-10-09 · Hungry / thirsty / panting likelihoods instead of the feeder's "empty" time, day clock under the tasks, periods on the day slider · installs over a live flock (no database or sheet change)
- **Why the feeder looked empty too early.** Day 20, 13 bags poured at 08:30 for 1,141 g birds: the app
  showed the feeder empty from 14:26; the farm expected about 16:00. The farm was right, for three reasons
  on the app's side:
  1. it spread the day's feed evenly over the 18 lit hours — but birds with a dark period eat most in the
     hours after the lights come on and before they go off, and least around midday (08:30–16:00 runs
     about 16 % under the day's average);
  2. it used the full chart ration (155 g a bird) although this flock eats about 94 % of it;
  3. it ignored the warm afternoon (31–32 °C outside), worth a few % while it lasts.
  131.6 kg an hour becomes about 100, and 14:26 becomes about 16:10 — between 15:00 and 17:50 if the flock
  eats a good deal faster or slower than expected.
- **The feeder's level, "hours left" and "empty since" are removed.** In their place, under the farm window
  and on it: how likely the birds are **hungry, thirsty or panting** — now, and in 3 hours if nothing more
  is poured. Likelihoods, not readings (there are no sensors): from the feedings logged (the plan's feedings
  while none is logged today), the day's likely intake and when in the day birds eat it, the lights, and the
  heat load from the weather at the farm. The pace is run slower and faster (±18 %) and the cases weighed,
  so a morning feeding shows a rising chance of hunger through the afternoon instead of a minute.
  The birds in the window pant in the same share.
- **"Eaten today"** in the Today card follows the same rhythm of the day (it used the even spread).
- **This flock's response to the weather cannot be read from its records yet**: every day since day 9 had
  an outside high of 31–32.6 °C — too steady to tell hot days from cool ones. What the flock does in this
  weather is already inside its appetite (94 % of the chart).
- **Day clock** moved from the Output page to the **Tasks** page, under the tasks.
- **Day slider**: a colour band under it marks the flock's periods to scale — brooding (days 0–10),
  growing (11–27), finishing (28 to the planned harvest), and any days run past it — with a white mark at today.
- Tests: `FlockNeedsTest` (the rhythm of the day, the farm's morning feeding, waking hungry and thirsty,
  heat), the clock on the Tasks page, the slider's periods.

## v43 — 2026-10-09 · Basic and advanced view, weather acting on the house and birds, entered and projected kept apart, projection lines, three more sheet tabs · **DB v17 (non-destructive)** · sheet schema 7
Checked against the farm's own flock (KGF, September 2026, entries to day 19) — the flock is now a test
fixture, and the projection methods were chosen by replaying it.
- **Basic / Advanced switch** above the day slider (remembered on the phone). Basic: the farm window, the
  weather, the day clock, today's entered and projected figures, alerts, and three short cards — birds, feed
  and water, house. Advanced: everything, as before. In Entry, Basic shows what is entered every day
  (weights, deaths, feed, deliveries, notes); the sampling map, birds weighed one by one, lifting and diesel
  show in Advanced or once they hold a value.
- **Entered and projected never share a box.** "Today" has two columns: Entered (as typed, it does not
  move) and Projected. For today the projected column moves with the clock — weight grown since the last
  weighing, feed and water gone so far, deaths so far, live birds, FCR; for an earlier day it is what the
  app had projected for that day before its entry. Comparison lines show "—" and the projection on the
  line under it until the value is entered. **DB v17**: the time the weights are saved is recorded
  (`weighedAt`), so the weight projection starts from the real weighing time.
- **Weather at the farm** (switch under the farm window, on by default; off = the ideal house as before).
  The weather service's hourly temperature and humidity at the farm's location (kept on the phone for the
  flock's days, also offline) → the house the fans, pads and heaters would give → what the birds feel →
  feed (−1.5 % per °C above comfort, steeper in strong heat), water (+6 % per °C), growth (falls more than
  feed), body temperature, breathing and the risk of losing birds. Sources: Baziz 1996 and Teyssier 2022
  (feed), NRC 1994 (water), Tao & Xin 2003 (body temperature and danger steps). The air comes through one
  interface (sensor → set by hand → weather → ideal), so house sensors can be added without changing the model.
- **Birds in the farm window behave for their size**: mostly lying — about 12 % of the time on the move
  under 0.5 kg, 5 % by 0.9 kg, 4 % above 2 kg; walking at 0.13 m/s as chicks and 0.10 m/s from 0.8 kg
  (Tickle and others 2018; before, they walked about five times too fast and far too often); busiest at the
  feeders after the lights come on and before they go off. In heat they move and eat less, drink more, lie
  apart, hold the wings out and pant; cold chicks crowd together.
- **Farm window labels**: "Body temp" is the body temperature (it is measured at the vent — the two names
  were one number); with the weather on it is worked out for the house's air now. Density shows birds per
  ft² (it only changes with deaths and the barricade) and kg per ft² (grows every day). The birds' sizes use
  the CV — and there is now a CV: estimated from the group weighings when no birds are weighed one by one.
- **Feeding plan, shorter**: bags to pour → times a day → each time → pans; then "how the bags were worked
  out" in four boxes (chart ration → what the flock eats of it → likely eaten, with its range → feed in the
  lines) and one line of proof (plan against entries over the last 3 and 7 days). The rest is under "Each
  feeding in detail". **New method**: the bags entered are bags poured, not exactly eaten (in the farm's
  records one day swings ±19 % around the forecast, three days together ±5 %), so the filter now follows the
  flock's appetite and the feed sitting in the lines together instead of chasing each entry.
- **Ventilation, shorter**: house air (hold, and the house now), fans for the air outside, minimum
  ventilation, the grid, the controller; limits, measurements, litter and the air figures are folded away.
  The two dials stand at the weather at the farm; a dial turned by hand holds for 30 seconds, then goes back.
- **Projected line in every chart** (weight, FCR, cFCR, mortality, feed per day, feed till date): for each
  day what the app projected for it from the days before; after the last entry, the forecast. The Present
  line now holds entries only.
- **Sheet (schema 7)** — upgrades itself as before (snapshot kept, backups upgraded, nothing dropped):
  `Computed` (every value the app works out for a day beside the entries it comes from), `Projections`
  (projected, entered and the miss in %, for weight, feed, FCR, deaths, mortality and live birds — and the
  forecast for the days ahead), `Formulas` (how each value is worked out and from which columns), and a
  `WeighedAt` column in DailyData. The phone and the sheet use the same calculation code.
- **Ideal curve confirmed**: all 50 rows match Aviagen's Ross 308 AP 2022 as-hatched table; days 50–56,
  which the booklet also gives, are now the published rows instead of the model (day 56: 4,446 g).
- Entry: a new day's feed row starts with the variety entered last; the feed-type box shows the code only
  (a long name used to break mid-word).
- Stock: a symbol on received, used and in store. Farms header: the mark is the F of the name ("lockIt"
  follows it) instead of an F beside "FlockIt".
- Tests: `RealFlockTest` (the farm's flock: calculation, projections scored against the entries, the three
  new tabs), `EnvModelTest` (heat, cold, behaviour by size, clock projections, CV from groups), the feed
  forecast, schema 6 → 7, migration v12 → v17, the dials' 30 seconds, the basic view at 360 dp and at 130 % text.

## v42 — 2026-10-04 · The app's own type and theme · installs over a live flock
- **Font** (option A from the sheet): **Rubik** for all text — soft square corners, like the bars of the
  mark — and **JetBrains Mono** for every number, also on the charts, the clock and the farm window, so
  digits line up in columns. Both are bundled in the app (no download at run time) under the SIL Open Font
  License; the licence texts are in `design/fonts`.
- **Theme from the mark**: the comb's red and the beak's orange are the app's accent colours (errors and
  the marker under the chosen tab in the bottom bar); black, white outlines and the value colours stay.
- **Wordmark**: the mark and the name on the sign-in screen and above "Your Farms".
- Value chips: labels a touch lighter so the longer ones fit with the new type.

## v41 — 2026-10-04 · Feed by variety in the sheet, report tabs, any number of weighing spots, forecasts, projection check · **DB v16 (non-destructive)** · sheet schema 6
- **Sheet (schema 6)** — upgrades itself the first time the app opens the farm (a snapshot of the old sheet
  is kept in Drive; backups are upgraded too; nothing is dropped):
  - **DailyData**: each feed variety's bags sit in their own column right beside the total
    (`FeedBagsUsed · Used B1 · Used B2 …`) instead of at the far right; sample locations beyond the fifth
    get their own columns (`W6 · N6 …`) beside the first five, with a `Locations` count.
  - **FeedLedger** (new tab, kept up to date by the app): for every flock day, each variety's bags
    received, used, received and used till date and in store, then all varieties together, and kg.
  - **DailySummary** (new tab): live birds, deaths, culls and lifting with running totals, mortality %,
    locations and birds weighed, average weight, CV, feed bags and kg with running totals, feed per bird,
    FCR, diesel.
  - The two report tabs are rebuilt from the input rows on every save and every repair, and are never read
    back, so an edit made in them cannot corrupt anything.
- **Company chart checked.** All 55 rows match the chart. The chart starts at day 1 (the first day in the
  house: 13 g eaten, 50 g at the end of it); the app starts at day 0 (placement). The weights were aligned
  correctly, but feed and deaths were one day behind: what happens during flock day N is the chart's row
  N + 1. Fixed — today's ration, "eaten yesterday", feed till date, the commercial mortality and the feed
  curves now use the right row. Example, day 11 at 370.5 g: 64.6 g a bird (was 59.5 g).
- **No fixed last day.** A flock has a row for every day up to its planned harvest day, and gets one more
  each day it runs past it, up to day 70. The Ross / Cobb table (to day 49) and the company chart (to day
  55) are carried on to day 70 with a growth model: weight on a Gompertz curve fitted to the table, daily
  feed = maintenance + growth, FCR from those; the company curve grows in step with the breed curve and
  keeps its 180 g a day.
- **Target weight and harvest day** are set on the Output page (Birds → Target and forecast). The card shows
  the day the flock's own weighings say the target will be reached (likely, earliest, latest), the forecast
  weight at the harvest day with its range, and the chance the target is met by then.
- **Flock trends** (new card, from the flock's own records): average daily gain, FCR of the last 7 days, days
  ahead of or behind the standard, first-week mortality, mortality of the last 7 days, and FCR with the
  birds lost counted in. Nothing was removed.
- **Feeding suggestion** in the feeding plan: what the flock is likely to eat today, learned from what it has
  eaten so far (a Kalman filter on its appetite against the plan), the range it will most likely fall in,
  the chance each whole-bag amount is enough, the safe range to load, and whether to keep the plan, give
  more or give less.
- **Projection check** (new card): for weight, feed and FCR, what the app projected against what was then
  entered — the latest check and the average miss over all checks.
- **Colours follow what has been entered.** Weight, and everything worked out from it, is yellow
  (projected) until the day is weighed — also in the animation box and the named birds; live birds,
  mortality and livability are yellow until today's deaths are in; FCR, cFCR and EPEF need both today's
  weight and yesterday's feed; "deaths today" shows — until entered.
- **Weight samples**: + and − add or remove a location (1 to 12); the entry opens with as many as the last
  weighing had. A map of the birds' floor under it shows where to catch them: spread evenly along the
  house, switching sides, with the walk and each spot's distance from the front.
- **Feeding the birds in the animation**: the bags-fed box sits right under the animation, with the time
  of the feeding (now, or pick the time it was given).
- **Day clock**: every hour numbered on the dial; under it the day's totals (how often, how much each time),
  and the whole day in order — time, job, which of how many, bags or litres.
- **Opening and loading**: the app opens on its mark alone in the middle of a black screen, put together
  once; the loading animation is in the middle of the screen and a little bigger, on a soft dark glow.
- **Logo in the repository**: `design/logo.png` / `logo.svg`, shown at the top of the README.
- **Font**: six options with the logo in `design/font-options.png`; the app keeps its current type until
  one is chosen.
- Tests: `DataBackendTest` (layout, upgrade from schema 5, report tabs), `ForecastTest` (curves to day 70,
  chart-day alignment, feed and growth forecasts, trend measures), `MigrationTest` (v12 → v16).

## v40 — 2026-10-03 · New app icon and loading animation (the leaning F rooster) · installs over a live flock
- **App icon**: option 45 from the icon sheets — the letter F built from three white bars, leaning
  forward, its stem a rooster with a red comb, an orange beak and an eye, on pure black. Adaptive icon
  (foreground, black background, a one-colour version for themed icons) and the bitmaps for older phones,
  all generated from one description in `design/make_app_icon.py`.
- **Loading animation**: the mark is put together from its six separate parts, each moved into place
  whole — the stem rises, the top arm and the middle arm slide in from the right, the comb is set down on
  top, the beak comes in from the left, the eye last — held for a moment, then cleared and built again.
  Nothing is cut up, stretched or redrawn. Used for pull-to-refresh (pulling down puts the mark together
  as far as the pull has gone; while refreshing it builds over and over) and for the weather forecast.
- **Start-up screen** is black with the mark (it used the phone's default background).
- Tests: `ScreensRenderTest.loaderFrames` renders the animation frame by frame.

## v39 — 2026-10-03 · No broken words, Stock and Tasks as their own tabs, clock with pictures · installs over a live flock
- **Broken words fixed**: names such as Temperature, Humidity, Moisture and Static pressure were split
  across lines although there was room. The name now has the whole first line beside its value; the
  references (com, ideal, min, max) sit on the line under it and wrap as whole items at large text sizes.
  Labels in front of chip rows are one short word and never break; four chips make 2 + 2 instead of 3 + 1;
  the house / line / pan table has one-word row names; the Ventilation tab has its full name back.
- **Bottom bar: Entry · Output · Stock · Tasks.** Stock and Tasks are their own pages again, no longer
  inside Output. Output tabs are Birds · Ventilation · Feed & water.
- **Stock keeps stock only**: for each feed type Received, Used and In store, the same for all types, the
  godown and diesel. "Needed", "To order" and "Days" are gone, and so are the "order now" and "no B1 in
  store" alerts; the store's own alerts (negative stock, godown over capacity, no deliveries entered)
  show on the Stock page.
- **Day clock**: the outer ring is the day in sections (sleep, light, hot hours) cut apart at their
  edges, with a picture in each; inside it every job has its own lane with its picture at each time —
  walks, feed, water. A bar on the ring marks the time now. Under the dial: what is next (not in the
  middle any more), then one line per job with all its times (next one bright, past ones dim), and the
  sleep and hot hours with how long they last.
- **Animation box order**: the window, then the width buttons and the house map, then the numbers, then
  the named birds.
- **Add task** button was drawn black on black; it is white now.
- **Loading animation** is a plain thin ring until the app's mark is chosen. Twelve icon options are in
  `design/icon-options.png` (source `design/make_icon_options.py`, one SVG each in `design/icons/`).
- Tests: `ScreensRenderTest` renders the Stock page and the whole Output page at 130 % text.

## v38 — 2026-10-03 · Output reordered, day clock, uniformity histogram, ⓘ explanations, new icon · installs over a live flock
- **Output page order**: the animation, then the day clock, then **Today** (weight, FCR, mortality, feed with
  commercial and ideal; live birds, bags to load, water, feeder %), alerts, then the tabs for detail:
  **Birds · Vent · Feed & water · Stock & tasks**. Tasks moved into the Output page (Stock & tasks tab); the
  bottom bar is Entry and Output only.
- **Animation box**: three widths: full, half or quarter of the house × 10 ft. The half and quarter views
  start on the middle feeder line and can be moved on the map. Under the window there is a grid of the day's
  numbers: weight, ideal temperature, minimum ventilation per bird and for the house, NH₃ and CO₂ limits, vent
  (cloacal) temperature range, feet, resting breaths a minute and the panting threshold (air, humidity and
  feels-like stay on the window itself).
- **Day clock** (to look at, not to move): a 24-hour dial with the dark block, the hottest hours, feed loads,
  tank refills, walks and the time now; the middle shows what is next. The times are worked out, not dragged:
  dark is one block ending at 05:00; the first load comes at lights on and the last ends 2 h before the dark,
  so birds never go into the dark hungry; extra loads go to the coolest light hours and never into the hottest
  4 hours or the 2 h before them; the tank is refilled before the birds wake and before the heat; walks come
  after lights on, an hour after each load, at peak heat and before the dark. The times-a-day choice in the
  feeding plan moves the clock with it.
- **Feeding plan in order**: Bags today → Times a day → Each time (bags and per line) → Pans on/off, with
  the times-a-day chips under it. "Max walk" is now **Travel to pan** (and the limit "Max travel to pan").
  No clock inside the plan.
- **Feed correction** (advice only, capped): when weight is more than 3 % under commercial, load up to 5 %
  more (half the gap); when FCR is more than 3 % over commercial and weight is on target, up to 3 % less;
  none in the first week. Shown next to the plan with the bags it would make.
- **Eaten**: feed per bird yesterday and till date (commercial and ideal), bags and kg till date for each
  feed type and in total.
- **Water**: a small tank picture filled to the share of the day's water, "× n" refills, the tank size, fills
  needed and litres each refill; nipple line pressure in inches, height and flow per line.
- **Uniformity**: CV and even birds, then a weight histogram: share of birds in 5 % steps around the
  average with the weights under them, light / even / heavy shares, and lightest / average / heaviest. With
  10 or more birds weighed one by one it shows those birds; otherwise the expected spread at that CV, drawn
  paler and labelled.
- **Bars**: ideal band and tick above the centre line, commercial below, so they no longer overlap; a ring
  dot marks the ideal when there is no reading; units sit next to the value; "=" replaces the orange dash for
  on par; every reading shows "com" and "ideal", with NA where there is no curve.
- **ⓘ explanations** on each card (one per card, not on every line): what it is, the exact formula, what it
  needs, what changes it, and how to keep it right.
- **Loading animation**: a chick pecking beside a filling pan, with no box behind it (pull-to-refresh,
  weather). **New app icon**: the same chick and pan in a white ring on black.
- **Code structure**: new `domain` package for pure rules (`DaySchedule`, `FeedCorrection`) with unit
  tests; ARCHITECTURE.md now documents the layers, patterns, rules for future changes and security notes.
- Tests: `DayScheduleTest`, `FeedCorrectionTest`; `ScreensRenderTest` renders the Stock & tasks tab, the
  quarter view and the ⓘ dialog.

## v37 — 2026-10-03 · Output in three tabs, trend markers, true CV, real numbers in the sheet · **DB v15 (non-destructive)**
- **Output tabs** below the animation, feed log and alerts: **Birds** (flock and deaths, start and
  removals, growth, uniformity, comfort, curves), **Ventilation** (house air, air quality, litter, minimum
  ventilation, fan finder, fans by outside air, controller), **Feed · water · stock** (feeding plan, first
  week, eaten, feed curves, water, store). Related numbers sit together; the overview tiles are gone.
- **Trend markers** on every KPI and every reading with a range: ▲ / ▼ for above / below the commercial
  standard (or ideal / safe range), coloured by whether that is good — green good, orange a little off,
  red well off — and an orange dash when on par (±3 %). Ranges: green dash inside, orange / red outside.
- **Compact KPI lines**: name (and the whole-flock figure), trend marker and value, then commercial and
  ideal in small type, with the slim comparison bar. Commercial and ideal now show for every KPI; ranges
  show min · ideal · max. About a third of the old height.
- **Feeding plan, macro and micro**: feed needed and per bird (vs commercial / ideal), full bags, times a
  day, the pan pattern (filled %, hopper), then one table — House | Line | Pan — for bags and kg per
  feeding, pans on, birds, floor and walk, with the limits; top view, pan drawing and feeding clock.
- **CV checked and corrected**: the old "CV" was the spread between the 5 bucket averages, which hides
  most of the bird-to-bird spread (a 9 % flock read about 2–3 %). Now: an optional field for birds weighed
  one by one (10 or more) gives the true CV and uniformity (birds within ±10 % of the mean); bulk weighing
  shows "spread between locations" under its own name. CV alerts use the true CV only.
- **Entry**: saved values stay on screen and readable (no more grey on black). "Unlock day" (was Revert)
  opens the day with every value kept, so only the wrong ones need changing; **Clear all entries** is a
  separate hold button.
- **Feed varieties kept apart**: the daily recalculation now uses the per-type split you enter (it used to
  put all of a day's bags on the first type) and each type's own bag weight (FCR and stock).
- **Sheet (schema 5)**: numbers and true/false are written as real cell values (no text with a leading '),
  one "Used B1 / Used B2 / …" bags column per feed variety (a hand edit there changes the split and the
  total), and an IndividualWeights column. Older sheets and their backups upgrade automatically once.
- **Animation views**: full width × 10 ft and half width × 10 ft (the half view can be moved across the
  house on the map). Seen from higher, long side left to right; big crowds drawn more simply to stay smooth.
- Tests: `SheetSchemaTest` (per-variety columns, typed cells, true CV), `MigrationTest` (v12 → v15),
  `ScreensRenderTest` (tab renders; scroll position kept in the Feed tab).

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
