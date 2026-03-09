/**
 * Aion Language — Lexer Grammar
 *
 * Design goals (AI-agent-optimal):
 *   - Minimal keyword surface area
 *   - No implicit anything — every operation is spelled out
 *   - Consistent token shapes so AI can predict structure from first token
 *   - Line-oriented where it reduces ambiguity (no expression-statement confusion)
 */
lexer grammar AionLexer;

// ── Keywords ──────────────────────────────────────────────────────────────────
LET         : 'let' ;
MUT         : 'mut' ;
FN          : 'fn' ;
RETURN      : 'return' ;
TYPE        : 'type' ;
ENUM        : 'enum' ;
MATCH       : 'match' ;
IF          : 'if' ;
ELSE        : 'else' ;
WHILE       : 'while' ;
FOR         : 'for' ;
IN          : 'in' ;
IMPORT      : 'import' ;
AS          : 'as' ;
TRUE        : 'true' ;
FALSE       : 'false' ;
NONE        : 'none' ;
SOME        : 'some' ;
OK          : 'ok' ;
ERR         : 'err' ;
AND         : 'and' ;
OR          : 'or' ;
NOT         : 'not' ;
ASSERT      : 'assert' ;     // assert <expr>, "message"
DESCRIBE    : 'describe' ;   // describe "doc-string"  (inside fn body, first stmt)
TRUSTED     : 'trusted' ;    // trusted <expr>  — mark value as trusted
UNTRUSTED   : 'untrusted' ;  // untrusted <expr> — mark value as untrusted
CONST       : 'const' ;      // const NAME: Type = expr  (module-level constant)
BREAK       : 'break' ;      // break  (exit innermost loop)
CONTINUE    : 'continue' ;   // continue  (next iteration of innermost loop)
WHERE       : 'where' ;      // where { self … }  — refinement constraint on a type alias

// ── Effect annotations ────────────────────────────────────────────────────────
ANN_PURE       : '@pure' ;
ANN_IO         : '@io' ;
ANN_MUT        : '@mut' ;
ANN_THROWS     : '@throws' ;
ANN_ASYNC      : '@async' ;
ANN_TEST       : '@test' ;
ANN_DEPRECATED : '@deprecated' ;
ANN_TOOL       : '@tool' ;                   // callable by AI agents
ANN_REQUIRES   : '@requires' ;               // @requires(expr)   pre-condition
ANN_ENSURES    : '@ensures' ;                // @ensures(expr)    post-condition
ANN_TIMEOUT    : '@timeout' ;                // @timeout(ms)      max execution time
ANN_TRUSTED    : '@trusted' ;               // @trusted          all params treated as trusted
ANN_UNTRUSTED  : '@untrusted' ;             // @untrusted        all params treated as untrusted
ANN_ON_FAIL    : '@on_fail' ;               // @on_fail("hint")  structured error hint for agents

// ── Built-in types ────────────────────────────────────────────────────────────
T_INT       : 'Int' ;
T_FLOAT     : 'Float' ;
T_BOOL      : 'Bool' ;
T_STR       : 'Str' ;
T_UNIT      : 'Unit' ;
T_OPTION    : 'Option' ;
T_RESULT    : 'Result' ;
T_LIST      : 'List' ;
T_MAP       : 'Map' ;

// ── Symbols ───────────────────────────────────────────────────────────────────
ARROW       : '->' ;
FAT_ARROW   : '=>' ;
PIPE        : '|>' ;
COLON       : ':' ;
DCOLON      : '::' ;
COMMA       : ',' ;
DOT         : '.' ;
DOTDOT      : '..' ;
EQ          : '=' ;
EQEQ        : '==' ;
NEQ         : '!=' ;
LT          : '<' ;
LE          : '<=' ;
GT          : '>' ;
GE          : '>=' ;
PLUS        : '+' ;
MINUS       : '-' ;
STAR        : '*' ;
SLASH       : '/' ;
PERCENT     : '%' ;
BANG        : '!' ;
AMP         : '&' ;
QUESTION    : '?' ;
LPAREN      : '(' ;
RPAREN      : ')' ;
LBRACE      : '{' ;
RBRACE      : '}' ;
LBRACKET    : '[' ;
RBRACKET    : ']' ;
UNDERSCORE  : '_' ;

// ── Literals ──────────────────────────────────────────────────────────────────
INT_LIT     : '-'? [0-9]+ ;
FLOAT_LIT   : '-'? [0-9]+ '.' [0-9]+ ;
// Interpolated string — contains at least one ${...} hole; matched before plain STR_LIT
INTERP_STR  : '"' (ESC | '$' '{' ~[}]+ '}' | ~["\\\n$])* '$' '{' ~[}]+ '}' (ESC | '$' '{' ~[}]+ '}' | ~["\\\n$])* '"' ;
STR_LIT     : '"' (ESC | ~["\\\n])* '"' ;
fragment ESC: '\\' [nrt"\\] ;

// ── Identifiers ───────────────────────────────────────────────────────────────
IDENT       : [a-z_][a-zA-Z0-9_]* ;   // values, functions, fields — lowercase-start
TYPE_IDENT  : [A-Z][a-zA-Z0-9_]* ;    // types, enum variants — uppercase-start

// ── Whitespace / comments ─────────────────────────────────────────────────────
NEWLINE     : '\r'? '\n' -> skip ;
WS          : [ \t]+ -> skip ;
LINE_COMMENT: '//' ~[\r\n]* -> skip ;
BLOCK_COMMENT: '/*' .*? '*/' -> skip ;

