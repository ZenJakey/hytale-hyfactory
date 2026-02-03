package com.trinex.hyfactory.commands

import com.hypixel.hytale.component.Ref
import com.hypixel.hytale.component.Store
import com.hypixel.hytale.math.util.ChunkUtil
import com.hypixel.hytale.server.core.Message
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType
import com.hypixel.hytale.server.core.command.system.CommandContext
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent
import com.hypixel.hytale.server.core.universe.PlayerRef
import com.hypixel.hytale.server.core.universe.world.World
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore
import com.trinex.hyfactory.pipe.PipeRotationUtil
import com.trinex.lib.api.itemtransport.BlockSide
import com.trinex.lib.api.itemtransport.ItemTransportComponent
import com.trinex.lib.api.itemtransport.ItemTransportMode
import kotlin.math.floor
import kotlin.math.max

class PlaceItemPipeTestCommand : AbstractPlayerCommand("pipetest", "Places every item pipe configuration in a grid.") {
    companion object {
        private const val BASE_BLOCK_KEY = "HyFactory_Item_Pipe"
        private const val SPACING = 4
        private const val COLUMNS = 64
        private const val UPDATE_SETTINGS = 0
    }

    override fun execute(
        context: CommandContext,
        store: Store<EntityStore>,
        ref: Ref<EntityStore>,
        playerRef: PlayerRef,
        world: World,
    ) {
        val transform = store.getComponent(ref, TransformComponent.getComponentType())
        if (transform == null) {
            context.sendMessage(Message.raw("Player position unavailable."))
            return
        }

        val origin = transform.getPosition()
        val baseY = max(2, floor(origin.y).toInt() + 1)
        val startX = floor(origin.x).toInt() + 2
        val startZ = floor(origin.z).toInt() + 2

        val baseBlockType = BlockType.getAssetMap().getAsset(BASE_BLOCK_KEY)
        if (baseBlockType == null) {
            context.sendMessage(Message.raw("Base block '$BASE_BLOCK_KEY' not found."))
            return
        }

        var placed = 0
        var index = 0
        val values = IntArray(PipeRotationUtil.SIDE_ORDER.size)
        val total = Math.pow(4.0, PipeRotationUtil.SIDE_ORDER.size.toDouble()).toInt()

        for (n in 0 until total) {
            decodeBase4(n, values)
            val col = index % COLUMNS
            val row = index / COLUMNS
            val x = startX + col * SPACING
            val z = startZ + row * SPACING
            val y = baseY
        placeConfiguredPipe(world, x, y, z, values)
            placed++
            index++
        }

        context.sendMessage(
            Message.raw(
                "Placed $placed item pipe configurations starting at ($startX,$baseY,$startZ) with spacing $SPACING.",
            ),
        )
    }

    private fun decodeBase4(index: Int, values: IntArray) {
        var value = index
        for (i in values.size - 1 downTo 0) {
            values[i] = value % 4
            value /= 4
        }
    }

    private fun placeConfiguredPipe(world: World, x: Int, y: Int, z: Int, values: IntArray) {
        world.setBlock(x, y, z, BASE_BLOCK_KEY, UPDATE_SETTINGS)
        world.performBlockUpdate(x, y, z, true)

        val chunk = world.getChunk(ChunkUtil.indexChunkFromBlock(x, z)) ?: return
        val componentRef = chunk.getBlockComponentEntity(x, y, z) ?: return
        val component =
            world.chunkStore.store.getComponent(componentRef, ItemTransportComponent.getComponentType()) ?: return

        component.sideModes.clear()
        component.sideFilters.clear()

        for (i in PipeRotationUtil.SIDE_ORDER.indices) {
            val side = PipeRotationUtil.SIDE_ORDER[i]
            when (values[i]) {
                2 -> component.setSideMode(side, ItemTransportMode.PUSH)
                3 -> component.setSideMode(side, ItemTransportMode.PULL)
                else -> component.setSideMode(side, ItemTransportMode.DISABLED)
            }
        }

        for (i in PipeRotationUtil.SIDE_ORDER.indices) {
            val side = PipeRotationUtil.SIDE_ORDER[i]
            if (values[i] == 1) {
                placeNeighbor(world, x, y, z, side)
            }
        }
    }

    private fun placeNeighbor(world: World, x: Int, y: Int, z: Int, side: BlockSide) {
        val offset = side.offset
        val nx = x + offset.x
        val ny = y + offset.y
        val nz = z + offset.z
        world.setBlock(nx, ny, nz, BASE_BLOCK_KEY, UPDATE_SETTINGS)
        world.performBlockUpdate(nx, ny, nz, true)
    }
}
