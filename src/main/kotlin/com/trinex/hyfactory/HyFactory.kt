package com.trinex.hyfactory

import com.hypixel.hytale.logger.HytaleLogger
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.Interaction
import com.hypixel.hytale.server.core.plugin.JavaPlugin
import com.hypixel.hytale.server.core.plugin.JavaPluginInit
import com.trinex.hyfactory.commands.VersionCommand
import com.trinex.hyfactory.devices.Devices
import com.trinex.hyfactory.interactions.ConfigureSolarInteraction
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

        this
            .getCodecRegistry(Interaction.CODEC)
            .register("ConfigureSolar", ConfigureSolarInteraction::class.java, ConfigureSolarInteraction.CODEC)

        this.commandRegistry.registerCommand(VersionCommand(this))
    }

    companion object {
        private var instance: HyFactory? = null

        fun get(): HyFactory = instance ?: throw IllegalStateException("Plugin not initialized yet!")
    }
}
