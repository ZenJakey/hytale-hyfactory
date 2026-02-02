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
        val inverseRotationIndex: Int,
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
                if (rotatedIndex < bestIndex || (rotatedIndex == bestIndex && mapping.inverseRotationIndex < bestRotation)) {
                    bestIndex = rotatedIndex
                    bestRotation = mapping.inverseRotationIndex
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

        val mappingsByKey = HashMap<String, Pair<Int, IntArray>>(16)
        for (yaw in Rotation.VALUES) {
            for (pitch in Rotation.VALUES) {
                val roll = Rotation.None
                val mapping = IntArray(SIDE_ORDER.size)
                for (i in SIDE_ORDER.indices) {
                    val offset = SIDE_ORDER[i].offset
                    val rotated = Rotation.rotate(offset, yaw, pitch, roll)
                    val destIndex = byVector[vectorKey(rotated)]
                        ?: error("Unknown rotated vector for side ${SIDE_ORDER[i]}: $rotated")
                    // Active rotation: value at source side i moves to destIndex.
                    mapping[destIndex] = i
                }
                val rotationIndex = RotationTuple.index(yaw, pitch, roll)
                val key = mapping.joinToString(",")
                val existing = mappingsByKey[key]
                if (existing == null || rotationIndex < existing.first) {
                    mappingsByKey[key] = rotationIndex to mapping
                }
            }
        }
        val rotationIndexByKey = HashMap<String, Int>(mappingsByKey.size)
        for ((key, value) in mappingsByKey) {
            rotationIndexByKey[key] = value.first
        }

        val mappings = ArrayList<RotationMapping>(mappingsByKey.size)
        for ((key, value) in mappingsByKey) {
            val mapping = value.second
            val inverse = IntArray(SIDE_ORDER.size)
            for (i in mapping.indices) {
                inverse[mapping[i]] = i
            }
            val inverseKey = inverse.joinToString(",")
            val inverseRotationIndex =
                rotationIndexByKey[inverseKey]
                    ?: continue
            mappings.add(RotationMapping(value.first, mapping, inverseRotationIndex))
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
