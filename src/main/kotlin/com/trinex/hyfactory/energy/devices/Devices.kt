package com.trinex.hyfactory.energy.devices

import com.trinex.hyfactory.energy.devices.types.Consumer
import com.trinex.hyfactory.energy.devices.types.Solar
import com.trinex.lib.api.energy.device.EnergyDeviceTypeRegistry

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
