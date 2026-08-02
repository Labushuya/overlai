package de.overlai.feature.overlay

import android.content.Context
import android.graphics.PixelFormat
import android.os.Build
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.WindowManager
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
                        // Drag verschiebt das Overlay-Fenster (delta-basiert aus Compose),
                        // geclampt auf den sichtbaren Bereich → nicht mehr verlierbar.
                        onDrag = { dx, dy ->
                            bubbleParams.x = clampX(bubbleParams.x + dx)
                            bubbleParams.y = clampY(bubbleParams.y + dy)
                            bubbleView?.let { windowManager.updateViewLayout(it, bubbleParams) }
                        },
                        // Loslassen: an den nächsten horizontalen Rand snappen.
                        onDragEnd = { snapToEdge() },
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

        val panelWidth = (screenWidth() * 0.86f).toInt()
        // Panel an der Bubble-Seite ausrichten: Bubble links → Panel links, sonst rechts.
        val bubbleOnRight =
            ::bubbleParams.isInitialized && bubbleParams.x + dpToPx(BUBBLE_SIZE_DP) / 2 >= screenWidth() / 2
        val panelX = if (bubbleOnRight) (screenWidth() - panelWidth).coerceAtLeast(0) else 0
        // Panel-y nahe der Bubble, aber so, dass es (grob geschätzte Höhe) on-screen bleibt.
        val rawY = if (::bubbleParams.isInitialized) bubbleParams.y + dpToPx(BUBBLE_SIZE_DP) + dpToPx(8f) else 360
        val estPanelHeight = dpToPx(360f) // Deckelung: Liste max 280dp + Header/Composer
        val panelY =
            rawY.coerceIn(
                statusBarHeight(),
                (screenHeight() - estPanelHeight).coerceAtLeast(statusBarHeight()),
            )

        val params =
            WindowManager.LayoutParams(
                panelWidth,
                WindowManager.LayoutParams.WRAP_CONTENT,
                overlayType,
                // Das Panel MUSS fokussierbar sein (Texteingabe/IME) — daher KEIN
                // NOT_FOCUSABLE. WATCH_OUTSIDE_TOUCH lässt uns einen Tipp außerhalb
                // erkennen und das Panel schließen (Bubble bleibt).
                WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH,
                PixelFormat.TRANSLUCENT,
            ).apply {
                gravity = Gravity.TOP or Gravity.START
                x = panelX
                y = panelY
                // Tastatur soll das fokussierte Eingabefeld nicht verdecken: das Fenster
                // wird bei sichtbarer IME nach oben geschoben statt überdeckt.
                softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_PAN
            }

        val owner = OverlayLifecycleOwner()
        val view =
            ComposeView(context).apply {
                setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnLifecycleDestroyed(owner))
                setContent { OverlayPanel(chat = chatState, onClose = ::removePanel) }
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
    }

    // Alles abräumen (Service-Stop). Reihenfolge: Panel zuerst, dann Bubble.
    fun removeAll() {
        removePanel()
        bubbleView?.let { view ->
            if (view.isAttachedToWindow) windowManager.removeView(view)
        }
        bubbleOwner?.destroy()
        bubbleView = null
        bubbleOwner = null
    }

    // dp → px anhand der aktuellen Display-Dichte (feste Overlay-Fenstergröße).
    private fun dpToPx(dp: Float): Int =
        TypedValue
            .applyDimension(TypedValue.COMPLEX_UNIT_DIP, dp, context.resources.displayMetrics)
            .toInt()

    // --- Positionierung / Robustheit ---

    private fun screenWidth(): Int = context.resources.displayMetrics.widthPixels

    private fun screenHeight(): Int = context.resources.displayMetrics.heightPixels

    private fun statusBarHeight(): Int {
        val id = context.resources.getIdentifier("status_bar_height", "dimen", "android")
        return if (id > 0) context.resources.getDimensionPixelSize(id) else dpToPx(24f)
    }

    // Bubble-x im sichtbaren Bereich halten (0 .. Bildschirmbreite - Bubble).
    private fun clampX(x: Int): Int = x.coerceIn(0, (screenWidth() - dpToPx(BUBBLE_SIZE_DP)).coerceAtLeast(0))

    // Bubble-y zwischen Statusbar und unterem Rand halten.
    private fun clampY(y: Int): Int =
        y.coerceIn(statusBarHeight(), (screenHeight() - dpToPx(BUBBLE_SIZE_DP)).coerceAtLeast(statusBarHeight()))

    // Beim Loslassen an den näheren horizontalen Rand ziehen (links oder rechts).
    private fun snapToEdge() {
        if (!::bubbleParams.isInitialized) return
        val maxX = (screenWidth() - dpToPx(BUBBLE_SIZE_DP)).coerceAtLeast(0)
        bubbleParams.x = if (bubbleParams.x + dpToPx(BUBBLE_SIZE_DP) / 2 < screenWidth() / 2) 0 else maxX
        bubbleParams.y = clampY(bubbleParams.y)
        bubbleView?.let { windowManager.updateViewLayout(it, bubbleParams) }
    }

    // Konfig-Änderung (v.a. Rotation): Bubble neu clampen + offenes Panel neu vermessen.
    fun onConfigChanged() {
        if (::bubbleParams.isInitialized) {
            snapToEdge() // clampt y + zieht x an den passenden Rand, aktualisiert das View
        }
        // Offenes Panel neu aufbauen, damit Breite/Position zur neuen Orientierung passen.
        if (panelView != null) {
            removePanel()
            showPanel()
        }
    }

    private companion object {
        // Feste Bubble-Kantenlänge in dp (muss zur BubbleSize in OverlayBubble.kt passen).
        const val BUBBLE_SIZE_DP = 56f
    }
}
