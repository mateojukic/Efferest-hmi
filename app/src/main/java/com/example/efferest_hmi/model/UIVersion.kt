package com.example.efferest_hmi.model

enum class UIVersion {
    VERSION_A,
    VERSION_B,
    VERSION_C;

    fun next(): UIVersion = when (this) {
        VERSION_A -> VERSION_B
        VERSION_B -> VERSION_C
        VERSION_C -> VERSION_A
    }
}