# FlockIt changelog

All app changes, newest first. Update this file (and [ISSUES.md](ISSUES.md)) in the same commit
as every release, so earlier changes and fixes are never forgotten or repeated.

Install note: unless a version says **DB bump**, it installs over a live flock without clearing
anything. A DB bump clears the phone's local cache and re-pulls everything from the Google Sheet.

---

## v64 — 2026-11-13 · Flock book: entered numbers only, start and end of day, running totals; sync and sheet code read through with three fixes · no DB or sheet layout change
- **Flock book**
  - No projected values and no Company column. Weight, FCR, cFCR and kg/m² show only on a day with a weighing or a
    lifting, in green, and are blank otherwise. On a lifting day without a weighing the weight is the trucks' own average
    (kilos ÷ birds) — the lifted kilos are a weighing too.
  - **SoD** and **EoD**: the birds at the start of the day and at its end (after the day's deaths, culls and liftings).
  - **Mort** (the day's dead and culled) and **Tot mort** (the total to that row); **Bags** (eaten that day) with
    **Tot bags** and **Tot feed kg** (the totals to that row).
- **Sync and sheet code read through** (`SheetsSyncManager` save / load / upgrade paths, `SheetSchema` parse, merge and
  clash rules, `FlockRepository` load and resend). Every entry field goes to the sheet and comes back (checked field by
  field against the day row); a load never lands on a row with an unsent edit; unsent rows are sent before a load.
  Three faults fixed:
  - A day row written twice in the sheet: a save updated the FIRST copy while a load reads the LAST, so the save could
    seem lost. A save now updates the one a load reads.
  - A value with no column in an older sheet got a column only if it was on a short list written by hand, which left out
    the lifting laps and the side barricades; on such a sheet they would not be written and the next load would clear
    them on the phone. Now any column of the layout that has a value gets its place.
  - Bringing a sheet's layout up to date rewrote it from the copy read at the start of the load; an entry another phone
    sent in between could be written over. The sheet is read again right before the rewrite.

## v63 — 2026-11-13 · Every calculation file read line by line; the projections, KPIs, EPEF, heat and weather brought to one definition each; unused old screens removed · no DB or sheet layout change
Read in full: `FlockCalc`, `OutputData`, `PhysiologicalEngine`, `CompanyStandard`, `IbController`, `Projection`,
`IntakeForecast`, `GrowthForecast`, `FlockKpis`, `Suggest`, `FeedCorrection`, `BirdEnvironment`, `SpacePlan`, `Litter`,
`Travel`, `Radar`, `FlockNeeds`, `DaySchedule`, `HouseMap` (the bird counts and heat in it), `SheetReports`.
- **Projections** (the amber line on the graphs, the sheet's Projections tab, the model log) now use the same facts as the
  day rows:
  - culls count as deaths (they were left out, so projected mortality and live birds disagreed with the page since v60);
  - projected FCR is feed over the birds in the house AND the kilos already sold (it left the sold kilos out, so it
    shot up after every lifting);
  - after a lifting the weight is carried on from the average of the birds left, not from the weighing before it;
  - feed per bird is over the birds that ate; feed per bird till date is added up day by day (as the page since v62).
- **The flock's appetite** (the bags advised) is learned against the plan for the birds that ate each day. On a lifting
  day the plan was for the birds left while the feed entered was eaten by the lifted ones too, which read as a jump in
  appetite.
- **First-week and last-7-days mortality** count the culls.
- **EPEF**: one function for the page and the sheet's Computed tab. The sheet used the day's weight; the page the average
  of every bird produced (live and lifted). Now both the latter — they differ only after a lifting.
- **Coolest / middle / hottest hour** (the cooling grid, the fan finder's start, the heat warnings) come from the weather
  at the farm when the day has it. They were always the season's typical day, even with a forecast on the phone (O16).
- **The heat a bird gives off** (10.62 × kg^0.75 W and its sensible share): one function; the controller model, the
  house map and the page each had a copy. The walk distance likewise (`Travel.furthest`).
- **Bag weight**: the day rows had no fallback when the farm's bag weight was 0 (a division by zero); 50 kg, as elsewhere.
- **Removed**: six screens and three components no page opened any more (Targets, Feed, Water, Insights, Vent/Climate,
  Graphs; climate envelope, fan visualizer, house floor plan) and the old targets table in the engine. They held older
  versions of the formulas above.

## v62 — 2026-11-13 · One definition for every bird count and feed-per-bird figure; no number bent out of sight · no DB or sheet layout change (existing entries untouched; worked-out columns are recalculated on the next load)
The rule this version applies: **entries are facts and are never changed; a worked-out number has one definition, used by
the page, the graphs, the phone and the sheet; a limit is applied only where a sum needs it, and is said when it acts.**
- **Live birds is kept with its sign everywhere** (phone and the sheet's LiveBirds column, not only the page): received −
  died on the way − dead − culled − lifted. v61 showed −19 on the page while the sheet still said 0. The sums that need
  real birds (feed, floor, air, water, projections) take `inHouse` — the count, never fewer than none.
- **Feed per bird till date** is each day's feed over the birds that ate it, added up (`FlockCalc.cumFeedPerBirdG`). It
  was all the feed so far over the birds in the house today, which gave the birds left after a lifting the feed of every
  bird sold (and the living the feed of the dead). Page, feed graph and the sheet's report tabs use the one function.
  Before a lifting it reads about 1–2 % lower than before; after a lifting it no longer jumps.
- **The birds that ate on a day** (`FlockCalc.eaters`) is the one definition for the page, graphs and sheet reports.
- **The projected cFCR** uses the same company formula function as the entered one (it had its own copy of the numbers).
- **Said, not hidden**: when the truck's kilos would leave the remaining birds more than 20 % under or 10 % over the
  flock's average, the weight is still held at that limit, and Lifting now shows "Birds left, weight … held at the
  limit: truck kilos and weighing disagree".
- **No stand-in values**: "Eaten yesterday" is — when no bird was on the count that day (it was divided by 1 bird); an
  empty house needs 0 water tanks (it said 1).
- Reviewed and left as they are (limits of real things, or model choices already listed in ISSUES): barricade and pan
  bounds, the ±25 % cap on the advised bags (O25), the own curve's limits (O82), default lifting time 20:00 (O48).

## v61 — 2026-11-13 · The bird count shown as it is (signed, red) when it does not add up, lifted and received birds, the flock book lights the cell touched, past days at their last time, the time rail in colours · no DB or sheet change
- **The count is never bent to fit.** Live birds are received − died on the way − dead − culled − lifted, with the sign.
  621 birds in the count and 640 lifted shows **−19** in red ("19 more lifted than the count held") on that day and the
  days after — before, the page showed 1 that evening and 0 the next day. A closed batch with birds in the count that
  were never lifted shows **+N** in red. Nothing entered is changed. In Birds (Live birds), Lifting (Left by the count),
  the flock book's Live column and the reminder after a lifting ("Check the count").
- **Lifting** shows Birds received (with the birds that died on the way), Birds lifted, and Left by the count with the
  dead and the culled beside it. **Birds** shows Birds lifted (birds and kilos, as far as the time shown) once a lifting
  is in.
- **Flock book**: a touch lights the cell, its day and its column; touched again it goes out. The book no longer closes
  or jumps to another day on a touch (days are opened from the top bar).
- **Time**: a day gone by opens at its last time (23:55 — everything that happened in it is in) instead of midday; a day
  to come still opens at midday. The rail is green for the time gone by, amber for the time to come, the white dot is now.
- **Output ends at the graphs and the flock book**: the Map and the event list are no longer on the page (events are
  still logged and sent to the sheet).
- **Checked from first principles, and corrected**:
  - Feed per bird on a lifting day was the day's feed over the birds LEFT; it is now over the birds that ate it — those
    left, and the lifted for the part of the day before their truck (`OutputData.eatersOn`). This moves "eaten yesterday",
    the feed graph, the company/ideal feed to date, the flock book's g/bird and the own curve's feed entries on the day
    after a lifting.
  - Livability is what was not lost (received − dead − culled) over the birds received; it was (birds left + lifted),
    which a count that does not add up could move. Same number when the count adds up.
  - The stored day alert for high mortality counted the dead only, over the birds left after a lifting; now the dead and
    the culled over the birds the day began with (as on the page since v59).
  - FCR by the clock no longer counts a bird that is not there once the house is empty (the count was held at 1).

## v60 — 2026-11-12 · The flock's own curve, the present first with the projection under it, barricades along the house, culls counted as mortality · **DB 23 → 24 (adds two columns, nothing cleared)** · **sheet schema 12** (DailyData gains PenSideStartFt, PenSideEndFt)
- **The flock's own curve** (`domain/DynamicCurve`, orange everywhere): the company's curve bent to this flock by every
  value entered so far. It learns the flock's ratio to the standard, so one entry already moves the whole curve and each
  further day makes it better; ahead of the last entry the recent drift is carried on a few days, damped. Entries from a
  flock's first days count for less (day 0 an eighth, in full from day 7) and one entry never moves the curve by more than
  a fifth — a bad weighing bends it, it does not take it away. For weight, feed a bird a day, mortality %, FCR and (from
  those) cFCR and live birds. Shown as "own" beside com and ideal, as an orange diamond on every comparison bar, and as an
  orange line on the weight, birds, FCR, cFCR, mortality and feed graphs. On Output the reference is learned from the
  days BEFORE the one shown, so today's entry is held against what the flock's own curve expected of it.
- **Backtest on the real flock** (each entry forecast from the entries before it): weight 4.7 % out against the company
  curve's 5.4 %; feed a bird a day 20.7 % against 26.5 %; mortality 13.7 % against 29.0 %. On a flock 10 % under the standard: 11.1 % out with no entry,
  3.2 % after one day, 1.8 % after two, 1.3 % after three, 0.7 % after seven.
- **Kept for training later**: each day's log line now holds what the own curve forecast for the next day (weight, feed,
  mortality, FCR) and how many entries it had learned from, beside the day's entries.
- **The present leads, the projection sits under it** (Birds): live birds, weight, FCR, cFCR, mortality, deaths today and
  stocking density show the last value entered in green (with "day N" when it is not today's) and "projected …" in amber
  under it for the time on the slider. No more projected number standing where an entered one should be.
- **Barricades along the house**: a second slider under the first — near wall to far wall — sets the floor's width; the
  drawing shows the fences along the house; the floor, density and ft² per bird follow the narrowed floor.
- **Culls are mortality**: birds taken out by hand count in the mortality % (and in today's losses), shown as
  "N dead · M culled". The Entry field is named "Culls (birds taken out by hand)".

## v59 — 2026-11-11 · Liftings truck by truck, the map in 3D one step at a time, everything following the time slider, sums checked from first principles · **DB 22 → 23 (adds two columns, nothing cleared)** · **sheet schema 11** (DailyData gains LiftLaps, _Farm gains malesIn10)
- **Liftings truck by truck**: Entry → Lifting lists every lifting of the day (time, birds, kg, kg a bird) and their sum;
  type the next one and hold "Hold to add lifting N" — each has its own time; × takes one out. The day's birds and kilos
  lifted are the sums, the lifting's time the last one's. On Output the house empties truck by truck along the time slider.
- **The Map, rebuilt**: three dimensions and one thing at a time. The flock with its groups on a ring; a group with its
  points on rings; a number with its wiring — what it comes from on the left, what it moves on the right (stronger links
  nearer and thicker, green raises, red lowers, dashed acts later, an arrow the way the effect runs), the events that set it
  above, the parts it hangs with below. Every point is a real button (a tap always lands) and becomes the middle; a finger
  sideways turns the scene; the trail above (Flock › Birds › Weight now) steps back. It stays this simple however many
  points and links are added.
- **The time slider moves what can move**: live birds (deaths expected so far, culls, each truck off), weight (grown on
  from the weighing, "weighed 08:15 · +12 g since"), stocking density and ft² per bird, "Eaten so far", "Drunk so far",
  hungry / thirsty, the feed drawing's hour, "Lifted today", and the map's values. FCR, cFCR and mortality stay the day's
  figures (they are reckoned on the day's entries).
- **Deaths today** in Birds: the count, its share of the birds the day began with, against the company's.
- **Ideal barricades**: blue marks under the barricade slider and "ideal 0–180" beside it and on the Barricades line — the
  brooding plan's floor while the chicks are small, then the floor that holds the birds at the comfortable density.
- **Where to catch the birds is back** in the weight box, plain: the birds' floor, the numbered spots and the feet (no
  feeder or drinker lines, no text under it).
- **Males : females placed** in the farm settings (3 : 7 unless changed); the weight box's ideal ♀/♂ split follows it.
- **A closed batch takes no entry**: only Output and Stock show; nothing on Output can be set.
- **Same colours on every phone**: the app's window is dark whatever the phone's mode, the system bars black, and phones
  that recolour apps on their own ("force dark") are told to leave it.
- **No "Screen edges" setting and no side margin** of the app's own.
- **Flock book**: room after the last column and under the last day, columns wide enough for the long numbers and growing
  with the phone's text size.
- **A shared farm shows up on its own**: the app could only see the sheets it had made itself, so a farm someone shared
  never appeared on a refresh — it had to be opened by its code. Sign-in now also asks to "see information about your
  Google Drive files" (names and owners only, never contents); with it, farms shared with the account are found on a
  refresh. Accounts signed in before v59 must sign out and in once to grant it (until then the code still works).
- **Viewers work**: a farm shared to look only opens as Viewer — Output and Stock, no entry — instead of failing on the
  first save; the role follows what the owner sets in Drive.
- **Sharing says why it failed** (sign-in run out, not the owner, no Google account at that address …) and shares without
  the invitation mail when Google refuses the mail.
- **Feed types** in the farm settings are named plainly (no "_FeedTypes").
- **Checked from first principles** (a test on the real flock, every day): birds conserved (placed − reception − deaths −
  culls − lifted), meat conserved (in the house + lifted = produced), FCR, cFCR, mortality, livability, density, ft² per
  bird, water against feed, the weighing's average. Three things corrected: today's deaths were a share of the birds left
  after a lifting (now of the birds the day began with); the FCR and live birds "by the clock" left out the kilos and birds
  lifted; the company's deaths for the day were reckoned on the birds left.

## v58 — 2026-11-08 · Events and properties: an event log with two times, the flock as a property graph, the Map · **DB 21 → 22 (adds a table, nothing cleared)** · a new Events tab in the sheet
- **The event log**: every save is kept as events — weighing, deaths, culls, feed used, feed received, a lifting, a feeding,
  the barricades moved, the weather kept (one event per run, not one a day), house readings, notes, diesel, chicks placed,
  farm settings — each with when it
  happened (the weighing's time, the lifting's, the feeding's …) and when it was entered, who entered it, and the values
  before and after. Written once, never changed. Kept on the phone (new table) and sent to a new **Events** tab in the
  sheet (added the first time; one row per event, readable time first); the other phones read it back on load. The
  background sender sends unsent events too.
- **The flock as a property graph** (`graph/FlockGraph`): 91 points — every number, part of the house and kind of event —
  in seven groups, with 167 links: what each number is worked out from (raises / lowers / shapes, how strongly, and the
  feedbacks that act later), how the parts hang together (relations that carry their own figures: lines, pans a line, ft
  apart …) and which events change what. It checks itself (no unknown key, no number worked out from itself).
- **Map** (Output, after Graphs): the whole graph as one dense mind map round the flock — dots coloured by the value's kind,
  links green (raises), red (lowers), white (shapes), violet (events), dashed when they act later. Two fingers or a double
  tap zoom; a tap on a point shows its value, what it comes from, what it moves, the parts it hangs with, the events that set
  it, and — when it is worked out — which of its inputs are not entered. The event log under it, newest first, with both times.
- **Barricade slider under the house drawing**: the house front to back as a line, the birds' floor bright between two
  barricade uprights with round grips and their feet above them; "front" and "back" at the ends.
- **Ideal ♀/♂ split in the weight box**: from the counts typed — half and half for an as-hatched flock (e.g. 25 ♀ 25 ♂ a spot).
- **No outline round the top bar** (it was cut by round screen corners).

## v57 — 2026-11-08 · The lifting saved on its own with its own time, a reminder after it, no house drawing in the weight box · no DB or sheet-layout change
- **The lifting has its own Save and its own time** (Entry → Lifting: birds lifted, lift weight, "Hold to save the lifting",
  then the time the trucks were loaded — as saved, now, or 20:00 on a past day). It stays open on a locked day, like the
  notes. The day's own Save no longer touches the lifting, and its time never becomes the lifting's: the morning weighing
  keeps its time and its weights, so updating a day with a lifting no longer changes the morning average.
- **The flock changes at the lifting's time**: on Output, before that time the house holds the birds as weighed that
  morning (live birds, weight, stocking density and ft² per bird); from it, the birds left and their average.
- **After the lifting, a reminder**: spread the flock (birds left), lower the feed (bags a day now and before), move the
  barricades (ft² per bird now and before).
- **The weight box**: the "Where to catch the birds" house drawing (whole house, feeder and drinker lines) is gone; a
  small ♂♀ mark sits by the title.

## v56 — 2026-11-05 · Right colours, one FCR and one cFCR, no targets, the walk drawn again, thin sliders, turning fans · no DB or sheet-layout change
- **Fixed: yesterday's gain** showed today's gain (projected, amber, until today's weighing) under yesterday's name. It is
  yesterday's own now: yesterday's weighing against the one before, entered (green) when yesterday was weighed.
- **Colours**: the notes under a number take the colour of what they are (2 × 9.5 per line amber, 0.76 ft² per bird
  like the weight, the reception count green …) instead of grey; the outside weather is green for the hours gone by and
  amber for the hours forecast (it was uncoloured).
- **One FCR and one cFCR**, both in Birds: cFCR is the company's formula only — FCR + (2 − the average weight of every
  bird produced) × 0.25, written out under it with the numbers. The "cFCR, breed" and "FCR, dead counted" lines are gone
  (also from the sheet's Computed tab and its Formulas).
- **No weight or age target**: no target weight, harvest day, "reaches target" or "at harvest" in Lifting, and no harvest
  age asked for a new flock (the app follows the flock, its curves and its record). The day bar runs to tomorrow (and the
  days entered), its periods by age only; the graphs run a week ahead; the flock list shows "Day N". With no harvest age
  the lights stay at 18 h after the first week.
- **The walk to feed and to water is drawn again** (it went in v53): the floor from above to scale, the pans or nipples,
  the furthest bird and its walk, the walk now, the ideal and the most — under each walk line, with no box.
- **Where the meat is** reads at a glance: one line each — in the house, lifted, dead and culled — with its kilos and
  share above a thin bar filled to that share.
- **Thin barricade slider** (two small handles on a thin line, drag or tap); **the graphs have no sliders**: a finger along
  the chart reads a day, Zoom opens it in landscape where two fingers spread or close the days shown.
- **The fans turn**: the fans running spin (on their timer they turn for the on part of each cycle); the others stand still.
- **"Fans by outside air" grid removed** (the fans for the air outside, with its dials, stays).
- **Lower camera** on the house drawings and the farm window (less from straight above).
- **Plain names**: per bird, per nipple, per pan, per line, per hour.

## v55 — 2026-11-05 · Safer sync for two phones: asked again when Google is busy, sent in the background, no silent overwrite · **DB 20 → 21**, no sheet change
- **When Google is busy or has a passing fault** (too many requests, unavailable, a server error, a timeout) the request is
  asked again after 1, 2 and 4 seconds (or as long as Google asks) instead of failing to the next load. Only requests that
  do the same thing however often they run are repeated after a fault or a timeout (reads, cell writes); adding rows,
  creating or copying a file and changing tabs are not, so nothing is ever done twice. With no network at all nothing
  waits.
- **Entries saved without a network are sent by themselves** as soon as the phone is online again — also with FlockIt
  closed (Android's WorkManager; tried again later while something is unsent, nothing ever pops up). The note says
  "saved on the phone — it will be sent when the network is back".
- **Two phones changing the same entry**: each phone remembers what the sheet held when it changed a value. If the other
  phone changed that value in the meantime, it is no longer overwritten: a window shows both ("This phone 14 · The sheet
  12" for Day 12 · Deaths) and a tap keeps one. Until then that value waits on the phone; everything else on the day is
  sent. Different values on the same day still merge by themselves, as before. Numbers written another way (9.5 and
  9.500000001) count as the same; values worked out from others (feed by type, times stamped at saving) are not compared.
- The app's reads and writes of the sheet and the background sender take turns (one lock for the whole app).
- **DB 20 → 21** (`MIGRATION_20_21`): two columns on the phone only (what the sheet held, clashes waiting); the sheet is
  unchanged.

## v54 — 2026-11-05 · No boxes, the v46 line for every number, barricades set on the feed plan, a straight house, lighter type · no DB or sheet change
- **No boundaries in the page**: no outlined cards or tiles on Output, Entry and Stock — every section and number sits
  straight on the black, set apart by space; the top bar keeps its look. The page uses more of the width.
- **The v46 line for every number**: name, arrow and value on one line, `com` and `ideal` (or the limits) on the next, then
  the slim bar — the company's ±5 % band and tick under the line, the ideal's above, the flock's dot — or the range bar
  (min · ideal band · max) for numbers with a safe range. One number a line, no tiles.
- **Far fewer signs**: none on the numbers, the tabs or the choices; one small sign by each section's name only.
- **Lighter, smaller type** across the app: Material's sizes, regular weight (medium only for section names); the
  charts and drawings use the regular faces too.
- **Feed and floor: set the first and last barricade there** — drag the two along the house (5 ft steps); the pans, the
  bags and the feedings follow at once (shown dashed until set); **Set from day N** keeps them from that day on. No Auto /
  Barricades / Pans modes, and the app never suggests a barricade of its own. Until a barricade is set the brooding plan
  stands; once one is set (or "none"), the floor is exactly what was set.
- **Farm settings**: the barricade count and the floor modes are gone. Pans are entered as **all the pans on a line
  (sensor pans included)** and **the sensor pans of them**; the feed pans are shown worked out ("114 feed pans").
- **Order**: Birds · Feed · **Lifting** · Air · Water · Graphs (lifting above the air).
- **Water** has no house drawing.
- **The house drawing is straight**: seen from the front and above, not turned.
- **Trends** keep only what rests on counts and weighings: placed and started, removed (dead and culls), average daily
  gain, first-week deaths, deaths in the last 7 days. Gone: the 7-day FCR, days ahead / behind, the 3- and 7-day advice
  and the projection check.
- **Fixed: scrolling over the graphs.** The chart took any sideways wobble of a finger as moving its cursor, so the page
  would not scroll there; now only a sideways drag moves the cursor (a tap places it), up and down scroll the page.

## v53 — 2026-11-05 · The Output page in the farm's order with whole-house farm views and game-style signs, barricades the farm has, the weather kept, safe screen edges · **DB 19 → 20**, sheet schema 9 → 10
- **The page in the farm's order**, six sections with tabs that carry their sign: **Birds · Feed · Air · Lifting · Water · Graphs**.
  Every number is a tile: its sign, its name, the value (entered green, projected amber) with its arrow, `co` and `id`
  under it and the line between them. No number is shown twice; the long cards (Floor, Walk, Litter, FCR, the glance) are
  folded into the sections.
- **Birds**: the farm window, then Live birds · Weight · FCR · cFCR · Mortality · Stocking density (kg/m², ft² a bird) ·
  CV with how the weights spread (light / even / heavy) — then the one-score radar. The small numbers under the window
  are gone (they are the tiles now); what the birds are doing and the feeding times stay.
- **Feed and floor**: the whole house from above and the side (no turning): every pan of every line as the plan opens
  it (amber on, white off, crossed outside the barricades), the hoppers, the drinker lines and the barricades where they
  go (dashed while they are the plan's, not yet put up), a ruler in ft with the barricades' places. Pinch to zoom, slide
  sideways, double tap fits it again. Tiles: bags to use (feedings × bags, bags a line), feed a bird (g), yesterday's
  gain, gain today from the curve, the pans (series, open a line, birds a pan), the barricades, the walk to feed, hungry
  now, eaten yesterday, the feeding times; trays and paper in the first week.
- **Air and ventilation**: the same house, small and still, in four views — **Air** (heat on the floor, the air moving
  from the pads to the fans), **Fans** (those running lit and turning, on their timer), **Pads** (wet or dry), **Litter**
  (moisture) — each with its tiles: temperature, humidity, what the birds feel, air speed, static pressure, CO₂, outside,
  body temperature, panting, light; minimum ventilation with the fan timer and cycle, fans now, air changes, the
  controller; pads now and at the hottest hour, the layout; litter moisture, temperature, under the drinkers, ammonia,
  depth, caking. **No sensors yet**: each tile keeps a place for a sensor's reading ("sensor —") and the house shows where
  sensors would stand. The fan dials (another outside temperature) sit under Fans.
- **Lifting**: one bar of where the meat is (in the house, lifted, dead), the birds lifted, livability, EPEF, produced,
  FCR with the dead counted, the breed's cFCR, the target and the harvest forecast (set from here).
- **Water**: the house with its drinker lines and the tank (pinch to zoom), refills ×, the day's water, a bird's, an
  hour's, the walk to water, birds a nipple, height, pressure, flow, water temperature and pH, thirsty now.
- **Graphs**: the graph card, the trends as tiles (placed, removed, daily gain, last week's FCR, days ahead, first-week
  and last-week deaths), the last 3 and 7 days, the projection check, the flock book.
- **Game-style signs**: about 60 small symbols drawn in the app (the same on every phone) — a chick, a weight, a feed pan,
  a tombstone, a bell curve, a sack, pans, a fence, a fan, a thermometer, a drop, a honeycomb pad, a litter heap, a tank,
  a truck, a trophy … — one for each section and number. All in `GameIcons.kt`, to change together.
- **Barricades the farm has** (Farm settings, 0–4) and **how a floor too big for the birds is set**: Auto (barricade on a
  cool day, pans on a warm one) · Barricades (pen them whenever roomy) · Pans (never a barricade). Also on the Feed
  section. The plan uses only the barricades there are; two leave the same empty floor at both ends; with none (or Pans)
  the chicks have the whole house from the start.
- **Barricades shown everywhere**: in the farm window a barricade out of view is named at its edge with its place in ft;
  the map under the window draws fences with posts, the floor past them dark and their place in ft; the feed view draws
  them on the whole house.
- **Pad layout** in Farm settings: side by side (both side walls) or C-type (the end wall too); drawn on the air views.
- **The weather is kept with each day**: once a day is over, its lowest, average and highest temperature and humidity are
  saved in the day row and on the sheet (6 new DailyData columns). The house and bird models use them for days whose
  hours have gone from the phone (it keeps about 80 days), instead of the season's typical day.
- **Fixed: the Save button in Farm settings had no words** (black on black) — and the same on Open farm, Add new batch,
  Create farm, Share and the other plain buttons: their words are white now.
- **Screen edges**: room is kept clear at the sides of the screen (the phone's curved-edge and cut-out insets, and 8 dp
  more) so outlines are not cut on a curved or rounded screen; the account menu sets it (0–24 dp).

## v52 — 2026-11-05 · The time at Save, plain entry headings, a Now button, one score on the radar, arrows green or red · no DB or sheet change
- **The time of the entries is picked at Save**: no separate "Time of these entries" box. Hold Save (or Update), and a
  small window shows the time — for a day updated again the time its data was first entered (its weighing, else its
  lifting, else its first save that day), for a new entry now (a past day 08:00) — change it there if needed, then
  Save / Update, or Cancel. A locked day's notes and feed received save straight away, their time unchanged.
- **Entry headings without numbers**: Weight samples · Mortality and feed used · Feed received · Lifting, culls and
  barricades · Notes and diesel; the sentences under them are gone.
- **Back to the present in one tap**: while another time is picked, a **Now** button sits at the bottom right of the
  page wherever it is scrolled; letting go of a drag on the rail within a quarter of an hour of now also goes back to
  the clock. The rail stops above the button.
- **The radar is minimal**: no sentence over it and no pair of scores under each name — the six names, the outlines and
  one score in the middle (the six averaged, each as entered else as projected; green at or above the company, red
  below). What the scores are is behind the glance's ⓘ.
- **Arrows are only markers, colours say good or bad**: up / down / = against the company; green good, red bad, nothing
  in between (no orange "a little off"), and no key with "good · a little off · far off". Good or bad follows the number,
  not the direction: FCR, cFCR and mortality up is red, weight, gain, EPEF or live birds down is red; on par is green.
  Ranges (CV, the air): inside green, outside red. The same two colours on the KPI lines and the projection check.

## v51 — 2026-11-05 · Two barricades, the tube counted in a line's fill, the model suite and the house map, one see-through radar, graphs like a market chart, lighter type · **DB 18 → 19**, sheet schema 8 → 9
- **Fixed: Unlock day.** A day with only a lifting saved was locked with no banner and no Unlock button; now every locked
  day shows both. Unlocking holds through a reload, and what is typed after it is kept.
- **Fixed: "sheet not loaded" over other apps.** The 3-minute auto-load runs only while FlockIt is on screen; a load
  the app starts by itself fails quietly (the sync chip says offline); no message ever pops up from the background.
- **Fixed: decimals in Entry.** "3,5" (a keyboard's comma decimal), spaces, "1,234.5", and "12.0" for a count are read.
- **Front and back barricades** (Entry: ft from the front wall, ft from the back wall; the floor in between is shown).
  The birds' floor, density, the pans inside it and the pan series all follow both barricades; pans outside are shut;
  the pan map, the farm window and its strip draw both fences (white).
- **The tube counts in a line's fill**: Farm settings has "Bags to fill 1 line, pans open" (required) and "…, pans shut"
  (optional: the tube alone; the box shows about what a line that long holds, 0.72 kg a metre). Filling a line takes
  tube + (whole line − tube) × open ÷ all pans, so shutting half the pans does not halve the bags; the plan's "bags to
  fill" and the pans each feeding reaches follow it. Left empty, the tube is not counted (the plan as before).
- **The model suite** (`models/`): one place for every prediction model, its version, its weights
  (`assets/models/params.json`, overridable by a newer file without a new app) and its inputs (entries, weather, sensor
  readings — none yet, the place is there). Every day's inputs and outputs are kept as a log (one line a day and model);
  "Export the model log" in the flock book hands it to another app.
- **The house map** (first model in the suite): litter moisture, air humidity, stocking density and heat, place by place,
  from the birds' heat and moisture carried along by the air, the lines, the barricades, the weather and the litter
  model. **Views on the farm window**: Floor · Litter · Humidity · Density · Heat, coloured on the window's floor and the
  strip, with a key (green ideal · orange past it · red past the most).
- **The farm window**: no named birds; under it in small type what the birds in view are doing (% eating, drinking,
  resting, walking…), the model's feeding times, and the day's numbers (avg wt, CV, ft²/bird, kg/m², mortality, FCR,
  cFCR, feels, ideal temp / RH / air / air changes / pressure, light), names in white, each name kept with its number
  when the line wraps. The view chips are as wide as their words (scroll sideways with large text). The strip shows
  the hoppers, the sensor pans and both barricades.
- **No "Log a feeding"**: the feedings are the model's (after lights-on, before lights-off, clear of the hot hours); the
  farm window feeds the birds at them and the feed card shows the same times.
- **One radar**, the birds against the company, see-through: entered (green), projected at the time shown (amber),
  the company's ring (100, the only dashed line) and the ideal (blue) all as thin outlines; an outline needs three
  scores (fewer show as dots); under each name entered · projected; one colour key above it. The sensory radars are
  gone.
- **Graphs**: a slider moves the cursor (no tapping), a range slider picks the days shown, values sit beside the dots in
  the zoom, lifting badges are numbered (L1, L2 …), the ends are no longer cut, and Zoom lays the chart out in
  landscape.
- **Walk to feed and water**: no coloured floor; round the nearest open pan or nipple three circles — the furthest
  bird's walk now (green), the ideal (blue), the most (red).
- **Order of the page**: Glance · Air · Water · Feed · Birds · Floor · Walk · Litter · FCR · Graphs · Book (air can kill
  in minutes, water in hours, feed over the day).
- **Lighter and clearer**: bold and black type made medium everywhere (the wordmark kept as it was); names and headings
  larger and white; a colour key's words in their colour; marks that mean nothing (the selected tab's bar, the time on
  the glance and the rail's handle, plain headings, fences) white. No nipple-line drawing. Chips sit fewer to a row
  when their names are long, so a name ("Reception deaths") is never cut.

## v50 — 2026-11-04 · Four radars, company and ideal on every number, the walk to feed and water, tabs that stay on top, fewer cards · no DB or sheet change
- **The walk to feed and water** (new card): the furthest bird's walk to an open pan and to a nipple against the safe walk
  and the most for the age (chicks 1.0 / 1.5 m, days 4–10 1.5 / 2.5 m, then 2.0 / 3.0 m — Aviagen: chicks within 1 m of
  water; the guides: no bird more than 3 m from feed or water). Each as a line (green safe · orange up to the most · red
  too far) and as the floor from above, to scale: the reach of every open pan or nipple drawn round it, the floor no bird
  reaches in time red, the furthest bird marked ✕ with its walk. Trays and chick drinkers count while they are down. It
  follows the feedings picked in the feed card (1 on · 2 off makes the walk longer).
- **Four radars in the glance**, alike things together: Birds (livability, FCR, EPEF, growth, evenness, gain — 100 = the
  company, the breed's ideal drawn thin), Feed and water (eaten, birds a pan, walk to feed, walk to water, birds a
  nipple), Air (temperature, humidity, what the birds feel, air speed, ammonia), Floor and litter (kg/m², room, litter
  depth, moisture, caking) — 100 = inside the ideal band. Each with its average by the title.
- **Company and ideal on every number** (the v46 line is back): the glance's twelve numbers each show `co` and `id` under
  the value and the line under that (violet tick = company, blue tick = ideal, dot = the flock); "NA" where there is no
  standard. In the cards: feed a bird today, eaten, water a bird, livability, FCR and cFCR as lines with company and
  ideal; litter, floor a bird and birds a pan as range lines with ideal, min and max. The Birds graph has the ideal line,
  and the company's and the ideal's birds have the lifted birds taken off (as in the house).
- **What the colours mean**, where they are used: number colours (entered · projected · company · ideal), arrows and
  scores (good · a little off · far off), the radar's shapes, the range lines (min · max), the walk's zones, and the day
  slider now names its periods (Brooding · Growing · Finishing) under its own track.
- **Tabs that stay at the top of Output**: Farm · Glance · Feed · Water · Walk · Air · Birds · Floor · Litter · FCR ·
  Graphs · Book — the one being read is lit; a tap goes there. The page is in the order a day is worked: feed, water,
  walk and air first.
- **Fewer cards** (26 → 15): Feed holds the plan, the pans, the feeding log, how the bags were worked out, what was
  eaten and the first week; Birds holds the start and removals, the trends and the last 3 and 7 days; Growth and evenness
  holds the target, uniformity and the projection check; Air holds outside, what the birds feel, hungry / thirsty /
  panting, the weather switch, the house air, the birds' comfort and what the weather does; Fan settings holds the
  minimum ventilation, the fans by outside air and the controller.

## v49 — 2026-11-04 · The day at a glance with the radar, a time rail, one graph card, less text, no swipe · no DB or sheet change
- **The day at a glance** (top of Output, under the farm window): the performance radar — growth, FCR, livability,
  evenness, appetite, EPEF, each 100 = the company, the breed's ideal drawn thin beside it — and the twelve numbers that
  matter in three columns (weight, gain, FCR, cFCR, mortality, live, lifted, feed, water, kg/m², CV, EPEF), entered green
  and projected amber with the company's under each and an up/down mark; under them the air and hungry / thirsty /
  panting at the time shown. These numbers are only here; the cards below show how they come about.
- **Jump buttons** under it: Feed · Floor and FCR · Birds · House · Graphs · Book — one tap scrolls there.
- **The time rail**: the time slider is now a thin rail down the right edge (00:00 at the top). Touch it and it opens out
  — hours, dark hours shaded, the entries' time (green), now (white dot: tap it to go back to the clock) — and the time
  follows the finger; it folds away again on its own. The time card is gone.
- **Graphs in one card**: pick Weight · Birds · FCR · cFCR · Mortality · Feed a day · Feed total; one larger chart; drag
  along it to read any day (every line's value in a box); lifting days are rose bands with an L badge; Zoom opens it full
  screen (one finger reads, two fingers zoom).
- **Less text**: the floor plan is three short lines (Floor · Pans · Feed), the entry page's banners are one line, the
  long notes under cards are gone (the ⓘ keeps the explanations).
- **No swipe between pages** (by request): the bottom bar changes the page.
- **No bird in the litter and nipple-line drawings.**

## v48 — 2026-11-04 · One page with every number once, a saved day locks again, the plan for a half-empty house, swipe between pages, the litter and the nipple line drawn, a flock book, charts to zoom · no DB or sheet change
- **No folds, no "choose what shows".** The Output page shows everything, grouped under Feed · Floor and FCR · Birds ·
  House · Records, and each number has one place: the "At a glance" grid and the grid under the farm window (which
  repeated each other) are gone; one table, "Flock at HH:MM", holds weight, gain, live birds, deaths, mortality, feed
  eaten and water drunk — entered (with the time they were entered) against projected (at the time shown) and the
  company, the ideal under the name. FCR, cFCR (both) and EPEF are only on the FCR card, density only on the floor card,
  CV only on the uniformity card, litter and ammonia only on the litter card. No drop-downs inside cards either.
- **A time slider for the day**, like the day slider: the page (the farm window's lights and feeders, the weather row,
  hungry / thirsty / panting, the flock table, the litter, the house air) at any time of the day; the green mark is when
  the day's entries were made, the dark hours are shaded; Now goes back to the clock.
- **A saved day locks again** (by request after v47): weights, deaths, feed used, lifting, culls, the barricade and
  diesel lock once saved, so a second hold does not save the morning's entry again; notes and feed received stay open.
  "Unlock day N" (asked once) opens it; it locks again once saved. **The saved time is kept**: saving again (notes, or
  after unlocking) keeps 08:15 unless another time is picked — v47 put "now" on it.
- **The hold button shows the hold**: a fill sweeps across, a ring round the icon closes and the text counts up; let go
  early and it runs back. Before, the fill had no height inside the scrolling Entry page, so nothing showed. It also
  always runs the latest values (it had kept the first ones it was drawn with).
- **One function at a time, with a card that says so**: saving a day, clearing it, repairing / backing up the sheet,
  closing a batch, logging a feeding, settings, sharing, deleting … show "Saving day 45… · 3 s" over the page, and every
  other button waits until it is done (a second press is turned away with a note).
- **Swipe between Entry · Output · Stock · Tasks** (the bar still works).
- **Floor, pans and feedings — a plan for a half-empty house** (or few bags), from first principles, in three steps:
  1) floor — the whole house on a warm day (space sheds heat), the barricade on a cool day with room to spare;
  2) pans — shut pans in a series so each open one has about 45 birds (under 25 the feed goes stale and the pans never
  empty), never more than 2 m to walk, and every pan past a barricade shut; 3) feedings — each pour must fill the open
  pans to the last one (bags to fill = open pans ÷ the pans one bag fills), keeping the feedings for the age while it
  does. Feed today follows the same plan, so the two never disagree. Day 44 of the farm's flock (7,222 birds left,
  20 bags): a warm day — the whole house, pans 1 on · 2 off (38 open a line, 47.5 birds a pan, the furthest walk
  1.87 m), 2 feedings of 10 bags (2.5 a line; 4 bags fill the open pans); a cool day — the barricade at 185 ft and the
  42 pans a line past it shut.
- **Livability and EPEF count lifted birds as sold, not lost.** Livability = (live + lifted) ÷ started; it read 46 %
  on day 44 and pulled EPEF to 220 — now 96.7 % and EPEF about 438 (company 468), on the average of every bird produced.
- **The feeder plan view shows the barricade**: a fence with posts, the floor past it dark ("no birds · pans shut"),
  the pans past it crossed out — always visible in Feed today (no longer folded away), with the pans on and off.
- **Litter, drawn from the side** (new card): the bed's depth over the concrete against the ideal 5–10 cm, coloured by
  how wet it is (wetter and caked under the drinker line), a bird and a pan for scale, a drop from the nipple and
  ammonia rising off the top; with depth, moisture (house and under drinkers), temperature, ammonia and the caked share
  — projected against ideal. A model (no sensors).
- **The nipple line, drawn** (water card): the regulator's sight tube with the water column at the line pressure, a dial
  reading the same, the line at its height over the litter and a bird of today's size drinking.
- **Charts**: lifting days marked (dashed, L); every chart opens full screen ("⤢ Zoom") to pinch into the days, drag
  along them and tap a day; a new "Birds in the house" chart (live present and projected, lifted till date, company);
  FCR and cFCR present lines count the lifting (as the FCR card).
- **Flock book** (new): one line a day like a small sheet — date, live, dead, mortality %, lifted, lift kg, bags, g a
  bird, weight, company weight, CV, FCR, cFCR, kg/m²; entered green, projected yellow, lifting days marked; Day stays
  put while the rest scrolls; tap a day to open it; full screen too.
- **The farm window**: full or quarter width (half removed); the house map layers removed; the words over the window
  are only what is about the window (its size, the birds in view, the time).

## v47 — 2026-11-03 · Lifting counted in FCR, nothing locks, safe loading, one view with a choice of what shows, map layers · **DB 17 → 18 (kept, no clearing)**, sheet schema 7 → 8 (one upgrade, snapshot first)
Made with the farm's live sheet (read only, nothing written by hand): 45 days, 15,664 placed, 7,876 birds lifted on
days 34, 40, 42 and 43 (22,646 kg). A copy of the sheet as it stood is kept beside the APK.
- **FCR counts the lifted birds.** FCR = feed ÷ (live birds × weight + kg lifted till date). On the sheet it went
  1.54 → 1.78 → 2.29 → 3.08 over the lifting days because the lifted meat was left out; now 1.34 (day 34) … 1.50 (day 43).
  Also shown: FCR with the dead counted (deaths and culls × the weight of their day), the kg in the house, lifted and
  dead, and the average of every bird produced ("cumulative body weight").
- **Both cFCRs**, worked out in front of the reader: the company's FCR + (2 − cumulative body weight) × 0.25, and the
  breed curve's (FCR − Ross table FCR at that weight + Ross table FCR at 2.0 kg). What the company's rule means is
  written under it.
- **The birds left after a lifting get their own average**: the morning's weighing grown on to the lifting time, the
  lifted kg taken out, ÷ the birds left (day 34: 2,297 g weighed → 2,345 g at the lifting → lifted 2,390 g → 2,345 g left);
  the days after are carried on from it, and density is on the birds left.
- **Nothing locks.** No 11:00 / 20:00 cut-off, no "saved & locked", no Unlock day: any day up to today can be changed
  and saved again by holding Update (a day to come still cannot be entered). The entry page shows what is saved, when
  and by whom.
- **Time of the entries.** The entry page has "Time of these entries": now (today) or an earlier time picked; on a
  past day the day's own time. It is saved as the weighing time (starts the weight projection) and the new lifting
  time (decides whether the weighing is grown on before the lifted kg come out).
- **Safe loading and saving.** One load or save at a time; a save writes only the cells it changed and merges with what
  another phone entered meanwhile (both phones' entries stay); a draft keeps only what was typed, so a stale draft can
  no longer put back an old value; an unsent edit is never overwritten by a load.
- **Loads by itself** when the app is opened or comes back, when a farm or a flock is opened, and every 3 minutes
  while a flock is open — with a loading bar and a line saying so. **Why it only worked on the farm page:** the sign-in
  token (it lasts an hour) was renewed only by the farm list's refresh; now it is renewed before it runs out and once
  more when Google refuses a read.
- **One view.** Basic / Advanced is gone. The Output page: At a glance (numbers you pick), the farm window, the weather,
  hungry/thirsty/panting, log a feeding, alerts, then sections that fold to one line that says what is in them — Today,
  Feed today, Birds and growth, Lifting and FCR, Space and density, Last 3 and 7 days, Water, Ventilation, What the
  weather does. "Choose what shows" ticks numbers and sections on or off (kept on the phone).
- **Feed today**: one conclusion — "N bags today; F feedings of B bags, X bags on each of the 4 lines" — the number of
  feedings picked right there (best one marked), what a line takes to fill and how much of the open pans each feeding
  fills; how the bags were worked out folds away. "Chart" is now "company" everywhere.
- **Space and density** (new): kg/m² on the floor the birds have (the brooding area while penned, not the whole house —
  the old figure was too low while brooding), the floor a bird has against what it needs today (30 kg/m² hot, 34 warm,
  the cap otherwise), tight · right · roomy, the walk to feed and water, and after lifting the ways to set the floor:
  the whole house, a barricade at N ft, or the whole house with pans shut — with the one to use and why (warm: keep the
  floor and shut pans; cool and roomy: pen the birds) — from day 21 or once birds are lifted (a young flock grows into
  its floor). A barricade field is on the entry page (it holds until changed). The birds in the farm window roam more on a
  roomy floor and less on a tight one.
- **Last 3 and 7 days** (new): eaten and gained against the company, CV first → last, and what to do (e.g. day 33:
  eating 5.6 % under the company but growing 16.8 % over it — keep the ration).
- **Map layers on the farm window** (like a map app): Plain, Body temp, Density, Litter temp, Litter moisture, Ammonia,
  Air temp, Humidity — Projected (today's model) or Ideal — seen from the Top or the Side (the floor to the roof). The
  3D window colours its floor, and shows the air as see-through sheets at floor, middle and roof height. A model, said so.
- **Reception deaths shown properly**: placed, reception deaths and their %, the birds started, mortality with and
  without reception deaths.
- **Weather emojis**: the sky now in the top bar, by the weather row, and on each day of the 14-day forecast.
- **Colours used for what they mean**: weather readings are white (not "Present"), likelihoods and weather effects are
  Projected (not "Max"), the window's density reads kg/m² and ft² a bird.
- **Buttons**: the hold buttons are taller, their words wrap instead of being cut, and a risky one has a red edge.
- **Feedings on the sheet**: a feeding logged under the farm window is kept in the day's new Feedings column, so every
  phone sees it (older ones on the phone still count).
- Sheet schema 8: DailyData gains LiftedAt and Feedings; Computed gains the lifting columns (lifted birds and kg,
  average after lifting, live / dead / produced kg, cumulative body weight, FCR with the dead, cFCR company and breed);
  Formulas explains each. DB 18: liftedAt, feedings and the unsent-columns list (phone only).
- Tests: the real flock to day 44 (FCR through the lifting days, the birds left, density on the floor in use, the
  barricade carried on), the space plan, the 3/7-day reading, a schema 7 sheet upgraded to 8 keeping everything, two
  phones on one day keeping each other's entries, DB 17 → 18, the map layers' directions; every screen re-rendered at
  360 dp and 130 % text. 117 tests, all passing.

## v46 — 2026-10-09 · The original look is back: complete black · installs over a live flock (no database or sheet change)
- **The v45 look is taken back** — it was too colourful. The app is as it was in v44: a pure black screen,
  thin white outlines, the calmer value colours (soft green, amber, blue, violet, cyan, rose), no blue
  accent, outlined tabs and Basic / Advanced switch.
- **Kept from v45**: the day slider with its periods in the track itself (no separate band, no legend),
  in the calmer colours — orange brooding, green growing, blue finishing, red past the planned harvest —
  with the white bar at the chosen day, the white dot at today, and the dot beside "Day N".

## v45 — 2026-10-09 · A new look after Expo's design system; the day slider and its periods as one piece · installs over a live flock (no database or sheet change)
- **The look.** The reference picked by the farm is Expo's own website, built with their open-source design
  system (`@expo/styleguide`, MIT), which stands on Radix Colors. The app now follows its dark theme:
  - the screen is near-black (`#0C0D0E`), not pure black; panels are see-through — a few percent of white
    over the screen — with one hairline and rounder corners, instead of white outlines;
  - values sit on a faint fill, with no outline round each one; far fewer lines and boxes on a page;
  - tabs and the Basic / Advanced switch are one soft bar with the chosen part lighter;
  - one accent, blue (links, the chosen choice, the weather switch, the cursor in a field);
  - the value colours are the same six kinds in the Radix shades: present green, projected amber, ideal
    blue, commercial purple, min cyan, max red.
  The type stays Rubik and JetBrains Mono (the farm's own choice; Expo's site uses Inter).
- **Day slider**: the separate colour band and its legend are gone. The slider's own track is coloured by
  period — brooding orange (days 0–10), growing green (11–27), finishing blue (28 to the planned harvest),
  red for days past it — bright up to the chosen day and dim after it, with a white bar at the chosen day
  and a small white dot at today. The dot beside "Day N" has the colour of that day's period.
- Tests: the slider chooses a day by tap and by an accessibility action; every screen re-rendered at 360 dp.

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
