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
| O13 | Pan plan uses the farm's "bags to fill 1 line" as a straight pans-per-bag rate | Pan spacing and line gap are farm settings (default 2.5 ft, width ÷ lines); first pan 5 ft from the front wall; 3 % slack on the fill; birds-per-pan limit scaled by weight^⅓ below 2 kg (Ross gives 45–80 for grown birds only). |
| O14 | Lighting schedule assumes the dark period ends at 05:00 | Make it a farm setting if the farm's dark period differs. |
| O15 | Coop environment values are ideals | No house sensors; birds behave as in comfortable air. |
| O16 | Cooling grid and fan finder start from the season's typical temperatures | Weather is not used for inside values until house sensors exist. |
| O17 | Farm settings still hold the old default of 60 pans per line unless changed | Set feed pans (e.g. 112), sensor pans (2) and bags to fill a line (e.g. 3.3) in Farm settings for the plan to match the shed. |
| O18 | Pour per line can be a fraction of a bag (e.g. 2.38 = 118.8 kg) | Shown in bags and kg; the day's total is always whole bags. |
| O19 | Day clock times are rules, not settings | Dark ends at 05:00 (O14); the hot window comes from the forecast or the season's typical day. Say if the farm needs a different dark period or feeding hours. |
| O20 | Farm window draws up to 1,800 birds at once | A width that would hold more at today's density steps down to half or quarter width. |
| O23 | True CV needs birds weighed one by one | Bulk (bucket) weighing can't give the bird-to-bird CV; enter 10+ single weights (50–100 is better) on sample days. |
| O24 | Trend tolerance is ±3 % against the commercial standard (or ideal) | Within it the marker is "=" and green. Since v52 there are two colours only: worse than the company by more than 3 % is red (feed and water amounts: more than 6 % either way). The radar's one score is green from 97. A guide; tell me if some KPIs need a tighter or wider band. |
| O22 | Sheet upgrade and repair are checked by unit tests, not yet against a live Google account | The layout logic is tested; the Drive/Sheets calls need a real run on the farm's sheet. |
| O25 | Feed correction is advice only | Capped at +5 % / −3 %, none in week 1; birds eat to appetite, so check pans are cleared before adding feed. |
| O26 | Breaths a minute in the animation box are typical resting rates, not measured | 30–50 in week 1, 20–40 later; panting over 60. |
| O27 | Access token cached in plain preferences and app backup allowed | See ARCHITECTURE.md §10; fix with the P6 sign-in change. |
| O28 | Larger refactor (use cases, DI, Gradle modules) is planned, not done | Steps listed in ARCHITECTURE.md §9; done gradually so each release installs over a live flock. |
| O30 | Stock page does not plan orders | By request: received, used and in store only. Needed / to order / days left can come back later. |
| O31 | Which chart day is which flock day is an assumption | Taken as: the chart's day 1 is the placement day (the app's day 0). If the company counts the day AFTER placement as day 1, feed and commercial mortality should move back one row — say so and it is a one-line change (`CompanyStandard.duringDay`). |
| O32 | Curves past day 49 (breed) and day 55 (company) are modelled, not from a table | Gompertz weight, maintenance + growth feed. Replace with the published rows if the farm keeps flocks that long. |
| O33 | Growth forecast (target day) still uses the v41 share-of-curve filter | Replaying the farm's flock, it lags when the flock changes pace (7.8 % one-step miss against 4.2 % for the weighing carried along the curve). The daily projection already uses the better one; the target-day forecast should follow. |
| O34 | Sheet schema 7 upgrade is tested on data, not against a live Google account | The schema 6 upgrade did run on the farm's live sheet (4 Oct 2026, ActivityLog); schema 7 uses the same path and adds three report tabs and one column. A snapshot of the old sheet is made first. |
| O35 | The weather → house → bird model is not calibrated to this house | The house is worked out from the weather (no sensors) and the bird responses are from published trials. It shows direction and the hours to watch; it is not a measurement. Add sensors (the model takes them as a source) or compare with a thermometer on hot days. |
| O36 | Past days' plans carry no weather effect | The weather switch acts on the selected day's plan and on "now"; the appetite learned from past entries already contains whatever the weather did. |
| O38 | Day 0 weights of the running flock look wrong | 12.5–33 g a chick (average 22.5 g; a placed chick weighs about 40 g): probably fewer than 10 chicks in each weighing. It only moves the first point of the weight curve; use Revert day to correct the counts if wanted. |
| O39 | CV is an estimate until birds are weighed one by one | From group weighings: spread between groups × √birds per group, pooled over 3 weighings. Rough, and it reads high when one end of the house is lighter (day 16: locations 11 and 12 were 8 % lighter). |
| O40 | Which Ross line is placed is not known | Asked on 9 Oct 2026: the farm does not know. The ideal curve stays Ross 308 AP 2022 (the line Aviagen sells in Asia; confirmed row by row). The plain Ross 308 2022 table is about 1–2 % lighter (day 14: 533 g against 540 g). It only moves the "ideal" reference and the weight carried on between weighings; the feed plan runs on the company chart. The chick box or the company's supervisor can settle it. |
| O41 | Farm settings worth a look | In the farm sheet: nipples per line 0 (birds per nipple cannot be checked), manual drinkers 0, feeder line gap 0 (taken as width ÷ lines = 9.75 ft), bags that fill a line 3.0 (3.3 was mentioned). |
| O42 | The rhythm of the day is from published behaviour, checked against one feeding on this farm | Peaks after lights-on and before lights-off (about 1.4 × the average), a midday lull (about 0.8 ×). It reproduces the farm's own estimate for day 20 (13 bags from 08:30 lasting to about 16:00), but that is one observation. When the pans run empty much earlier or later than the likelihoods suggest, note the time: it can be tuned from a few such notes. |
| O43 | The lights are taken from the programme, not from the house | 18 h of light on day 20, dark 23:00–05:00. If the house is lit differently, eating shifts with it and the likelihoods are off. A farm setting for the real lights-on / lights-off times would fix it. |
| O44 | Thirst assumes water is in the lines | There is no record of tank refills, so a dry tank cannot be seen. |
| O45 | The flock's own response to heat is unknown | The weather has been too steady since day 9 (outside high 31–32.6 °C every day) to learn it from the records; the published figures are used. |
| O21 | Output review checklist (keep / change / drop) is waiting for the farm's choices | https://claude.ai/artifact/VDtyZmDKtLiJBu5Nxq6B3g |
| O47 | Sheet schema 8 upgrade is tested on data, not yet run on the live sheet | It runs once on the first load after installing v47 and keeps a snapshot of the old sheet (same path as 6 → 7, which ran on this sheet on 9 Oct). A copy of the sheet as it stood on 3 Nov is also saved beside the APK. |
| O48 | Lifting time is not known for the four liftings already entered | They are taken as 20:00 (the trucks loaded in the evening; the entries were saved 20:28–23:04). Pick the time when entering the next lifting. |
| O51 | "Comfortable" density on hot days (30 / 34 kg/m²) is a rule of thumb | From heat-stress guidance; the farm's cap (39 kg/m²) stays the most. Say if the company uses other figures. |
| O52 | Two phones changing the same value at the same time: the later save wins | Different values on the same day are merged; the same value is not. |
| O53 | The litter is a model, and the bedding depth is assumed | 7.5 cm of bedding at placement unless known; droppings from the feed entered; moisture, warmth and ammonia from age, crowding, the house air and air speed. Not fitted to this house — a squeeze test and the nose decide. A farm setting for the bedding depth (and material) would firm it up. |
| O54 | The nipple line drawing shows the age curve, not a reading | Pressure and height are the ideal for the age (there is no entry for them). Readings could be added to the entry page. |
| O55 | The radars' scales are a choice | Birds: 100 = the company, evenness against an 8 % CV, drawn up to 150. The other three: 100 inside the ideal band, 50 at the limit, 0 as far again past it; eaten loses 2 points per % off the company's feed. Say if other weights suit the farm better. |
| O56 | The floor plan's pan figures are rules of thumb | About 45 birds a pan aimed for, under 25 counted as too few (feed goes stale), 2 m the longest walk (Ross). Say if the company uses others. |
| O57 | The walk limits between the first days and grown birds are rules of thumb | Published: chicks within 1 m of water in the first 24 h (Aviagen), no bird more than 3 m from feed or water (management guides). Days 4–10 (1.5 / 2.5 m) and the 2 m "safe" for grown birds are in between. The walk to water takes nipples 25 cm apart and the drinker lines spread evenly over the width. |
| O59 | The house map is a model, not fitted to this house | Heat and moisture carried by the air (tunnel: end to end; minimum ventilation: 20 % of it shows), birds drawn to the lines and away from the warm end, litter wetter under the drinkers and where birds crowd. Which end the fans are at is taken as the back (setting `house_map.fans_at_back` in the weights file). Sensors can be added as readings; until then it shows where to look, not a measurement. |
| O60 | The tube is counted only once "Bags to fill 1 line, pans shut" is entered | Without it every pan takes its share of the whole line (as before v51), which overstates how far a pour reaches when many pans are shut. Fill a line once with every pan shut and enter the bags; the settings box shows about 0.72 kg a metre of line as a guide. Whether the tube stays full between feedings (so later pours fill pans only) is not modelled: every feeding is planned as a fill. |
| O61 | The model log grows on the phone only | One line a day and model in the app's files; export it from the flock book. A sheet tab or a server copy can come later. |
| O62 | Sensor readings have a place but no source yet | `models/SensorReading`; the house map pulls its field to a reading near it. Nothing reads sensors yet. |
| O58 | Water has no company standard | The company's chart has no water column, so water shows "co NA"; the ideal is 1.8 × the breed's feed (Ross: 1.6–2.0 at 21 °C). |

## Fixed
| Version | Issue | Cause | Fix |
|---|---|---|---|
| v52 | The time of the entries was a separate box at the top of Entry | v47 put the time in its own card so it could be picked before saving | Picked after the hold, in the save window; an update offers the time first saved |
| v52 | Entry cards numbered 1–5 with sentences under the headings | Kept from the early step-by-step entry | Plain headings, no sentences |
| v52 | Hard to get back to the present on the time rail | The only ways were a small white dot on the rail and the Now pill on the glance, which scrolls away | A Now button at the bottom right while another time is picked; a drag let go near now snaps to it |
| v52 | Radar too wordy | A sentence over it and two scores under every name | Names, outlines and one overall score in the middle |
| v52 | Arrows read as "up = good"; three grades with a key | The key drew three up-arrows labelled good / a little off / far off; orange sat between green and red | Arrow = direction only, colour = good or bad by what the number is; green or red only, no key |
| v51 | Could not unlock a saved day after updating | A day with only a lifting saved was locked, but the banner and Unlock button showed only for days with weights, deaths or feed saved | Every locked day shows the lock and Unlock; unlocking survives a reload |
| v51 | "Couldn't load latest from Google Sheet" popped up over other apps | The 3-minute auto-load kept running in the background and its failure was shown as a toast (toasts float over other apps) | Auto-load only while on screen; its failures go to the sync chip; no toast while away |
| v51 | Entry would not take some decimals | Fields read with toDouble: "3,5" and "12.0" (as a count) were dropped | A reader for numbers as typed |
| v51 | Pan plan had no way to trim the floor at both ends; closing half the pans halved the bags | One barricade from the front; bags per pan = whole line ÷ pans (the tube shared out over the pans) | Front and back barricades; a line's fill = tube + open pans' share; "pans shut" fill setting |
| v51 | Four radars hard to read; filled shapes hid each other | Every group on its own small radar, the flock filled | One birds radar, outlines only: entered, projected, company, ideal |
| v51 | Graphs: zoom upright, ends cut, tap to read, lifting not numbered | Portrait dialog; no end padding; tap / drag only | Landscape zoom, end padding, cursor and range sliders, values by the dots, L1, L2 … |
| v51 | Text too bold; labels and marks in value colours | Bold / black weights; marks drawn in the projected colour | Medium type, white marks and headings, key words in their colour |
| v51 | Named birds (Pip, Dot …) and their table | Kept since v34 | Gone; shares of birds by activity and the day's numbers in small type |
| v50 | Ideal missing where the company was shown (glance, feed bags, FCR card …) | v48–v49 cut the numbers to tiles and chips with the company only | Company and ideal under every number with the v46 line; "NA" where there is none |
| v50 | Colours had no key (radar scores, arrows, the day slider's periods, range lines) | Keys were dropped to keep cards short | A one-line key where colours are used; the slider names its periods in its own track |
| v50 | Still too many cards; feed, water and air far down the page | 26 cards; the glance and the jump buttons above them | 15 cards, feed / water / walk / air first, tabs that stay at the top and light the section being read |
| v50 | One radar for everything | Only the birds' performance was scored | Four radars of alike things: birds, feed and water, air, floor and litter |
| v50 | The walk to feed and water was one number | Shown as "Furthest 1.87 m · Most 2.0" in the floor card | Its own card: safe and most for the age, a line and the floor drawn to scale with each pan's and nipple's reach |
| v50 | The Birds graph's company line kept the lifted birds | Company birds = started × (1 − company mortality) | Lifted birds taken off the company's and the ideal's lines; the ideal line added |
| v49 | Too much scrolling, no overview | v48 put every card open in one long page and dropped the glance | The day at a glance (radar + 12 numbers) on top, jump buttons to each group, one graph card |
| v49 | The time slider sat in a card and took room | A horizontal slider in its own card | A rail down the right edge that opens when touched; tap the white dot for now |
| v49 | Graphs hard to read, lifting marks not visible | Thin dashed lines for lifting, values in boxes above each chart | One chart at a time, a value box on the cursor, lifting days as rose bands with an L badge |
| v48 | Livability 46 % and EPEF 220 after lifting (company 468) | Livability = live ÷ started: the lifted birds counted as lost | (live + lifted) ÷ started; EPEF on the average of every bird produced |
| v48 | Holding Save again re-saved the morning's entry with the time moved to now | v47 removed the lock, and an empty time picker meant "now" | A saved day locks (notes and feed received stay open) until unlocked; a save keeps the saved time unless another is picked |
| v48 | No hold animation on the Save button | The fill was a child box sized to the button's height, which is unbounded inside the scrolling Entry page: 0 px tall | Drawn behind the button; a ring and a count too; timed by frames |
| v48 | The Save button could save stale values | The press gesture kept the action from the first drawing (pointerInput(Unit)) | The latest action is read when it fires (also the task tick) |
| v48 | Two functions could be started at once (save and repair …), with nothing showing they ran | No shared "running" state | One function at a time, a card over the page with what runs and for how long; other buttons wait |
| v48 | The same numbers several times on the Output page; folds and a "choose" list | "At a glance", the grid under the window and the cards each carried weight, FCR, density … | One table for the flock at a time; each number has one card; no folds |
| v48 | No plan for a half-empty house: pans on/off or barricade, how many feedings | The space card and the feed card decided apart; the feed card had no lower limit on birds a pan | One plan (floor → pans → feedings) from the line fill, birds a pan and the walk; Feed today follows it |
| v48 | The barricade was hard to see on the pan plan, which was folded away | A thin dashed line; the drawing sat in a fold | A fence, the floor past it dark, its pans crossed out; always shown |
| v47 | FCR jumped to 3.08 over the lifting days | FCR = feed ÷ (live birds × weight): the lifted birds' kg were not counted | FCR on produced kg (live × weight + kg lifted); the birds left get their own average |
| v47 | cFCR used the flock's weight, not the average of all birds produced | Same cause | Company cFCR on the cumulative body weight; a breed-curve cFCR beside it |
| v47 | Loading only worked by swiping the farm list; flock pages did not refresh | The Google token (1 hour) was renewed only by the farm list's refresh; an expired one made every flock-page load fail quietly | Token renewed before it runs out and once more on a refused read; loads on open, on return and every 3 min; a loading bar |
| v47 | A save could overwrite what another phone entered, a load could land on a save | Whole rows were written; loads and saves ran side by side; drafts kept every field | Only changed cells are written and merged with the sheet's row; one load or save at a time; drafts keep only what was typed |
| v47 | Past days could not be changed; the cut-off locked today | Time lock and per-field locks | Removed: any day up to today can be changed and saved again |
| v47 | Density too low while brooding; "kg/ft²" and "birds/ft²" puzzling | kg/m² was over the whole house, not the floor in use | kg/m² on the floor the birds have; ft² a bird; the space card |
| v47 | Reception deaths hard to find | Shown only as "Reception" in one row | Placed, reception deaths and %, started, mortality with and without them |
| v47 | Too scattered: Basic / Advanced, tabs, repeated numbers, "chart" | Two views and three tabs | One view, sections that fold, a choice of what shows; "company" for the company's standard |
| v47 | Feed plan had many bag numbers and no clear answer | Plan flow, options, details all at once | One conclusion line; feedings picked in place; details folded |
| v47 | Value colours used for alerts (Max for high likelihoods, Present for the weather) | Colours borrowed to flag | Each colour means one kind of value only |
| v47 | No weather emojis | The weather code was not asked from the service | Weather code read; sky emoji in the top bar, the weather row and the forecast |
| v47 | Hold-button words cut and small | One line, fixed height | Taller, words wrap, risky ones red-edged |
| v46 | The v45 look was too colourful and not black | A whole new theme (vivid value colours, a blue accent, a near-black screen) was shipped from one reference picture without showing a sample first | Back to the original look: pure black, white outlines, the calmer colours. A change of look is shown as a sample before it is built in |
| v45 | The colour band and legend under the day slider were not what was asked | The request was for the colour coding and the slider to be one thing | The slider's own track carries the periods; band and legend removed |
| v45 | Too many outlines and boxes | Every value and card had a white outline on pure black | Expo-styleguide look: see-through panels with a hairline, faint fills, one blue accent |
| v44 | Feeder shown empty from 14:26 when 13 bags poured at 08:30 would last to about 16:00 | Feed spread evenly over the lit hours, at the full chart ration, with no heat | The day's rhythm (meals after lights-on and before lights-off, midday lull), the flock's appetite and the hot hours; and the claim of a minute replaced by likelihoods of hungry / thirsty / panting |
| v44 | Day clock took room on the Output page for little use | Shown above the day's numbers | Moved to the Tasks page, under the tasks |
| v43 | Was a feed bag really 60 kg? (the sheet's value is also the app's default) | Not confirmed until asked | Confirmed by the farm on 9 Oct 2026: 60 kg. The feed figures and the FCR (1.13 at day 18) stand as shown |
| v43 | Birds in the farm window walked far too much and too fast | Activity was picked from fixed weights and the walking speed was about 0.5 m/s at every age | Time budget and speeds by body weight from published work (`domain/BirdBehaviour`) |
| v43 | Feed forecast chased each day's entry | The filter treated the bags entered as the feed eaten; they are bags poured (feed stays in the lines), so a big entry is followed by a small one | A filter that follows appetite and feed in the lines together; first days left out; chosen by replaying the farm's flock |
| v43 | Projected and entered values shared one place and one line in the charts | A value's colour was the only difference | Two columns in Today, the projection on its own line under a comparison, its own line in every chart |
| v43 | No CV for the flock; the farm window used an assumed 8 % | CV was only worked out from birds weighed one by one | Estimated from the group weighings and marked as an estimate |
| v43 | "Vent temp" and "Body" looked like two things | One number under two names | "Body temp" everywhere, with the ideal range and the value worked out for the air now |
| v43 | Feed type box broke long names mid-word ("Finis-her") | Code and name in a narrow box | The box shows the code; names are in the list |
| v43 | A new day's feed row always started with the first variety | Default was the first feed type | The variety entered last |
| v43 | Farms header read as two F's | Mark beside "FlockIt" | The mark is the F of the name |
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
- A sed over every `FontWeight.Black` also hits the wordmark (`FlockLoader.kt`): branding is kept as it is — check
  the diff of components before committing a sweeping change.
- An Android toast from an app in the background shows over other apps: never show one unless the activity is resumed.
- A new Room version needs its migration added to `MigrationTest` too (12 → current), or the test fails.
- A Compose test that hangs can run for hours (v51: two hours in `performScrollTo` on a tab off the row's edge): unit
  tests time out after 20 minutes (`build.gradle.kts`); tap an off-screen chip with `SemanticsActions.OnClick`.
- Read the day's figures (`OutputData`) on the main thread; only file writes go to `Dispatchers.IO`.
- A text field inside an `AlertDialog` never lets Compose go idle in the screen tests (v52: `AppNotIdleException`,
  "did not get idle after 84,863 attempts", with the clock paused): in a dialog show a value as text with a Change
  button (the time picker opens from it), not a read-only text field.
- The PC went to sleep in the middle of a test run (v52: 21 minutes, the build took 1 h 19 min): keep the machine awake
  for long builds.
