package com.trinex.hyfactory.energy

import com.hypixel.hytale.component.ArchetypeChunk
import com.hypixel.hytale.component.CommandBuffer
import com.hypixel.hytale.component.Store
import com.hypixel.hytale.component.query.Query
import com.hypixel.hytale.component.system.tick.EntityTickingSystem
import com.hypixel.hytale.math.util.ChunkUtil
import com.hypixel.hytale.math.vector.Vector3i
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType
import com.hypixel.hytale.server.core.modules.block.BlockModule
import com.hypixel.hytale.server.core.universe.world.World
import com.hypixel.hytale.server.core.universe.world.chunk.WorldChunk
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore
import com.trinex.lib.api.energy.EnergyComponent
import com.trinex.lib.api.itemtransport.BlockSide
import com.trinex.hyfactory.pipe.PipeRotationUtil

class EnergyPipeVisualSystem : EntityTickingSystem<ChunkStore?>() {
    override fun getQuery(): Query<ChunkStore?> = Query.and(EnergyComponent.getComponentType())

    override fun tick(
        dt: Float,
        index: Int,
        archetypeChunk: ArchetypeChunk<ChunkStore?>,
        store: Store<ChunkStore?>,
        commandBuffer: CommandBuffer<ChunkStore?>,
    ) {
        val stateInfo = archetypeChunk.getComponent(index, BlockModule.BlockStateInfo.getComponentType()) ?: return
        val wc = commandBuffer.getComponent(stateInfo.chunkRef, WorldChunk.getComponentType()) ?: return
        val world = wc.world ?: return

        val blockIndex = stateInfo.index
        val x = ChunkUtil.worldCoordFromLocalCoord(wc.x, ChunkUtil.xFromBlockInColumn(blockIndex))
        val y = ChunkUtil.yFromBlockInColumn(blockIndex)
        val z = ChunkUtil.worldCoordFromLocalCoord(wc.z, ChunkUtil.zFromBlockInColumn(blockIndex))

        val currentBlockType = wc.getBlockType(x, y, z) ?: return
        if (!EnergyPipeVisuals.isEnergyPipeBlockType(currentBlockType)) return

        val canonical = EnergyPipeVisuals.buildCanonicalState(world, Vector3i(x, y, z))
        val desiredBlockKey = EnergyPipeVisuals.resolveBlockKeyForState(canonical.key)
        if (desiredBlockKey == null || desiredBlockKey == currentBlockType.id) return

        val blockTypeMap = BlockType.getAssetMap()
        val desiredId = blockTypeMap.getIndex(desiredBlockKey)
        if (desiredId == Integer.MIN_VALUE) return
        val desiredBlockType = blockTypeMap.getAsset(desiredId) ?: return

        val blockChunk = wc.blockChunk ?: return
        val blockSection = blockChunk.getSectionAtBlockY(y)
        val filler = blockSection.getFiller(x, y, z)
        wc.setBlock(x, y, z, desiredId, desiredBlockType, canonical.rotationIndex, filler, EnergyPipeVisuals.UPDATE_SETTINGS)
    }
}

object EnergyPipeVisuals {
    private const val BASE_BLOCK_KEY = "HyFactory_Energy_Pipe"

    // Skip block entity/state rebuild + skip particles
    const val UPDATE_SETTINGS: Int = 2 or 4

    fun buildCanonicalState(
        world: World,
        pos: Vector3i,
    ): PipeRotationUtil.CanonicalState {
        val values = IntArray(PipeRotationUtil.SIDE_ORDER.size)
        for (i in PipeRotationUtil.SIDE_ORDER.indices) {
            val side = PipeRotationUtil.SIDE_ORDER[i]
            values[i] = computeSideState(world, pos, side)
        }
        return PipeRotationUtil.canonicalize(values)
    }

    fun resolveBlockKeyForState(stateKey: String): String? {
        val base = BlockType.getAssetMap().getAsset(BASE_BLOCK_KEY) ?: return null
        return base.getBlockKeyForState(stateKey) ?: base.id
    }

    fun isEnergyPipeBlockType(blockType: BlockType): Boolean {
        val id = blockType.id
        return id == BASE_BLOCK_KEY || id.startsWith("*$BASE_BLOCK_KEY") || id.startsWith("${BASE_BLOCK_KEY}_")
    }

    private fun computeSideState(
        world: World,
        pos: Vector3i,
        side: BlockSide,
    ): Int {
        val neighborPos = pos.clone().add(side.offset)
        val chunk = world.getChunk(ChunkUtil.indexChunkFromBlock(neighborPos.x, neighborPos.z)) ?: return 0
        val componentRef = chunk.getBlockComponentEntity(neighborPos.x, neighborPos.y, neighborPos.z) ?: return 0
        val energyComponent = world.chunkStore.store.getComponent(componentRef, EnergyComponent.getComponentType())
        return if (energyComponent != null) 1 else 0
    }
}
