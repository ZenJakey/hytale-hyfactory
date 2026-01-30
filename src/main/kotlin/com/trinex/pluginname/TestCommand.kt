package com.trinex.pluginname

import com.hypixel.hytale.protocol.GameMode
import com.hypixel.hytale.server.core.Message
import com.hypixel.hytale.server.core.command.system.CommandContext
import com.hypixel.hytale.server.core.command.system.basecommands.CommandBase

class TestCommand(val plugin: PluginName)
    : CommandBase("test", "Test command") {

    private val pluginName = plugin.name
    private val pluginVersion = plugin.manifest.version

    init {
        setPermissionGroup(GameMode.Adventure)
    }

    override fun executeSync(ctx: CommandContext) {
        ctx.sendMessage(Message.raw("Hello from $pluginName version $pluginVersion!"))
    }

}