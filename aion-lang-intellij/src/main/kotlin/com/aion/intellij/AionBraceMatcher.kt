package com.aion.intellij

import com.intellij.lang.BracePair
import com.intellij.lang.PairedBraceMatcher
import com.intellij.psi.PsiFile
import com.intellij.psi.tree.IElementType

// We reuse the BRACE token type but need distinct types for each pair.
// Since our lexer emits a single BRACE token, we define lightweight wrappers here.
object AionBraceTokens {
    val LBRACE  = IElementType("AION_LBRACE",  AionLanguage)
    val RBRACE  = IElementType("AION_RBRACE",  AionLanguage)
    val LBRACKET= IElementType("AION_LBRACKET",AionLanguage)
    val RBRACKET= IElementType("AION_RBRACKET",AionLanguage)
    val LPAREN  = IElementType("AION_LPAREN",  AionLanguage)
    val RPAREN  = IElementType("AION_RPAREN",  AionLanguage)
}

class AionBraceMatcher : PairedBraceMatcher {
    private val PAIRS = arrayOf(
        BracePair(AionBraceTokens.LBRACE,   AionBraceTokens.RBRACE,   true),
        BracePair(AionBraceTokens.LBRACKET, AionBraceTokens.RBRACKET, false),
        BracePair(AionBraceTokens.LPAREN,   AionBraceTokens.RPAREN,   false),
    )

    override fun getPairs(): Array<BracePair> = PAIRS
    override fun isPairedBracesAllowedBeforeType(lbraceType: IElementType, contextType: IElementType?): Boolean = true
    override fun getCodeConstructStart(file: PsiFile, openingBraceOffset: Int): Int = openingBraceOffset
}
