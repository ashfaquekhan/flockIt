package com.example.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp

/*
 * The look of the app's surfaces, after Expo's design system (@expo/styleguide, dark theme, built on Radix
 * Colors): a near-black screen, panels that are a few percent lighter and see-through rather than outlined,
 * hairlines instead of borders, and as few boxes as possible. Colour stays on the numbers.
 *
 *  - a card:            GlassBox (OutputScreen.kt) — see-through fill, one hairline, 20 dp corners
 *  - a value in a card: [softBox] — a faint fill, no outline
 *  - the chosen one of several: [chosenBox] — a stronger fill and a thin blue line
 */

/** A faint fill behind a value or a small group — no outline. */
fun softBox(shape: Shape = RoundedCornerShape(10.dp)): Modifier = Modifier.background(SoftFill, shape)

/** The chosen one of several choices: a stronger fill and a thin line in the link blue; nothing when not chosen. */
fun chosenBox(on: Boolean, shape: Shape = RoundedCornerShape(10.dp)): Modifier =
    if (on) Modifier.background(SoftFillStrong, shape).border(1.dp, LinkBlue.copy(alpha = 0.75f), shape) else Modifier
