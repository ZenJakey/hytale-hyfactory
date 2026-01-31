package com.trinex.hyfactory.interactions

import com.hypixel.hytale.codec.builder.BuilderCodec
import com.hypixel.hytale.math.util.ChunkUtil
import com.hypixel.hytale.math.vector.Vector3i
import com.hypixel.hytale.protocol.InteractionType
import com.hypixel.hytale.server.core.Message
import com.hypixel.hytale.server.core.entity.InteractionContext
import com.hypixel.hytale.server.core.entity.entities.Player
import com.hypixel.hytale.server.core.modules.interaction.interaction.CooldownHandler
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.SimpleInteraction
import com.trinex.lib.api.energy.EnergyComponent
import com.trinex.lib.api.energy.EnergyDefinitions

class ConfigureSolarInteraction : SimpleInteraction() {
    override fun tick0(
        firstRun: Boolean,
        time: Float,
        type: InteractionType,
        context: InteractionContext,
        cooldownHandler: CooldownHandler,
    ) {
        val owningEntity = context.owningEntity
        val store = owningEntity.store

        val player = store.getComponent(owningEntity, Player.getComponentType()) ?: return
        val world = player.world ?: return
        val targetBlock = context.targetBlock ?: return
        val targetVector = Vector3i(targetBlock.x, targetBlock.y, targetBlock.z)

        if (type == InteractionType.Use) {
            val chunk = world.getChunk(ChunkUtil.indexChunkFromBlock(targetVector.x, targetVector.z)) ?: return
            val componentRef = chunk.getBlockComponentEntity(targetVector.x, targetVector.y, targetVector.z) ?: return
            val energyComponent = world.chunkStore.store.getComponent(componentRef, EnergyComponent.getComponentType())

            if (energyComponent != null) {
                player.sendMessage(
                    Message.raw(
                        "Energy: ${EnergyDefinitions.getAbbreviationFor(
                            energyComponent.energy,
                        )} / ${EnergyDefinitions.getAbbreviationFor(energyComponent.energyCapacity)}",
                    ),
                )
            } else {
                player.sendMessage(Message.raw("No energy component found"))
            }
        }
    }

    companion object {
        val CODEC =
            BuilderCodec
                .builder(
                    ConfigureSolarInteraction::class.java,
                    { ConfigureSolarInteraction() },
                    SimpleInteraction.CODEC,
                ).build()
    }
}
