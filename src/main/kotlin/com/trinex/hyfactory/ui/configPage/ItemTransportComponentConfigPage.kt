package com.trinex.hyfactory.ui.configPage

import com.hypixel.hytale.codec.Codec
import com.hypixel.hytale.codec.ExtraInfo
import com.hypixel.hytale.codec.KeyedCodec
import com.hypixel.hytale.codec.builder.BuilderCodec
import com.hypixel.hytale.codec.codecs.EnumCodec
import com.hypixel.hytale.codec.schema.SchemaContext
import com.hypixel.hytale.codec.schema.config.Schema
import com.hypixel.hytale.codec.util.RawJsonReader
import com.hypixel.hytale.component.Ref
import com.hypixel.hytale.component.Store
import com.hypixel.hytale.protocol.packets.interface_.CustomPageLifetime
import com.hypixel.hytale.protocol.packets.interface_.CustomUIEventBindingType
import com.hypixel.hytale.server.core.Message
import com.hypixel.hytale.server.core.entity.entities.Player
import com.hypixel.hytale.server.core.entity.entities.player.pages.InteractiveCustomUIPage
import com.hypixel.hytale.server.core.modules.block.BlockModule
import com.hypixel.hytale.server.core.ui.DropdownEntryInfo
import com.hypixel.hytale.server.core.ui.LocalizableString
import com.hypixel.hytale.server.core.ui.builder.EventData
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder
import com.hypixel.hytale.server.core.ui.builder.UIEventBuilder
import com.hypixel.hytale.server.core.universe.PlayerRef
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore
import com.trinex.lib.api.itemtransport.BlockSide
import com.trinex.lib.api.itemtransport.ItemFilter
import com.trinex.lib.api.itemtransport.ItemFilterMode
import com.trinex.lib.api.itemtransport.ItemTransportComponent
import com.trinex.lib.api.itemtransport.ItemTransportMode
import org.bson.BsonBoolean
import org.bson.BsonValue

class ItemTransportComponentConfigPage(
    playerRef: PlayerRef,
    private val componentRef: Ref<ChunkStore>,
) : InteractiveCustomUIPage<ItemTransportComponentConfigPage.PageEventData>(
        playerRef,
        CustomPageLifetime.CanDismissOrCloseThroughInteraction,
        PageEventData.CODEC,
    ) {
    private val initialSnapshot = snapshotFromComponent()

    override fun build(
        ref: Ref<EntityStore>,
        commandBuilder: UICommandBuilder,
        eventBuilder: UIEventBuilder,
        store: Store<EntityStore>,
    ) {
        commandBuilder.append(LAYOUT)

        val component = componentRef.getStore().getComponent(componentRef, ItemTransportComponent.getComponentType())
        if (component == null) {
            commandBuilder.set("#ErrorLabel.Visible", true)
            commandBuilder.set("#ConfigArea.Visible", false)
            return
        }

        commandBuilder.set("#ErrorLabel.Visible", false)
        commandBuilder.set("#ConfigArea.Visible", true)
        commandBuilder.set("#TransferSpeed.Value", component.itemsPerSecond.toString())

        for (side in SIDE_ORDER) {
            val selector = sideSelector(side)
            commandBuilder.set("$selector #Mode.Entries", MODE_ENTRIES)
            commandBuilder.set("$selector #Mode.Value", component.getSideMode(side).name)
            commandBuilder.set("$selector #FilterMode.Entries", FILTER_MODE_ENTRIES)

            val filter = component.getSideFilter(side)
            val ids = filter?.ids?.joinToString(", ") ?: ""
            val mode = filter?.mode ?: ItemFilterMode.WHITELIST
            val matchMetadata = filter?.matchMetadata ?: false
            commandBuilder.set("$selector #FilterIds.Value", ids)
            commandBuilder.set("$selector #FilterMode.Value", mode.name)
            commandBuilder.set("$selector #MatchMeta.Value", matchMetadata)
        }

        eventBuilder.addEventBinding(
            CustomUIEventBindingType.Activating,
            "#ApplyButton",
            buildEventData(PageAction.Apply),
        )
        eventBuilder.addEventBinding(
            CustomUIEventBindingType.Activating,
            "#ResetButton",
            buildEventData(PageAction.Reset),
        )
        eventBuilder.addEventBinding(
            CustomUIEventBindingType.Activating,
            "#CloseButton",
            buildEventData(PageAction.Close),
        )
    }

    override fun handleDataEvent(
        ref: Ref<EntityStore>,
        store: Store<EntityStore>,
        data: PageEventData,
    ) {
        val component = componentRef.getStore().getComponent(componentRef, ItemTransportComponent.getComponentType())
        if (component == null) {
            close()
            return
        }

        when (data.action) {
            PageAction.Apply -> {
                applyFromData(component, data)
                markComponentDirty()
                val player = store.getComponent(ref, Player.getComponentType())
                player?.sendMessage(Message.raw("Item pipe settings updated."))
            }
            PageAction.Reset -> {
                applySnapshot(component, initialSnapshot)
                markComponentDirty()
                rebuild()
            }
            PageAction.Close -> close()
        }
    }

    private fun applyFromData(
        component: ItemTransportComponent,
        data: PageEventData,
    ) {
        val speed = data.transferSpeed.toIntOrNull()?.coerceAtLeast(1) ?: component.itemsPerSecond
        component.itemsPerSecond = speed
        component.sideModes.clear()
        component.sideFilters.clear()

        for (side in SIDE_ORDER) {
            val mode =
                try {
                    ItemTransportMode.valueOf(data.mode(side))
                } catch (ex: IllegalArgumentException) {
                    ItemTransportMode.DISABLED
                }
            component.setSideMode(side, mode)

            val filterMode =
                try {
                    ItemFilterMode.valueOf(data.filterMode(side))
                } catch (ex: IllegalArgumentException) {
                    ItemFilterMode.WHITELIST
                }
            val filter = buildFilter(data.filterIds(side), filterMode, data.filterMeta(side))
            component.setSideFilter(side, filter)
        }
    }

    private fun buildEventData(action: PageAction): EventData {
        val data =
            EventData()
                .append(PageEventData.KEY_ACTION, action)
                .append(PageEventData.KEY_TRANSFER_SPEED, "#TransferSpeed.Value")

        for (side in SIDE_ORDER) {
            val selector = sideSelector(side)
            data.append(PageEventData.keyMode(side), "$selector #Mode.Value")
            data.append(PageEventData.keyFilterIds(side), "$selector #FilterIds.Value")
            data.append(PageEventData.keyFilterMode(side), "$selector #FilterMode.Value")
            data.append(PageEventData.keyFilterMeta(side), "$selector #MatchMeta.Value")
        }

        return data
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

    private fun snapshotFromComponent(): ItemTransportSnapshot? {
        val component = componentRef.getStore().getComponent(componentRef, ItemTransportComponent.getComponentType()) ?: return null
        return snapshot(component)
    }

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
        snapshot: ItemTransportSnapshot?,
    ) {
        if (snapshot == null) {
            return
        }
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

    private fun markComponentDirty() {
        val stateInfo = componentRef.getStore().getComponent(componentRef, BlockModule.BlockStateInfo.getComponentType())
        stateInfo?.markNeedsSaving()
    }

    private fun sideSelector(side: BlockSide): String =
        "#Side" + side.name.lowercase().replaceFirstChar { it.titlecase() }

    private data class ItemTransportSnapshot(
        val transferSpeed: Int,
        val sideModes: Map<BlockSide, ItemTransportMode>,
        val sideFilters: Map<BlockSide, ItemFilter>,
    )

    enum class PageAction {
        Apply,
        Reset,
        Close,
        ;

        companion object {
            val CODEC: Codec<PageAction> = EnumCodec(PageAction::class.java)
        }
    }

    class PageEventData {
        var action: PageAction = PageAction.Apply
        var transferSpeed: String = ""
        var modeNorth: String = ItemTransportMode.DISABLED.name
        var modeSouth: String = ItemTransportMode.DISABLED.name
        var modeEast: String = ItemTransportMode.DISABLED.name
        var modeWest: String = ItemTransportMode.DISABLED.name
        var modeUp: String = ItemTransportMode.DISABLED.name
        var modeDown: String = ItemTransportMode.DISABLED.name
        var filterIdsNorth: String = ""
        var filterIdsSouth: String = ""
        var filterIdsEast: String = ""
        var filterIdsWest: String = ""
        var filterIdsUp: String = ""
        var filterIdsDown: String = ""
        var filterModeNorth: String = ItemFilterMode.WHITELIST.name
        var filterModeSouth: String = ItemFilterMode.WHITELIST.name
        var filterModeEast: String = ItemFilterMode.WHITELIST.name
        var filterModeWest: String = ItemFilterMode.WHITELIST.name
        var filterModeUp: String = ItemFilterMode.WHITELIST.name
        var filterModeDown: String = ItemFilterMode.WHITELIST.name
        var filterMetaNorth: Boolean = false
        var filterMetaSouth: Boolean = false
        var filterMetaEast: Boolean = false
        var filterMetaWest: Boolean = false
        var filterMetaUp: Boolean = false
        var filterMetaDown: Boolean = false

        fun mode(side: BlockSide): String =
            when (side) {
                BlockSide.NORTH -> modeNorth
                BlockSide.SOUTH -> modeSouth
                BlockSide.EAST -> modeEast
                BlockSide.WEST -> modeWest
                BlockSide.UP -> modeUp
                BlockSide.DOWN -> modeDown
            }

        fun filterIds(side: BlockSide): String =
            when (side) {
                BlockSide.NORTH -> filterIdsNorth
                BlockSide.SOUTH -> filterIdsSouth
                BlockSide.EAST -> filterIdsEast
                BlockSide.WEST -> filterIdsWest
                BlockSide.UP -> filterIdsUp
                BlockSide.DOWN -> filterIdsDown
            }

        fun filterMode(side: BlockSide): String =
            when (side) {
                BlockSide.NORTH -> filterModeNorth
                BlockSide.SOUTH -> filterModeSouth
                BlockSide.EAST -> filterModeEast
                BlockSide.WEST -> filterModeWest
                BlockSide.UP -> filterModeUp
                BlockSide.DOWN -> filterModeDown
            }

        fun filterMeta(side: BlockSide): Boolean =
            when (side) {
                BlockSide.NORTH -> filterMetaNorth
                BlockSide.SOUTH -> filterMetaSouth
                BlockSide.EAST -> filterMetaEast
                BlockSide.WEST -> filterMetaWest
                BlockSide.UP -> filterMetaUp
                BlockSide.DOWN -> filterMetaDown
            }

        companion object {
            const val KEY_ACTION = "Action"
            const val KEY_TRANSFER_SPEED = "@TransferSpeed"

            fun keyMode(side: BlockSide): String = "@Mode_${side.name}"

            fun keyFilterIds(side: BlockSide): String = "@FilterIds_${side.name}"

            fun keyFilterMode(side: BlockSide): String = "@FilterMode_${side.name}"

            fun keyFilterMeta(side: BlockSide): String = "@FilterMeta_${side.name}"

            val CODEC: BuilderCodec<PageEventData> =
                BuilderCodec
                    .builder(PageEventData::class.java, ::PageEventData)
                    .append(
                        KeyedCodec(KEY_ACTION, PageAction.CODEC),
                        { data, value -> data.action = value },
                        { data -> data.action },
                    ).add()
                    .append(
                        KeyedCodec(KEY_TRANSFER_SPEED, Codec.STRING),
                        { data, value -> data.transferSpeed = value },
                        { data -> data.transferSpeed },
                    ).add()
                    .append(
                        KeyedCodec(keyMode(BlockSide.NORTH), Codec.STRING),
                        { data, value -> data.modeNorth = value },
                        { data -> data.modeNorth },
                    ).add()
                    .append(
                        KeyedCodec(keyMode(BlockSide.SOUTH), Codec.STRING),
                        { data, value -> data.modeSouth = value },
                        { data -> data.modeSouth },
                    ).add()
                    .append(
                        KeyedCodec(keyMode(BlockSide.EAST), Codec.STRING),
                        { data, value -> data.modeEast = value },
                        { data -> data.modeEast },
                    ).add()
                    .append(
                        KeyedCodec(keyMode(BlockSide.WEST), Codec.STRING),
                        { data, value -> data.modeWest = value },
                        { data -> data.modeWest },
                    ).add()
                    .append(
                        KeyedCodec(keyMode(BlockSide.UP), Codec.STRING),
                        { data, value -> data.modeUp = value },
                        { data -> data.modeUp },
                    ).add()
                    .append(
                        KeyedCodec(keyMode(BlockSide.DOWN), Codec.STRING),
                        { data, value -> data.modeDown = value },
                        { data -> data.modeDown },
                    ).add()
                    .append(
                        KeyedCodec(keyFilterIds(BlockSide.NORTH), Codec.STRING),
                        { data, value -> data.filterIdsNorth = value },
                        { data -> data.filterIdsNorth },
                    ).add()
                    .append(
                        KeyedCodec(keyFilterIds(BlockSide.SOUTH), Codec.STRING),
                        { data, value -> data.filterIdsSouth = value },
                        { data -> data.filterIdsSouth },
                    ).add()
                    .append(
                        KeyedCodec(keyFilterIds(BlockSide.EAST), Codec.STRING),
                        { data, value -> data.filterIdsEast = value },
                        { data -> data.filterIdsEast },
                    ).add()
                    .append(
                        KeyedCodec(keyFilterIds(BlockSide.WEST), Codec.STRING),
                        { data, value -> data.filterIdsWest = value },
                        { data -> data.filterIdsWest },
                    ).add()
                    .append(
                        KeyedCodec(keyFilterIds(BlockSide.UP), Codec.STRING),
                        { data, value -> data.filterIdsUp = value },
                        { data -> data.filterIdsUp },
                    ).add()
                    .append(
                        KeyedCodec(keyFilterIds(BlockSide.DOWN), Codec.STRING),
                        { data, value -> data.filterIdsDown = value },
                        { data -> data.filterIdsDown },
                    ).add()
                    .append(
                        KeyedCodec(keyFilterMode(BlockSide.NORTH), Codec.STRING),
                        { data, value -> data.filterModeNorth = value },
                        { data -> data.filterModeNorth },
                    ).add()
                    .append(
                        KeyedCodec(keyFilterMode(BlockSide.SOUTH), Codec.STRING),
                        { data, value -> data.filterModeSouth = value },
                        { data -> data.filterModeSouth },
                    ).add()
                    .append(
                        KeyedCodec(keyFilterMode(BlockSide.EAST), Codec.STRING),
                        { data, value -> data.filterModeEast = value },
                        { data -> data.filterModeEast },
                    ).add()
                    .append(
                        KeyedCodec(keyFilterMode(BlockSide.WEST), Codec.STRING),
                        { data, value -> data.filterModeWest = value },
                        { data -> data.filterModeWest },
                    ).add()
                    .append(
                        KeyedCodec(keyFilterMode(BlockSide.UP), Codec.STRING),
                        { data, value -> data.filterModeUp = value },
                        { data -> data.filterModeUp },
                    ).add()
                    .append(
                        KeyedCodec(keyFilterMode(BlockSide.DOWN), Codec.STRING),
                        { data, value -> data.filterModeDown = value },
                        { data -> data.filterModeDown },
                    ).add()
                    .append(
                        KeyedCodec(keyFilterMeta(BlockSide.NORTH), LENIENT_BOOLEAN_CODEC),
                        { data, value -> data.filterMetaNorth = value },
                        { data -> data.filterMetaNorth },
                    ).add()
                    .append(
                        KeyedCodec(keyFilterMeta(BlockSide.SOUTH), LENIENT_BOOLEAN_CODEC),
                        { data, value -> data.filterMetaSouth = value },
                        { data -> data.filterMetaSouth },
                    ).add()
                    .append(
                        KeyedCodec(keyFilterMeta(BlockSide.EAST), LENIENT_BOOLEAN_CODEC),
                        { data, value -> data.filterMetaEast = value },
                        { data -> data.filterMetaEast },
                    ).add()
                    .append(
                        KeyedCodec(keyFilterMeta(BlockSide.WEST), LENIENT_BOOLEAN_CODEC),
                        { data, value -> data.filterMetaWest = value },
                        { data -> data.filterMetaWest },
                    ).add()
                    .append(
                        KeyedCodec(keyFilterMeta(BlockSide.UP), LENIENT_BOOLEAN_CODEC),
                        { data, value -> data.filterMetaUp = value },
                        { data -> data.filterMetaUp },
                    ).add()
                    .append(
                        KeyedCodec(keyFilterMeta(BlockSide.DOWN), LENIENT_BOOLEAN_CODEC),
                        { data, value -> data.filterMetaDown = value },
                        { data -> data.filterMetaDown },
                    ).add()
                    .build()
        }
    }

    companion object {
        private const val LAYOUT = "Pages/ItemTransportComponentConfigPage.ui"

        private val SIDE_ORDER =
            listOf(
                BlockSide.NORTH,
                BlockSide.SOUTH,
                BlockSide.EAST,
                BlockSide.WEST,
                BlockSide.UP,
                BlockSide.DOWN,
            )

        private val MODE_ENTRIES =
            listOf(
                DropdownEntryInfo(LocalizableString.fromString("Disabled"), ItemTransportMode.DISABLED.name),
                DropdownEntryInfo(LocalizableString.fromString("Pull"), ItemTransportMode.PULL.name),
                DropdownEntryInfo(LocalizableString.fromString("Push"), ItemTransportMode.PUSH.name),
            )

        private val FILTER_MODE_ENTRIES =
            listOf(
                DropdownEntryInfo(LocalizableString.fromString("Whitelist"), ItemFilterMode.WHITELIST.name),
                DropdownEntryInfo(LocalizableString.fromString("Blacklist"), ItemFilterMode.BLACKLIST.name),
            )

        private val LENIENT_BOOLEAN_CODEC =
            object : Codec<Boolean> {
                override fun decode(bsonValue: BsonValue, extraInfo: ExtraInfo): Boolean =
                    when {
                        bsonValue.isBoolean -> bsonValue.asBoolean().value
                        bsonValue.isString -> parseBoolean(bsonValue.asString().value)
                        bsonValue.isInt32 -> bsonValue.asInt32().value != 0
                        bsonValue.isInt64 -> bsonValue.asInt64().value != 0L
                        bsonValue.isDouble -> bsonValue.asDouble().value != 0.0
                        else -> false
                    }

                override fun encode(t: Boolean, extraInfo: ExtraInfo): BsonValue = BsonBoolean(t)

                override fun decodeJson(reader: RawJsonReader, extraInfo: ExtraInfo): Boolean {
                    reader.consumeWhiteSpace()
                    return when (val next = reader.peek()) {
                        '"'.code -> parseBoolean(reader.readString())
                        't'.code,
                        'T'.code,
                        'f'.code,
                        'F'.code,
                        -> reader.readBooleanValue()
                        '-'.code,
                        '0'.code,
                        '1'.code,
                        '2'.code,
                        '3'.code,
                        '4'.code,
                        '5'.code,
                        '6'.code,
                        '7'.code,
                        '8'.code,
                        '9'.code,
                        -> reader.readIntValue() != 0
                        else -> {
                            val read = reader.read()
                            read != -1 && read != 0 && read != '0'.code
                        }
                    }
                }

                override fun toSchema(context: SchemaContext): Schema = Codec.BOOLEAN.toSchema(context)
            }

        private fun parseBoolean(raw: String): Boolean =
            when (raw.trim().lowercase()) {
                "true",
                "1",
                "yes",
                "y",
                "on",
                -> true
                "false",
                "0",
                "no",
                "n",
                "off",
                "",
                -> false
                else -> raw.toDoubleOrNull()?.let { it != 0.0 } ?: false
            }
    }
}
