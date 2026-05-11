package uk.ac.nott.cs.comp3012.coursework;

import uk.ac.nott.cs.comp3012.coursework.ast.Ast;

/**
 * Interface for any class that can walk an AST.
 *
 * @param <T> type that the visit methods should return
 */
public interface AstVisitor<T> {

    default T visit(Ast ast) {
        return switch (ast) {
            // Program Structure
            case Ast.ProgramUnit programUnit -> visitProgramUnit(programUnit);
            case Ast.ProgramDef programDef -> visitProgramDef(programDef);
            case Ast.SubroutineDef subroutineDef -> visitSubroutineDef(subroutineDef);
            case Ast.FunctionDef functionDef -> visitFunctionDef(functionDef);
            case Ast.DerivedDef derivedDef -> visitDerivedDef(derivedDef);

            // Declarations
            case Ast.Declaration.SimpleDecl simpleDecl -> visitSimpleDecl(simpleDecl);
            case Ast.Declaration.FixedArrayDecl fixedArrayDecl -> visitFixedArrayDecl(fixedArrayDecl);
            case Ast.Declaration.PointerDecl pointerDecl -> visitPointerDecl(pointerDecl);
            case Ast.Declaration.DynamicArrayDecl dynamicArrayDecl -> visitDynamicArrayDecl(dynamicArrayDecl);

            // Variable Types
            case Ast.VarType.IntegerType integerType -> visitIntegerType(integerType);
            case Ast.VarType.RealType realType -> visitRealType(realType);
            case Ast.VarType.CharacterType characterType -> visitCharacterType(characterType);
            case Ast.VarType.LogicalType logicalType -> visitLogicalType(logicalType);
            case Ast.VarType.CustomType customType -> visitCustomType(customType);

            // Statements
            case Ast.Statement.Assignment assignment -> visitAssignment(assignment);
            case Ast.Statement.DerivedTypeAssignment derivedTypeAssignment -> visitDerivedTypeAssignment(derivedTypeAssignment);
            case Ast.Statement.SubroutineCallStatement subroutineCallStatement -> visitSubroutineCallStatement(subroutineCallStatement);
            case Ast.Statement.IfStatement ifStatement -> visitIfStatement(ifStatement);
            case Ast.Statement.IfThenElseStatement ifThenElseStatement -> visitIfThenElseStatement(ifThenElseStatement);
            case Ast.Statement.DoStatement doStatement -> visitDoStatement(doStatement);
            case Ast.Statement.DoWhileStatement doWhileStatement -> visitDoWhileStatement(doWhileStatement);
            case Ast.Statement.ReadStatement readStatement -> visitReadStatement(readStatement);
            case Ast.Statement.WriteStatement writeStatement -> visitWriteStatement(writeStatement);
            case Ast.Statement.AllocateStatement allocateStatement -> visitAllocateStatement(allocateStatement);
            case Ast.Statement.DeallocateStatement deallocateStatement -> visitDeallocateStatement(deallocateStatement);

            // Allocate Size
            case Ast.AllocSize.VariableSize variableSize -> visitVariableSize(variableSize);
            case Ast.AllocSize.ConstantSize constantSize -> visitConstantSize(constantSize);

            // Expressions
            case Ast.Expression.BinaryOp binaryOp -> visitBinaryOp(binaryOp);
            case Ast.Expression.FunctionCall functionCall -> visitFunctionCall(functionCall);
            case Ast.Expression.Variable variable -> visitVariable(variable);
            case Ast.Expression.FieldAccess fieldAccess -> visitFieldAccess(fieldAccess);
            case Ast.Expression.ConstantExpr constantExpr -> visitConstantExpr(constantExpr);

            // Constants
            case Ast.Constants.IntConstant intConstant -> visitIntConstant(intConstant);
            case Ast.Constants.RealConstant realConstant -> visitRealConstant(realConstant);
            case Ast.Constants.BooleanConstant booleanConstant -> visitBooleanConstant(booleanConstant);
            case Ast.Constants.StringConstant stringConstant -> visitStringConstant(stringConstant);
        };
    }

    // Program Structure
    T visitProgramUnit(Ast.ProgramUnit programUnit);
    T visitProgramDef(Ast.ProgramDef programDef);
    T visitSubroutineDef(Ast.SubroutineDef subroutineDef);
    T visitFunctionDef(Ast.FunctionDef functionDef);
    T visitDerivedDef(Ast.DerivedDef derivedDef);

    // Declarations
    T visitSimpleDecl(Ast.Declaration.SimpleDecl simpleDecl);
    T visitFixedArrayDecl(Ast.Declaration.FixedArrayDecl fixedArrayDecl);
    T visitPointerDecl(Ast.Declaration.PointerDecl pointerDecl);
    T visitDynamicArrayDecl(Ast.Declaration.DynamicArrayDecl dynamicArrayDecl);

    // Variable Types
    T visitIntegerType(Ast.VarType.IntegerType integerType);
    T visitRealType(Ast.VarType.RealType realType);
    T visitCharacterType(Ast.VarType.CharacterType characterType);
    T visitLogicalType(Ast.VarType.LogicalType logicalType);
    T visitCustomType(Ast.VarType.CustomType customType);

    // Statements
    T visitAssignment(Ast.Statement.Assignment assignment);
    T visitDerivedTypeAssignment(Ast.Statement.DerivedTypeAssignment derivedTypeAssignment);
    T visitSubroutineCallStatement(Ast.Statement.SubroutineCallStatement subroutineCallStatement);
    T visitIfStatement(Ast.Statement.IfStatement ifStatement);
    T visitIfThenElseStatement(Ast.Statement.IfThenElseStatement ifThenElseStatement);
    T visitDoStatement(Ast.Statement.DoStatement doStatement);
    T visitDoWhileStatement(Ast.Statement.DoWhileStatement doWhileStatement);
    T visitReadStatement(Ast.Statement.ReadStatement readStatement);
    T visitWriteStatement(Ast.Statement.WriteStatement writeStatement);
    T visitAllocateStatement(Ast.Statement.AllocateStatement allocateStatement);
    T visitDeallocateStatement(Ast.Statement.DeallocateStatement deallocateStatement);

    // Allocate Size
    T visitVariableSize(Ast.AllocSize.VariableSize variableSize);
    T visitConstantSize(Ast.AllocSize.ConstantSize constantSize);

    // Expressions
    T visitBinaryOp(Ast.Expression.BinaryOp binaryOp);
    T visitFunctionCall(Ast.Expression.FunctionCall functionCall);
    T visitVariable(Ast.Expression.Variable variable);
    T visitFieldAccess(Ast.Expression.FieldAccess fieldAccess);
    T visitConstantExpr(Ast.Expression.ConstantExpr constantExpr);

    // Constants
    T visitIntConstant(Ast.Constants.IntConstant intConstant);
    T visitRealConstant(Ast.Constants.RealConstant realConstant);
    T visitBooleanConstant(Ast.Constants.BooleanConstant booleanConstant);
    T visitStringConstant(Ast.Constants.StringConstant stringConstant);
}