@file:OptIn(androidx.compose.animation.ExperimentalSharedTransitionApi::class)

package com.aeswox.arcmusic

import dev.chrisbanes.haze.HazeStyle
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.filled.MoreVert
import kotlin.math.roundToInt
import androidx.compose.ui.unit.IntOffset
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.ui.graphics.Brush
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.Animatable
import androidx.compose.ui.geometry.Rect
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.animation.SharedTransitionScope.OverlayClip
import com.aeswox.arcmusic.ui.animations.LocalJigglePhysicsSettings
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.compose.animation.core.*
import androidx.compose.animation.*

import androidx.compose.material3.ButtonDefaults
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.getValue
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.ui.Modifier
import androidx.compose.foundation.clickable
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.lerp
import dev.chrisbanes.haze.HazeState
import androidx.compose.runtime.*
import kotlinx.coroutines.launch
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.DragHandle
import coil.compose.AsyncImage
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material3.*
import androidx.compose.ui.graphics.Color
import dev.chrisbanes.haze.hazeChild
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.ui.draw.blur
import androidx.compose.material3.Button
import androidx.compose.foundation.background
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.LibraryMusic
import com.aeswox.arcmusic.ui.components.HugeIcons
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import com.aeswox.arcmusic.backdrop.Backdrop
import com.aeswox.arcmusic.backdrop.drawBackdrop
import com.aeswox.arcmusic.backdrop.effects.blur
import com.aeswox.arcmusic.backdrop.backdrops.layerBackdrop
import dev.chrisbanes.haze.haze
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.composed
import androidx.compose.foundation.shape.CornerBasedShape

import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import com.aeswox.arcmusic.ui.animations.JigglePhysicsSettings
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlin.math.abs
import coil.imageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import android.graphics.drawable.BitmapDrawable
import androidx.palette.graphics.Palette
import androidx.compose.ui.graphics.toArgb
import kotlin.math.sqrt
import com.aeswox.arcmusic.ui.animations.jellyClick
import com.aeswox.arcmusic.ui.animations.jelly
import com.aeswox.arcmusic.ui.components.*

// Process-wide cache of extracted Palette swatches keyed by image URI. The immersive page bg,
// accent, bottom scrim and mini-player tint all derive from these. Caching lets every consumer
// read the color synchronously on the first frame instead of waiting on Coil + Palette, which
// used to arrive a few hundred ms late and desync the color morph from the page transition.
object PaletteCache {
    private val dominantCache = android.util.LruCache<String, Int>(128)
    private val vibrantCache = android.util.LruCache<String, Int>(128)
    fun dominant(key: String): Color? = dominantCache.get(key)?.let { Color(it) }
    fun vibrant(key: String): Color? = vibrantCache.get(key)?.let { Color(it) }
    fun putDominant(key: String, color: Color) { dominantCache.put(key, color.toArgb()) }
    fun putVibrant(key: String, color: Color) { vibrantCache.put(key, color.toArgb()) }
}

@Composable
fun rememberDominantColor(imageUrl: String?, defaultColor: Color): State<Color> {
    val context = LocalContext.current
    val colorState = remember(imageUrl) {
        mutableStateOf(imageUrl?.let { PaletteCache.dominant(it) } ?: defaultColor)
    }

    LaunchedEffect(imageUrl) {
        if (imageUrl == null) {
            colorState.value = defaultColor
            return@LaunchedEffect
        }
        PaletteCache.dominant(imageUrl)?.let { colorState.value = it; return@LaunchedEffect }

        val request = ImageRequest.Builder(context)
            .data(imageUrl)
            .size(128)
            .allowHardware(false)
            .build()
            
        val result = context.imageLoader.execute(request)
        if (result is SuccessResult) {
            val bitmap = (result.drawable as? BitmapDrawable)?.bitmap
            if (bitmap != null) {
                Palette.from(bitmap).generate { palette ->
                    val rgb = palette?.dominantSwatch?.rgb ?: palette?.mutedSwatch?.rgb
                    if (rgb != null) {
                        PaletteCache.putDominant(imageUrl, Color(rgb))
                        colorState.value = Color(rgb)
                    }
                }
            }
        } else {
            colorState.value = defaultColor
        }
    }
    
    return colorState
}

@Composable
fun rememberVibrantColor(imageUrl: String?, fallbackColor: Color, dominantColor: Color): State<Color> {
    val context = LocalContext.current
    val colorState = remember(imageUrl, fallbackColor, dominantColor) {
        mutableStateOf(pickAccent(imageUrl?.let { PaletteCache.vibrant(it) }, fallbackColor, dominantColor))
    }

    LaunchedEffect(imageUrl, fallbackColor, dominantColor) {
        if (imageUrl == null) {
            colorState.value = pickAccent(null, fallbackColor, dominantColor)
            return@LaunchedEffect
        }
        PaletteCache.vibrant(imageUrl)?.let {
            colorState.value = pickAccent(it, fallbackColor, dominantColor)
            return@LaunchedEffect
        }

        val request = ImageRequest.Builder(context)
            .data(imageUrl)
            .size(128)
            .allowHardware(false)
            .build()

        val result = context.imageLoader.execute(request)
        if (result is SuccessResult) {
            val bitmap = (result.drawable as? BitmapDrawable)?.bitmap
            if (bitmap != null) {
                Palette.from(bitmap).generate { palette ->
                    palette?.vibrantSwatch?.rgb?.let { PaletteCache.putVibrant(imageUrl, Color(it)) }
                    colorState.value = pickAccent(
                        palette?.vibrantSwatch?.rgb?.let { Color(it) },
                        fallbackColor,
                        dominantColor
                    )
                }
            }
        } else {
            colorState.value = pickAccent(null, fallbackColor, dominantColor)
        }
    }

    return colorState
}

// Fire-and-forget pre-warm of the palette cache for an artwork URI. Call from list/row items so
// the dominant + vibrant colors are already cached by the time the user opens the immersive page,
// making the page bg / scrim / mini-player tint resolve on the first frame (no color stutter).
@Composable
fun PreloadImmersivePalette(imageUrl: String?) {
    val context = LocalContext.current
    LaunchedEffect(imageUrl) {
        if (imageUrl == null) return@LaunchedEffect
        if (PaletteCache.dominant(imageUrl) != null && PaletteCache.vibrant(imageUrl) != null) return@LaunchedEffect
        val request = ImageRequest.Builder(context)
            .data(imageUrl)
            .size(128)
            .allowHardware(false)
            .build()
        val result = context.imageLoader.execute(request)
        if (result is SuccessResult) {
            val bitmap = (result.drawable as? BitmapDrawable)?.bitmap
            if (bitmap != null) {
                Palette.from(bitmap).generate { palette ->
                    (palette?.dominantSwatch?.rgb ?: palette?.mutedSwatch?.rgb)?.let { PaletteCache.putDominant(imageUrl, Color(it)) }
                    palette?.vibrantSwatch?.rgb?.let { PaletteCache.putVibrant(imageUrl, Color(it)) }
                }
            }
        }
    }
}

private fun pickAccent(vibrant: Color?, secondaryColor: Color, backgroundColor: Color): Color = when {
    vibrant != null && !vibrant.isCloseTo(backgroundColor) -> vibrant
    secondaryColor != Color.Unspecified && secondaryColor.alpha > 0.01f &&
        !secondaryColor.isCloseTo(backgroundColor) -> secondaryColor
    else -> backgroundColor.contrastingShade()
}

private fun Color.isCloseTo(other: Color): Boolean {
    val dr = red - other.red
    val dg = green - other.green
    val db = blue - other.blue
    return sqrt(dr * dr + dg * dg + db * db) < 0.15f
}

fun Color.contrastingShade(): Color {
    val hsv = FloatArray(3)
    android.graphics.Color.colorToHSV(this.toArgb(), hsv)
    hsv[2] = if (hsv[2] < 0.35f) (hsv[2] + 0.35f).coerceAtMost(0.55f) else (hsv[2] * 0.55f).coerceAtLeast(0.15f)
    return Color(android.graphics.Color.HSVToColor(hsv))
}

fun immersiveBackground(color: Color, isDark: Boolean = false): Color {
    val luminance = 0.2126f * color.red + 0.7152f * color.green + 0.0722f * color.blue
    val base = if (luminance <= 0.55f) color else {
        val hsl = FloatArray(3)
        rgbToHsl(color.red, color.green, color.blue, hsl)
        hsl[2] = minOf(hsl[2], 0.40f)
        hslToColor(hsl[0], hsl[1], hsl[2])
    }
    return darkenTowardBlack(base, isDark, threshold = 0.30f, maxMix = 0.50f)
}

// Dark mode only: nudge overly bright immersive colors toward black so immersive pages, their
// accent buttons, and the dropdown stay readable on a dark screen. No-op in light mode or when
// the color is already dark enough (luminance <= threshold); mix scales from 0 at the threshold
// up to maxMix at pure white, so mildly-bright colors are barely touched and glaring ones cut more.
fun darkenTowardBlack(color: Color, isDark: Boolean, threshold: Float, maxMix: Float): Color {
    if (!isDark) return color
    val luminance = 0.2126f * color.red + 0.7152f * color.green + 0.0722f * color.blue
    if (luminance <= threshold) return color
    val t = ((luminance - threshold) / (1f - threshold)).coerceIn(0f, 1f)
    return lerp(color, Color.Black, t * maxMix)
}

@Composable
fun isDarkTheme(): Boolean = MaterialTheme.colorScheme.background.luminance() < 0.05f

private fun rgbToHsl(r: Float, g: Float, b: Float, out: FloatArray) {
    val max = maxOf(r, g, b)
    val min = minOf(r, g, b)
    val l = (max + min) / 2f
    val d = max - min
    var h = 0f
    var s = 0f
    if (d != 0f) {
        s = if (l < 0.5f) d / (max + min) else d / (2f - max - min)
        h = when (max) {
            r -> ((g - b) / d + (if (g < b) 6f else 0f))
            g -> ((b - r) / d + 2f)
            else -> ((r - g) / d + 4f)
        }
        h /= 6f
    }
    out[0] = h
    out[1] = s
    out[2] = l
}

private fun hslToColor(h: Float, s: Float, l: Float): Color {
    if (s == 0f) return Color(l, l, l)
    val q = if (l < 0.5f) l * (1f + s) else l + s - l * s
    val p = 2f * l - q
    fun hue(t0: Float): Float {
        var t = t0
        if (t < 0f) t += 1f
        if (t > 1f) t -= 1f
        return when {
            t < 1f / 6f -> p + (q - p) * 6f * t
            t < 1f / 2f -> q
            t < 2f / 3f -> p + (q - p) * (2f / 3f - t) * 6f
            else -> p
        }
    }
    return Color(hue(h + 1f / 3f), hue(h), hue(h - 1f / 3f))
}

@Composable
fun immersiveCardColor(immersive: Boolean): Color =
    if (immersive) Color.White.copy(alpha = 0.08f)
    else MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.55f)

@Composable
fun immersiveTextColor(immersive: Boolean): Color =
    if (immersive) Color.White
    else MaterialTheme.colorScheme.onSurface

@Composable
fun immersiveMutedColor(immersive: Boolean): Color =
    if (immersive) Color.White.copy(alpha = 0.6f)
    else MaterialTheme.colorScheme.onSurfaceVariant

/**
 * How far above the bottom of the hero item the artwork finishes fading: the
 * title and buttons hang at the very bottom of it, so the title reaches the
 * top of the screen this far before the hero is scrolled off.
 */
private val ImmersiveHeaderEndReserve = 210.dp

/**
 * Distance from the hero item's bottom edge up to the vertical centre of the
 * title: the title block starts 90dp above that edge and the title line itself
 * is 46dp tall. The snap parks the hero's bottom this far below the header
 * row's centre, which puts the title on the same line as the buttons.
 */
private val ImmersiveTitleCenterFromHeroBottom = 67.dp

private fun immersiveHeroHeight(
    listState: androidx.compose.foundation.lazy.LazyListState
): Float = listState.layoutInfo.visibleItemsInfo
    .firstOrNull { it.index == 0 }?.size?.toFloat() ?: 0f

/**
 * How far the hero has been scrolled off the top. Reports as past everything
 * once the hero itself is gone, so callers never mistake a scrolled-away hero
 * for an untouched one.
 */
private fun immersiveHeroOffset(
    listState: androidx.compose.foundation.lazy.LazyListState
): Float = if (listState.firstVisibleItemIndex == 0) {
    listState.firstVisibleItemScrollOffset.toFloat()
} else {
    Float.MAX_VALUE
}

private fun immersiveSnapOffsetPx(
    listState: androidx.compose.foundation.lazy.LazyListState,
    reservePx: Float
): Float = (immersiveHeroHeight(listState) - reservePx).coerceAtLeast(0f)

/**
 * Scroll-driven state for an immersive detail header. [ImmersiveHeaderState.collapse]
 * runs 0f → 1f and hits 1f when the title block reaches the top of the screen.
 * [ImmersiveHeaderState.scrollOffsetPx] is the raw hero scroll in pixels. Both
 * are derived straight from the LazyListState, so there is no new animation
 * spec, and jelly overscroll leaves them untouched because that effect is
 * visual-only.
 */
data class ImmersiveHeaderState(
    val collapse: Float,
    val scrollOffsetPx: Float
)

@Composable
fun rememberImmersiveHeaderState(
    listState: androidx.compose.foundation.lazy.LazyListState,
    endReserve: androidx.compose.ui.unit.Dp = ImmersiveHeaderEndReserve
): ImmersiveHeaderState {
    val reservePx = with(LocalDensity.current) { endReserve.toPx() }
    return derivedStateOf {
        val heroHeight = immersiveHeroHeight(listState)
        if (listState.firstVisibleItemIndex > 0 || heroHeight <= 0f) {
            ImmersiveHeaderState(collapse = 1f, scrollOffsetPx = heroHeight)
        } else {
            val offset = listState.firstVisibleItemScrollOffset.toFloat().coerceIn(0f, heroHeight)
            val travel = heroHeight - reservePx
            ImmersiveHeaderState(
                collapse = if (travel <= 0f) 0f else (offset / travel).coerceIn(0f, 1f),
                scrollOffsetPx = offset
            )
        }
    }.value
}

/**
 * Magnetic detent for an immersive detail header. When a scroll comes to rest
 * within [captureRadius] of the point where the title's centre lines up with
 * [headerRowCenter] — the vertical centre of the back / more button row — the
 * list is pulled the last few pixels onto that point and a light haptic tick
 * fires. A fling that blows past the point is left alone, and the pull uses
 * the same damped oscillator family as the jelly overscroll rather than a
 * Material spec. The magnet disarms after each catch until the list has moved
 * [reArmDistance] clear, so nudging around the point never re-glues it.
 *
 * Returns true from the moment the title reaches that line and stays true for
 * the rest of the page, clearing only once the list travels back up past it.
 * While it is true the title itself answers taps as "go back", which is what
 * keeps the page escapable after [rememberImmersiveHeaderActionsVisible] has
 * retired the button row.
 */
@Composable
fun rememberImmersiveHeaderSnap(
    listState: androidx.compose.foundation.lazy.LazyListState,
    headerRowCenter: androidx.compose.ui.unit.Dp,
    enabled: Boolean = true,
    captureRadius: androidx.compose.ui.unit.Dp = 48.dp,
    reArmDistance: androidx.compose.ui.unit.Dp = 90.dp
): Boolean {
    val reservePx = with(LocalDensity.current) {
        (headerRowCenter + ImmersiveTitleCenterFromHeroBottom).toPx()
    }
    val capturePx = with(LocalDensity.current) { captureRadius.toPx() }
    val reArmPx = with(LocalDensity.current) { reArmDistance.toPx() }
    val physics = LocalJigglePhysicsSettings.current
    val haptics = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()

    var armed by remember { mutableStateOf(true) }
    // Our own pull-in also reports isScrollInProgress, so the handler needs to
    // know when the scroll it is seeing belongs to us.
    val snapping = remember { BooleanArray(1) }

    LaunchedEffect(listState, enabled, reservePx, capturePx) {
        if (!enabled) {
            armed = true
            return@LaunchedEffect
        }
        snapshotFlow { listState.isScrollInProgress }
            .distinctUntilChanged()
            .collect { inProgress ->
                if (inProgress || snapping[0]) return@collect

                val target = immersiveSnapOffsetPx(listState, reservePx)
                val offset = immersiveHeroOffset(listState)
                if (!armed || target <= 0f || abs(offset - target) > capturePx) return@collect

                armed = false
                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                snapping[0] = true
                scope.launch {
                    try {
                        animateJellyScrollTo(listState, offset, target, physics)
                    } finally {
                        snapping[0] = false
                    }
                }
            }
    }

    // Re-arm from the live scroll position rather than the resting one. Each
    // frame the list spends further from the detent than [reArmDistance] the
    // magnet wakes back up, so a gesture that drifts away and comes straight
    // back can catch again — with an idle-only check its final position could
    // sit inside the dead zone and leave it permanently off.
    LaunchedEffect(listState, enabled, reservePx, reArmPx) {
        if (!enabled) return@LaunchedEffect
        snapshotFlow {
            abs(immersiveHeroOffset(listState) - immersiveSnapOffsetPx(listState, reservePx)) > reArmPx
        }
            .distinctUntilChanged()
            .collect { farAway -> if (farAway) armed = true }
    }

    return derivedStateOf {
        val target = immersiveSnapOffsetPx(listState, reservePx)
        enabled && target > 0f && immersiveHeroOffset(listState) >= target
    }.value
}

/**
 * Direction-driven visibility for an immersive header's actions: they retire as
 * soon as the list travels down by [scrollThreshold] and return as soon as it
 * travels back up by the same amount, regardless of how far into the page the
 * user is. Position never decides anything, so a snap releasing or a fling
 * coasting past a point cannot bring the buttons back on their own.
 *
 * The threshold is a drift accumulator rather than an instantaneous read, which
 * absorbs the sub-pixel jitter at the tail of a fling that would otherwise make
 * the row strobe.
 */
@Composable
fun rememberImmersiveHeaderActionsVisible(
    listState: androidx.compose.foundation.lazy.LazyListState,
    enabled: Boolean = true,
    scrollThreshold: androidx.compose.ui.unit.Dp = 24.dp
): Boolean {
    if (!enabled) return true
    val thresholdPx = with(LocalDensity.current) { scrollThreshold.toPx() }
    val visible = remember { mutableStateOf(true) }

    LaunchedEffect(listState, thresholdPx) {
        var previous = listState.firstVisibleItemIndex to listState.firstVisibleItemScrollOffset
        var drift = 0f
        snapshotFlow { listState.firstVisibleItemIndex to listState.firstVisibleItemScrollOffset }
            .collect { current ->
                if (current.first != previous.first) {
                    // Crossing item bounds means the list moved further in one
                    // frame than any offset delta can describe, so take the
                    // direction at face value and trip immediately.
                    drift = if (current.first > previous.first) thresholdPx else -thresholdPx
                } else {
                    drift += (current.second - previous.second).toFloat()
                }
                previous = current
                if (abs(drift) >= thresholdPx) {
                    visible.value = drift < 0f
                    drift = 0f
                }
            }
    }
    return visible.value
}

/**
 * Seeks the list from [from] to [to] by integrating the project's damped
 * harmonic oscillator frame by frame. Damping is heavier than the overscroll
 * jiggle on purpose: a detent should click into place with a hint of
 * overshoot, not wobble. Stops early if the list runs into its own bound.
 */
private suspend fun animateJellyScrollTo(
    listState: androidx.compose.foundation.lazy.LazyListState,
    from: Float,
    to: Float,
    physics: JigglePhysicsSettings
) {
    val omega0 = sqrt(physics.stiffness / physics.mass)
    val damping = 0.55f * 2f * omega0
    val stiffnessOverMass = omega0 * omega0

    listState.scroll {
        var position = from
        var actual = from
        var velocity = 0f
        var lastFrame = withFrameNanos { it }
        val deadline = lastFrame + 900_000_000L

        while (true) {
            val now = withFrameNanos { it }
            if (now >= deadline) {
                scrollBy(to - actual)
                break
            }
            val dt = ((now - lastFrame) / 1_000_000_000f).coerceAtMost(1f / 30f)
            lastFrame = now

            velocity += (stiffnessOverMass * (to - position) - damping * velocity) * dt
            position += velocity * dt

            val wanted = position - actual
            val consumed = scrollBy(wanted)
            actual += consumed
            if (abs(consumed - wanted) > 0.5f) break
            if (abs(to - actual) < 0.5f && abs(velocity) < 1f) break
        }
    }
}

/**
 * Jelly fade + slide used to retire the header's actions while the title is
 * magnetically parked. Reuses the same bouncy spec the jelly buttons already
 * animate with. [slideLeft] mirrors the direction for left-aligned controls.
 */
@Composable
fun ImmersiveActionVisibility(
    visible: Boolean,
    slideLeft: Boolean = false,
    content: @Composable () -> Unit
) {
    val slide: (Int) -> Int = if (slideLeft) { x -> -x / 2 } else { x -> x / 2 }
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow)) +
            slideInHorizontally(spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow), initialOffsetX = slide) +
            scaleIn(spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow), initialScale = 0.7f),
        exit = fadeOut(spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow)) +
            slideOutHorizontally(spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow), targetOffsetX = slide) +
            scaleOut(spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow), targetScale = 0.7f)
    ) { content() }
}

/**
 * Immersive artwork layer. It sits BEHIND the scrolling content and does not
 * participate in the jelly overscroll. On scroll it drifts up at
 * [parallaxFactor] of the content's speed and fades its own alpha to 0,
 * revealing the flat [immersiveColor] background underneath (so there is never
 * a blend seam). The caller supplies the image via [artwork]; the bottom
 * gradient is applied here. Place inside a Box and pass
 * `Modifier.align(Alignment.TopCenter)`.
 */
@Composable
fun ImmersiveArtworkBackdrop(
    listState: androidx.compose.foundation.lazy.LazyListState,
    immersiveColor: Color,
    modifier: Modifier = Modifier,
    parallaxFactor: Float = 0.6f,
    artwork: @Composable () -> Unit
) {
    val header = rememberImmersiveHeaderState(listState)
    Box(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .graphicsLayer {
                alpha = 1f - header.collapse
                translationY = -header.scrollOffsetPx * parallaxFactor
            }
    ) {
        artwork()
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(260.dp)
                .align(Alignment.BottomCenter)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(immersiveColor.copy(alpha = 0f), immersiveColor)
                    )
                )
        )
    }
}

/**
 * Immersive status-bar scrim: a short band of the page's own background colour
 * that fades to transparent going down, sized to the status bar inset plus a
 * small tail. Draw it above the scrolling content so the clock and battery
 * icons stay readable once the artwork is gone. Place inside a Box and pass
 * `Modifier.align(Alignment.TopCenter)`.
 *
 * Cross-fades with the hero artwork off the same [collapse] progress the
 * artwork backdrop uses, so the band is invisible while artwork sits under it
 * and reaches full strength exactly when the artwork finishes dissolving into
 * the background rather than stacking on top of it.
 */
@Composable
fun ImmersiveTopScrim(
    listState: androidx.compose.foundation.lazy.LazyListState,
    color: Color,
    height: androidx.compose.ui.unit.Dp,
    modifier: Modifier = Modifier
) {
    val header = rememberImmersiveHeaderState(listState)
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .graphicsLayer { alpha = header.collapse }
            .background(
                Brush.verticalGradient(
                    colorStops = arrayOf(
                        0f to color,
                        0.45f to color.copy(alpha = 0.72f),
                        1f to color.copy(alpha = 0f)
                    )
                )
            )
    )
}

val LocalAppBackdrop = staticCompositionLocalOf<Backdrop?> { null }



val AppCornerRadius = 32.dp

fun Modifier.applyHazeAndBackdrop(hazeState: HazeState?): Modifier = composed {
    var modifier = this
    if (hazeState != null && android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
        modifier = modifier.haze(state = hazeState)
    } else if (android.os.Build.VERSION.SDK_INT in 31..33) {
        val backdrop = LocalAppBackdrop.current
        if (backdrop is com.aeswox.arcmusic.backdrop.backdrops.LayerBackdrop) {
            modifier = modifier.layerBackdrop(backdrop)
        }
    }
    modifier
}

fun Modifier.glassEffect(
    hazeState: HazeState?,
    tintTransparency: Float,
    noiseFactor: Float,
    shape: Shape = RoundedCornerShape(AppCornerRadius),
    forceFallback: Boolean = false,
    tintOverride: Color? = null,
    tintAlphaOverride: Float? = null
): Modifier = composed {
    // Detect dark mode from the actual applied color scheme (luminance < 0.05 = dark background).
    val bgLuminance = MaterialTheme.colorScheme.background.luminance()
    val isDark = bgLuminance < 0.05f
    // tintOverride lets callers (e.g. immersive mini-player) supply an animated custom tint;
    // when null we fall back to the theme-aware white/black glass.
    val tintBase = tintOverride ?: if (isDark) Color.Black else Color.White
    // Dark mode uses slightly higher alpha to keep the glass visible against black.
    val adjustedAlpha = tintAlphaOverride ?: if (isDark) (tintTransparency + 0.3f).coerceAtMost(0.85f) else tintTransparency

    if (!forceFallback && android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
        if (hazeState != null) {
            this.hazeChild(
                state = hazeState,
                shape = shape,
                style = HazeStyle(
                    blurRadius = 24.dp,
                    tint = tintBase.copy(alpha = adjustedAlpha),
                    noiseFactor = noiseFactor
                )
            )
        } else {
            this.background(tintBase.copy(alpha = adjustedAlpha), shape)
        }
    } else if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
        val backdrop = LocalAppBackdrop.current
        if (backdrop != null && shape is CornerBasedShape) {
            val density = LocalDensity.current
            val blurPx = with(density) { 24.dp.toPx() } * 0.5f // scale factor
            
            this.drawBackdrop(
                backdrop = backdrop,
                shape = { shape },
                effects = { blur(blurPx) },
                onDrawSurface = { drawRect(tintBase.copy(alpha = adjustedAlpha)) },
                backdropScale = 0.5f
            )
        } else {
            this.background(tintBase.copy(alpha = adjustedAlpha + 0.3f), shape)
        }
    } else {
        // Fallback to semi-transparent background for Android 11 and below
        this.background(tintBase.copy(alpha = adjustedAlpha + 0.3f), shape)
    }
}

@Composable
fun AnimatedGlowBackground(modifier: Modifier = Modifier, glowIntensity: Float, color: Color = Color(0xFF5E90A7)) {
    val animatedColor by animateColorAsState(
        targetValue = color,
        animationSpec = tween(durationMillis = 1000)
    )
    val infiniteTransition = rememberInfiniteTransition(label = "glowTransition")
    val animatedAlpha by infiniteTransition.animateFloat(
        initialValue = glowIntensity * 0.75f,
        targetValue = glowIntensity,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 4000, easing = EaseInOutSine),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glowAlpha"
    )
    val animatedOffsetX by infiniteTransition.animateFloat(
        initialValue = -30f,
        targetValue = 30f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 6000, easing = EaseInOutSine),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glowOffsetX"
    )
    val animatedOffsetY by infiniteTransition.animateFloat(
        initialValue = -20f,
        targetValue = 20f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 7500, easing = EaseInOutSine),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glowOffsetY"
    )
    Box(modifier = modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Box(
            modifier = Modifier
                .align(Alignment.Center)
                .offset(x = animatedOffsetX.dp, y = animatedOffsetY.dp)
                .size(1000.dp)
                .graphicsLayer {
                    scaleX = 1.2f
                    scaleY = 1.8f
                }
                .background(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            animatedColor.copy(alpha = animatedAlpha),
                            animatedColor.copy(alpha = animatedAlpha * 0.6f),
                            animatedColor.copy(alpha = 0f)
                        )
                    ),
                    shape = CircleShape
                )
        )
    }
}

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun MiniPlayer(
    modifier: Modifier = Modifier, 
    title: String = "Care", 
    artist: String = "Conan Gray", 
    imageUrl: String = "https://lh3.googleusercontent.com/aida-public/AB6AXuDcxr5OkSQfpI_jTkSInZTLTIQNPElvx4VTAwf6InyR5cV2DD4SLzOgYsBC1gNArokFiZMFSwmKVi6VW-OeV6ouanmXDcfN4aD-RtGJFuMNyYZTx5P6VkXi-b4eY5GWUNpAaGeTkiqgkdzS6Of-mtUzJt7rz9IYbGhj7V3IcTi8iHjlof7t5fJzN09WsP72jlTq2o-VEsgIRAPXzreisxiQKK8kmsYEbFlDl442gyzxMfa0UGT2M3aJ5eafCHY0tM_wkFed6Lty8vDU", 
    hazeState: HazeState? = null, 
    tintTransparency: Float = 0.4f, 
    noiseFactor: Float = 0.06f,
    immersive: Boolean = false,
    immersiveBg: Color? = null,
    isPlaying: Boolean = false,
    onPlayPauseClick: () -> Unit = {},
    onSkipNextClick: () -> Unit = {},
    onClick: () -> Unit = {}, 
    onDismiss: () -> Unit = {},
    animatedVisibilityScope: AnimatedVisibilityScope? = null,
    horizontalPadding: androidx.compose.ui.unit.Dp = 24.dp,
    applyShapeAndBackground: Boolean = true,
    enableSwipeToDismiss: Boolean = true
) {
    val coroutineScope = rememberCoroutineScope()
    val offsetY = remember { Animatable(0f) }

    // Immersive tint: a same-hue "raised card" — a slightly lighter shade of the page's own
    // background — so the mini-player blends into an immersive detail page. Glass blur/noise/shape
    // are untouched; only the tint color, its alpha and the ink colors change. Animates over 600ms
    // (matching the page transition) as we enter/leave an immersive route. Non-immersive keeps the
    // theme-aware white/black glass and theme ink.
    val themeIsDark = MaterialTheme.colorScheme.background.luminance() < 0.05f
    val themeTintBase = if (themeIsDark) Color.Black else Color.White
    val themeAlpha = if (themeIsDark) (tintTransparency + 0.3f).coerceAtMost(0.85f) else tintTransparency
    val raisedTarget = immersiveBg?.let { lerp(it, Color.White, 0.18f) }
    val glassTint by animateColorAsState(
        targetValue = if (immersive && raisedTarget != null) raisedTarget else themeTintBase,
        animationSpec = tween(durationMillis = 600),
        label = "miniPlayerGlassTint"
    )
    val glassAlpha by animateFloatAsState(
        targetValue = if (immersive) tintTransparency else themeAlpha,
        animationSpec = tween(durationMillis = 600),
        label = "miniPlayerGlassAlpha"
    )
    val titleColor by animateColorAsState(
        targetValue = if (immersive) Color.White else MaterialTheme.colorScheme.onSurface,
        animationSpec = tween(durationMillis = 600),
        label = "miniPlayerTitleColor"
    )
    val artistColor by animateColorAsState(
        targetValue = if (immersive) Color.White.copy(alpha = 0.6f) else MaterialTheme.colorScheme.onSurfaceVariant,
        animationSpec = tween(durationMillis = 600),
        label = "miniPlayerArtistColor"
    )

    @OptIn(ExperimentalSharedTransitionApi::class)
    val sharedScope = LocalSharedTransitionScope.current
    @OptIn(ExperimentalSharedTransitionApi::class)
    val navScope = animatedVisibilityScope ?: LocalNavAnimatedVisibilityScope.current
    
    val jiggleSettings = LocalJigglePhysicsSettings.current
    @OptIn(ExperimentalSharedTransitionApi::class)
    val boundsTransform = remember {
        { _: Rect, _: Rect ->
            spring<Rect>(
                dampingRatio = androidx.compose.animation.core.Spring.DampingRatioNoBouncy,
                stiffness = androidx.compose.animation.core.Spring.StiffnessLow
            )
        }
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .offset { IntOffset(0, offsetY.value.roundToInt()) }
            .then(if (enableSwipeToDismiss) Modifier.pointerInput(Unit) {
                detectVerticalDragGestures(
                    onDragEnd = {
                        coroutineScope.launch {
                            if (offsetY.value > 150f) {
                                onDismiss()
                                offsetY.snapTo(0f)
                            } else {
                                offsetY.animateTo(0f)
                            }
                        }
                    },
                    onDragCancel = { 
                        coroutineScope.launch { offsetY.animateTo(0f) }
                    },
                    onVerticalDrag = { change, dragAmount ->
                        change.consume()
                        coroutineScope.launch { offsetY.snapTo(offsetY.value + dragAmount) }
                    }
                )
            } else Modifier)
            .padding(horizontal = horizontalPadding)
            .fillMaxWidth()
            .height(76.dp)
            .then(if (applyShapeAndBackground) Modifier.clip(RoundedCornerShape(AppCornerRadius)).glassEffect(hazeState, tintTransparency, noiseFactor, tintOverride = glassTint, tintAlphaOverride = glassAlpha) else Modifier)
            .jellyClick(scaleDownTo = 0.92f, onClick = onClick)
            .padding(horizontal = 16.dp)
    ) {
        AsyncImage(
            model = imageUrl,
            contentDescription = title,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(16.dp))
        )
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title, 
                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold), 
                color = titleColor, 
                maxLines = 1
            )
            Text(
                text = artist, 
                style = MaterialTheme.typography.bodyMedium, 
                color = artistColor, 
                maxLines = 1
            )
        }
        JellyIconButton(onClick = onPlayPauseClick) {
            Icon(
                imageVector = if (isPlaying) com.aeswox.arcmusic.ui.components.HugeIcons.Pause else com.aeswox.arcmusic.ui.components.HugeIcons.Play,
                contentDescription = if (isPlaying) "Pause" else "Play",
                tint = titleColor,
                modifier = Modifier.size(22.dp)
            )
        }
        JellyIconButton(onClick = onSkipNextClick) {
            Icon(
                imageVector = com.aeswox.arcmusic.ui.components.HugeIcons.Next, 
                contentDescription = "Skip Next", 
                tint = titleColor,
                modifier = Modifier.size(22.dp)
            )
        }
    }
}

@Composable
fun BottomNavigation(
    currentTab: Int = 0,
    onTabSelected: (Int) -> Unit = {},
    modifier: Modifier = Modifier, 
    hazeState: HazeState? = null, 
    tintTransparency: Float = 0.4f, 
    noiseFactor: Float = 0.06f
) {
    val rowInteractionSource = remember { MutableInteractionSource() }
    val tab0InteractionSource = remember { MutableInteractionSource() }
    val tab1InteractionSource = remember { MutableInteractionSource() }
    val tab2InteractionSource = remember { MutableInteractionSource() }

    val rowPressed by rowInteractionSource.collectIsPressedAsState()
    val tab0Pressed by tab0InteractionSource.collectIsPressedAsState()
    val tab1Pressed by tab1InteractionSource.collectIsPressedAsState()
    val tab2Pressed by tab2InteractionSource.collectIsPressedAsState()

    val anyPressed = rowPressed || tab0Pressed || tab1Pressed || tab2Pressed

    val rowScale by animateFloatAsState(
        targetValue = if (anyPressed) 0.96f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "navbar_jelly_scale"
    )

    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .height(70.dp)
            .clip(RoundedCornerShape(AppCornerRadius))
            .glassEffect(hazeState, tintTransparency, noiseFactor)
            .graphicsLayer {
                scaleX = rowScale
                scaleY = rowScale
            }
            .clickable(
                interactionSource = rowInteractionSource,
                indication = null,
                onClick = {}
            )
            .padding(horizontal = 20.dp)
    ) {
        val tabCount = 3
        val tabWidth = maxWidth / tabCount
        val pillWidth = 64.dp
        val pillHeight = 44.dp

        val targetOffset = tabWidth * (currentTab.coerceIn(0, 2) + 0.5f) - (pillWidth / 2)
        val animatedOffset by animateDpAsState(
            targetValue = targetOffset,
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioMediumBouncy,
                stiffness = Spring.StiffnessMediumLow
            ),
            label = "navbar_pill_offset"
        )

        // Sliding Pill Highlight Indicator (One UI 7 capsule style)
        Box(
            modifier = Modifier
                .offset(x = animatedOffset)
                .align(Alignment.CenterStart)
                .size(width = pillWidth, height = pillHeight)
                .background(
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.09f),
                    shape = CircleShape
                )
        )

        // Navigation Items
        Row(
            modifier = Modifier.fillMaxSize(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            NavBarItem(
                selected = currentTab == 0,
                icon = HugeIcons.Home,
                selectedIcon = HugeIcons.HomeFilled,
                contentDescription = "Home",
                interactionSource = tab0InteractionSource,
                modifier = Modifier.weight(1f),
                onClick = { onTabSelected(0) }
            )
            NavBarItem(
                selected = currentTab == 1,
                icon = HugeIcons.Search,
                selectedIcon = HugeIcons.SearchFilled,
                contentDescription = "Search",
                interactionSource = tab1InteractionSource,
                modifier = Modifier.weight(1f),
                onClick = { onTabSelected(1) }
            )
            NavBarItem(
                selected = currentTab == 2,
                icon = HugeIcons.Library,
                selectedIcon = HugeIcons.LibraryFilled,
                contentDescription = "Library",
                interactionSource = tab2InteractionSource,
                modifier = Modifier.weight(1f),
                onClick = { onTabSelected(2) }
            )
        }
    }
}

/**
 * Animated nav bar item that transitions between outlined (unselected) and filled (selected),
 * matching the One UI 7 icon design with cutout negative space and soft capsule highlight.
 */
@Composable
private fun NavBarItem(
    selected: Boolean,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    selectedIcon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    interactionSource: MutableInteractionSource,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    // Icon tint: Crisp high-contrast when selected, muted onSurfaceVariant when unselected
    val iconTint by animateColorAsState(
        targetValue = if (selected)
            MaterialTheme.colorScheme.onSurface
        else
            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.65f),
        animationSpec = spring(stiffness = Spring.StiffnessLow),
        label = "icon_tint_$contentDescription"
    )

    // Subtle scale pop when selected
    val iconScale by animateFloatAsState(
        targetValue = if (selected) 1.05f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "icon_scale_$contentDescription"
    )

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .fillMaxHeight()
            .jellyClick(interactionSource = interactionSource, scaleDownTo = 0.88f) { onClick() }
    ) {
        Crossfade(
            targetState = selected,
            animationSpec = tween(durationMillis = 200),
            label = "icon_crossfade_$contentDescription"
        ) { isSelected ->
            Icon(
                imageVector = if (isSelected) selectedIcon else icon,
                contentDescription = contentDescription,
                tint = iconTint,
                modifier = Modifier
                    .size(24.dp)
                    .graphicsLayer {
                        scaleX = iconScale
                        scaleY = iconScale
                    }
            )
        }
    }
}


@Composable
fun AppPrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: androidx.compose.ui.graphics.vector.ImageVector? = null,
    containerColor: Color = MaterialTheme.colorScheme.primary,
    contentColor: Color = MaterialTheme.colorScheme.onPrimary,
    contentPadding: PaddingValues = PaddingValues(horizontal = 24.dp, vertical = 12.dp)
) {
    JellyButton(
        onClick = onClick,
        colors = ButtonDefaults.buttonColors(
            containerColor = containerColor,
            contentColor = contentColor
        ),
        shape = RoundedCornerShape(50),
        contentPadding = contentPadding,
        elevation = ButtonDefaults.buttonElevation(defaultElevation = 2.dp),
        modifier = modifier
    ) {
        if (icon != null) {
            Icon(imageVector = icon, contentDescription = null, modifier = Modifier.padding(end = 8.dp))
        }
        Text(text, fontWeight = FontWeight.Bold, fontSize = 16.sp)
    }
}

@Composable
fun DialogTextButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary
) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.Bold,
        color = color,
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .jellyClick(onClick = onClick)
            .padding(8.dp)
    )
}

@Composable
fun ReorderableDragHandle(
    modifier: Modifier = Modifier,
    onDragStart: () -> Unit,
    onDragEnd: () -> Unit,
    onDragCancel: () -> Unit,
    onVerticalDrag: (change: androidx.compose.ui.input.pointer.PointerInputChange, dragAmount: Float) -> Unit,
    tint: Color = MaterialTheme.colorScheme.onSurfaceVariant
) {
    Icon(
        imageVector = Icons.Default.DragHandle, 
        contentDescription = "Reorder",
        tint = tint,
        modifier = modifier.pointerInput(Unit) {
            detectVerticalDragGestures(
                onDragStart = { _ -> onDragStart() },
                onDragEnd = onDragEnd,
                onDragCancel = onDragCancel,
                onVerticalDrag = onVerticalDrag
            )
        }
    )
}

@Composable
fun AppIconButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tint: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    size: androidx.compose.ui.unit.Dp = 24.dp
) {
    JellyIconButton(onClick = onClick, modifier = modifier) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = tint,
            modifier = Modifier.size(size)
        )
    }
}

@Composable
fun ArcDropdownMenu(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    hazeState: HazeState? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismissRequest,
        shape = RoundedCornerShape(AppCornerRadius),
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = modifier
            .width(220.dp)
            .clip(RoundedCornerShape(AppCornerRadius))
    ) {
        content()
    }
}

@Composable
fun ArcDropdownMenuItem(
    text: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit,
    isDestructive: Boolean = false
) {
    val color = if (isDestructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
    val iconTint = if (isDestructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
    
    DropdownMenuItem(
        text = { 
            Text(text, style = MaterialTheme.typography.bodyMedium, color = color) 
        },
        onClick = onClick,
        leadingIcon = { Icon(icon, contentDescription = null, tint = iconTint) }
    )
}

@Composable
fun TrackListItemSkeleton(
    modifier: Modifier = Modifier,
    showCover: Boolean = true,
    showTrackNumber: Boolean = false
) {
    Row(modifier = modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        if (showCover) {
            Box(modifier = Modifier.size(56.dp).clip(RoundedCornerShape(8.dp)).background(MaterialTheme.colorScheme.surfaceVariant))
            Spacer(modifier = Modifier.width(16.dp))
        } else if (showTrackNumber) {
            Box(modifier = Modifier.size(24.dp).clip(RoundedCornerShape(4.dp)).background(MaterialTheme.colorScheme.surfaceVariant))
            Spacer(modifier = Modifier.width(16.dp))
        }
        Column(modifier = Modifier.weight(1f)) {
            Box(modifier = Modifier.height(16.dp).fillMaxWidth(0.6f).background(MaterialTheme.colorScheme.surfaceVariant))
            Spacer(modifier = Modifier.height(8.dp))
            Box(modifier = Modifier.height(12.dp).fillMaxWidth(0.4f).background(MaterialTheme.colorScheme.surfaceVariant))
        }
    }
}

@Composable
fun SkeletonImageGridItem() {
    Column(modifier = Modifier.width(120.dp).padding(4.dp)) {
        Box(modifier = Modifier.size(120.dp).clip(RoundedCornerShape(8.dp)).background(MaterialTheme.colorScheme.surfaceVariant))
        Spacer(modifier = Modifier.height(8.dp))
        Box(modifier = Modifier.height(14.dp).fillMaxWidth(0.8f).background(MaterialTheme.colorScheme.surfaceVariant))
        Spacer(modifier = Modifier.height(4.dp))
        Box(modifier = Modifier.height(12.dp).fillMaxWidth(0.5f).background(MaterialTheme.colorScheme.surfaceVariant))
    }
}
