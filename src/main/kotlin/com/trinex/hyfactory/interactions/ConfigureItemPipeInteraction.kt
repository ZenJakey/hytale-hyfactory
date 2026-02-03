package com.trinex.hyfactory.interactions

import com.hypixel.hytale.codec.Codec
import com.hypixel.hytale.codec.ExtraInfo
import com.hypixel.hytale.codec.builder.BuilderCodec
import com.hypixel.hytale.component.Store
import com.hypixel.hytale.logger.HytaleLogger
import com.hypixel.hytale.math.util.ChunkUtil
import com.hypixel.hytale.math.vector.Vector3i
import com.hypixel.hytale.protocol.InteractionType
import com.hypixel.hytale.server.core.Message
import com.hypixel.hytale.server.core.entity.InteractionContext
import com.hypixel.hytale.server.core.entity.entities.Player
import com.hypixel.hytale.server.core.modules.interaction.interaction.CooldownHandler
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.SimpleInteraction
import com.hypixel.hytale.server.core.universe.PlayerRef
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore
import com.trinex.hyfactory.ui.configPage.ItemTransportComponentConfigPage
import com.trinex.lib.api.itemtransport.ItemTransportComponent

class ConfigureItemPipeInteraction : SimpleInteraction() {
    val logger = HytaleLogger.forEnclosingClass()

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
        val playerRef = store.getComponent(owningEntity, PlayerRef.getComponentType()) ?: return
        val world = player.world ?: return
        val targetBlock = context.targetBlock ?: return
        val targetVector = Vector3i(targetBlock.x, targetBlock.y, targetBlock.z)

        if (type == InteractionType.Use) {
            val chunk = world.getChunk(ChunkUtil.indexChunkFromBlock(targetVector.x, targetVector.z)) ?: return
            val componentRef = chunk.getBlockComponentEntity(targetVector.x, targetVector.y, targetVector.z) ?: return
            val itemTransportComponent = world.chunkStore.store.getComponent(componentRef, ItemTransportComponent.getComponentType())
            if (itemTransportComponent != null) {
                logger.atInfo().log(ItemTransportComponent.CODEC.encode(itemTransportComponent, ExtraInfo.THREAD_LOCAL.get()).toString())
                openConfigPage(owningEntity, playerRef, store, componentRef)
            } else {
                player.sendMessage(Message.raw("No item transport component found"))
            }
        }
    }

    private fun openConfigPage(
        owningEntity: com.hypixel.hytale.component.Ref<EntityStore>,
        playerRef: PlayerRef,
        store: Store<EntityStore>,
        componentRef: com.hypixel.hytale.component.Ref<com.hypixel.hytale.server.core.universe.world.storage.ChunkStore>,
    ) {
        val page = ItemTransportComponentConfigPage(playerRef, componentRef)
        val player = store.getComponent(owningEntity, Player.getComponentType()) ?: return
        player.pageManager.openCustomPage(owningEntity, store, page)
    }

    companion object {
        val CODEC =
            BuilderCodec
                .builder(
                    ConfigureItemPipeInteraction::class.java,
                    { ConfigureItemPipeInteraction() },
                    SimpleInteraction.CODEC,
                ).build()
    }
}
