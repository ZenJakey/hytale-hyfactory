package com.trinex.hyfactory.pipe

import com.hypixel.hytale.math.vector.Vector3i
import com.hypixel.hytale.server.core.asset.type.blocktype.config.Rotation
import com.hypixel.hytale.server.core.asset.type.blocktype.config.RotationTuple
import com.trinex.lib.api.itemtransport.BlockSide

object PipeRotationUtil {
    private const val BASE = 4
    private const val STATE_COUNT = BASE * BASE * BASE * BASE * BASE * BASE

    val SIDE_ORDER: Array<BlockSide> =
        arrayOf(
            BlockSide.NORTH,
            BlockSide.SOUTH,
            BlockSide.EAST,
            BlockSide.WEST,
            BlockSide.UP,
            BlockSide.DOWN,
        )

    private val SIDE_NAMES =
        arrayOf(
            "North",
            "South",
            "East",
            "West",
            "Up",
            "Down",
        )

    private data class RotationMapping(
        val rotationIndex: Int,
        val mapping: IntArray,
    )

    private val keyByIndex = Array(STATE_COUNT) { indexToKey(it) }
    private val canonicalKeyByIndex = Array(STATE_COUNT) { "" }
    private val rotationByIndex = IntArray(STATE_COUNT)

    init {
        val mappings = buildRotationMappings()
        for (stateIndex in 0 until STATE_COUNT) {
            val values = valuesFromIndex(stateIndex)
            var bestIndex = Int.MAX_VALUE
            var bestRotation = 0

            for (mapping in mappings) {
                val rotated = IntArray(SIDE_ORDER.size)
                for (i in SIDE_ORDER.indices) {
                    rotated[i] = values[mapping.mapping[i]]
                }
                val rotatedIndex = indexFromValues(rotated)
                if (rotatedIndex < bestIndex || (rotatedIndex == bestIndex && mapping.rotationIndex < bestRotation)) {
                    bestIndex = rotatedIndex
                    bestRotation = mapping.rotationIndex
                }
            }

            canonicalKeyByIndex[stateIndex] = keyByIndex[bestIndex]
            rotationByIndex[stateIndex] = bestRotation
        }
    }

    data class CanonicalState(
        val key: String,
        val rotationIndex: Int,
    )

    fun canonicalize(values: IntArray): CanonicalState {
        val index = indexFromValues(values)
        return CanonicalState(canonicalKeyByIndex[index], rotationByIndex[index])
    }

    fun indexFromValues(values: IntArray): Int {
        var index = 0
        for (value in values) {
            index = index * BASE + value
        }
        return index
    }

    private fun buildRotationMappings(): List<RotationMapping> {
        val byVector = HashMap<String, Int>(SIDE_ORDER.size)
        for (i in SIDE_ORDER.indices) {
            val offset = SIDE_ORDER[i].offset
            byVector[vectorKey(offset)] = i
        }

        val mappings = ArrayList<RotationMapping>(4)
        for (yaw in Rotation.VALUES) {
            val mapping = IntArray(SIDE_ORDER.size)
            for (i in SIDE_ORDER.indices) {
                val offset = SIDE_ORDER[i].offset
                val rotated = Rotation.rotate(offset, yaw, Rotation.None, Rotation.None)
                mapping[i] = byVector[vectorKey(rotated)]
                    ?: error("Unknown rotated vector for side ${SIDE_ORDER[i]}: $rotated")
            }
            val rotationIndex = RotationTuple.index(yaw, Rotation.None, Rotation.None)
            mappings.add(RotationMapping(rotationIndex, mapping))
        }
        return mappings
    }

    private fun indexToKey(index: Int): String {
        val values = valuesFromIndex(index)
        val parts = ArrayList<String>(SIDE_ORDER.size)
        for (i in SIDE_ORDER.indices) {
            parts.add("${SIDE_NAMES[i]}${values[i]}")
        }
        return parts.joinToString("_")
    }

    private fun valuesFromIndex(index: Int): IntArray {
        var value = index
        val values = IntArray(SIDE_ORDER.size)
        for (i in SIDE_ORDER.size - 1 downTo 0) {
            values[i] = value % BASE
            value /= BASE
        }
        return values
    }

    private fun vectorKey(vector: Vector3i): String = "${vector.x},${vector.y},${vector.z}"
}
