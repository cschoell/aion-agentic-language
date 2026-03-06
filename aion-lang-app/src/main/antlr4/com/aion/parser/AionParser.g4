/**
 * Aion Language — Parser Grammar
 *
 * Every construct starts with a unique leading token so an AI can predict
 * the full syntactic shape from the first word alone:
 *
 *   fn   → function definition
 *   let  → immutable binding
 *   mut  → mutable binding
 *   type → record/alias definition
 *   enum → sum type
 *   match→ exhaustive pattern match
 *   import → module import
 *
 * Effect annotations precede fn:  @pure fn ...  @io fn ...  @throws(E) fn ...
 */
parser grammar AionParser;
options { tokenVocab = AionLexer; }

// ── Module (top-level) ────────────────────────────────────────────────────────
module
    : topDecl* EOF
    ;

topDecl
    : importDecl
    | typeDecl
    | enumDecl
    | fnDecl
    | constDecl
    ;

// ── Constants ─────────────────────────────────────────────────────────────────
constDecl
    : CONST TYPE_IDENT COLON typeRef EQ expr
    ;

// ── Imports ───────────────────────────────────────────────────────────────────
importDecl
    : IMPORT modulePath (AS IDENT)?
    ;

modulePath
    : IDENT (DOT IDENT)*
    ;

// ── Type declarations ─────────────────────────────────────────────────────────
typeDecl
    : TYPE TYPE_IDENT typeParams? EQ recordBody   # RecordTypeDecl
    | TYPE TYPE_IDENT typeParams? EQ typeRef       # AliasTypeDecl
    ;

recordBody
    : LBRACE fieldDecl (COMMA fieldDecl)* COMMA? RBRACE
    ;

fieldDecl
    : IDENT COLON typeRef
    ;

typeParams
    : LBRACKET TYPE_IDENT (COMMA TYPE_IDENT)* RBRACKET
    ;

// ── Enum declarations ─────────────────────────────────────────────────────────
enumDecl
    : ENUM TYPE_IDENT typeParams? LBRACE enumVariant (COMMA enumVariant)* COMMA? RBRACE
    ;

enumVariant
    : TYPE_IDENT (LPAREN typeRef (COMMA typeRef)* RPAREN)?   # TupleVariant
    | TYPE_IDENT LBRACE fieldDecl (COMMA fieldDecl)* RBRACE  # RecordVariant
    ;

// ── Function declarations ─────────────────────────────────────────────────────
fnDecl
    : annotation* FN IDENT typeParams? LPAREN paramList? RPAREN ARROW returnType block
    ;

paramList
    : param (COMMA param)*
    ;

param
    : IDENT COLON typeRef
    ;

returnType
    : typeRef
    ;

annotation
    : ANN_PURE
    | ANN_IO
    | ANN_MUT
    | ANN_ASYNC
    | ANN_TEST
    | ANN_DEPRECATED
    | ANN_THROWS    LPAREN typeRef RPAREN
    | ANN_TOOL
    | ANN_REQUIRES  LPAREN expr RPAREN
    | ANN_ENSURES   LPAREN expr RPAREN
    | ANN_TIMEOUT   LPAREN INT_LIT RPAREN
    | ANN_TRUSTED
    | ANN_UNTRUSTED
    ;

// ── Statements ────────────────────────────────────────────────────────────────
block
    : LBRACE stmt* RBRACE
    ;

stmt
    : letStmt
    | mutStmt
    | assignStmt
    | returnStmt
    | exprStmt
    | ifStmt
    | whileStmt
    | forStmt
    | assertStmt
    | describeStmt
    | breakStmt
    | continueStmt
    ;

// ...existing code...

// break — exit innermost loop
breakStmt
    : BREAK
    ;

// continue — skip to next iteration of innermost loop
continueStmt
    : CONTINUE
    ;

letStmt
    : LET IDENT (COLON typeRef)? EQ expr
    ;

mutStmt
    : MUT IDENT (COLON typeRef)? EQ expr
    ;

assignStmt
    : assignTarget EQ expr
    ;

assignTarget
    : IDENT (DOT IDENT)*
    | IDENT LBRACKET expr RBRACKET
    ;

returnStmt
    : RETURN expr?
    ;

exprStmt
    : expr
    ;

// assert <condition> [ , <message> ]
assertStmt
    : ASSERT expr (COMMA expr)?
    ;

// describe "doc-string" — structured documentation embedded in the function body
describeStmt
    : DESCRIBE STR_LIT
    ;

ifStmt
    : IF expr block (ELSE IF expr block)* (ELSE block)?
    ;

whileStmt
    : WHILE expr block
    ;

forStmt
    : FOR IDENT IN expr block
    ;

// ── Expressions (precedence encoded via rule hierarchy) ───────────────────────
expr
    : pipeExpr
    ;

pipeExpr
    : logicOrExpr (PIPE logicOrExpr)*
    ;

logicOrExpr
    : logicAndExpr (OR logicAndExpr)*
    ;

logicAndExpr
    : equalityExpr (AND equalityExpr)*
    ;

equalityExpr
    : relationalExpr ((EQEQ | NEQ) relationalExpr)*
    ;

relationalExpr
    : addExpr ((LT | LE | GT | GE) addExpr)*
    ;

addExpr
    : mulExpr ((PLUS | MINUS) mulExpr)*
    ;

mulExpr
    : unaryExpr ((STAR | SLASH | PERCENT) unaryExpr)*
    ;

unaryExpr
    : NOT unaryExpr
    | MINUS unaryExpr
    | postfixExpr
    ;

postfixExpr
    : primaryExpr postfix*
    ;

postfix
    : DOT IDENT                                          # FieldAccess
    | DOT IDENT LPAREN argList? RPAREN                   # MethodCall
    | LBRACKET expr RBRACKET                             # IndexAccess
    | QUESTION                                           # PropagateOp   // ? unwrap Result/Option
    ;

primaryExpr
    : INT_LIT                                            # IntLit
    | FLOAT_LIT                                          # FloatLit
    | STR_LIT                                            # StrLit
    | INTERP_STR                                         # InterpStrLit
    | TRUE                                               # BoolTrue
    | FALSE                                              # BoolFalse
    | NONE                                               # NoneLit
    | SOME LPAREN expr RPAREN                            # SomeLit
    | OK LPAREN expr RPAREN                              # OkLit
    | ERR LPAREN expr RPAREN                             # ErrLit
    | TRUSTED   LPAREN expr RPAREN                       # TrustedExpr
    | UNTRUSTED LPAREN expr RPAREN                       # UntrustedExpr
    | IDENT LPAREN argList? RPAREN                       # FnCall
    | IDENT                                              # VarRef
    | TYPE_IDENT DCOLON TYPE_IDENT LBRACE namedArgList? RBRACE  # EnumRecordLit
    | TYPE_IDENT DCOLON TYPE_IDENT LPAREN argList? RPAREN       # EnumTupleLit
    | TYPE_IDENT DCOLON TYPE_IDENT                       # EnumVariantRef
    | TYPE_IDENT LBRACE namedArgList? RBRACE             # RecordLit
    | TYPE_IDENT LPAREN argList? RPAREN                  # ConstFnCall
    | TYPE_IDENT                                         # ConstRef
    | matchExpr                                          # MatchExprRef
    | blockExpr                                          # BlockExprRef
    | listLit                                            # ListLitRef
    | mapLit                                             # MapLitRef
    | LPAREN expr RPAREN                                 # Parens
    ;

argList
    : namedArg (COMMA namedArg)*
    ;

namedArgList
    : namedArg (COMMA namedArg)*
    ;

namedArg
    : IDENT COLON expr    # NamedArgument
    | expr                # PosArgument
    ;

listLit
    : LBRACKET (expr (COMMA expr)* COMMA?)? RBRACKET
    ;

mapLit
    : LBRACE (mapEntry (COMMA mapEntry)* COMMA?)? RBRACE
    ;

mapEntry
    : expr ARROW expr
    ;

blockExpr
    : LBRACE stmt* expr RBRACE    // last expr is the value
    ;

// ── Match expression ──────────────────────────────────────────────────────────
matchExpr
    : MATCH expr LBRACE matchArm (COMMA matchArm)* COMMA? RBRACE
    ;

matchArm
    : pattern (IF expr)? FAT_ARROW (expr | block)
    ;

pattern
    : UNDERSCORE                                          # WildcardPattern
    | INT_LIT                                             # IntPattern
    | FLOAT_LIT                                           # FloatPattern
    | STR_LIT                                             # StrPattern
    | TRUE                                                # TruePattern
    | FALSE                                               # FalsePattern
    | NONE                                                # NonePattern
    | SOME LPAREN pattern RPAREN                          # SomePattern
    | OK LPAREN pattern RPAREN                            # OkPattern
    | ERR LPAREN pattern RPAREN                           # ErrPattern
    | TYPE_IDENT DCOLON TYPE_IDENT                        # EnumPattern
    | TYPE_IDENT DCOLON TYPE_IDENT LPAREN pattern (COMMA pattern)* RPAREN # EnumTuplePattern
    | TYPE_IDENT DCOLON TYPE_IDENT LBRACE fieldPattern (COMMA fieldPattern)* RBRACE # EnumRecordPattern
    | TYPE_IDENT LBRACE fieldPattern (COMMA fieldPattern)* RBRACE # RecordPattern
    | IDENT                                               # BindPattern
    ;

fieldPattern
    : IDENT COLON pattern
    | IDENT          // shorthand: `name` binds field `name` to variable `name`
    ;

// ── Type references ───────────────────────────────────────────────────────────
typeRef
    : T_INT                                                    # IntType
    | T_FLOAT                                                  # FloatType
    | T_BOOL                                                   # BoolType
    | T_STR                                                    # StrType
    | T_UNIT                                                   # UnitType
    | T_OPTION LBRACKET typeRef RBRACKET                       # OptionType
    | T_RESULT LBRACKET typeRef COMMA typeRef RBRACKET         # ResultType
    | T_LIST LBRACKET typeRef RBRACKET                         # ListType
    | T_MAP LBRACKET typeRef COMMA typeRef RBRACKET            # MapType
    | TYPE_IDENT (LBRACKET typeRef (COMMA typeRef)* RBRACKET)? # NamedType
    | LPAREN typeRef (COMMA typeRef)* RPAREN ARROW typeRef     # FnType
    ;

