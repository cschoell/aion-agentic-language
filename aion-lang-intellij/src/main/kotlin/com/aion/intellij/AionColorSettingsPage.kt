package com.aion.intellij

import com.intellij.openapi.editor.colors.TextAttributesKey
import com.intellij.openapi.fileTypes.SyntaxHighlighter
import com.intellij.openapi.options.colors.AttributesDescriptor
import com.intellij.openapi.options.colors.ColorDescriptor
import com.intellij.openapi.options.colors.ColorSettingsPage
import javax.swing.Icon
import com.intellij.icons.AllIcons

class AionColorSettingsPage : ColorSettingsPage {
    private val DESCRIPTORS = arrayOf(
        AttributesDescriptor("Keyword",       AionColors.KEYWORD),
        AttributesDescriptor("Built-in function", AionColors.BUILTIN),
        AttributesDescriptor("Annotation",    AionColors.ANNOTATION),
        AttributesDescriptor("Type name",     AionColors.TYPE_IDENT),
        AttributesDescriptor("Identifier",    AionColors.IDENT),
        AttributesDescriptor("Number",        AionColors.NUMBER),
        AttributesDescriptor("String",        AionColors.STRING),
        AttributesDescriptor("Line comment",  AionColors.LINE_COMMENT),
        AttributesDescriptor("Block comment", AionColors.BLOCK_COMMENT),
        AttributesDescriptor("Operator",      AionColors.OPERATOR),
        AttributesDescriptor("Braces/brackets", AionColors.BRACE),
        AttributesDescriptor("Comma",         AionColors.COMMA),
        AttributesDescriptor("Semicolon",     AionColors.SEMICOLON),
        AttributesDescriptor("Bad character", AionColors.BAD_CHAR),
    )

    override fun getIcon(): Icon = AllIcons.FileTypes.Text
    override fun getHighlighter(): SyntaxHighlighter = AionSyntaxHighlighter()
    override fun getDemoText(): String = """
        // Aion language demo
        /* block comment */
        @pure fn add(a: Int, b: Int) -> Int {
            return a + b
        }

        type Score = Int where { self >= 0 and self <= 100 }

        enum Shape {
            Circle { radius: Float },
            Rect   { width: Float, height: Float },
        }

        @io fn main() {
            let x = 0xFF
            let y = 0b1010_1010
            let s = "Hello, ${'$'}{x}!"
            let result = add(x, y)
            print(str(result))
            for i in 1..=10 {
                print(str(i))
            }
        }
    """.trimIndent()

    override fun getAdditionalHighlightingTagToDescriptorMap(): Map<String, TextAttributesKey>? = null
    override fun getAttributeDescriptors(): Array<AttributesDescriptor> = DESCRIPTORS
    override fun getColorDescriptors(): Array<ColorDescriptor> = ColorDescriptor.EMPTY_ARRAY
    override fun getDisplayName(): String = "Aion"
}
