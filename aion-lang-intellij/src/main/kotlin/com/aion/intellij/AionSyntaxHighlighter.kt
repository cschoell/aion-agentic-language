package com.aion.intellij

import com.intellij.lexer.Lexer
import com.intellij.openapi.editor.DefaultLanguageHighlighterColors
import com.intellij.openapi.editor.colors.TextAttributesKey
import com.intellij.openapi.editor.colors.TextAttributesKey.createTextAttributesKey
import com.intellij.openapi.fileTypes.SyntaxHighlighterBase
import com.intellij.psi.tree.IElementType

object AionColors {
    val KEYWORD      = createTextAttributesKey("AION_KEYWORD",      DefaultLanguageHighlighterColors.KEYWORD)
    val BUILTIN      = createTextAttributesKey("AION_BUILTIN",      DefaultLanguageHighlighterColors.STATIC_METHOD)
    val ANNOTATION   = createTextAttributesKey("AION_ANNOTATION",   DefaultLanguageHighlighterColors.METADATA)
    val TYPE_IDENT   = createTextAttributesKey("AION_TYPE_IDENT",   DefaultLanguageHighlighterColors.CLASS_NAME)
    val IDENT        = createTextAttributesKey("AION_IDENT",        DefaultLanguageHighlighterColors.IDENTIFIER)
    val NUMBER       = createTextAttributesKey("AION_NUMBER",       DefaultLanguageHighlighterColors.NUMBER)
    val STRING       = createTextAttributesKey("AION_STRING",       DefaultLanguageHighlighterColors.STRING)
    val LINE_COMMENT = createTextAttributesKey("AION_LINE_COMMENT", DefaultLanguageHighlighterColors.LINE_COMMENT)
    val BLOCK_COMMENT= createTextAttributesKey("AION_BLOCK_COMMENT",DefaultLanguageHighlighterColors.BLOCK_COMMENT)
    val OPERATOR     = createTextAttributesKey("AION_OPERATOR",     DefaultLanguageHighlighterColors.OPERATION_SIGN)
    val BRACE        = createTextAttributesKey("AION_BRACE",        DefaultLanguageHighlighterColors.BRACES)
    val COMMA        = createTextAttributesKey("AION_COMMA",        DefaultLanguageHighlighterColors.COMMA)
    val SEMICOLON    = createTextAttributesKey("AION_SEMICOLON",    DefaultLanguageHighlighterColors.SEMICOLON)
    val BAD_CHAR     = createTextAttributesKey("AION_BAD_CHAR",     DefaultLanguageHighlighterColors.INVALID_STRING_ESCAPE)
}

class AionSyntaxHighlighter : SyntaxHighlighterBase() {
    override fun getHighlightingLexer(): Lexer = AionLexer()

    override fun getTokenHighlights(tokenType: IElementType): Array<TextAttributesKey> =
        when (tokenType) {
            AionTokenTypes.KEYWORD       -> pack(AionColors.KEYWORD)
            AionTokenTypes.BUILTIN       -> pack(AionColors.BUILTIN)
            AionTokenTypes.ANNOTATION    -> pack(AionColors.ANNOTATION)
            AionTokenTypes.TYPE_IDENT    -> pack(AionColors.TYPE_IDENT)
            AionTokenTypes.IDENT         -> pack(AionColors.IDENT)
            AionTokenTypes.NUMBER        -> pack(AionColors.NUMBER)
            AionTokenTypes.STRING        -> pack(AionColors.STRING)
            AionTokenTypes.LINE_COMMENT  -> pack(AionColors.LINE_COMMENT)
            AionTokenTypes.BLOCK_COMMENT -> pack(AionColors.BLOCK_COMMENT)
            AionTokenTypes.OPERATOR      -> pack(AionColors.OPERATOR)
            AionTokenTypes.BRACE,
            AionBraceTokens.LBRACE, AionBraceTokens.RBRACE,
            AionBraceTokens.LBRACKET, AionBraceTokens.RBRACKET,
            AionBraceTokens.LPAREN, AionBraceTokens.RPAREN -> pack(AionColors.BRACE)
            AionTokenTypes.COMMA         -> pack(AionColors.COMMA)
            AionTokenTypes.SEMICOLON     -> pack(AionColors.SEMICOLON)
            AionTokenTypes.BAD_CHAR      -> pack(AionColors.BAD_CHAR)
            else                         -> emptyArray()
        }
}
