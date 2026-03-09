package com.aion.intellij

import com.intellij.extapi.psi.PsiFileBase
import com.intellij.psi.FileViewProvider

class AionFile(viewProvider: FileViewProvider) : PsiFileBase(viewProvider, AionLanguage) {
    override fun getFileType() = AionFileType
    override fun toString() = "Aion File"
}
