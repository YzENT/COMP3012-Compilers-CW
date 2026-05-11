package uk.ac.nott.cs.comp3012.coursework.ast;

import java.util.*;
/**
 * Base interface type for all AST classes. Modify it, delete it, or do whatever you want with it.
 */
public sealed interface Ast {

    // ============= Program Structure =============

    record ProgramUnit(
        List<SubroutineDef> subroutinesBefore,
        List<FunctionDef> functionsBefore,
        List<DerivedDef> derivedBefore,
        ProgramDef program,
        List<SubroutineDef> subroutinesAfter,
        List<FunctionDef> functionsAfter,
        List<DerivedDef> derivedAfter
    ) implements Ast {}

    record ProgramDef(
        String name,
        List<Declaration> declarations,
        List<Statement> statements
    ) implements Ast {}

    record SubroutineDef(
        String name,
        List<String> parameters, // parameterList
        List<Declaration> declarations,
        List<Statement> statements
    ) implements Ast {}

    record FunctionDef(
        String name,
        List<String> parameters,
        String returnParameter,  // returnArg
        List<Declaration> declarations,
        List<Statement> statements
    ) implements Ast {}

    record DerivedDef(
        String name,
        List<Declaration> declarations
    ) implements Ast {}

    // ============= Declarations =============

    sealed interface Declaration extends Ast {
        record SimpleDecl(VarType type, List<String> names) implements Declaration {}
        record FixedArrayDecl(VarType type, List<Integer> dimensions, List<String> names) implements Declaration {}
        record PointerDecl(VarType type, List<String> names) implements Declaration {}
        record DynamicArrayDecl(VarType type, int rank, List<String> names) implements Declaration {} // rank = amount of number of '*'
    }

    sealed interface VarType extends Ast {
        record IntegerType() implements VarType {}
        record RealType() implements VarType {}
        record CharacterType() implements VarType {}
        record LogicalType() implements VarType {}
        record CustomType(String typeName) implements VarType {}
    }

    // ============= Statements =============

    sealed interface Statement extends Ast {
        record Assignment(String varName, List<Expression> arrayIndices, Expression value) implements Statement {}
        record DerivedTypeAssignment(String varName, String fieldName, List<Expression> arrayIndices, Expression value) implements Statement {}
        record SubroutineCallStatement(String name, List<Expression> arguments) implements Statement {}
        record IfStatement(Expression condition, Statement statement) implements Statement {}
        record IfThenElseStatement(Expression condition, List<Statement> thenBlock, List<Statement> elseBlock) implements Statement {}
        record DoStatement(String loopVar, Expression start, Expression end, Expression step, List<Statement> body) implements Statement {}
        record DoWhileStatement(Expression condition, List<Statement> body) implements Statement {}
        record ReadStatement(List<Expression> expressions) implements Statement {}
        record WriteStatement(List<Expression> expressions) implements Statement {}
        record AllocateStatement(String name, AllocSize allocSize) implements Statement {} // put it as string, then later decide parse as integer or smth
        record DeallocateStatement(String name) implements Statement {}
    }

    sealed interface AllocSize extends Ast {
        record VariableSize(String varName) implements AllocSize {}
        record ConstantSize(Constants.IntConstant value) implements AllocSize {}
    }

    // ============= Expressions =============

    sealed interface Expression extends Ast {
        record BinaryOp(BinaryOperator op, Expression left, Expression right) implements Expression {}
        record FunctionCall(String name, List<Expression> arguments) implements Expression {}
        record Variable(String name, List<Expression> arrayIndices) implements Expression {} // Simple variable & array
        record FieldAccess(String typeName, String varName, List<Expression> arrayIndices) implements Expression {}
        record ConstantExpr(Constants constant) implements Expression {}
    }

    enum BinaryOperator {
        PLUS, MINUS, MULT, DIV, POWER,
        EQ, NEQ, LT, GT, LE, GE,
        AND, OR,
        CONCAT
    }

    // ============= Constants =============

    sealed interface Constants extends Ast {
        record IntConstant(int value, IntBase base) implements Constants {}
        record RealConstant(float value) implements Constants {}
        record BooleanConstant(boolean value) implements Constants {}
        record StringConstant(String value) implements Constants {}
    }

    enum IntBase {
        BASE10, BASE2, BASE7, BASE16
    }
}
