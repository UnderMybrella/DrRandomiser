package org.abimon.drRandomiser

data class RandomiserData(
        val randomiseSprites: Boolean = true,
        val randomiseText: Boolean = false,
        val randomiseMusic: Boolean = false,

        val randomise: List<List<String>> = emptyList(),

        val exempt: List<String> = listOf(".*font.*"),

        val anarchySprites: Boolean = false,
        val anarchyText: Boolean = false,
        val anarchyMusic: Boolean = false
)