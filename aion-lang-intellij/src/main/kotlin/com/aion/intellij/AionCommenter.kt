package com.aion.intellij

import com.intellij.lang.CodeDocumentationAwareCommenter
import com.intellij.psi.PsiComment
import com.intellij.psi.tree.IElementType

class AionCommenter : CodeDocumentationAwareCommenter {
    override fun getLineCommentPrefix(): String = "// "
    override fun getBlockCommentPrefix(): String = "/* "
    override fun getBlockCommentSuffix(): String = " */"
    override fun getCommentedBlockCommentPrefix(): String = "/* "
    override fun getCommentedBlockCommentSuffix(): String = " */"
    override fun getLineCommentTokenType(): IElementType = AionTokenTypes.LINE_COMMENT
    override fun getBlockCommentTokenType(): IElementType = AionTokenTypes.BLOCK_COMMENT
    override fun getDocumentationCommentTokenType(): IElementType? = null
    override fun getDocumentationCommentPrefix(): String? = null
    override fun getDocumentationCommentLinePrefix(): String? = null
    override fun getDocumentationCommentSuffix(): String? = null
    override fun isDocumentationComment(element: PsiComment): Boolean = false
}
