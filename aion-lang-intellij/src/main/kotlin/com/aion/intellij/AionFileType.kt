package com.aion.intellij

import com.intellij.openapi.fileTypes.LanguageFileType
import javax.swing.Icon
import com.intellij.icons.AllIcons

object AionFileType : LanguageFileType(AionLanguage) {
    override fun getName(): String = "Aion"
    override fun getDescription(): String = "Aion language source file"
    override fun getDefaultExtension(): String = "aion"
    override fun getIcon(): Icon = AllIcons.FileTypes.Text
}
