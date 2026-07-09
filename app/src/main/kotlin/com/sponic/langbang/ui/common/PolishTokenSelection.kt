package com.sponic.langbang.ui.common

import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInParent

private object PolishTokenSelectionSession {
    var activeOwner by mutableStateOf<Any?>(null)
}

internal class PolishTokenSelectionState(
    private val owner: Any
) {
    private var anchorIndex by mutableStateOf<Int?>(null)
    private var focusIndex by mutableStateOf<Int?>(null)
    private var boundsByIndex by mutableStateOf<Map<Int, Rect>>(emptyMap())

    val selectedIndexes: Set<Int>
        get() {
            if (PolishTokenSelectionSession.activeOwner != owner) return emptySet()
            val start = anchorIndex ?: return emptySet()
            val end = focusIndex ?: return emptySet()
            val range = if (start <= end) start..end else end..start
            return range.toSet()
        }

    val hasMultiSelection: Boolean
        get() = selectedIndexes.size > 1

    fun updateBounds(index: Int, coordinates: LayoutCoordinates) {
        boundsByIndex = boundsByIndex + (index to coordinates.boundsInParent())
    }

    fun selectionOrTokenText(index: Int, tokens: List<String>): String {
        val selected = selectedIndexes
        return if (index in selected && selected.size > 1) {
            selected.sorted()
                .mapNotNull { tokens.getOrNull(it) }
                .joinToString(" ")
        } else {
            selectSingle(index)
            tokens.getOrNull(index).orEmpty()
        }
    }

    fun selectSingle(index: Int) {
        activate()
        anchorIndex = index
        focusIndex = index
    }

    fun clearSelection() {
        clear()
    }

    fun dragModifier(tokens: List<String>): Modifier = Modifier.pointerInput(tokens) {
        detectDragGestures(
            onDragStart = { offset ->
                tokenIndexAt(offset)?.let {
                    activate()
                    anchorIndex = it
                    focusIndex = it
                }
            },
            onDrag = { change, _ ->
                tokenIndexAt(change.position)?.let { focusIndex = it }
            },
            onDragEnd = {
                if (selectedIndexes.size <= 1) clear()
            },
            onDragCancel = { clear() }
        )
    }

    private fun tokenIndexAt(offset: Offset): Int? {
        boundsByIndex.firstNotNullOfOrNull { (index, rect) ->
            index.takeIf { rect.contains(offset) }
        }?.let { return it }

        return boundsByIndex.minByOrNull { (_, rect) ->
            val dx = when {
                offset.x < rect.left -> rect.left - offset.x
                offset.x > rect.right -> offset.x - rect.right
                else -> 0f
            }
            val dy = when {
                offset.y < rect.top -> rect.top - offset.y
                offset.y > rect.bottom -> offset.y - rect.bottom
                else -> 0f
            }
            dx * dx + dy * dy
        }?.key
    }

    private fun activate() {
        PolishTokenSelectionSession.activeOwner = owner
    }

    private fun clear() {
        anchorIndex = null
        focusIndex = null
    }
}

@Composable
internal fun rememberPolishTokenSelectionState(ownerKey: Any? = null): PolishTokenSelectionState {
    val localOwner = remember { Any() }
    val owner = ownerKey ?: localOwner
    return remember(owner) { PolishTokenSelectionState(owner) }
}
