package com.avi.peek.util

import kotlinx.coroutines.flow.MutableStateFlow

object DeepLinkBus {
    val link = MutableStateFlow<String?>(null)

    fun offer(value: String?) {
        if (value.isNullOrBlank()) return
        if (value.startsWith("peek://")) link.value = value
    }

    fun consume() {
        link.value = null
    }
}
