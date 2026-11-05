package com.quinton.sandroid

import android.app.Instrumentation
import android.content.Intent
import android.graphics.Rect
import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.Spinner

/** Real touch drags and accessibility scrolling, including reopening at the last selected row. */
internal class ScrollingChecks(private val instrumentation: Instrumentation) {
    private val automation = instrumentation.uiAutomation

    private fun nodes(root: AccessibilityNodeInfo): List<AccessibilityNodeInfo> = listOf(root) +
        (0 until root.childCount).flatMap { root.getChild(it)?.let(::nodes) ?: emptyList() }

    private fun currentNodes(): List<AccessibilityNodeInfo> {
        instrumentation.waitForIdleSync()
        automation.waitForIdle(100, 5000)
        return nodes(checkNotNull(automation.rootInActiveWindow))
    }

    private fun swipe(bounds: Rect, forward: Boolean) {
        val x = bounds.centerX().toFloat()
        val top = bounds.top + bounds.height() * 0.2f
        val bottom = bounds.top + bounds.height() * 0.8f
        val start = if (forward) bottom else top
        val end = if (forward) top else bottom
        val down = SystemClock.uptimeMillis()
        fun send(action: Int, y: Float) {
            val event = MotionEvent.obtain(down, SystemClock.uptimeMillis(), action, x, y, 0)
            try { instrumentation.sendPointerSync(event) } finally { event.recycle() }
        }
        send(MotionEvent.ACTION_DOWN, start)
        for (step in 1..12) {
            SystemClock.sleep(20)
            send(MotionEvent.ACTION_MOVE, start + (end - start) * step / 12)
        }
        send(MotionEvent.ACTION_UP, end)
    }

    private fun chooseRow(name: String, forward: Boolean) {
        repeat(30) {
            val nodes = currentNodes()
            val list = nodes.singleOrNull { it.className == "android.widget.ListView" }
            checkNotNull(list) { "Voice dialog list is missing while choosing $name: ${nodes.joinToString { "${it.className}: ${it.text}" }}" }
            nodes.firstOrNull { it.text?.toString() == name && it.isVisibleToUser }?.let { row ->
                check(row.performAction(AccessibilityNodeInfo.ACTION_CLICK)) { "Cannot select $name" }
                instrumentation.waitForIdleSync()
                return
            }
            val bounds = Rect().also(list::getBoundsInScreen)
            swipe(bounds, forward)
        }
        error("Cannot scroll to $name")
    }

    fun run() {
        val context = instrumentation.targetContext
        val screen = instrumentation.startActivitySync(
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) as MainActivity
        try {
            instrumentation.waitForIdleSync()
            fun views(root: View): List<View> = listOf(root) + if (root is ViewGroup)
                (0 until root.childCount).flatMap { views(root.getChildAt(it)) } else emptyList()
            lateinit var engine: Spinner
            lateinit var voice: Spinner
            instrumentation.runOnMainSync {
                val pickers = views(screen.window.decorView).filterIsInstance<Spinner>()
                engine = pickers.single { it.contentDescription == context.getString(R.string.label_engine) }
                voice = pickers.single { it.contentDescription == context.getString(R.string.label_voice) }
            }
            for (group in 0 until engine.count) {
                instrumentation.runOnMainSync { engine.setSelection(group) }
                instrumentation.waitForIdleSync()
                lateinit var first: String
                lateinit var last: String
                instrumentation.runOnMainSync {
                    first = voice.getItemAtPosition(0).toString()
                    last = voice.getItemAtPosition(voice.count - 1).toString()
                    voice.setSelection(voice.count - 1)
                    voice.performClick()
                }
                chooseRow(first, forward = false)
                instrumentation.runOnMainSync {
                    check(voice.selectedItemPosition == 0)
                    voice.performClick()
                }
                chooseRow(last, forward = true)
                instrumentation.runOnMainSync { check(voice.selectedItemPosition == voice.count - 1) }
            }
            // Exercise the independent engine dialog too, starting from its final row.
            instrumentation.runOnMainSync { engine.setSelection(engine.count - 1); engine.performClick() }
            chooseRow(engine.getItemAtPosition(0).toString(), forward = false)

            fun reach(label: String, forward: Boolean) {
                repeat(30) {
                    val nodes = currentNodes()
                    if (nodes.any { it.text?.toString().equals(label, true) && it.isVisibleToUser }) return
                    val scroll = nodes.single { it.className == "android.widget.ScrollView" }
                    val bounds = Rect().also(scroll::getBoundsInScreen)
                    swipe(bounds, forward)
                }
                error("Settings cannot scroll to $label")
            }
            reach(context.getString(R.string.action_ok), forward = true)
            reach(context.getString(R.string.label_engine), forward = false)
            // Check screen reader scrolling as well as touch, even if all controls fit.
            currentNodes().single { it.className == "android.widget.ScrollView" }
                .performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD)
            reach(context.getString(R.string.action_cancel), forward = true)
            currentNodes().single { it.className == "android.widget.ScrollView" }
                .performAction(AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD)
            reach(context.getString(R.string.label_engine), forward = false)
        } finally {
            instrumentation.runOnMainSync { screen.finish() }
        }
    }
}
