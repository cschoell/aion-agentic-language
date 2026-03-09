package com.aion.intellij

import com.intellij.lexer.LexerBase
import com.intellij.psi.tree.IElementType

// Token types
object AionTokenTypes {
    val KEYWORD      = IElementType("AION_KEYWORD",      AionLanguage)
    val BUILTIN      = IElementType("AION_BUILTIN",      AionLanguage)
    val ANNOTATION   = IElementType("AION_ANNOTATION",   AionLanguage)
    val TYPE_IDENT   = IElementType("AION_TYPE_IDENT",   AionLanguage)
    val IDENT        = IElementType("AION_IDENT",        AionLanguage)
    val NUMBER       = IElementType("AION_NUMBER",       AionLanguage)
    val STRING       = IElementType("AION_STRING",       AionLanguage)
    val LINE_COMMENT = IElementType("AION_LINE_COMMENT", AionLanguage)
    val BLOCK_COMMENT= IElementType("AION_BLOCK_COMMENT",AionLanguage)
    val OPERATOR     = IElementType("AION_OPERATOR",     AionLanguage)
    val BRACE        = IElementType("AION_BRACE",        AionLanguage)
    val COMMA        = IElementType("AION_COMMA",        AionLanguage)
    val SEMICOLON    = IElementType("AION_SEMICOLON",    AionLanguage)
    val WHITESPACE   = IElementType("AION_WHITESPACE",   AionLanguage)
    val BAD_CHAR     = IElementType("AION_BAD_CHAR",     AionLanguage)
}

private val KEYWORDS = setOf(
    "fn", "let", "mut", "if", "else", "while", "for", "in", "return",
    "match", "enum", "type", "import", "const", "describe", "break",
    "continue", "true", "false", "some", "none", "ok", "err",
    "async", "await", "trait", "impl", "self"
)

private val BUILTINS = setOf(
    "print", "str", "int", "float", "len", "assert",
    "is_some", "is_none", "is_ok", "is_err", "unwrap", "unwrap_or",
    "abs", "min", "max", "pow", "sqrt", "floor", "ceil"
)

class AionLexer : LexerBase() {
    private var buffer: CharSequence = ""
    private var startOffset = 0
    private var endOffset = 0
    private var pos = 0
    private var tokenStart = 0
    private var tokenType: IElementType? = null

    override fun start(buffer: CharSequence, startOffset: Int, endOffset: Int, initialState: Int) {
        this.buffer = buffer
        this.startOffset = startOffset
        this.endOffset = endOffset
        this.pos = startOffset
        this.tokenStart = startOffset
        this.tokenType = null
        advance()
    }

    override fun getState(): Int = 0
    override fun getTokenType(): IElementType? = tokenType
    override fun getTokenStart(): Int = tokenStart
    override fun getTokenEnd(): Int = pos

    override fun advance() {
        tokenStart = pos
        if (pos >= endOffset) {
            tokenType = null
            return
        }
        tokenType = nextToken()
    }

    override fun getBufferSequence(): CharSequence = buffer
    override fun getBufferEnd(): Int = endOffset

    private fun ch(offset: Int = pos): Char = if (offset < endOffset) buffer[offset] else '\u0000'

    private fun nextToken(): IElementType {
        val c = ch()

        // Whitespace
        if (c.isWhitespace()) {
            while (pos < endOffset && ch().isWhitespace()) pos++
            return AionTokenTypes.WHITESPACE
        }

        // Line comment
        if (c == '/' && ch(pos + 1) == '/') {
            while (pos < endOffset && ch() != '\n') pos++
            return AionTokenTypes.LINE_COMMENT
        }

        // Block comment
        if (c == '/' && ch(pos + 1) == '*') {
            pos += 2
            while (pos < endOffset) {
                if (ch() == '*' && ch(pos + 1) == '/') { pos += 2; break }
                pos++
            }
            return AionTokenTypes.BLOCK_COMMENT
        }

        // String literal (handles interpolation as part of string token for simplicity)
        if (c == '"') {
            pos++
            while (pos < endOffset) {
                val sc = ch()
                if (sc == '\\') { pos += 2; continue }
                if (sc == '"') { pos++; break }
                pos++
            }
            return AionTokenTypes.STRING
        }

        // Annotation @word
        if (c == '@') {
            pos++
            while (pos < endOffset && (ch().isLetterOrDigit() || ch() == '_')) pos++
            return AionTokenTypes.ANNOTATION
        }

        // Number: 0x / 0b / 0o / decimal / float
        if (c.isDigit() || (c == '0' && pos + 1 < endOffset)) {
            if (c == '0' && (ch(pos + 1) == 'x' || ch(pos + 1) == 'X')) {
                pos += 2; while (pos < endOffset && (ch().isLetterOrDigit() || ch() == '_')) pos++
            } else if (c == '0' && (ch(pos + 1) == 'b' || ch(pos + 1) == 'B')) {
                pos += 2; while (pos < endOffset && (ch() == '0' || ch() == '1' || ch() == '_')) pos++
            } else if (c == '0' && (ch(pos + 1) == 'o' || ch(pos + 1) == 'O')) {
                pos += 2; while (pos < endOffset && (ch() in '0'..'7' || ch() == '_')) pos++
            } else {
                while (pos < endOffset && (ch().isDigit() || ch() == '_')) pos++
                if (pos < endOffset && ch() == '.' && ch(pos + 1).isDigit()) {
                    pos++
                    while (pos < endOffset && (ch().isDigit() || ch() == '_')) pos++
                }
            }
            return AionTokenTypes.NUMBER
        }

        // Identifier or keyword
        if (c.isLetter() || c == '_') {
            while (pos < endOffset && (ch().isLetterOrDigit() || ch() == '_')) pos++
            val word = buffer.subSequence(tokenStart, pos).toString()
            return when {
                word in KEYWORDS -> AionTokenTypes.KEYWORD
                word in BUILTINS -> AionTokenTypes.BUILTIN
                word[0].isUpperCase() -> AionTokenTypes.TYPE_IDENT
                else -> AionTokenTypes.IDENT
            }
        }

        // Braces / brackets / parens
        if (c == '{') { pos++; return AionBraceTokens.LBRACE }
        if (c == '}') { pos++; return AionBraceTokens.RBRACE }
        if (c == '[') { pos++; return AionBraceTokens.LBRACKET }
        if (c == ']') { pos++; return AionBraceTokens.RBRACKET }
        if (c == '(') { pos++; return AionBraceTokens.LPAREN }
        if (c == ')') { pos++; return AionBraceTokens.RPAREN }

        // Comma
        if (c == ',') { pos++; return AionTokenTypes.COMMA }

        // Semicolon
        if (c == ';') { pos++; return AionTokenTypes.SEMICOLON }

        // Operators (multi-char first)
        val twoChar = if (pos + 1 < endOffset) "${c}${ch(pos + 1)}" else ""
        if (twoChar in setOf("->", "=>", ">>", "..", "..=", "==", "!=", "<=", ">=", "&&", "||", "::", "?")) {
            pos += if (twoChar == "..=") 3 else 2
            return AionTokenTypes.OPERATOR
        }
        if (c in "=<>+-*/%!&|.:?") { pos++; return AionTokenTypes.OPERATOR }

        pos++
        return AionTokenTypes.BAD_CHAR
    }
}
