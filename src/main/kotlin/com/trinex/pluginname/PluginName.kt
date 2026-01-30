package com.trinex.pluginname

import com.hypixel.hytale.logger.HytaleLogger
import com.hypixel.hytale.server.core.plugin.JavaPlugin
import com.hypixel.hytale.server.core.plugin.JavaPluginInit

class PluginName(init: JavaPluginInit) : JavaPlugin(init) {
    private val logger = HytaleLogger.forEnclosingClass()

    init {
        logger.atInfo().log("Hello from " + this.name + " version " + this.manifest.version)
    }

    override fun setup() {
        logger.atInfo().log("Setting up plugin " + this.name)

        this.commandRegistry.registerCommand(TestCommand(this))
    }
}