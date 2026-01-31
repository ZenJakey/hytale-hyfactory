package com.trinex.hyfactory.commands

import com.hypixel.hytale.protocol.GameMode
import com.hypixel.hytale.server.core.command.system.CommandContext
import com.hypixel.hytale.server.core.command.system.basecommands.CommandBase
import com.trinex.hyfactory.HyFactory

class VersionCommand(
    val plugin: HyFactory,
) : CommandBase("hyfactory", "Gets the plugin version.") {
    private val pluginName = plugin.name
    private val pluginVersion = plugin.manifest.version

    init {
        setPermissionGroup(GameMode.Adventure)
    }

    override fun executeSync(ctx: CommandContext) {
        plugin.messenger.sendMessage("$pluginName version $pluginVersion!", ctx)
    }
}
