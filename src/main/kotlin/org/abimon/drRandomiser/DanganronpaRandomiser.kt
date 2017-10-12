package org.abimon.drRandomiser

import org.abimon.imperator.handle.Imperator
import org.abimon.imperator.impl.InstanceSoldier
import org.abimon.spiral.modding.IPlugin
import kotlin.reflect.full.memberProperties

class DanganronpaRandomiser: IPlugin {
    companion object {
        val registerSoldiers: Any.(Imperator) -> Unit = { imperator -> this.javaClass.kotlin.memberProperties.filter { it.returnType.classifier == InstanceSoldier::class }.forEach { imperator.hireSoldier(it.get(this) as? InstanceSoldier<*> ?: return@forEach) } }
        val deregisterSoldiers: Any.(Imperator) -> Unit = { imperator -> this.javaClass.kotlin.memberProperties.filter { it.returnType.classifier == InstanceSoldier::class }.forEach { imperator.fireSoldier(it.get(this) as? InstanceSoldier<*> ?: return@forEach) } }

        var loaded: Boolean = false
    }

    override fun enable(imperator: Imperator) {
        if(!loaded) {
            Randomiser.registerSoldiers(imperator)
            loaded = true
        }
    }

    override fun disable(imperator: Imperator) {
        if(loaded) {
            Randomiser.deregisterSoldiers(imperator)
            loaded = false
        }
    }
}