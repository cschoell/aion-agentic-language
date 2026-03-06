package com.aion.ast;

import java.util.List;

/** Marker interface for all AST nodes. */
public interface Node {

    record Pos(int line, int col) {}

    // ── Top-level ─────────────────────────────────────────────────────────────
    record Module(List<Node> decls, String sourceName) implements Node {}

    record ImportDecl(List<String> path, String alias, Pos pos) implements Node {}

    record TypeDecl(String name, List<String> typeParams,
                    TypeDeclBody body, Pos pos) implements Node {}

    sealed interface TypeDeclBody permits TypeDeclBody.Record, TypeDeclBody.Alias {
        record Record(List<FieldDecl> fields) implements TypeDeclBody {}
        record Alias(TypeRef ref)             implements TypeDeclBody {}
    }

    record FieldDecl(String name, TypeRef type) {}

    record EnumDecl(String name, List<String> typeParams,
                    List<EnumVariant> variants, Pos pos) implements Node {}

    sealed interface EnumVariant permits EnumVariant.Tuple, EnumVariant.Record {
        record Tuple(String name, List<TypeRef> fields)     implements EnumVariant {}
        record Record(String name, List<FieldDecl> fields)  implements EnumVariant {}
    }

    record FnDecl(List<Annotation> annotations,
                  String name,
                  List<String> typeParams,
                  List<Param> params,
                  TypeRef returnType,
                  Stmt.Block body,
                  Pos pos) implements Node {}

    record Param(String name, TypeRef type) {}

    // ── Annotations ───────────────────────────────────────────────────────────
    sealed interface Annotation permits
            Annotation.Pure, Annotation.Io, Annotation.Mut,
            Annotation.Async, Annotation.Test, Annotation.Deprecated,
            Annotation.Throws,
            Annotation.Tool, Annotation.Requires, Annotation.Ensures,
            Annotation.Timeout, Annotation.TrustedAnn, Annotation.UntrustedAnn {
        record Pure()                implements Annotation {}
        record Io()                  implements Annotation {}
        record Mut()                 implements Annotation {}
        record Async()               implements Annotation {}
        record Test()                implements Annotation {}
        record Deprecated()          implements Annotation {}
        record Throws(TypeRef type)  implements Annotation {}
        /** Marks a function as an AI-agent–callable tool. */
        record Tool()                implements Annotation {}
        /** Pre-condition checked before the function body runs. */
        record Requires(Expr condition) implements Annotation {}
        /** Post-condition checked after the function body returns (bound to `result`). */
        record Ensures(Expr condition)  implements Annotation {}
        /** Maximum execution time in milliseconds. */
        record Timeout(long millis)     implements Annotation {}
        /** All parameters are treated as trusted input. */
        record TrustedAnn()          implements Annotation {}
        /** All parameters are treated as untrusted (external) input. */
        record UntrustedAnn()        implements Annotation {}
    }

    // ── Statements ────────────────────────────────────────────────────────────
    sealed interface Stmt permits
            Stmt.Block, Stmt.Let, Stmt.Mut, Stmt.Assign,
            Stmt.Return, Stmt.ExprStmt, Stmt.If, Stmt.While, Stmt.For,
            Stmt.Assert, Stmt.Describe {

        record Block(List<Stmt> stmts, Pos pos) implements Stmt {}
        record Let(String name, TypeRef type, Expr value, Pos pos) implements Stmt {}
        record Mut(String name, TypeRef type, Expr value, Pos pos) implements Stmt {}
        record Assign(AssignTarget target, Expr value, Pos pos)    implements Stmt {}
        record Return(Expr value, Pos pos)                         implements Stmt {}
        record ExprStmt(Expr expr, Pos pos)                        implements Stmt {}
        record If(List<IfBranch> branches, Stmt.Block elseBranch, Pos pos) implements Stmt {}
        record While(Expr condition, Stmt.Block body, Pos pos)     implements Stmt {}
        record For(String var, Expr iterable, Stmt.Block body, Pos pos) implements Stmt {}
        /** Runtime-checked assertion: assert <cond> [, <message>] */
        record Assert(Expr condition, Expr message, Pos pos)       implements Stmt {}
        /** Inline doc-string: describe "..." — appears in tool descriptors */
        record Describe(String text, Pos pos)                      implements Stmt {}
    }

    record IfBranch(Expr condition, Stmt.Block body) {}

    sealed interface AssignTarget permits AssignTarget.Field, AssignTarget.Index {
        record Field(List<String> path)         implements AssignTarget {}
        record Index(String name, Expr index)   implements AssignTarget {}
    }

    // ── Expressions ───────────────────────────────────────────────────────────
    sealed interface Expr permits
            Expr.IntLit, Expr.FloatLit, Expr.StrLit, Expr.BoolLit,
            Expr.NoneLit, Expr.SomeLit, Expr.OkLit, Expr.ErrLit,
            Expr.VarRef, Expr.EnumVariantRef, Expr.RecordLit,
            Expr.FnCall, Expr.MethodCall, Expr.FieldAccess, Expr.IndexAccess,
            Expr.BinOp, Expr.UnaryOp, Expr.Pipe,
            Expr.Match, Expr.BlockExpr, Expr.ListLit, Expr.MapLit,
            Expr.Propagate, Expr.TrustedExpr, Expr.UntrustedExpr {

        record IntLit(long value, Pos pos)          implements Expr {}
        record FloatLit(double value, Pos pos)      implements Expr {}
        record StrLit(String value, Pos pos)        implements Expr {}
        record BoolLit(boolean value, Pos pos)      implements Expr {}
        record NoneLit(Pos pos)                     implements Expr {}
        record SomeLit(Expr inner, Pos pos)         implements Expr {}
        record OkLit(Expr inner, Pos pos)           implements Expr {}
        record ErrLit(Expr inner, Pos pos)          implements Expr {}
        record VarRef(String name, Pos pos)         implements Expr {}
        record EnumVariantRef(String typeName, String variant, Pos pos) implements Expr {}
        record RecordLit(String typeName, List<NamedArg> fields, Pos pos) implements Expr {}
        record FnCall(String name, List<Arg> args, Pos pos)              implements Expr {}
        record MethodCall(Expr receiver, String method, List<Arg> args, Pos pos) implements Expr {}
        record FieldAccess(Expr receiver, String field, Pos pos)         implements Expr {}
        record IndexAccess(Expr receiver, Expr index, Pos pos)           implements Expr {}
        record BinOp(BinOpKind op, Expr left, Expr right, Pos pos)       implements Expr {}
        record UnaryOp(UnaryOpKind op, Expr operand, Pos pos)            implements Expr {}
        record Pipe(Expr left, Expr right, Pos pos)                      implements Expr {}
        record Match(Expr subject, List<MatchArm> arms, Pos pos)         implements Expr {}
        record BlockExpr(List<Stmt> stmts, Expr value, Pos pos)          implements Expr {}
        record ListLit(List<Expr> elements, Pos pos)                     implements Expr {}
        record MapLit(List<MapEntry> entries, Pos pos)                   implements Expr {}
        record Propagate(Expr inner, Pos pos)                            implements Expr {}
        /** Marks a value as explicitly trusted — safe to pass to @trusted fns. */
        record TrustedExpr(Expr inner, Pos pos)                          implements Expr {}
        /** Marks a value as explicitly untrusted — from external/agent input. */
        record UntrustedExpr(Expr inner, Pos pos)                        implements Expr {}
    }

    record MatchArm(Pattern pattern, Expr body) {}
    record MapEntry(Expr key, Expr value) {}

    sealed interface Arg permits Arg.Named, Arg.Positional {
        record Named(String name, Expr value)   implements Arg {}
        record Positional(Expr value)           implements Arg {}
    }

    record NamedArg(String name, Expr value) {}

    enum BinOpKind {
        ADD, SUB, MUL, DIV, MOD,
        EQ, NEQ, LT, LE, GT, GE,
        AND, OR
    }

    enum UnaryOpKind { NEG, NOT }

    // ── Patterns ─────────────────────────────────────────────────────────────
    sealed interface Pattern permits
            Pattern.Wildcard, Pattern.IntPat, Pattern.FloatPat, Pattern.StrPat,
            Pattern.BoolPat, Pattern.NonePat, Pattern.SomePat,
            Pattern.OkPat, Pattern.ErrPat,
            Pattern.EnumPat, Pattern.EnumTuplePat, Pattern.EnumRecordPat,
            Pattern.RecordPat, Pattern.Bind {

        record Wildcard()                                           implements Pattern {}
        record IntPat(long value)                                   implements Pattern {}
        record FloatPat(double value)                               implements Pattern {}
        record StrPat(String value)                                 implements Pattern {}
        record BoolPat(boolean value)                               implements Pattern {}
        record NonePat()                                            implements Pattern {}
        record SomePat(Pattern inner)                               implements Pattern {}
        record OkPat(Pattern inner)                                 implements Pattern {}
        record ErrPat(Pattern inner)                                implements Pattern {}
        record EnumPat(String typeName, String variant)             implements Pattern {}
        record EnumTuplePat(String typeName, String variant, List<Pattern> fields) implements Pattern {}
        record EnumRecordPat(String typeName, String variant, List<FieldPat> fields) implements Pattern {}
        record RecordPat(String typeName, List<FieldPat> fields)    implements Pattern {}
        record Bind(String name)                                    implements Pattern {}
    }

    record FieldPat(String field, Pattern pattern) {}

    // ── Type references ───────────────────────────────────────────────────────
    sealed interface TypeRef permits
            TypeRef.IntT, TypeRef.FloatT, TypeRef.BoolT, TypeRef.StrT, TypeRef.UnitT,
            TypeRef.OptionT, TypeRef.ResultT, TypeRef.ListT, TypeRef.MapT,
            TypeRef.Named, TypeRef.FnT {

        record IntT()                                                   implements TypeRef {}
        record FloatT()                                                 implements TypeRef {}
        record BoolT()                                                  implements TypeRef {}
        record StrT()                                                   implements TypeRef {}
        record UnitT()                                                  implements TypeRef {}
        record OptionT(TypeRef inner)                                   implements TypeRef {}
        record ResultT(TypeRef ok, TypeRef err)                         implements TypeRef {}
        record ListT(TypeRef element)                                   implements TypeRef {}
        record MapT(TypeRef key, TypeRef value)                         implements TypeRef {}
        record Named(String name, List<TypeRef> args)                   implements TypeRef {}
        record FnT(List<TypeRef> params, TypeRef returnType)            implements TypeRef {}
    }
}
