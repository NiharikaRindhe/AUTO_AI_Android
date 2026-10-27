package com.vega.actions

import com.vega.brain.Replies
import com.vega.llm.VegaLine
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

val CAR_WINDOWS = listOf("driver", "passenger", "rear_left", "rear_right")

/** Simulated windows, AC and lights. Replaced by the CAN-box SDK after day 15; the tools stay the same. */
class CarSimActions {

    data class CarState(
        val windows: Map<String, Int> = CAR_WINDOWS.associateWith { 0 },
        val acOn: Boolean = false,
        val tempC: Int = 24,
        val fan: Int = 2,
        val ambient: String = "blue",
    )

    private val _state = MutableStateFlow(CarState())
    val state: StateFlow<CarState> = _state.asStateFlow()

    fun handle(tool: VegaLine.Tool): ActionResult = when (tool.name) {
        "win.set" -> {
            val pos = position(tool.arg("pos"))
            val pct = (tool.int("pct") ?: 100).coerceIn(0, 100)
            setWindows(pos) { pct }
            ActionResult(true, Replies.window(pos, pct))
        }
        "win.adjust" -> {
            val pos = position(tool.arg("pos"))
            val delta = tool.int("delta") ?: 20
            setWindows(pos) { (it + delta).coerceIn(0, 100) }
            ActionResult(true, Replies.windowNudged(pos, delta > 0))
        }
        "hvac.power" -> {
            val on = tool.bool("on") ?: true
            _state.update { it.copy(acOn = on) }
            ActionResult(true, Replies.acPower(on))
        }
        "hvac.temp" -> {
            val current = _state.value
            val target = (tool.int("val") ?: (current.tempC + (tool.int("delta") ?: 0))).coerceIn(16, 30)
            _state.update { it.copy(acOn = true, tempC = target) }
            ActionResult(true, Replies.acTemp(target, turnedOn = !current.acOn))
        }
        "hvac.fan" -> {
            val current = _state.value.fan
            val level = (tool.int("level") ?: (current + if (tool.arg("dir") == "down") -1 else 1)).coerceIn(0, 5)
            _state.update { it.copy(fan = level) }
            ActionResult(true, Replies.fan(level))
        }
        "light.ambient" -> {
            val color = tool.arg("color") ?: "blue"
            _state.update { it.copy(ambient = color) }
            ActionResult(true, Replies.ambient(color))
        }
        else -> ActionResult(false, Replies.CANT_YET)
    }

    private fun position(raw: String?): String =
        raw?.takeIf { it in CAR_WINDOWS || it in GROUPS } ?: "driver"

    private companion object {
        val GROUPS = setOf("all", "front", "rear")
    }

    private fun setWindows(pos: String, change: (Int) -> Int) {
        val targets = when (pos) {
            "all" -> CAR_WINDOWS
            "front" -> listOf("driver", "passenger")
            "rear" -> listOf("rear_left", "rear_right")
            else -> listOf(pos)
        }
        _state.update { s -> s.copy(windows = s.windows.mapValues { (k, v) -> if (k in targets) change(v) else v }) }
    }
}
