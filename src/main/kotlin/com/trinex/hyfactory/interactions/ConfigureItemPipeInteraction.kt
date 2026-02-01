package com.trinex.hyfactory.interactions

import au.ellie.hyui.builders.PageBuilder
import com.hypixel.hytale.codec.Codec
import com.hypixel.hytale.codec.ExtraInfo
import com.hypixel.hytale.codec.builder.BuilderCodec
import com.hypixel.hytale.component.Store
import com.hypixel.hytale.logger.HytaleLogger
import com.hypixel.hytale.math.util.ChunkUtil
import com.hypixel.hytale.math.vector.Vector3i
import com.hypixel.hytale.protocol.InteractionType
import com.hypixel.hytale.protocol.packets.interface_.CustomUIEventBindingType
import com.hypixel.hytale.server.core.Message
import com.hypixel.hytale.server.core.entity.InteractionContext
import com.hypixel.hytale.server.core.entity.entities.Player
import com.hypixel.hytale.server.core.modules.interaction.interaction.CooldownHandler
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.SimpleInteraction
import com.hypixel.hytale.server.core.universe.PlayerRef
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore
import com.trinex.hyfactory.ui.configPage.ItemTransportComponentConfigPage
import com.trinex.lib.api.itemtransport.BlockSide
import com.trinex.lib.api.itemtransport.ItemFilter
import com.trinex.lib.api.itemtransport.ItemFilterMode
import com.trinex.lib.api.itemtransport.ItemTransportComponent
import com.trinex.lib.api.itemtransport.ItemTransportMode

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
                openConfigPage(playerRef, store, player, itemTransportComponent)
            } else {
                player.sendMessage(Message.raw("No item transport component found"))
            }
        }
    }

    private fun openConfigPage(
        playerRef: PlayerRef,
        store: Store<EntityStore>,
        player: Player,
        component: ItemTransportComponent,
    ) {
        val initialSnapshot = snapshot(component)
        logger.atInfo().log()
        PageBuilder
            .pageForPlayer(playerRef)
            .fromHtml(ItemTransportComponentConfigPage.htmlFor(component))
            .addEventListener(
                ItemTransportComponentConfigPage.applyId,
                CustomUIEventBindingType.Activating,
            ) { _: Any?, ctx ->
                applyFromUi(component, ctx, player)
            }.addEventListener(
                ItemTransportComponentConfigPage.resetId,
                CustomUIEventBindingType.Activating,
            ) { _: Any?, ctx ->
                applySnapshot(component, initialSnapshot)
                ctx.getPage().ifPresent { it.close() }
                openConfigPage(playerRef, store, player, component)
            }.addEventListener(
                ItemTransportComponentConfigPage.closeId,
                CustomUIEventBindingType.Activating,
            ) { _: Any?, ctx ->
                ctx.getPage().ifPresent { it.close() }
            }.open(store)
    }

    private fun applyFromUi(
        component: ItemTransportComponent,
        ctx: au.ellie.hyui.events.UIContext,
        player: Player,
    ) {
        val speed =
            ctx
                .getValue(ItemTransportComponentConfigPage.transferSpeedId, Number::class.java)
                .map { it.toInt().coerceAtLeast(1) }
                .orElse(component.itemsPerSecond)
        component.itemsPerSecond = speed

        component.sideModes.clear()
        component.sideFilters.clear()

        for (side in BlockSide.entries) {
            val modeName =
                ctx.getValue(ItemTransportComponentConfigPage.modeId(side), String::class.java).orElse(ItemTransportMode.DISABLED.name)
            val mode =
                try {
                    ItemTransportMode.valueOf(modeName)
                } catch (ex: IllegalArgumentException) {
                    ItemTransportMode.DISABLED
                }
            component.setSideMode(side, mode)

            val filterIds = ctx.getValue(ItemTransportComponentConfigPage.filterId(side), String::class.java).orElse("")
            val filterModeName =
                ctx.getValue(ItemTransportComponentConfigPage.filterModeId(side), String::class.java).orElse(ItemFilterMode.WHITELIST.name)
            val filterMode =
                try {
                    ItemFilterMode.valueOf(filterModeName)
                } catch (ex: IllegalArgumentException) {
                    ItemFilterMode.WHITELIST
                }
            val matchMetadata =
                ctx
                    .getValue(ItemTransportComponentConfigPage.filterMetaId(side), Boolean::class.java)
                    .orElse(false)
            val filter = buildFilter(filterIds, filterMode, matchMetadata)
            component.setSideFilter(side, filter)
        }

        player.sendMessage(Message.raw("Item pipe settings updated."))
    }

    private fun buildFilter(
        rawIds: String,
        mode: ItemFilterMode,
        matchMetadata: Boolean,
    ): ItemFilter? {
        val ids =
            rawIds
                .split(Regex("[,\\s]+"))
                .map { it.trim() }
                .filter { it.isNotEmpty() }
                .toMutableSet()
        if (ids.isEmpty()) {
            return null
        }
        return ItemFilter(mode = mode, ids = ids, matchMetadata = matchMetadata)
    }

    private data class ItemTransportSnapshot(
        val transferSpeed: Int,
        val sideModes: Map<BlockSide, ItemTransportMode>,
        val sideFilters: Map<BlockSide, ItemFilter>,
    )

    private fun snapshot(component: ItemTransportComponent): ItemTransportSnapshot =
        ItemTransportSnapshot(
            component.itemsPerSecond,
            component.sideModes.toMap(),
            component.sideFilters.mapValues { (_, filter) ->
                filter.copy(
                    ids = filter.ids.toMutableSet(),
                    metadataById = filter.metadataById.toMutableMap(),
                )
            },
        )

    private fun applySnapshot(
        component: ItemTransportComponent,
        snapshot: ItemTransportSnapshot,
    ) {
        component.itemsPerSecond = snapshot.transferSpeed
        component.sideModes.clear()
        component.sideModes.putAll(snapshot.sideModes)
        component.sideFilters.clear()
        component.sideFilters.putAll(
            snapshot.sideFilters.mapValues { (_, filter) ->
                filter.copy(
                    ids = filter.ids.toMutableSet(),
                    metadataById = filter.metadataById.toMutableMap(),
                )
            },
        )
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
