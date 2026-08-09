package de.overlai.feature.overlay

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.PixelFormat
import android.os.Build
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.WindowManager
import android.view.animation.DecelerateInterpolator
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy

// CHANGE-MARKER v0.5.2: Overlay-Bubble (M3, siehe CHANGELOG.md)
// Kapselt die gesamte WindowManager-Logik der Overlay-Bubble. Läuft aus dem
// OverlayService heraus (ohne Activity). Verwaltet zwei TYPE_APPLICATION_OVERLAY-Views:
// die immer sichtbare Bubble und das aufklappbare Panel. Jedes Compose-Root bekommt
// einen eigenen OverlayLifecycleOwner (Compose braucht die ViewTree-Owner ohne Activity).
//
// Threading: alle add/update/remove-Aufrufe müssen auf dem Main-Thread laufen (der
// Service nutzt einen Main-Dispatcher-Scope). Alle Operationen sind defensiv gegen
// doppeltes add/remove (isAttachedToWindow-Prüfung bzw. null-Guards).
internal class OverlayWindowController(
    private val context: Context,
    private val chatState: OverlayChatState,
    // Vom Service auf stopSelf() gemappt — gerufen, wenn die Bubble auf den Papierkorb
    // gezogen und dort losgelassen wird.
    private val onRequestStop: () -> Unit,
) {
    private val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager

    // Bubble: ComposeView als direktes Fenster-Root. Touch/Drag/Tap laufen über Compose
    // (Modifier.pointerInput in OverlayBubble) — ein View.OnTouchListener am Root feuert
    // nicht, weil der ComposeView ACTION_DOWN selbst konsumiert (Android-Dispatch-Kontrakt).
    private var bubbleView: ComposeView? = null
    private var bubbleOwner: OverlayLifecycleOwner? = null
    private lateinit var bubbleParams: WindowManager.LayoutParams

    private var panelView: ComposeView? = null
    private var panelOwner: OverlayLifecycleOwner? = null

    // P2.1c/D: aktuelle Panel-Fensterparameter (für Header-Drag → Reposition).
    private var panelParams: WindowManager.LayoutParams? = null

    // Papierkorb-Zone — nur während eines Drags am Fenster. Highlight via State (Hit-Test).
    private var trashView: ComposeView? = null
    private var trashOwner: OverlayLifecycleOwner? = null
    private val trashHighlighted = mutableStateOf(false)

    // Laufende Snap-Animation; bei neuem Drag / Teardown abbrechen.
    private var snapAnimator: ValueAnimator? = null

    private val overlayType: Int
        get() =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            } else {
                @Suppress("DEPRECATION")
                WindowManager.LayoutParams.TYPE_PHONE
            }

    // Bubble anzeigen. Idempotent: doppeltes showBubble() ist ein No-Op.
    fun showBubble() {
        if (bubbleView != null) return

        val params =
            WindowManager.LayoutParams(
                // Feste Pixelgröße (56dp) statt WRAP_CONTENT: ein ComposeView im
                // WindowManager-Overlay misst mit WRAP_CONTENT unzuverlässig 0×0.
                dpToPx(BUBBLE_SIZE_DP),
                dpToPx(BUBBLE_SIZE_DP),
                overlayType,
                // NOT_FOCUSABLE: die Bubble greift keine Tasten/IME ab und lässt die
                // darunterliegende App normal weiterlaufen.
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                PixelFormat.TRANSLUCENT,
            ).apply {
                gravity = Gravity.TOP or Gravity.START
                x = 0
                // y unter der Statusbar starten (statt fixem Rohpixel-Wert).
                y = statusBarHeight() + dpToPx(8f)
            }
        bubbleParams = params

        val owner = OverlayLifecycleOwner()
        val compose =
            ComposeView(context).apply {
                // An DIESEN Lifecycle koppeln (nicht an einen Window-Pool ohne Activity).
                setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnLifecycleDestroyed(owner))
                setContent {
                    OverlayBubble(
                        // Drag-Beginn: Snap-Animation abbrechen + Papierkorb-Zone zeigen.
                        onDragStart = {
                            snapAnimator?.cancel()
                            showTrash()
                        },
                        // Drag: Fenster verschieben (delta-basiert), auf den Bildschirm
                        // clampen (nie off-screen), Papierkorb-Hover live prüfen.
                        onDrag = { dx, dy ->
                            bubbleParams.x = clampX(bubbleParams.x + dx)
                            bubbleParams.y = clampY(bubbleParams.y + dy)
                            bubbleView?.let { windowManager.updateViewLayout(it, bubbleParams) }
                            trashHighlighted.value = isOverTrash()
                        },
                        // Drag-Ende: über Papierkorb → Overlay beenden; sonst → an den Rand snappen.
                        onDragEnd = {
                            val overTrash = isOverTrash()
                            removeTrash()
                            if (overTrash) onRequestStop() else snapToEdge()
                        },
                        onTap = { togglePanel() },
                    )
                }
            }
        owner.attachTo(compose) // Owner setzen + Lifecycle CREATED (vor addView)

        bubbleOwner = owner
        bubbleView = compose
        windowManager.addView(compose, params)
        owner.markResumed() // NACH addView: Recomposer sieht den Übergang → komponiert
    }

    // Panel auf-/zuklappen. Öffnet neben der aktuellen Bubble-Position.
    fun togglePanel() {
        if (panelView != null) {
            removePanel()
        } else {
            showPanel()
        }
    }

    private fun showPanel() {
        if (panelView != null) return

        // P2.1c: Panel ist jetzt Vollsteuerung (Liste/Chat/Modelle) → deutlich größer.
        // Breite ~0.92, Höhe bis ~70% Screen (feste Pixel statt WRAP_CONTENT, damit die
        // interne LazyColumn eine begrenzte Höhe hat und scrollen kann).
        val panelW = (context.resources.displayMetrics.widthPixels * PANEL_WIDTH_FRACTION).toInt()
        val panelH = (context.resources.displayMetrics.heightPixels * PANEL_HEIGHT_FRACTION).toInt()
        val params =
            WindowManager.LayoutParams(
                panelW,
                panelH,
                overlayType,
                // Das Panel MUSS fokussierbar sein (Texteingabe/IME) — daher KEIN
                // NOT_FOCUSABLE. WATCH_OUTSIDE_TOUCH lässt uns einen Tipp außerhalb
                // erkennen und das Panel schließen (Bubble bleibt).
                WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH,
                PixelFormat.TRANSLUCENT,
            ).apply {
                gravity = Gravity.TOP or Gravity.START
                // On-screen halten: neben/unter der Bubble, aber nie über den Rand hinaus.
                x = if (::bubbleParams.isInitialized) bubbleParams.x else 0
                y = if (::bubbleParams.isInitialized) bubbleParams.y + dpToPx(64f) else 360
            }
        clampPanel(params)
        panelParams = params

        val owner = OverlayLifecycleOwner()
        val view =
            ComposeView(context).apply {
                setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnLifecycleDestroyed(owner))
                setContent {
                    OverlayPanel(
                        chat = chatState,
                        onClose = ::removePanel,
                        // P2.1c/D: Drag am Header verschiebt das Panel-Fenster live …
                        onHeaderDrag = { dx, dy -> movePanelBy(dx, dy) },
                        // … und beim Loslassen wandert die Bubble an die Panel-Position,
                        // snappt an den Rand und das Panel öffnet dort erneut (zu → auf).
                        onHeaderDragEnd = { repositionBubbleToPanelThenReopen() },
                    )
                }
            }
        owner.attachTo(view)
        // Außentipp schließt das Panel (Bubble bleibt).
        view.setOnTouchListener { _, event ->
            if (event.action == MotionEvent.ACTION_OUTSIDE) {
                removePanel()
                true
            } else {
                false
            }
        }

        panelOwner = owner
        panelView = view
        windowManager.addView(view, params)
        owner.markResumed()
        // Einmalig prüfen, ob ein Key hinterlegt ist (blendet sonst einen Hinweis ein).
        chatState.checkKey()
    }

    private fun removePanel() {
        panelView?.let { view ->
            if (view.isAttachedToWindow) windowManager.removeView(view)
        }
        panelOwner?.destroy()
        panelView = null
        panelOwner = null
        panelParams = null
    }

    // P2.1c/D: Panel-Fenster live um (dx,dy) verschieben (Header-Drag), on-screen geclampt.
    private fun movePanelBy(
        dx: Int,
        dy: Int,
    ) {
        val params = panelParams ?: return
        val view = panelView ?: return
        params.x += dx
        params.y += dy
        clampPanel(params)
        if (view.isAttachedToWindow) windowManager.updateViewLayout(view, params)
    }

    // P2.1c/D: Beim Loslassen des Header-Drags die Bubble an die (obere) Panel-Position setzen,
    // an den Rand snappen, Panel schließen und dort erneut öffnen (Bubble geht zu → auf).
    private fun repositionBubbleToPanelThenReopen() {
        val params = panelParams ?: return
        if (::bubbleParams.isInitialized) {
            bubbleParams.x = clampX(params.x)
            bubbleParams.y = clampY(params.y)
            bubbleView?.let { if (it.isAttachedToWindow) windowManager.updateViewLayout(it, bubbleParams) }
        }
        removePanel()
        snapToEdge()
        showPanel()
    }

    // Alles abräumen (Service-Stop). Reihenfolge: Snap-Animation, Trash, Panel, Bubble.
    fun removeAll() {
        snapAnimator?.cancel()
        snapAnimator = null
        removeTrash()
        removePanel()
        bubbleView?.let { view ->
            if (view.isAttachedToWindow) windowManager.removeView(view)
        }
        bubbleOwner?.destroy()
        bubbleView = null
        bubbleOwner = null
    }

    // --- Papierkorb-Zone (dritte Overlay-View, nur während eines Drags) ---

    private fun showTrash() {
        if (trashView != null) return
        trashHighlighted.value = false
        val params =
            WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                overlayType,
                // Zone darf den Drag NICHT abfangen (Finger ist auf der Bubble).
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
                PixelFormat.TRANSLUCENT,
            ).apply {
                gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
                y = dpToPx(48f) // etwas Abstand vom unteren Rand
            }
        val owner = OverlayLifecycleOwner()
        val view =
            ComposeView(context).apply {
                setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnLifecycleDestroyed(owner))
                setContent { OverlayTrashZone(highlighted = trashHighlighted.value) }
            }
        owner.attachTo(view)
        trashOwner = owner
        trashView = view
        windowManager.addView(view, params)
        owner.markResumed()
    }

    private fun removeTrash() {
        trashView?.let { view ->
            if (view.isAttachedToWindow) windowManager.removeView(view)
        }
        trashOwner?.destroy()
        trashView = null
        trashOwner = null
        trashHighlighted.value = false
    }

    // Liegt der Bubble-Mittelpunkt über der Papierkorb-Zone (unten zentriert)?
    private fun isOverTrash(): Boolean {
        if (trashView == null || !::bubbleParams.isInitialized) return false
        val bubblePx = dpToPx(BUBBLE_SIZE_DP)
        val centerX = bubbleParams.x + bubblePx / 2
        val centerY = bubbleParams.y + bubblePx / 2
        // Zonen-Rechteck: mittig-unten, großzügiger Radius um das Zonen-Zentrum.
        val zoneW = dpToPx(TRASH_HIT_DP)
        val zoneH = dpToPx(TRASH_HIT_DP)
        val zoneCenterX = screenWidth() / 2
        val zoneCenterY = screenHeight() - dpToPx(48f) - dpToPx(TRASH_CENTER_FROM_BOTTOM_DP)
        return kotlin.math.abs(centerX - zoneCenterX) <= zoneW / 2 &&
            kotlin.math.abs(centerY - zoneCenterY) <= zoneH / 2
    }

    // --- Positionierung / Snapping ---

    private fun screenWidth(): Int = context.resources.displayMetrics.widthPixels

    private fun screenHeight(): Int = context.resources.displayMetrics.heightPixels

    private fun statusBarHeight(): Int {
        val id = context.resources.getIdentifier("status_bar_height", "dimen", "android")
        return if (id > 0) context.resources.getDimensionPixelSize(id) else dpToPx(24f)
    }

    private fun clampX(x: Int): Int = x.coerceIn(0, (screenWidth() - dpToPx(BUBBLE_SIZE_DP)).coerceAtLeast(0))

    private fun clampY(y: Int): Int =
        y.coerceIn(statusBarHeight(), (screenHeight() - dpToPx(BUBBLE_SIZE_DP)).coerceAtLeast(statusBarHeight()))

    // Panel on-screen halten (horizontal + vertikal), damit es bei randnaher Bubble nicht
    // teils außerhalb landet. Clampt direkt auf dem übergebenen params-Objekt.
    private fun clampPanel(params: WindowManager.LayoutParams) {
        params.x = params.x.coerceIn(0, (screenWidth() - params.width).coerceAtLeast(0))
        val minY = statusBarHeight()
        params.y = params.y.coerceIn(minY, (screenHeight() - params.height).coerceAtLeast(minY))
    }

    // Beim Loslassen an den näheren horizontalen Rand animieren (ValueAnimator, Main-Thread).
    private fun snapToEdge() {
        if (!::bubbleParams.isInitialized) return
        val view = bubbleView ?: return
        val bubblePx = dpToPx(BUBBLE_SIZE_DP)
        val maxX = (screenWidth() - bubblePx).coerceAtLeast(0)
        val targetX = if (bubbleParams.x + bubblePx / 2 < screenWidth() / 2) 0 else maxX

        snapAnimator?.cancel()
        snapAnimator =
            ValueAnimator.ofInt(bubbleParams.x, targetX).apply {
                duration = SNAP_DURATION_MS
                interpolator = DecelerateInterpolator()
                addUpdateListener { a ->
                    bubbleParams.x = a.animatedValue as Int
                    bubbleParams.y = clampY(bubbleParams.y)
                    if (view.isAttachedToWindow) windowManager.updateViewLayout(view, bubbleParams)
                }
                start()
            }
    }

    // Konfig-Änderung (v.a. Rotation): Bubble neu clampen + an den passenden Rand snappen,
    // offenes Panel neu aufbauen (damit Breite/Position zur neuen Orientierung passen).
    fun onConfigChanged() {
        if (::bubbleParams.isInitialized) {
            bubbleParams.x = clampX(bubbleParams.x)
            bubbleParams.y = clampY(bubbleParams.y)
            bubbleView?.let { if (it.isAttachedToWindow) windowManager.updateViewLayout(it, bubbleParams) }
            snapToEdge()
        }
        if (panelView != null) {
            removePanel()
            showPanel()
        }
    }

    // dp → px anhand der aktuellen Display-Dichte (feste Overlay-Fenstergröße).
    private fun dpToPx(dp: Float): Int =
        TypedValue
            .applyDimension(TypedValue.COMPLEX_UNIT_DIP, dp, context.resources.displayMetrics)
            .toInt()

    private companion object {
        // Feste Bubble-Kantenlänge in dp (muss zur BubbleSize in OverlayBubble.kt passen).
        const val BUBBLE_SIZE_DP = 56f

        // Trefferfläche der Papierkorb-Zone in dp (großzügiger als das sichtbare Icon).
        const val TRASH_HIT_DP = 120f

        // Zonen-Zentrum liegt ~ auf halber Zonenhöhe über dem 48dp-Bottom-Offset.
        const val TRASH_CENTER_FROM_BOTTOM_DP = 40f

        const val SNAP_DURATION_MS = 220L

        // P2.1c: Panel-Fenstergröße als Bruchteil des Bildschirms (Vollsteuerung).
        const val PANEL_WIDTH_FRACTION = 0.92f
        const val PANEL_HEIGHT_FRACTION = 0.70f
    }
}
