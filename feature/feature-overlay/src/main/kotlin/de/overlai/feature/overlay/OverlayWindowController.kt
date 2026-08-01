package de.overlai.feature.overlay

import android.content.Context
import android.graphics.PixelFormat
import android.os.Build
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import androidx.compose.ui.platform.ComposeView
import kotlin.math.abs

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
) {
    private val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager

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
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                overlayType,
                // NOT_FOCUSABLE: die Bubble greift keine Tasten/IME ab und lässt die
                // darunterliegende App normal weiterlaufen.
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                PixelFormat.TRANSLUCENT,
            ).apply {
                gravity = Gravity.TOP or Gravity.START
                x = 0
                y = 200
            }
        bubbleParams = params

        val owner = OverlayLifecycleOwner()
        val view =
            ComposeView(context).apply {
                setContent { OverlayBubble() }
            }
        owner.attachTo(view)
        view.setOnTouchListener(BubbleTouchListener())

        bubbleOwner = owner
        bubbleView = view
        windowManager.addView(view, params)
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

        val params =
            WindowManager.LayoutParams(
                (context.resources.displayMetrics.widthPixels * 0.86f).toInt(),
                WindowManager.LayoutParams.WRAP_CONTENT,
                overlayType,
                // NOT_TOUCH_MODAL: Tipps außerhalb des Panels gehen an die App durch;
                // WATCH_OUTSIDE_TOUCH lässt uns einen Außentipp erkennen und schließen.
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                    WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH,
                PixelFormat.TRANSLUCENT,
            ).apply {
                gravity = Gravity.TOP or Gravity.START
                x = 0
                y = if (::bubbleParams.isInitialized) bubbleParams.y + 160 else 360
            }

        val owner = OverlayLifecycleOwner()
        val view =
            ComposeView(context).apply {
                setContent { OverlayPanel(onClose = ::removePanel) }
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

    // Unterscheidet Tap (togglet das Panel) von Drag (verschiebt die Bubble). Der
    // Schwellwert verhindert, dass ein minimaler Wackler als Tap durchgeht bzw. ein
    // gewollter Tap fälschlich als Drag zählt.
    private inner class BubbleTouchListener : View.OnTouchListener {
        private var initialX = 0
        private var initialY = 0
        private var touchStartX = 0f
        private var touchStartY = 0f
        private var moved = false

        override fun onTouch(
            view: View,
            event: MotionEvent,
        ): Boolean {
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = bubbleParams.x
                    initialY = bubbleParams.y
                    touchStartX = event.rawX
                    touchStartY = event.rawY
                    moved = false
                    return true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = (event.rawX - touchStartX).toInt()
                    val dy = (event.rawY - touchStartY).toInt()
                    if (abs(dx) > TOUCH_SLOP_PX || abs(dy) > TOUCH_SLOP_PX) {
                        moved = true
                        bubbleParams.x = initialX + dx
                        bubbleParams.y = initialY + dy
                        bubbleView?.let { windowManager.updateViewLayout(it, bubbleParams) }
                    }
                    return true
                }
                MotionEvent.ACTION_UP -> {
                    if (!moved) {
                        view.performClick()
                        togglePanel()
                    }
                    return true
                }
                else -> return false
            }
        }
    }

    private companion object {
        // Bewegungsschwelle in px, ab der ein Touch als Drag (nicht Tap) gilt.
        const val TOUCH_SLOP_PX = 16
    }
}
