package com.trinex.hyfactory.devices.types

import com.hypixel.hytale.component.ArchetypeChunk
import com.hypixel.hytale.component.CommandBuffer
import com.hypixel.hytale.component.Store
import com.hypixel.hytale.logger.HytaleLogger
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore
import com.trinex.lib.api.energy.EnergyComponent
import com.trinex.lib.api.energy.EnergyDeviceType

class Consumer : EnergyDeviceType {
    val logger = HytaleLogger.forEnclosingClass()

    override fun onTick(
        energyComponent: EnergyComponent,
        dt: Float,
        index: Int,
        archetypeChunk: ArchetypeChunk<ChunkStore?>,
        store: Store<ChunkStore?>,
        commandBuffer: CommandBuffer<ChunkStore?>,
    ) {
        if (energyComponent.energy < energyComponent.energyConsumptionPerTick) return
        energyComponent.removeEnergy(energyComponent.energyConsumptionPerTick)
    }
}
