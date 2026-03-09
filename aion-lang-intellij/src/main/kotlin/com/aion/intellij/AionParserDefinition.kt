package com.aion.intellij

import com.intellij.lang.ASTNode
import com.intellij.lang.ParserDefinition
import com.intellij.lang.PsiParser
import com.intellij.lexer.Lexer
import com.intellij.openapi.project.Project
import com.intellij.psi.FileViewProvider
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.tree.IFileElementType
import com.intellij.psi.tree.TokenSet

val AION_FILE = IFileElementType(AionLanguage)

class AionParserDefinition : ParserDefinition {
    override fun createLexer(project: Project): Lexer = AionLexer()

    // Aion has no full PSI parser yet — return a no-op parser that produces a flat file node.
    override fun createParser(project: Project): PsiParser = PsiParser { root, builder ->
        val marker = builder.mark()
        while (!builder.eof()) builder.advanceLexer()
        marker.done(root)
        builder.treeBuilt
    }

    override fun getFileNodeType(): IFileElementType = AION_FILE

    override fun getCommentTokens(): TokenSet =
        TokenSet.create(AionTokenTypes.LINE_COMMENT, AionTokenTypes.BLOCK_COMMENT)

    override fun getStringLiteralElements(): TokenSet =
        TokenSet.create(AionTokenTypes.STRING)

    override fun createElement(node: ASTNode): PsiElement =
        throw UnsupportedOperationException("No PSI elements for Aion yet")

    override fun createFile(viewProvider: FileViewProvider): PsiFile =
        AionFile(viewProvider)
}
