package com.aion.intellij

import com.intellij.lang.Language

object AionLanguage : Language("Aion") {
    private fun readResolve(): Any = AionLanguage
}
