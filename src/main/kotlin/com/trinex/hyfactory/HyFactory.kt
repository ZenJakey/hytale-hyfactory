package com.trinex.hyfactory

import com.hypixel.hytale.component.ResourceType
import com.hypixel.hytale.logger.HytaleLogger
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.Interaction
import com.hypixel.hytale.server.core.plugin.JavaPlugin
import com.hypixel.hytale.server.core.plugin.JavaPluginInit
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore
import com.trinex.hyfactory.commands.VersionCommand
import com.trinex.hyfactory.energy.devices.Devices
import com.trinex.hyfactory.interactions.ConfigureItemPipeInteraction
import com.trinex.hyfactory.interactions.ConfigureSolarInteraction
import com.trinex.hyfactory.itempipe.ItemPipeVisualSystem
import com.trinex.lib.messenger.Messenger

class HyFactory(
    init: JavaPluginInit,
) : JavaPlugin(init) {
    private val logger = HytaleLogger.forEnclosingClass()
    val messenger = Messenger(this.name)

    init {
        logger.atInfo().log("Hello from " + this.name + " version " + this.manifest.version)
        instance = this
    }

    override fun setup() {
        logger.atInfo().log("Setting up plugin " + this.name)
        Devices.init()

        this.getChunkStoreRegistry().registerSystem(ItemPipeVisualSystem())

        this
            .getCodecRegistry(Interaction.CODEC)
            .register("ConfigureSolar", ConfigureSolarInteraction::class.java, ConfigureSolarInteraction.CODEC)
            .register("ConfigureItemPipe", ConfigureItemPipeInteraction::class.java, ConfigureItemPipeInteraction.CODEC)

        this.commandRegistry.registerCommand(VersionCommand(this))
    }

    companion object {
        private var instance: HyFactory? = null

        fun get(): HyFactory = instance ?: throw IllegalStateException("Plugin not initialized yet!")
    }
}
