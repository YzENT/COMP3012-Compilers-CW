package uk.ac.nott.cs.comp3012.coursework.types;


import uk.ac.nott.cs.comp3012.coursework.AstVisitor;
import uk.ac.nott.cs.comp3012.coursework.ast.Ast;
import uk.ac.nott.cs.comp3012.coursework.symbol.SymbolData;
import uk.ac.nott.cs.comp3012.coursework.symbol.SymbolTable;
import uk.ac.nott.cs.comp3012.coursework.tac.TacGenerator;
import uk.ac.nott.cs.comp3012.coursework.tac.TacInstr;
import uk.ac.nott.cs.comp3012.coursework.tam.TamGenerator;
import uk.ac.nott.cs.comp3012.coursework.tam.TamInstruction;

import java.util.*;

public class TypeChecker implements AstVisitor<Type> {

    private SymbolTable currentScope;
    private final Map<String, SymbolTable> nestedScopes = new HashMap<>();
    private final List<String> errors;

    public TypeChecker() {
        this.currentScope = new SymbolTable();
        this.errors = new ArrayList<>();
    }

    public List<String> getErrors() {
        return new ArrayList<>(errors);
    }

    public boolean hasErrors() {
        return !errors.isEmpty();
    }

    private void reportError(String message) {
        errors.add(message);
    }

    public SymbolTable getCurrentScope() {
        return currentScope;
    }

    public Map<String, SymbolTable> getNestedScopes() {
        return nestedScopes;
    }

    @Override
    public Type visitProgramUnit(Ast.ProgramUnit programUnit) {
        // TODO: Complete Derived Type
//        for (Ast.DerivedDef derived : programUnit.derivedBefore()) {
//            visit(derived);
//        }
//        for (Ast.DerivedDef derived : programUnit.derivedAfter()) {
//            visit(derived);
//        }

        if (!programUnit.derivedAfter().isEmpty() || !programUnit.derivedBefore().isEmpty()) {
            throw new UnsupportedOperationException("Type checking error: Derived types currently unsupported, please remove in source code");
        }

        // Process function and subroutine definitions
        for (Ast.FunctionDef func : programUnit.functionsBefore()) {
            visit(func);
        }

        for (Ast.FunctionDef func : programUnit.functionsAfter()) {
            visit(func);
        }

        for (Ast.SubroutineDef sub : programUnit.subroutinesBefore()) {
            visit(sub);
        }

        for (Ast.SubroutineDef sub : programUnit.subroutinesAfter()) {
            visit(sub);
        }

        // Process main program
        visit(programUnit.program());
        return null;
    }

    @Override
    public Type visitProgramDef(Ast.ProgramDef programDef) {
        // Process declarations
        for (Ast.Declaration decl : programDef.declarations()) {
            visit(decl);
        }
//        System.out.println(currentScope.lookup("arr_test").get().getVarType());

        // Process statements
        for (Ast.Statement stmt : programDef.statements()) {
            visit(stmt);
        }
        return null;
    }

    @Override
    public Type visitSubroutineDef(Ast.SubroutineDef subroutineDef) {
        // Create new scope for subroutine
        SymbolTable previousScope = currentScope;
        currentScope = new SymbolTable(previousScope);
        nestedScopes.put("SUBR_" + subroutineDef.name(), currentScope);

        // Process declarations
        for (Ast.Declaration decl : subroutineDef.declarations()) {
            visit(decl);
        }

        List<Type> paramTypes = new ArrayList<>();
        for (String param : subroutineDef.parameters()) {
            Optional<SymbolData> symData = currentScope.lookup(param);
            if (symData.isPresent()) {
                paramTypes.add(symData.get().getVarType());
            } else {
                reportError("Argument parameter '" + param + "' not declared in subroutine '" + subroutineDef.name() + "'");
            }
        }

        Type subroutineType = new Type.SubroutineType(paramTypes);
        SymbolData subData = SymbolData.createSubroutine(
                subroutineDef.name(),
                subroutineType,
                subroutineDef.parameters()
        );
        previousScope.define("SUBR_" + subroutineDef.name(), subData);

        // Process statements
        for (Ast.Statement stmt : subroutineDef.statements()) {
            visit(stmt);
        }

        // Restore previous scope
        currentScope = previousScope;
        return null;
    }

    @Override
    public Type visitFunctionDef(Ast.FunctionDef functionDef) {
        // Create new scope for function
        SymbolTable previousScope = currentScope;
        currentScope = new SymbolTable(previousScope);
        nestedScopes.put("FUNC_" + functionDef.name(), currentScope);

        // Process declarations to determine return type
        for (Ast.Declaration decl : functionDef.declarations()) {
            visit(decl);
        }

        // Determine function type from return parameters
        Type returnType;
        Optional<SymbolData> retParamData = currentScope.lookup(functionDef.returnParameter());
        if (retParamData.isEmpty()) {
            reportError("Return parameter '" + functionDef.returnParameter() + "' not declared in function '" + functionDef.name() + "'");
            return null;
        } else {
            returnType = retParamData.get().getVarType();
        }

        List<Type> paramTypes = new ArrayList<>();
        for (String param : functionDef.parameters()) {
            Optional<SymbolData> symData = currentScope.lookup(param);
            if (symData.isEmpty()) {
                reportError("Argument parameter '" + param + "' not declared in function '" + functionDef.name() + "'");
                return null;
            } else {
                paramTypes.add(symData.get().getVarType());
            }
        }

        Type functionType = new Type.FunctionType(paramTypes, returnType);
        SymbolData funcData = SymbolData.createFunction(
                functionDef.name(),
                functionType,
                functionDef.parameters(),
                functionDef.returnParameter()
        );

        // doing this so if var name clashes with func name, no issue
        previousScope.define("FUNC_" + functionDef.name(), funcData);

        // Process statements
        for (Ast.Statement stmt : functionDef.statements()) {
            visit(stmt);
        }

        // Restore previous scope
        currentScope = previousScope;

        return null;
    }

    @Override
    public Type visitDerivedDef(Ast.DerivedDef derivedDef) {
        return null;
    }

    // Declarations
    @Override
    public Type visitSimpleDecl(Ast.Declaration.SimpleDecl simpleDecl) {
        Type baseType = convertVarTypeToType(simpleDecl.type());

        for (String name : simpleDecl.names()) {
//            if (currentScope.lookup(name).isEmpty()) {
//                SymbolData symData = SymbolData.createVariable(name, baseType);
//                currentScope.define(name, symData);
//            } else {
//                reportError("Duplicate variable name '" + name + "'");
//            }
            SymbolData symData = SymbolData.createVariable(name, baseType);
            currentScope.define(name, symData);
        }

        return null;
    }

    @Override
    public Type visitFixedArrayDecl(Ast.Declaration.FixedArrayDecl fixedArrayDecl) {
        Type baseType = convertVarTypeToType(fixedArrayDecl.type());
        Type arrayInfo = new Type.FixedArrayType(baseType, fixedArrayDecl.dimensions());

        for (String name : fixedArrayDecl.names()) {
            if (currentScope.lookup(name).isEmpty()) {
                SymbolData symData = SymbolData.createVariable(name, arrayInfo);
                currentScope.define(name, symData);
            } else {
                reportError("Duplicate variable name '" + name + "'");
            }
        }

        return null;
    }

    @Override
    public Type visitPointerDecl(Ast.Declaration.PointerDecl pointerDecl) {
        Type baseType = convertVarTypeToType(pointerDecl.type());
        Type pointerInfo = new Type.PointerType(baseType);

        for (String name : pointerDecl.names()) {
            if (currentScope.lookup(name).isEmpty()) {
                SymbolData symData = SymbolData.createVariable(name, pointerInfo);
                currentScope.define(name, symData);
            } else {
                reportError("Duplicate variable name '" + name + "'");
            }
        }

        return null;
    }

    @Override
    public Type visitDynamicArrayDecl(Ast.Declaration.DynamicArrayDecl dynamicArrayDecl) {
        Type baseType = convertVarTypeToType(dynamicArrayDecl.type());
        Type arrayInfo = new Type.DynamicArrayType(baseType, dynamicArrayDecl.rank());

        for (String name : dynamicArrayDecl.names()) {
            if (currentScope.lookup(name).isEmpty()) {
                SymbolData symData = SymbolData.createVariable(name, arrayInfo);
                currentScope.define(name, symData);
            } else {
                reportError("Duplicate variable name '" + name + "'");
            }
        }

        return null;
    }

    @Override
    public Type visitIntegerType(Ast.VarType.IntegerType integerType) {
        return Type.BaseType.INTEGER;
    }

    @Override
    public Type visitRealType(Ast.VarType.RealType realType) {
        return Type.BaseType.REAL;
    }

    @Override
    public Type visitCharacterType(Ast.VarType.CharacterType characterType) {
        return Type.BaseType.CHARACTER;
    }

    @Override
    public Type visitLogicalType(Ast.VarType.LogicalType logicalType) {
        return Type.BaseType.LOGICAL;
    }

    @Override
    public Type visitCustomType(Ast.VarType.CustomType customType) {
        return new Type.DerivedType(customType.typeName());
    }

    @Override
    public Type visitAssignment(Ast.Statement.Assignment assignment) {
        // Look up the variable
        Optional<SymbolData> symData = currentScope.lookup(assignment.varName());
        if (symData.isEmpty()) {
            reportError("Undefined variable: " + assignment.varName());
            return null;
        }

        Type varType = symData.get().getVarType();

        // If it's an array access, check indices
        if (!assignment.arrayIndices().isEmpty()) {
            for (Ast.Expression index : assignment.arrayIndices()) {
                Type indexType = visit(index);

                // Check if indexing type is integer type
                if (indexType != Type.BaseType.INTEGER) {
                    reportError("Array index must be integer, got: " + indexType + " for variable: " + assignment.varName());
                    return null;
                }

                int expectedArrayDimension = symData.get().getArrayDimensionSize();
                int argArrayDimension = assignment.arrayIndices().size();

                if (expectedArrayDimension != argArrayDimension) {
                    reportError("Array dimension mismatch for variable: " + assignment.varName() + ". Expected: " + expectedArrayDimension + " got " + argArrayDimension);
                    return null;
                }

            }

            // Extract base type from array
            if (varType instanceof Type.FixedArrayType fixedArrayType) {
                varType = fixedArrayType.baseType();
            } else if (varType instanceof Type.DynamicArrayType dynamicArrayType) {
                varType = dynamicArrayType.baseType();
            } else {
                reportError("Variable: '" + assignment.varName() + "' is not an array");
                return null;
            }
        }

        // Check the assigned value type
        Type valueType = visit(assignment.value());

        if (!isAssignmentCompatible(varType, valueType)) {
            reportError("Type mismatch: cannot assign " + valueType + " to " + varType + " for variable: " + assignment.varName());
            return null;
        }

        return null;
    }

    // TODO: Complete derived type
    @Override
    public Type visitDerivedTypeAssignment(Ast.Statement.DerivedTypeAssignment derivedTypeAssignment) {
        return null;
    }

    @Override
    public Type visitSubroutineCallStatement(Ast.Statement.SubroutineCallStatement subroutineCallStatement) {
        Optional<SymbolData> symData = currentScope.lookup("SUBR_" + subroutineCallStatement.name());
        if (symData.isEmpty()) {
            reportError("Undefined subroutine: " + subroutineCallStatement.name());
            return null;
        }

        if (!symData.get().isSubroutine()) {
            reportError("'" + subroutineCallStatement.name() + "' is not a subroutine");
            return null;
        }

        // Check argument count
        List<String> expectedParams = symData.get().getFunctionParameters();
        if (expectedParams.size() != subroutineCallStatement.arguments().size()) {
            reportError("Subroutine '" + subroutineCallStatement.name() + "' expects " +
                    expectedParams.size() + " arguments, got " + subroutineCallStatement.arguments().size());
            return null;
        }

        // Type check each argument (copied and pasted from visitFunctionCall)
        Type funcType = symData.get().getVarType();
        if (funcType instanceof Type.SubroutineType subroutineType) {
            List<Type> expectedParamTypes = subroutineType.parameterTypes();
            List<Ast.Expression> actualArgs = subroutineCallStatement.arguments();

            for (int i = 0; i < actualArgs.size(); i++) {
                Type actualArgType = visit(actualArgs.get(i));
                Type expectedParamType = expectedParamTypes.get(i);

                if (!isAssignmentCompatible(expectedParamType, actualArgType)) {
                    reportError("Type mismatch in function '" + subroutineCallStatement.name() +
                            "' variable '" + expectedParams.get(i) + "' : expected " + expectedParamType +
                            ", got " + actualArgType);
                }
            }
        }

        return null;
    }

    @Override
    public Type visitIfStatement(Ast.Statement.IfStatement ifStatement) {
        Type condType = visit(ifStatement.condition());
        if (condType != Type.BaseType.LOGICAL) {
            reportError("If condition must be logical, got: " + condType);
            return null;
        }

        visit(ifStatement.statement());

        return null;
    }

    @Override
    public Type visitIfThenElseStatement(Ast.Statement.IfThenElseStatement ifThenElseStatement) {
        Type condType = visit(ifThenElseStatement.condition());
        if (condType != Type.BaseType.LOGICAL) {
            reportError("If condition must be logical, got: " + condType);
            return null;
        }

        for (Ast.Statement stmt : ifThenElseStatement.thenBlock()) {
            visit(stmt);
        }

        for (Ast.Statement stmt : ifThenElseStatement.elseBlock()) {
            visit(stmt);
        }

        return null;
    }

    @Override
    public Type visitDoStatement(Ast.Statement.DoStatement doStatement) {
        // Check loop variable exists
        Optional<SymbolData> loopVar = currentScope.lookup(doStatement.loopVar());
        if (loopVar.isEmpty()) {
            reportError("Do loop variable '" + doStatement.loopVar() + "' not declared");
            return null;
        } else if (loopVar.get().getVarType() != Type.BaseType.INTEGER) {
            reportError("Do loop variable must be type integer, got: " + loopVar.get().getVarType());
            return null;
        }

        // Check start, end, step are integers
        Type startType = visit(doStatement.start());
        if (startType != Type.BaseType.INTEGER) {
            reportError("Do loop start must be integer");
            return null;
        }

        Type endType = visit(doStatement.end());
        if (endType != Type.BaseType.INTEGER) {
            reportError("Do loop end must be integer");
            return null;
        }

        Type stepType = visit(doStatement.step());
        if (stepType != Type.BaseType.INTEGER) {
            reportError("Do loop step must be integer");
            return null;
        }

        // Check body
        for (Ast.Statement stmt : doStatement.body()) {
            visit(stmt);
        }
        return null;
    }

    @Override
    public Type visitDoWhileStatement(Ast.Statement.DoWhileStatement doWhileStatement) {
        Type condType = visit(doWhileStatement.condition());
        if (condType != Type.BaseType.LOGICAL) {
            reportError("While condition must be logical");
            return null;
        }

        for (Ast.Statement stmt : doWhileStatement.body()) {
            visit(stmt);
        }
        return null;
    }

    @Override
    public Type visitReadStatement(Ast.Statement.ReadStatement readStatement) {
        for (Ast.Expression expr : readStatement.expressions()) {
            visit(expr);
        }
        return null;
    }

    @Override
    public Type visitWriteStatement(Ast.Statement.WriteStatement writeStatement) {
        for (Ast.Expression expr : writeStatement.expressions()) {
            visit(expr);
        }
        return null;
    }

    @Override
    public Type visitAllocateStatement(Ast.Statement.AllocateStatement allocateStatement) {
        Optional<SymbolData> symData = currentScope.lookup(allocateStatement.name());
        if (symData.isEmpty()) {
            reportError("Undefined variable in allocate: " + allocateStatement.name());
            return null;
        }

        if (!symData.get().isPointer()) {
            reportError("Variable '" + allocateStatement.name() + "' is not a pointer, cannot allocate memory");
            return null;
        }

        if (allocateStatement.allocSize() != null) {
            visit(allocateStatement.allocSize());
        }
        return null;
    }

    @Override
    public Type visitDeallocateStatement(Ast.Statement.DeallocateStatement deallocateStatement) {
        Optional<SymbolData> symData = currentScope.lookup(deallocateStatement.name());
        if (symData.isEmpty()) {
            reportError("Undefined variable in deallocate: " + deallocateStatement.name());
            return null;
        }

        if (!symData.get().isPointer()) {
            reportError("Variable '" + deallocateStatement.name() + "' is not a pointer, cannot deallocate");
            return null;
        }
        return null;
    }

    @Override
    public Type visitVariableSize(Ast.AllocSize.VariableSize variableSize) {
        Optional<SymbolData> symData = currentScope.lookup(variableSize.varName());
        if (symData.isEmpty()) {
            reportError("Undefined variable: " + variableSize.varName());
            return null;
        }

        Type varType = symData.get().getVarType();
        if (varType != Type.BaseType.INTEGER) {
            reportError("Memory allocation size must be type integer, got: " + varType);
            return null;
        }

        return null;
    }

    @Override
    public Type visitConstantSize(Ast.AllocSize.ConstantSize constantSize) {
        return Type.BaseType.INTEGER;
    }

    @Override
    public Type visitBinaryOp(Ast.Expression.BinaryOp binaryOp) {
        Type leftType = visit(binaryOp.left());
        Type rightType = visit(binaryOp.right());

        if (leftType == null || rightType == null) {
            return null;
        }

        return switch (binaryOp.op()) {
            case PLUS, MINUS, MULT, DIV, POWER -> {
                // Arithmetic operations: both operands must be numeric
                if (!isNumeric(leftType) || !isNumeric(rightType)) {
                    reportError("Arithmetic operation requires numeric operands");
                    yield null;
                }
                // If either is real, result is real; otherwise integer
                if (leftType == Type.BaseType.REAL || rightType == Type.BaseType.REAL) {
                    yield Type.BaseType.REAL;
                }
                yield Type.BaseType.INTEGER;
            }
            case EQ, NEQ, LT, GT, LE, GE -> {
                // Comparison operations: operands must be compatible
                if (!isComparable(leftType, rightType)) {
                    reportError("Cannot compare " + leftType + " and " + rightType);
                    yield null;
                }
                yield Type.BaseType.LOGICAL;
            }
            case AND, OR -> {
                // Logical operations: both operands must be logical
                if (leftType != Type.BaseType.LOGICAL || rightType != Type.BaseType.LOGICAL) {
                    reportError("Logical operation requires logical operands");
                    yield null;
                }
                yield Type.BaseType.LOGICAL;
            }
            case CONCAT -> {
                if (leftType != Type.BaseType.CHARACTER || rightType != Type.BaseType.CHARACTER) {
                    reportError("Concat operation only works on character type");
                    yield null;
                }
                yield Type.BaseType.CHARACTER;
            }
        };
    }

    @Override
    public Type visitFunctionCall(Ast.Expression.FunctionCall functionCall) {
        Optional<SymbolData> symData = currentScope.lookup("FUNC_" + functionCall.name());
        if (symData.isEmpty()) {
            reportError("Undefined function: " + functionCall.name());
            return null;
        }

        if (!symData.get().isFunction()) {
            reportError("'" + functionCall.name() + "' is not a function");
            return null;
        }

        // Check argument count
        List<String> expectedParams = symData.get().getFunctionParameters();
        if (expectedParams != null && expectedParams.size() != functionCall.arguments().size()) {
            reportError("Function '" + functionCall.name() + "' expects " +
                    expectedParams.size() + " arguments, got " + functionCall.arguments().size());
            return null;
        }

        // Type check each argument
        Type funcType = symData.get().getVarType();
        if (funcType instanceof Type.FunctionType functionType) {
            List<Type> expectedParamTypes = functionType.parameterTypes();
            List<Ast.Expression> actualArgs = functionCall.arguments();

//            System.out.println(expectedParamTypes);
//            System.out.println(actualArgs);

            for (int i = 0; i < actualArgs.size(); i++) {
                Type actualArgType = visit(actualArgs.get(i));
                Type expectedParamType = expectedParamTypes.get(i);

                if (!isAssignmentCompatible(expectedParamType, actualArgType)) {
                    if (expectedParams != null) {
                        reportError("Type mismatch in function '" + functionCall.name() +
                                "' variable '" + expectedParams.get(i) + "' : expected " + expectedParamType +
                                ", got " + actualArgType);
                    }
                }
            }

            return functionType.returnType();
        }

        return null;
    }

    @Override
    public Type visitVariable(Ast.Expression.Variable variable) {
        Optional<SymbolData> symData = currentScope.lookup(variable.name());
        if (symData.isEmpty()) {
            reportError("Undefined variable: " + variable.name());
            return null;
        }

        Type varType = symData.get().getVarType();

        // If array indices are present, check them and return element type
        if (!variable.arrayIndices().isEmpty()) {
            for (Ast.Expression index : variable.arrayIndices()) {
                Type indexType = visit(index);
                if (indexType != Type.BaseType.INTEGER) {
                    reportError("Array index must be integer, got: " + indexType + " for variable: " + variable.name());
                    return null;
                }

                int expectedArrayDimension = symData.get().getArrayDimensionSize();
                int argArrayDimension = variable.arrayIndices().size();

                if (expectedArrayDimension != argArrayDimension) {
                    reportError("Array dimension mismatch for variable: " + variable.name() + ". Expected: " + expectedArrayDimension + " got " + argArrayDimension);
                    return null;
                }
            }

            // Extract base type from array
            if (varType instanceof Type.FixedArrayType fixedArrayType) {
                return fixedArrayType.baseType();
            } else if (varType instanceof Type.DynamicArrayType dynamicArrayType) {
                return dynamicArrayType.baseType();
            } else {
                reportError("Variable '" + variable.name() + "' is not an array");
                return null;
            }
        }

        return varType;
    }

    // TODO: Field access for derived type
    @Override
    public Type visitFieldAccess(Ast.Expression.FieldAccess fieldAccess) {
        return null;
    }

    @Override
    public Type visitConstantExpr(Ast.Expression.ConstantExpr constantExpr) {
        return visit(constantExpr.constant());
    }

    @Override
    public Type visitIntConstant(Ast.Constants.IntConstant intConstant) {
        return Type.BaseType.INTEGER;
    }

    @Override
    public Type visitRealConstant(Ast.Constants.RealConstant realConstant) {
        return Type.BaseType.REAL;
    }

    @Override
    public Type visitBooleanConstant(Ast.Constants.BooleanConstant booleanConstant) {
        return Type.BaseType.LOGICAL;
    }

    @Override
    public Type visitStringConstant(Ast.Constants.StringConstant stringConstant) {
        return Type.BaseType.CHARACTER;
    }

    // Helper methods
    // convert Ast.VarType to Type
    private Type convertVarTypeToType(Ast.VarType varType) {
        return switch (varType) {
            case Ast.VarType.IntegerType() -> Type.BaseType.INTEGER;
            case Ast.VarType.RealType() -> Type.BaseType.REAL;
            case Ast.VarType.CharacterType() -> Type.BaseType.CHARACTER;
            case Ast.VarType.LogicalType() -> Type.BaseType.LOGICAL;
            case Ast.VarType.CustomType customType -> throw new UnsupportedOperationException("Type checking error: Derived types currently unsupported, please remove type: "
                    + customType.typeName() + ", in source code");
        };
    }

    private boolean isAssignmentCompatible(Type target, Type source) {
        // Exact match
        if (target.equals(source)) {
            return true;
        }
        // Integer can be assigned to real
        if (target == Type.BaseType.REAL && source == Type.BaseType.INTEGER) {
            return true;
        }
        return false;
    }

    private boolean isNumeric(Type type) {
        return type == Type.BaseType.INTEGER || type == Type.BaseType.REAL;
    }

    private boolean isComparable(Type left, Type right) {
        // Numeric types can be compared with each other
        if (isNumeric(left) && isNumeric(right)) {
            return true;
        }
        // Same types can be compared
        return left.equals(right);
    }

//     TODO: Remove
    public static void main(String[] args) {
        System.out.println("=".repeat(60));
        System.out.println("TYPECHECKER INTERACTIVE TEST");
        System.out.println("=".repeat(60));

        // Write your Fortran code here
        String fortranCode = """
function factorial(n) result(fact)
    integer :: n, fact, i
    fact = 1
    do i = 1, n, 1
        fact = fact * i
    end do
end function factorial

subroutine print_factorial(num)
    integer :: num, res
    res = factorial(num)
    write res
end subroutine print_factorial

program main
    integer :: value
    value = 5
    call print_factorial(value)
end program main
        """;

        // Parse the code into AST
        try {
            org.antlr.v4.runtime.CharStream input = org.antlr.v4.runtime.CharStreams.fromString(fortranCode);
            uk.ac.nott.cs.comp3012.coursework.NottscriptLexer lexer =
                    new uk.ac.nott.cs.comp3012.coursework.NottscriptLexer(input);
            org.antlr.v4.runtime.CommonTokenStream tokens = new org.antlr.v4.runtime.CommonTokenStream(lexer);
            uk.ac.nott.cs.comp3012.coursework.NottscriptParser parser =
                    new uk.ac.nott.cs.comp3012.coursework.NottscriptParser(tokens);

            uk.ac.nott.cs.comp3012.coursework.ast.AstBuilder builder =
                    new uk.ac.nott.cs.comp3012.coursework.ast.AstBuilder();

            Ast ast = builder.visit(parser.programUnit());

            // Print the AST structure
//            System.out.println("\n[PARSED AST]");
//            System.out.println("-".repeat(60));
//            printAst(ast, 0);

            // Run type checker
            System.out.println("\n[TYPE CHECKING RESULTS]");
            System.out.println("-".repeat(60));

            TypeChecker checker = new TypeChecker();
            checker.visit(ast);

            if (checker.hasErrors()) {
                System.out.println("❌ Type checking failed with " + checker.getErrors().size() + " error(s):");
                for (String error : checker.getErrors()) {
                    System.out.println("   • " + error);
                }
            } else {
                System.out.println("✅ Type checking passed! No errors found.");

                // Generate TAC
                TacGenerator tacGen = new TacGenerator(checker.currentScope, checker.getNestedScopes());
                List<TacInstr> tac = tacGen.visit(ast);

                // Print it for debugging
                TacGenerator.printTac(tac);

                TamGenerator tamGen = new TamGenerator(checker.currentScope, checker.getNestedScopes(), tacGen.getLabelInstructions());
                TamInstruction.InstructionList tamCode = tamGen.generate(tac);

                // Print for debugging
                TamGenerator.printTam(tamCode);

            }

            // Print symbol table
            System.out.println("\n[SYMBOL TABLE]");
            System.out.println("-".repeat(60));
            printSymbolTable(checker.currentScope, 0);

        } catch (Exception e) {
            System.err.println("❌ Error during parsing/type checking:");
            e.printStackTrace();
        }

        System.out.println("\n" + "=".repeat(60));
    }

    private static void printAst(Ast node, int indent) {
        String prefix = "  ".repeat(indent);

        switch (node) {
            case Ast.ProgramUnit unit -> {
                System.out.println(prefix + "ProgramUnit");
//                if (!unit.derivedBefore().isEmpty()) {
//                    System.out.println(prefix + "  Derived Types (before):");
//                    unit.derivedBefore().forEach(d -> printAst(d, indent + 2));
//                }
//                if (!unit.derivedAfter().isEmpty()) {
//                    System.out.println(prefix + "  Derived Types (after):");
//                    unit.derivedAfter().forEach(d -> printAst(d, indent + 2));
//                }
                if (!unit.functionsBefore().isEmpty()) {
                    System.out.println(prefix + "  Functions (before):");
                    unit.functionsBefore().forEach(f -> printAst(f, indent + 2));
                }
                if (!unit.functionsAfter().isEmpty()) {
                    System.out.println(prefix + "  Functions (after):");
                    unit.functionsAfter().forEach(f -> printAst(f, indent + 2));
                }
                if (!unit.subroutinesBefore().isEmpty()) {
                    System.out.println(prefix + "  Subroutines (before):");
                    unit.subroutinesBefore().forEach(s -> printAst(s, indent + 2));
                }
                if (!unit.subroutinesAfter().isEmpty()) {
                    System.out.println(prefix + "  Subroutines (after):");
                    unit.subroutinesAfter().forEach(s -> printAst(s, indent + 2));
                }
                System.out.println(prefix + "  Main Program:");
                printAst(unit.program(), indent + 2);
            }

            case Ast.ProgramDef prog -> {
                System.out.println(prefix + "Program: " + prog.name());
                if (!prog.declarations().isEmpty()) {
                    System.out.println(prefix + "  Declarations:");
                    prog.declarations().forEach(d -> printAst(d, indent + 2));
                }
                if (!prog.statements().isEmpty()) {
                    System.out.println(prefix + "  Statements:");
                    prog.statements().forEach(s -> printAst(s, indent + 2));
                }
            }

            case Ast.FunctionDef func -> {
                System.out.println(prefix + "Function: " + func.name() +
                        " params=" + func.parameters() +
                        " returns=" + func.returnParameter());
                func.declarations().forEach(d -> printAst(d, indent + 1));
                func.statements().forEach(s -> printAst(s, indent + 1));
            }

            case Ast.SubroutineDef sub -> {
                System.out.println(prefix + "Subroutine: " + sub.name() + " params=" + sub.parameters());
                sub.declarations().forEach(d -> printAst(d, indent + 1));
                sub.statements().forEach(s -> printAst(s, indent + 1));
            }

            case Ast.DerivedDef derived -> {
                System.out.println(prefix + "Type: " + derived.name());
                derived.declarations().forEach(d -> printAst(d, indent + 1));
            }

            case Ast.Declaration.SimpleDecl decl -> {
                System.out.println(prefix + "SimpleDecl: " + formatVarType(decl.type()) + " :: " + decl.names());
            }

            case Ast.Declaration.FixedArrayDecl decl -> {
                System.out.println(prefix + "FixedArrayDecl: " + formatVarType(decl.type()) +
                        decl.dimensions() + " :: " + decl.names());
            }

            case Ast.Declaration.PointerDecl decl -> {
                System.out.println(prefix + "PointerDecl: " + formatVarType(decl.type()) +
                        " POINTER :: " + decl.names());
            }

            case Ast.Declaration.DynamicArrayDecl decl -> {
                System.out.println(prefix + "DynamicArrayDecl: " + formatVarType(decl.type()) +
                        "(*".repeat(decl.rank()) + ") POINTER :: " + decl.names());
            }

            case Ast.Statement.Assignment assign -> {
                System.out.println(prefix + "Assignment: " + assign.varName() +
                        (assign.arrayIndices().isEmpty() ? "" : "[...]") + " = ");
                printAst(assign.value(), indent + 1);
            }

            case Ast.Statement.DerivedTypeAssignment assign -> {
                System.out.println(prefix + "DerivedTypeAssignment: " + assign.varName() +
                        "%" + assign.fieldName() + " = ");
                printAst(assign.value(), indent + 1);
            }

            case Ast.Statement.IfStatement ifStmt -> {
                System.out.println(prefix + "If:");
                System.out.println(prefix + "  Condition:");
                printAst(ifStmt.condition(), indent + 2);
                System.out.println(prefix + "  Then:");
                printAst(ifStmt.statement(), indent + 2);
            }

            case Ast.Statement.IfThenElseStatement ifElse -> {
                System.out.println(prefix + "If-Then-Else:");
                System.out.println(prefix + "  Condition:");
                printAst(ifElse.condition(), indent + 2);
                System.out.println(prefix + "  Then Block:");
                ifElse.thenBlock().forEach(s -> printAst(s, indent + 2));
                if (!ifElse.elseBlock().isEmpty()) {
                    System.out.println(prefix + "  Else Block:");
                    ifElse.elseBlock().forEach(s -> printAst(s, indent + 2));
                }
            }

            case Ast.Statement.DoStatement doStmt -> {
                System.out.println(prefix + "Do Loop: " + doStmt.loopVar() + " = ... to ...");
                doStmt.body().forEach(s -> printAst(s, indent + 1));
            }

            case Ast.Statement.DoWhileStatement doWhile -> {
                System.out.println(prefix + "Do While:");
                printAst(doWhile.condition(), indent + 1);
                doWhile.body().forEach(s -> printAst(s, indent + 1));
            }

            case Ast.Statement.WriteStatement write -> {
                System.out.println(prefix + "Write: " + write.expressions().size() + " expression(s)");
            }

            case Ast.Statement.ReadStatement read -> {
                System.out.println(prefix + "Read: " + read.expressions());
            }

            case Ast.Statement.SubroutineCallStatement call -> {
                System.out.println(prefix + "Call: " + call.name() + "(" + call.arguments().size() + " args)");
            }

            case Ast.Statement.AllocateStatement alloc -> {
                System.out.println(prefix + "Allocate: " + alloc.name());
            }

            case Ast.Statement.DeallocateStatement dealloc -> {
                System.out.println(prefix + "Deallocate: " + dealloc.name());
            }

            case Ast.Expression.BinaryOp binOp -> {
                System.out.println(prefix + "BinaryOp: " + binOp.op());
                printAst(binOp.left(), indent + 1);
                printAst(binOp.right(), indent + 1);
            }

            case Ast.Expression.Variable var -> {
                System.out.println(prefix + "Variable: " + var.name() +
                        (var.arrayIndices().isEmpty() ? "" : "[" + var.arrayIndices() + "]"));
            }

            case Ast.Expression.FunctionCall call -> {
                System.out.println(prefix + "FunctionCall: " + call.name() +
                        "(" + call.arguments().size() + " args)");
            }

            case Ast.Expression.FieldAccess field -> {
                System.out.println(prefix + "FieldAccess: " + field.typeName() + "%" + field.varName());
            }

            case Ast.Expression.ConstantExpr constExpr -> {
                printAst(constExpr.constant(), indent);
            }

            case Ast.Constants.IntConstant i -> {
                System.out.println(prefix + "IntConstant: " + i.value() + " (base=" + i.base() + ")");
            }

            case Ast.Constants.RealConstant r -> {
                System.out.println(prefix + "RealConstant: " + r.value());
            }

            case Ast.Constants.BooleanConstant b -> {
                System.out.println(prefix + "BooleanConstant: " + b.value());
            }

            case Ast.Constants.StringConstant s -> {
                System.out.println(prefix + "StringConstant: \"" + s.value() + "\"");
            }

            default -> System.out.println(prefix + node.getClass().getSimpleName());
        }
    }

    private static String formatVarType(Ast.VarType type) {
        return switch (type) {
            case Ast.VarType.IntegerType() -> "INTEGER";
            case Ast.VarType.RealType() -> "REAL";
            case Ast.VarType.CharacterType() -> "CHARACTER";
            case Ast.VarType.LogicalType() -> "LOGICAL";
            case Ast.VarType.CustomType custom -> "TYPE(" + custom.typeName() + ")";
        };
    }

    private static void printSymbolTable(SymbolTable table, int indent) {
        String prefix = "  ".repeat(indent);

        for (var entry : table.getSymbols().entrySet()) {
            String name = entry.getKey();
            SymbolData data = entry.getValue();
            Type type = data.getVarType();

            System.out.println(prefix + name + " : " + formatType(type));

            if (data.isFunction()) {
                System.out.println(prefix + "  (Function: params=" + data.getFunctionParameters() +
                        ", returns=" + data.getFunctionReturnParameter() + ")");
            } else if (data.isSubroutine()) {
                System.out.println(prefix + "  (Subroutine: params=" + data.getFunctionParameters() + ")");
            } else if (data.isDerivedType() && data.getFields() != null) {
                System.out.println(prefix + "  Fields:");
                for (var field : data.getFields().entrySet()) {
                    System.out.println(prefix + "    " + field.getKey() + " : " +
                            formatType(field.getValue().getVarType()));
                }
            }
        }
    }

    private static String formatType(Type type) {
        return switch (type) {
            case Type.BaseType.INTEGER -> "INTEGER";
            case Type.BaseType.REAL -> "REAL";
            case Type.BaseType.CHARACTER -> "CHARACTER";
            case Type.BaseType.LOGICAL -> "LOGICAL";
            case Type.FixedArrayType arr -> formatType(arr.baseType()) + arr.dimensions();
            case Type.PointerType ptr -> formatType(ptr.targetType()) + " POINTER";
            case Type.DynamicArrayType dyn -> formatType(dyn.baseType()) + "(*".repeat(dyn.rank()) + ") POINTER";
            case Type.DerivedType derived -> "TYPE(" + derived.typeName() + ")";
            case Type.SubroutineType subr -> "SUBROUTINE(" + subr.parameterTypes() + ")";
            case Type.FunctionType func -> "FUNCTION(" + func.parameterTypes() + " -> " + func.returnType() + ")";
        };
    }

}
