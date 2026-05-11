package uk.ac.nott.cs.comp3012.coursework.tac;

import uk.ac.nott.cs.comp3012.coursework.AstVisitor;
import uk.ac.nott.cs.comp3012.coursework.ast.Ast;
import uk.ac.nott.cs.comp3012.coursework.symbol.SymbolData;
import uk.ac.nott.cs.comp3012.coursework.symbol.SymbolTable;
import uk.ac.nott.cs.comp3012.coursework.types.Type;

import java.util.*;

public class TacGenerator implements AstVisitor<List<TacInstr>> {

    private int tempCounter = 0;
    private SymbolTable currentScope;
    private final Map<String, SymbolTable> nestedScopes;
    private final Map<String, TacInstr> labelInstructions = new HashMap<>();

//    private final String PROG_LABEL_FORMAT = "prog_";
//    private final String FUNC_LABEL_FORMAT = "func_";
//    private final String SUBR_LABEL_FORMAT = "subr_";

    public TacGenerator(SymbolTable globalScope, Map<String, SymbolTable> nestedScopes) {
        this.currentScope = globalScope;
        this.nestedScopes = nestedScopes;
    }

    public Map<String, TacInstr> getLabelInstructions() {
        return labelInstructions;
    }

    // TODO: Implement derived types
    @Override
    public List<TacInstr> visitProgramUnit(Ast.ProgramUnit programUnit) {
        List<TacInstr> code = new ArrayList<>();

        // PASS 1: Pre-create all function and subroutine label instructions
        for (Ast.FunctionDef func : programUnit.functionsBefore()) {
            TacInstr funcLabel = new TacInstr(TacOp.Label, null, null, null);
            labelInstructions.put("FUNC_" + func.name(), funcLabel);
        }

        for (Ast.FunctionDef func : programUnit.functionsAfter()) {
            TacInstr funcLabel = new TacInstr(TacOp.Label, null, null, null);
            labelInstructions.put("FUNC_" + func.name(), funcLabel);
        }

        for (Ast.SubroutineDef sub : programUnit.subroutinesBefore()) {
            TacInstr subLabel = new TacInstr(TacOp.Label, null, null, null);
            labelInstructions.put("SUBR_" + sub.name(), subLabel);
        }

        for (Ast.SubroutineDef sub : programUnit.subroutinesAfter()) {
            TacInstr subLabel = new TacInstr(TacOp.Label, null, null, null);
            labelInstructions.put("SUBR_" + sub.name(), subLabel);
        }

        // then only process function and subroutine definitions
        for (Ast.FunctionDef func : programUnit.functionsBefore()) {
            code.addAll(visit(func));
        }

        for (Ast.FunctionDef func : programUnit.functionsAfter()) {
            code.addAll(visit(func));
        }

        for (Ast.SubroutineDef sub : programUnit.subroutinesBefore()) {
            code.addAll(visit(sub));
        }

        for (Ast.SubroutineDef sub : programUnit.subroutinesAfter()) {
            code.addAll(visit(sub));
        }

        // Main program
        code.addAll(visit(programUnit.program()));

        return code;
    }

    @Override
    public List<TacInstr> visitProgramDef(Ast.ProgramDef programDef) {
        List<TacInstr> code = new ArrayList<>();

        // Create entry label for program
        TacInstr programLabel = new TacInstr(TacOp.Label, null, null, null);
        labelInstructions.put("PROG_" + programDef.name(), programLabel);
        code.add(programLabel);

        // Process statements
        for (Ast.Statement stmt : programDef.statements()) {
            code.addAll(visit(stmt));
        }

        return code;
    }

    @Override
    public List<TacInstr> visitSubroutineDef(Ast.SubroutineDef subroutineDef) {
        List<TacInstr> code = new ArrayList<>();

        TacInstr subLabel = labelInstructions.get("SUBR_" + subroutineDef.name());
        if (subLabel == null) {
            throw new RuntimeException("TAC Generation Error: Label not found for subroutine: " + subroutineDef.name());
        }
        code.add(subLabel);

        // Switch to subroutine's scope
        SymbolTable previousScope = currentScope;
        currentScope = nestedScopes.get("SUBR_" + subroutineDef.name());

        // Process statements
        for (Ast.Statement stmt : subroutineDef.statements()) {
            code.addAll(visit(stmt));
        }

        currentScope = previousScope;
        code.add(new TacInstr(TacOp.ReturnVoid, null, null, null));

        return code;
    }

    @Override
    public List<TacInstr> visitFunctionDef(Ast.FunctionDef functionDef) {
        List<TacInstr> code = new ArrayList<>();

        // Create entry label
        TacInstr funcLabel = labelInstructions.get("FUNC_" + functionDef.name());
        if (funcLabel == null) {
            throw new RuntimeException("TAC Generation Error: Label not found for function: " + functionDef.name());
        }
        code.add(funcLabel);

        // Switch to function's scope
        SymbolTable previousScope = currentScope;
        currentScope = nestedScopes.get("FUNC_" + functionDef.name());

        if (currentScope == null) {
            throw new RuntimeException("TAC Generation Error: No scope found for function: " + functionDef.name());
        }

        // Process statements
        for (Ast.Statement stmt : functionDef.statements()) {
            code.addAll(visit(stmt));
        }

        if (functionDef.returnParameter().isEmpty() || functionDef.returnParameter().isBlank()) {
            throw new RuntimeException("TAC Generation Error: Function must have a return parameter. "
                    + "Unable to generate return for function: " + functionDef.name());
        } else {
            String returnVar = functionDef.returnParameter();
            code.add(new TacInstr(TacOp.Return, null,
                    new TacParam.Variable(returnVar), null));
        }

        currentScope = previousScope;

        return code;
    }

    @Override
    public List<TacInstr> visitDerivedDef(Ast.DerivedDef derivedDef) {
        return List.of();
    }

    // Declarations don't generate TAC - memory allocation handled by TAM
    @Override
    public List<TacInstr> visitSimpleDecl(Ast.Declaration.SimpleDecl simpleDecl) {
        return List.of();
    }

    @Override
    public List<TacInstr> visitFixedArrayDecl(Ast.Declaration.FixedArrayDecl fixedArrayDecl) {
        return List.of();
    }

    @Override
    public List<TacInstr> visitPointerDecl(Ast.Declaration.PointerDecl pointerDecl) {
        return List.of();
    }

    @Override
    public List<TacInstr> visitDynamicArrayDecl(Ast.Declaration.DynamicArrayDecl dynamicArrayDecl) {
        return List.of();
    }

    @Override
    public List<TacInstr> visitIntegerType(Ast.VarType.IntegerType integerType) {
        return List.of();
    }

    @Override
    public List<TacInstr> visitRealType(Ast.VarType.RealType realType) {
        return List.of();
    }

    @Override
    public List<TacInstr> visitCharacterType(Ast.VarType.CharacterType characterType) {
        return List.of();
    }

    @Override
    public List<TacInstr> visitLogicalType(Ast.VarType.LogicalType logicalType) {
        return List.of();
    }

    @Override
    public List<TacInstr> visitCustomType(Ast.VarType.CustomType customType) {
        return List.of();
    }

    // Statements
    @Override
    public List<TacInstr> visitAssignment(Ast.Statement.Assignment assignment) {
        List<TacInstr> code = new ArrayList<>();

        // Generate code for RHS expression
        List<TacInstr> valueCode = visit(assignment.value());
        code.addAll(valueCode);
        TacParam valueParam = resolveParam(assignment.value(), valueCode);

        if (!assignment.arrayIndices().isEmpty()) {
            // Array assignment
            List<Integer> dimensions = getOriginalArrayDimensions(assignment.varName());

            // Compute linearized offset
            List<TacInstr> offsetCode = computeArrayLinearOffset(assignment.arrayIndices(), dimensions);
            code.addAll(offsetCode);
            TacParam offsetParam = resolveParam(assignment.arrayIndices().get(0), offsetCode);

            code.add(new TacInstr(TacOp.ArrayStore,
                    new TacParam.Variable(assignment.varName()),
                    offsetParam,
                    valueParam));
        } else {
            // Simple assignment
            code.add(new TacInstr(TacOp.Assign,
                    new TacParam.Variable(assignment.varName()),
                    valueParam,
                    null));
        }

        return code;
    }

    // TODO: Implement TAC for derived type
    @Override
    public List<TacInstr> visitDerivedTypeAssignment(Ast.Statement.DerivedTypeAssignment derivedTypeAssignment) {
        return List.of();
    }

    @Override
    public List<TacInstr> visitSubroutineCallStatement(Ast.Statement.SubroutineCallStatement subroutineCallStatement) {
        List<TacInstr> code = new ArrayList<>();

        // Push arguments
        for (Ast.Expression arg : subroutineCallStatement.arguments()) {
            List<TacInstr> argCode = visit(arg);
            code.addAll(argCode);
            TacParam argParam = resolveParam(arg, argCode);
            code.add(new TacInstr(TacOp.PushParam, null, argParam, null));
        }

        TacInstr subLabel = labelInstructions.get("SUBR_" + subroutineCallStatement.name());

        if (subLabel == null) {
            throw new RuntimeException("TAC Generation Error: Could not find subroutine's label for subroutine: " + subroutineCallStatement.name());
        }

        code.add(new TacInstr(TacOp.CallVoid,
                null,
                new TacParam.Label(subLabel.id()),
                null));

        return code;
    }

    @Override
    public List<TacInstr> visitIfStatement(Ast.Statement.IfStatement ifStatement) {
        List<TacInstr> code = new ArrayList<>();

        // Generate condition code
        List<TacInstr> condCode = visit(ifStatement.condition());
        code.addAll(condCode);
        TacParam condParam = resolveParam(ifStatement.condition(), condCode);

        // Jump to end label if cond = false
        TacInstr endLabel = new TacInstr(TacOp.Label, null, null, null);
        code.add(new TacInstr(TacOp.GotoIfFalse,
                new TacParam.Label(endLabel.id()),
                condParam,
                null));

        // Generate body code
        List<TacInstr> bodyCode = visit(ifStatement.statement());
        code.addAll(bodyCode);

        // Add end label
        code.add(endLabel);

        return code;
    }

    @Override
    public List<TacInstr> visitIfThenElseStatement(Ast.Statement.IfThenElseStatement ifThenElseStatement) {
        List<TacInstr> code = new ArrayList<>();

        // Generate condition code
        List<TacInstr> condCode = visit(ifThenElseStatement.condition());
        code.addAll(condCode);
        TacParam condParam = resolveParam(ifThenElseStatement.condition(), condCode);

        // Generate then and else code
        List<TacInstr> thenCode = new ArrayList<>();
        for (Ast.Statement stmt : ifThenElseStatement.thenBlock()) {
            thenCode.addAll(visit(stmt));
        }

        List<TacInstr> elseCode = new ArrayList<>();
        for (Ast.Statement stmt : ifThenElseStatement.elseBlock()) {
            elseCode.addAll(visit(stmt));
        }

        // Create labels
        TacInstr elseLabel = new TacInstr(TacOp.Label, null, null, null);
        TacInstr endLabel = new TacInstr(TacOp.Label, null, null, null);

        // If condition code = false, jump to else block
        code.add(new TacInstr(TacOp.GotoIfFalse,
                new TacParam.Label(elseLabel.id()),
                condParam,
                null));

        // if condition = true, execute then block, then jump to end
        code.addAll(thenCode);
        code.add(new TacInstr(TacOp.Goto,
                new TacParam.Label(endLabel.id()),
                null,
                null));

        // Else block, then after else block jump to end
        code.add(elseLabel);
        code.addAll(elseCode);
        code.add(endLabel);

        return code;
    }

    @Override
    public List<TacInstr> visitDoStatement(Ast.Statement.DoStatement doStatement) {
        List<TacInstr> code = new ArrayList<>();

        List<TacInstr> startCode = visit(doStatement.start());
        code.addAll(startCode);
        TacParam startParam = resolveParam(doStatement.start(), startCode);

        // main loop variable
        code.add(new TacInstr(TacOp.Assign,
                new TacParam.Variable(doStatement.loopVar()),
                startParam,
                null));

        // label for loop's starting point
        TacInstr loopLabel = new TacInstr(TacOp.Label, null, null, null);
        code.add(loopLabel);

        // Check condition: loopVar < end
        List<TacInstr> endCode = visit(doStatement.end());
        code.addAll(endCode);
        TacParam endParam = resolveParam(doStatement.end(), endCode);
        TacParam.Temp condTemp = newTemp();
        code.add(new TacInstr(TacOp.Lss,
                condTemp,
                new TacParam.Variable(doStatement.loopVar()),
                endParam));

        // If condition false, exit loop
        TacInstr endLabel = new TacInstr(TacOp.Label, null, null, null);
        code.add(new TacInstr(TacOp.GotoIfFalse,
                new TacParam.Label(endLabel.id()),
                condTemp,
                null));

        // Loop body (if condition true)
        for (Ast.Statement stmt : doStatement.body()) {
            code.addAll(visit(stmt));
        }

        // loopVar increment
        List<TacInstr> stepCode = visit(doStatement.step());
        code.addAll(stepCode);
        TacParam stepParam = resolveParam(doStatement.step(), stepCode);
        TacParam.Temp tempLoopVar = newTemp();
        code.add(new TacInstr(TacOp.Add,
                tempLoopVar,
                new TacParam.Variable(doStatement.loopVar()),
                stepParam));

        code.add(new TacInstr(TacOp.Assign,
                new TacParam.Variable(doStatement.loopVar()),
                tempLoopVar,
                null));

        // jump back to loop start after increment done
        code.add(new TacInstr(TacOp.Goto,
                new TacParam.Label(loopLabel.id()),
                null,
                null));

        // End label
        code.add(endLabel);

        return code;
    }

    @Override
    public List<TacInstr> visitDoWhileStatement(Ast.Statement.DoWhileStatement doWhileStatement) {
        List<TacInstr> code = new ArrayList<>();

        // start of loop
        TacInstr loopLabel = new TacInstr(TacOp.Label, null, null, null);
        code.add(loopLabel);

        // Check condition
        List<TacInstr> condCode = visit(doWhileStatement.condition());
        code.addAll(condCode);
        TacParam condParam = resolveParam(doWhileStatement.condition(), condCode);

        // If cond = false, exit loop
        TacInstr endLabel = new TacInstr(TacOp.Label, null, null, null);
        code.add(new TacInstr(TacOp.GotoIfFalse,
                new TacParam.Label(endLabel.id()),
                condParam,
                null));

        // Loop body (if condition true)
        for (Ast.Statement stmt : doWhileStatement.body()) {
            code.addAll(visit(stmt));
        }

        // Jump back start
        code.add(new TacInstr(TacOp.Goto,
                new TacParam.Label(loopLabel.id()),
                null,
                null));

        // End label
        code.add(endLabel);

        return code;
    }

    @Override
    public List<TacInstr> visitReadStatement(Ast.Statement.ReadStatement readStatement) {
        List<TacInstr> code = new ArrayList<>();

        for (Ast.Expression expr : readStatement.expressions()) {
            if (expr instanceof Ast.Expression.Variable var) {

                if (var.arrayIndices().isEmpty()) {
                    Optional<SymbolData> symData = currentScope.lookup(var.name());

                    if (symData.isEmpty()) {
                        throw new RuntimeException("TAC Generation Error: Variable '" +
                                var.name() + "' not found in current scope");
                    }

                    if (symData.get().getVarType() == Type.BaseType.INTEGER || symData.get().getVarType() == Type.BaseType.REAL) {
                        code.add(new TacInstr(TacOp.ReadInteger,
                                new TacParam.Variable(var.name()),
                                null,
                                null));
                    } else {
                        code.add(new TacInstr(TacOp.ReadCharacter,
                                new TacParam.Variable(var.name()),
                                null,
                                null));
                    }

                } else {
                    // Array element read
                    List<Integer> dimensions = getOriginalArrayDimensions(var.name());

                    List<TacInstr> offsetCode = computeArrayLinearOffset(var.arrayIndices(), dimensions);
                    code.addAll(offsetCode);
                    TacParam offsetParam = resolveParam(var.arrayIndices().get(0), offsetCode);

                    TacParam.Temp valueTemp = newTemp();
                    code.add(new TacInstr(TacOp.ReadInteger, valueTemp, null, null));
                    code.add(new TacInstr(TacOp.ArrayStore,
                            new TacParam.Variable(var.name()),
                            offsetParam,
                            valueTemp));
                }
            }
        }

        return code;
    }

    @Override
    public List<TacInstr> visitWriteStatement(Ast.Statement.WriteStatement writeStatement) {
        List<TacInstr> code = new ArrayList<>();

        for (Ast.Expression expr : writeStatement.expressions()) {
            List<TacInstr> exprCode = visit(expr);
            code.addAll(exprCode);
            TacParam exprParam = resolveParam(expr, exprCode);

            // Determine write operation based on expression type
            Type exprType = switch (expr) {
                case Ast.Expression.ConstantExpr constExpr -> switch (constExpr.constant()) {
                    case Ast.Constants.IntConstant ic -> Type.BaseType.INTEGER;
                    case Ast.Constants.RealConstant rc -> Type.BaseType.REAL;
                    case Ast.Constants.BooleanConstant bc -> Type.BaseType.LOGICAL;
                    case Ast.Constants.StringConstant sc -> Type.BaseType.CHARACTER;
                };

                case Ast.Expression.Variable var -> {
                    Optional<SymbolData> symData = currentScope.lookup(var.name());
                    if (symData.isEmpty()) {
                        throw new RuntimeException("TAC Generation Error: Variable '" + var.name() + "' not found");
                    }

                    Type varType = symData.get().getVarType();

                    // Handle array types - get base type
                    if (varType instanceof Type.FixedArrayType fat) {
                        yield fat.baseType();
                    } else if (varType instanceof Type.DynamicArrayType dat) {
                        yield dat.baseType();
                    }

                    yield varType;
                }

                case Ast.Expression.BinaryOp binOp -> switch (binOp.op()) {
                    case PLUS, MINUS, MULT, DIV, POWER -> Type.BaseType.INTEGER;
                    case EQ, NEQ, LT, GT, LE, GE, AND, OR -> Type.BaseType.LOGICAL;
                    case CONCAT -> Type.BaseType.CHARACTER;
                };

                case Ast.Expression.FunctionCall funcCall -> {
                    Optional<SymbolData> symData = currentScope.lookup("FUNC_" + funcCall.name());
                    if (symData.isEmpty()) {
                        throw new RuntimeException("TAC Generation Error: Function '" + funcCall.name() + "' not found");
                    }

                    Type funcType = symData.get().getVarType();
                    if (funcType instanceof Type.FunctionType ft) {
                        yield ft.returnType();
                    }

                    throw new RuntimeException("TAC Generation Error: Invalid function type for: " + funcCall.name());
                }

                case Ast.Expression.FieldAccess fieldAccess ->
                        throw new UnsupportedOperationException("Derived types not yet supported in TAC generation");
            };

            TacOp writeOp;
            if (exprType == Type.BaseType.CHARACTER) {
                writeOp = TacOp.WriteCharacter;
            } else {
                writeOp = TacOp.WriteInteger;
            }
            code.add(new TacInstr(writeOp, null, exprParam, null));
        }

        return code;
    }


//    @Override
//    public List<TacInstr> visitWriteStatement(Ast.Statement.WriteStatement writeStatement) {
//        List<TacInstr> code = new ArrayList<>();
//
//        for (Ast.Expression expr : writeStatement.expressions()) {
//            List<TacInstr> exprCode = visit(expr);
//            code.addAll(exprCode);
//            TacParam exprParam = resolveParam(expr, exprCode);
//
//            // Determine write operation based on expression type
//            TacOp writeOp;
//            if (expr instanceof Ast.Expression.ConstantExpr constExpr) {
//                writeOp = switch (constExpr.constant()) {
//                    case Ast.Constants.IntConstant ic -> TacOp.WriteInteger;
//                    case Ast.Constants.RealConstant rc -> TacOp.WriteInteger;
//                    case Ast.Constants.BooleanConstant bc -> TacOp.WriteInteger;
//                    case Ast.Constants.StringConstant sc -> TacOp.WriteCharacter;
//                };
//            } else if (expr instanceof Ast.Expression.Variable var) {
//                Optional<SymbolData> symData = currentScope.lookup(var.name());
//
//                if (symData.isPresent()) {
//                    Type varType = symData.get().getVarType();
//
//                    // Handle array types - get base type
//                    if (varType instanceof Type.FixedArrayType fat) {
//                        varType = fat.baseType();
//                    } else if (varType instanceof Type.DynamicArrayType dat) {
//                        varType = dat.baseType();
//                    }
//
//                    if (varType == Type.BaseType.INTEGER || varType == Type.BaseType.REAL || varType == Type.BaseType.LOGICAL) {
//                        writeOp = TacOp.WriteInteger;
//                    } else {
//                        writeOp = TacOp.WriteCharacter;
//                    }
//                } else {
//                    throw new RuntimeException("TAC Generation Error: Could not determine write type due to missing variable in symbol table: " + var.name());
//                }
//            } else {
//                throw new RuntimeException("TAC Generation Error: Could not determine write type for: " + expr);
//            }
//
//            code.add(new TacInstr(writeOp, null, exprParam, null));
//        }
//
//        return code;
//    }

    // TODO: Check if correct
    @Override
    public List<TacInstr> visitAllocateStatement(Ast.Statement.AllocateStatement allocateStatement) {
        List<TacInstr> code = new ArrayList<>();

        TacParam sizeParam;
        if (allocateStatement.allocSize() instanceof Ast.AllocSize.ConstantSize constSize) {
            sizeParam = new TacParam.Value(constSize.value().value());
        } else if (allocateStatement.allocSize() instanceof Ast.AllocSize.VariableSize varSize) {
            sizeParam = new TacParam.Variable(varSize.varName());
        } else {
            sizeParam = new TacParam.Value(1);
        }

        code.add(new TacInstr(TacOp.Alloc,
                new TacParam.Variable(allocateStatement.name()),
                sizeParam,
                null));

        return code;
    }

    // TODO: Check if correct
    @Override
    public List<TacInstr> visitDeallocateStatement(Ast.Statement.DeallocateStatement deallocateStatement) {
        List<TacInstr> code = new ArrayList<>();
        code.add(new TacInstr(TacOp.Dealloc,
                null,
                new TacParam.Variable(deallocateStatement.name()),
                null));
        return code;
    }

    @Override
    public List<TacInstr> visitVariableSize(Ast.AllocSize.VariableSize variableSize) {
        return List.of();
    }

    @Override
    public List<TacInstr> visitConstantSize(Ast.AllocSize.ConstantSize constantSize) {
        return List.of();
    }

    @Override
    public List<TacInstr> visitBinaryOp(Ast.Expression.BinaryOp binaryOp) {
        List<TacInstr> code = new ArrayList<>();

        List<TacInstr> leftCode = visit(binaryOp.left());
        code.addAll(leftCode);
        TacParam leftParam = resolveParam(binaryOp.left(), leftCode);

        List<TacInstr> rightCode = visit(binaryOp.right());
        code.addAll(rightCode);
        TacParam rightParam = resolveParam(binaryOp.right(), rightCode);

        TacParam.Temp resultTemp = newTemp();
        TacOp op = convertBinaryOp(binaryOp.op());
        code.add(new TacInstr(op, resultTemp, leftParam, rightParam));

        return code;
    }

    @Override
    public List<TacInstr> visitFunctionCall(Ast.Expression.FunctionCall functionCall) {
        List<TacInstr> code = new ArrayList<>();

        // Push arguments
        for (Ast.Expression arg : functionCall.arguments()) {
            List<TacInstr> argCode = visit(arg);
            code.addAll(argCode);
            TacParam argParam = resolveParam(arg, argCode);
            code.add(new TacInstr(TacOp.PushParam, null, argParam, null));
        }

        TacInstr funcLabel = labelInstructions.get("FUNC_" + functionCall.name());

        if (funcLabel == null) {
            throw new RuntimeException("TAC Generation Error: Could not find function's label for function: " + functionCall.name());
        }

        TacParam.Temp resultTemp = newTemp();
        code.add(new TacInstr(TacOp.Call,
                resultTemp,
                new TacParam.Label(funcLabel.id()),
                null));

        return code;
    }

    @Override
    public List<TacInstr> visitVariable(Ast.Expression.Variable variable) {
        List<TacInstr> code = new ArrayList<>();

        if (!variable.arrayIndices().isEmpty()) {
            // Array access still needs temp
            List<Integer> dimensions = getOriginalArrayDimensions(variable.name());
            List<TacInstr> offsetCode = computeArrayLinearOffset(variable.arrayIndices(), dimensions);
            code.addAll(offsetCode);
            TacParam offsetParam = resolveParam(variable.arrayIndices().get(0), offsetCode);

            TacParam.Temp resultTemp = newTemp();
            code.add(new TacInstr(TacOp.ArrayLoad,
                    resultTemp,
                    new TacParam.Variable(variable.name()),
                    offsetParam));
        }
        // Removed else block where simple variable does not need temp
        // return empty

        return code;
    }

    // TODO: Complete field access for derived type
    @Override
    public List<TacInstr> visitFieldAccess(Ast.Expression.FieldAccess fieldAccess) {
        return List.of();
    }

    @Override
    public List<TacInstr> visitConstantExpr(Ast.Expression.ConstantExpr constantExpr) {
        return visit(constantExpr.constant());
    }

    // Constants
    @Override
    public List<TacInstr> visitIntConstant(Ast.Constants.IntConstant intConstant) {
        return List.of();
    }

    @Override
    public List<TacInstr> visitRealConstant(Ast.Constants.RealConstant realConstant) {
        return List.of();
    }

    @Override
    public List<TacInstr> visitBooleanConstant(Ast.Constants.BooleanConstant booleanConstant) {
        return List.of();
    }

    @Override
    public List<TacInstr> visitStringConstant(Ast.Constants.StringConstant stringConstant) {
        return List.of();
    }

    // Helper methods

    // resolves nested parameters when code evaluation
    private TacParam resolveParam(Ast.Expression expr, List<TacInstr> code) {
        if (code.isEmpty()) {
            return extractDirectParam(expr);
        }
        return getResultLocation(code);
    }

    // if it's a simple variable, return []
    // if it has been somehow nested, we return the instruction where code was lastly saved (final destination)
    private TacParam getResultLocation(List<TacInstr> instructions) {
        if (instructions.isEmpty()) {
            return null; // simple expressions return empty list
        }
        return instructions.getLast().dst();
    }

    // extract param directly from AST
    private TacParam extractDirectParam(Ast.Expression expr) {
        return switch (expr) {
            case Ast.Expression.ConstantExpr ce ->
                    switch (ce.constant()) {
                        case Ast.Constants.IntConstant ic -> new TacParam.Value(ic.value());
                        case Ast.Constants.RealConstant rc -> new TacParam.RealValue(rc.value());
                        case Ast.Constants.BooleanConstant bc -> new TacParam.BoolValue(bc.value());
                        case Ast.Constants.StringConstant sc -> new TacParam.StringValue(sc.value());
                    };
            case Ast.Expression.Variable v when v.arrayIndices().isEmpty() ->
                    new TacParam.Variable(v.name());
            default -> throw new RuntimeException("Cannot extract direct param from: " + expr);
        };
    }

    // new temp variable
    private TacParam.Temp newTemp() {
        return new TacParam.Temp(tempCounter++);
    }

    // get original array dimensions (the ones defined in symbol table)
    private List<Integer> getOriginalArrayDimensions(String varName) {
        Optional<SymbolData> symData = currentScope.lookup(varName);
        if (symData.isEmpty()) {
            throw new RuntimeException("TAC Generation Error: Variable '" + varName + "' not found");
        }

        Type varType = symData.get().getVarType();
        if (varType instanceof Type.FixedArrayType fixedArray) {
            return fixedArray.dimensions();
        } else if (varType instanceof Type.DynamicArrayType) {
            throw new UnsupportedOperationException("Dynamic arrays not yet supported");
        } else {
            throw new RuntimeException("TAC Generation Error: Variable '" + varName + "' is not an array");
        }
    }

    /**
     *
     * Aight imma just pray and trust this works. Generated by Claude.
     *
     * Computes linearized offset for multi-dimensional array access.
     * Uses row-major ordering with 0-based Fortran indexing.
     *
     * Row-major formula: offset = (i0-1)*d1*d2*... + (i1-1)*d2*d3*... + ... + (in-1)
     *
     * Example: a(2, 3) with dimensions (5, 10)
     *   offset = (2-1)*10 + (3-1) = 1*10 + 2 = 12
     */
    private List<TacInstr> computeArrayLinearOffset(List<Ast.Expression> indices, List<Integer> dimensions) {
        List<TacInstr> code = new ArrayList<>();

        // Single dimension: offset = index (NO SUBTRACTION FOR 0-BASED)
        if (indices.size() == 1) {
            List<TacInstr> indexCode = visit(indices.get(0));
            code.addAll(indexCode);

            return code;
        }

        // For multi-dimensional arrays
        TacParam currentOffset = null;

        for (int dim = 0; dim < indices.size(); dim++) {
            List<TacInstr> indexCode = visit(indices.get(dim));
            code.addAll(indexCode);
            TacParam indexParam = resolveParam(indices.get(dim), indexCode);

            // Calculate multiplier
            int multiplier = 1;
            for (int j = dim + 1; j < dimensions.size(); j++) {
                multiplier *= dimensions.get(j);
            }

            TacParam contribution;
            if (multiplier == 1) {
                contribution = indexParam;  // Use indexParam directly
            } else {
                TacParam.Temp scaledIndex = newTemp();
                code.add(new TacInstr(TacOp.Mul,
                        scaledIndex,
                        indexParam,  // Use indexParam directly
                        new TacParam.Value(multiplier)));
                contribution = scaledIndex;
            }

            // Add to running offset
            if (currentOffset == null) {
                currentOffset = contribution;
            } else {
                TacParam.Temp newOffset = newTemp();
                code.add(new TacInstr(TacOp.Add,
                        newOffset,
                        currentOffset,
                        contribution));
                currentOffset = newOffset;
            }
        }

        return code;
    }

    private TacOp convertBinaryOp(Ast.BinaryOperator op) {
        return switch (op) {
            case PLUS -> TacOp.Add;
            case MINUS -> TacOp.Sub;
            case MULT -> TacOp.Mul;
            case DIV -> TacOp.Div;
            case POWER -> TacOp.Exp;
            case EQ -> TacOp.Eql;
            case NEQ -> TacOp.Neq;
            case LT -> TacOp.Lss;
            case GT -> TacOp.Grt;
            case LE -> TacOp.Leq;
            case GE -> TacOp.Geq;
            case AND -> TacOp.And;
            case OR -> TacOp.Or;
            case CONCAT -> TacOp.Concat;
        };
    }

    // TODO: Remove
    public static String prettyPrint(TacInstr instr) {
        StringBuilder sb = new StringBuilder();
        String idStr = instr.id().toString().substring(0, 8);

        // Format based on operation
        return switch (instr.op()) {
            case Label -> sb.append(idStr).append(":").toString();
            case Goto -> sb.append("goto ").append(instr.dst()).toString();
            case GotoIf -> sb.append("if ").append(instr.src1()).append(" goto ").append(instr.dst()).toString();
            case GotoIfFalse -> sb.append("ifFalse ").append(instr.src1()).append(" goto ").append(instr.dst()).toString();
            case Return -> sb.append("return ").append(instr.src1()).toString();
            case ReturnVoid -> sb.append("return").toString();
            case Call -> sb.append(instr.dst()).append(" = call ").append(instr.src1()).toString();
            case CallVoid -> sb.append("call ").append(instr.src1()).toString();
            case PushParam -> sb.append("push ").append(instr.src1()).toString();
            case ReadInteger -> sb.append("read(int) ").append(instr.dst()).toString();
            case WriteInteger -> sb.append("write(int) ").append(instr.src1()).toString();
            case ReadCharacter -> sb.append("read(char) ").append(instr.dst()).toString();
            case WriteCharacter -> sb.append("write(char) ").append(instr.src1()).toString();
            case Assign -> sb.append(instr.dst()).append(" = ").append(instr.src1()).toString();
            case ArrayLoad -> sb.append(instr.dst()).append(" = ").append(instr.src1()).append("[").append(instr.src2()).append("]").toString();
            case ArrayStore -> sb.append(instr.dst()).append("[").append(instr.src1()).append("] = ").append(instr.src2()).toString();
            case FieldAccess -> sb.append(instr.dst()).append(" = ").append(instr.src1()).append(instr.src2()).toString();
            case FieldStore -> sb.append(instr.dst()).append(instr.src1()).append(" = ").append(instr.src2()).toString();
            case Alloc -> sb.append("allocate(").append(instr.dst()).append(", ").append(instr.src1()).append(")").toString();
            case Dealloc -> sb.append("deallocate(").append(instr.src1()).append(")").toString();
            default -> {
                // Binary operations
                if (instr.src2() != null) {
                    yield sb.append(instr.dst()).append(" = ").append(instr.src1())
                            .append(" ").append(opSymbol(instr.op())).append(" ")
                            .append(instr.src2()).toString();
                } else {
                    yield sb.append(instr.dst()).append(" = ").append(opSymbol(instr.op()))
                            .append(instr.src1()).toString();
                }
            }
        };
    }

    private static String opSymbol(TacOp op) {
        return switch (op) {
            case Add -> "+";
            case Sub -> "-";
            case Mul -> "*";
            case Div -> "/";
            case Exp -> "**";
            case Eql -> "==";
            case Neq -> "!=";
            case Lss -> "<";
            case Grt -> ">";
            case Leq -> "<=";
            case Geq -> ">=";
            case And -> "&&";
            case Or -> "||";
            case Concat -> "//";
            default -> op.toString();
        };
    }

    public static void printTac(List<TacInstr> instructions) {
        System.out.println("=== Generated TAC ===");
        for (TacInstr instr : instructions) {
            System.out.println(prettyPrint(instr));
        }
    }
}