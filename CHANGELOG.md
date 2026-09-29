# FlockIt changelog

All app changes, newest first. Update this file (and [ISSUES.md](ISSUES.md)) in the same commit
as every release, so earlier changes and fixes are never forgotten or repeated.

Install note: unless a version says **DB bump**, it installs over a live flock without clearing
anything. A DB bump clears the phone's local cache and re-pulls everything from the Google Sheet.

---

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
