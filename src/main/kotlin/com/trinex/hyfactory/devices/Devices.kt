package com.trinex.hyfactory.devices

import com.trinex.hyfactory.devices.types.Consumer
import com.trinex.hyfactory.devices.types.Solar
import com.trinex.lib.api.device.EnergyDeviceTypeRegistry

object Devices {
    val deviceTypes =
        buildList {
            add(Solar())
            add(Consumer())
        }

    fun init() {
        deviceTypes.forEach {
            EnergyDeviceTypeRegistry.register("Trinex", it.javaClass.simpleName, it)
        }
    }
}
