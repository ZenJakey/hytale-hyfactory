package com.trinex.hyfactory.devices

import com.trinex.hyfactory.devices.types.Solar
import com.trinex.lib.api.device.EnergyDeviceTypeRegistry

object Devices {
    val deviceTypes =
        buildList {
            add(Solar())
        }

    fun init() {
        deviceTypes.forEach {
            EnergyDeviceTypeRegistry.register("Trinex", it.javaClass.simpleName, it)
        }
    }
}
