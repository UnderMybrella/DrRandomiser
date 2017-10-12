package org.abimon.drRandomiser

data class RandomiserData(
        val fullChaos: Boolean = false,
        val randomiseSprites: Boolean = true,
        val randomiseText: Boolean = false,
        val randomiseMusic: Boolean = false,
        val heatdeath: Boolean = true
)