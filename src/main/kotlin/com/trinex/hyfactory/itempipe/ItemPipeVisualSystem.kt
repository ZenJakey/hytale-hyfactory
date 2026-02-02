package com.trinex.hyfactory.itempipe

import com.hypixel.hytale.component.ArchetypeChunk
import com.hypixel.hytale.component.CommandBuffer
import com.hypixel.hytale.component.Store
import com.hypixel.hytale.component.query.Query
import com.hypixel.hytale.component.system.tick.EntityTickingSystem
import com.hypixel.hytale.logger.HytaleLogger
import com.hypixel.hytale.math.util.ChunkUtil
import com.hypixel.hytale.math.vector.Vector3i
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType
import com.hypixel.hytale.server.core.modules.block.BlockModule
import com.hypixel.hytale.server.core.universe.world.World
import com.hypixel.hytale.server.core.universe.world.chunk.WorldChunk
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore
import com.trinex.hyfactory.pipe.PipeRotationUtil
import com.trinex.lib.TrinexLib
import com.trinex.lib.api.itemtransport.BlockSide
import com.trinex.lib.api.itemtransport.ItemTransportComponent
import com.trinex.lib.api.itemtransport.ItemTransportMode

class ItemPipeVisualSystem : EntityTickingSystem<ChunkStore?>() {
    private val logger = HytaleLogger.forEnclosingClass()

    override fun getQuery(): Query<ChunkStore?> = Query.and(TrinexLib.get().itemTransportComponentType)

    override fun tick(
        dt: Float,
        index: Int,
        archetypeChunk: ArchetypeChunk<ChunkStore?>,
        store: Store<ChunkStore?>,
        commandBuffer: CommandBuffer<ChunkStore?>,
    ) {
        val transport = archetypeChunk.getComponent(index, TrinexLib.get().itemTransportComponentType) ?: return
        val stateInfo = archetypeChunk.getComponent(index, BlockModule.BlockStateInfo.getComponentType()) ?: return
        val wc = commandBuffer.getComponent(stateInfo.chunkRef, WorldChunk.getComponentType()) ?: return
        val world = wc.world ?: return

        val blockIndex = stateInfo.index
        val x = ChunkUtil.worldCoordFromLocalCoord(wc.x, ChunkUtil.xFromBlockInColumn(blockIndex))
        val y = ChunkUtil.yFromBlockInColumn(blockIndex)
        val z = ChunkUtil.worldCoordFromLocalCoord(wc.z, ChunkUtil.zFromBlockInColumn(blockIndex))

        val currentBlockType = wc.getBlockType(x, y, z) ?: return
        val baseKey = ItemPipeVisuals.resolveBaseKey(currentBlockType) ?: return

        val canonical = ItemPipeVisuals.buildCanonicalState(baseKey, transport, world, Vector3i(x, y, z))
        val desiredBlockKey = ItemPipeVisuals.resolveBlockKeyForState(baseKey, canonical.key)
        if (desiredBlockKey == null) {
            logger.atInfo().log("ItemPipe state lookup failed at ($x,$y,$z). state=${canonical.key} current=${currentBlockType.id}")
            return
        }
        if (desiredBlockKey == currentBlockType.id) return

        val blockTypeMap = BlockType.getAssetMap()
        val desiredId = blockTypeMap.getIndex(desiredBlockKey)
        if (desiredId == Integer.MIN_VALUE) {
            // logger.atInfo().log(
            //    "ItemPipe desired block key not found at ($x,$y,$z). state=$stateKey desired=$desiredBlockKey current=${currentBlockType.id}",
            // )
            return
        }
        val desiredBlockType = blockTypeMap.getAsset(desiredId) ?: return

        val blockChunk = wc.blockChunk ?: return
        val blockSection = blockChunk.getSectionAtBlockY(y)
        val filler = blockSection.getFiller(x, y, z)
        logger.atInfo().log(
            "ItemPipe updating block at ($x,$y,$z). state=${canonical.key} current=${currentBlockType.id} desired=$desiredBlockKey",
        )
        wc.setBlock(x, y, z, desiredId, desiredBlockType, canonical.rotationIndex, filler, ItemPipeVisuals.UPDATE_SETTINGS)
    }
}

object ItemPipeVisuals {
    private val BASE_BLOCK_KEYS =
        listOf(
            "HyFactory_Item_Pipe",
            "HyFactory_Item_Pipe2",
        )

    // Skip block entity/state rebuild + skip particles
    const val UPDATE_SETTINGS: Int = 2 or 4

    fun buildCanonicalState(
        baseKey: String,
        transport: ItemTransportComponent,
        world: World,
        pos: Vector3i,
    ): PipeRotationUtil.CanonicalState {
        val values = IntArray(PipeRotationUtil.SIDE_ORDER.size)
        for (i in PipeRotationUtil.SIDE_ORDER.indices) {
            val side = PipeRotationUtil.SIDE_ORDER[i]
            values[i] = computeSideState(baseKey, transport, world, pos, side)
        }
        return PipeRotationUtil.canonicalize(values)
    }

    fun resolveBaseKey(blockType: BlockType): String? {
        val id = blockType.id.removePrefix("*")
        return BASE_BLOCK_KEYS.firstOrNull { baseKey ->
            id == baseKey || id.startsWith("${baseKey}_")
        }
    }

    fun resolveBlockKeyForState(
        baseKey: String,
        stateKey: String,
    ): String? {
        val base = BlockType.getAssetMap().getAsset(baseKey) ?: return null
        return base.getBlockKeyForState(stateKey) ?: base.id
    }

    fun isItemPipeBlockType(blockType: BlockType): Boolean {
        return resolveBaseKey(blockType) != null
    }

    private fun computeSideState(
        baseKey: String,
        transport: ItemTransportComponent,
        world: World,
        pos: Vector3i,
        side: BlockSide,
    ): Int {
        val mode = transport.getSideMode(side)
        if (mode == ItemTransportMode.PUSH) return 2
        if (mode == ItemTransportMode.PULL) return 3

        val neighborPos = pos.clone().add(side.offset)
        val chunk = world.getChunkIfInMemory(ChunkUtil.indexChunkFromBlock(neighborPos.x, neighborPos.z)) ?: return 0
        val neighborType = chunk.getBlockType(neighborPos.x, neighborPos.y, neighborPos.z) ?: return 0
        val neighborBase = resolveBaseKey(neighborType) ?: return 0
        return if (neighborBase == baseKey) 1 else 0
    }
}
