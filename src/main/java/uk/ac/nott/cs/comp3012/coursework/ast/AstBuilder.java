package uk.ac.nott.cs.comp3012.coursework.ast;

import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.TokenStream;
import org.antlr.v4.runtime.tree.ParseTree;
import uk.ac.nott.cs.comp3012.coursework.NottscriptBaseVisitor;
import uk.ac.nott.cs.comp3012.coursework.NottscriptLexer;
import uk.ac.nott.cs.comp3012.coursework.NottscriptParser;
import uk.ac.nott.cs.comp3012.coursework.symbol.SymbolData;
import uk.ac.nott.cs.comp3012.coursework.symbol.SymbolTable;
import uk.ac.nott.cs.comp3012.coursework.types.Type;

import java.util.*;
import java.util.stream.Collectors;

public class AstBuilder extends NottscriptBaseVisitor<Ast>{

    // Tracks declared variable within a scope
    // Prevents ambiguity issues for function and array index
    private SymbolTable currentScope;

    public AstBuilder() {
        this.currentScope = new SymbolTable();
    }

    // ============= Program Structure =============

    @Override
    public Ast visitProgramUnit(NottscriptParser.ProgramUnitContext ctx) {
        List<Ast.SubroutineDef> subroutinesBefore = new ArrayList<>();
        List<Ast.FunctionDef> functionsBefore = new ArrayList<>();
        List<Ast.DerivedDef> derivedBefore = new ArrayList<>();

        List<Ast.SubroutineDef> subroutinesAfter = new ArrayList<>();
        List<Ast.FunctionDef> functionsAfter = new ArrayList<>();
        List<Ast.DerivedDef> derivedAfter = new ArrayList<>();

        Ast.ProgramDef program = null;

        boolean foundProgram = false;

        for (var child : ctx.children) {
            if (child instanceof NottscriptParser.SubroutineDefContext) {
                Ast.SubroutineDef sub = (Ast.SubroutineDef) visit(child);

                if (!foundProgram) {
                    subroutinesBefore.add(sub);
                } else {
                    subroutinesAfter.add(sub);
                }

            } else if (child instanceof NottscriptParser.FunctionDefContext) {
                Ast.FunctionDef func = (Ast.FunctionDef) visit(child);

                if (!foundProgram) {
                    functionsBefore.add(func);
                } else {
                    functionsAfter.add(func);
                }

            } else if (child instanceof NottscriptParser.DerivedDefContext) {
                Ast.DerivedDef derived = (Ast.DerivedDef) visit(child);

                if (!foundProgram) {
                    derivedBefore.add(derived);
                } else {
                    derivedAfter.add(derived);
                }

            } else if (child instanceof NottscriptParser.ProgramDefContext) {
                program = (Ast.ProgramDef) visit(child);
                foundProgram = true;
            }
        }

        return new Ast.ProgramUnit(subroutinesBefore, functionsBefore, derivedBefore,
                program, subroutinesAfter, functionsAfter, derivedAfter);
    }

    @Override
    public Ast visitProgramDef(NottscriptParser.ProgramDefContext ctx) {
        String startName = ctx.Name(0).getText();
        String endName = ctx.Name(1).getText();

        if (!startName.equals(endName)) {
            throw new RuntimeException(
                    "Program name mismatch: 'program " + startName + "' ends with 'end program " + endName + "'"
            );
        }

        enterSymbolScope();
        List<Ast.Declaration> declarations = new ArrayList<>();
        List<Ast.Statement> statements = new ArrayList<>();

        for (var decl : ctx.declaration()) {
            declarations.add((Ast.Declaration) visit(decl));
        }

        for (var stmt : ctx.statement()) {
            statements.add((Ast.Statement) visit(stmt));
        }

        exitSymbolScope();
        return new Ast.ProgramDef(startName, declarations, statements);
    }

    @Override
    public Ast visitSubroutineDef(NottscriptParser.SubroutineDefContext ctx) {
        String startName = ctx.Name(0).getText();
        String endName = ctx.Name(1).getText();

        if (!startName.equals(endName)) {
            throw new RuntimeException(
                    "Subroutine name mismatch: 'subroutine " + startName + "' ends with 'end subroutine " + endName + "'"
            );
        }

        enterSymbolScope();
        List<String> parameters = new ArrayList<>();
        List<Ast.Declaration> declarations = new ArrayList<>();
        List<Ast.Statement> statements = new ArrayList<>();

        if (ctx.parameterList() != null) {
            parameters = extractParameterList(ctx.parameterList());
//            parameters.forEach(this::declareVariable);
        }

        for (var decl : ctx.declaration()) {
            declarations.add((Ast.Declaration) visit(decl));
        }

        for (var stmt : ctx.statement()) {
            statements.add((Ast.Statement) visit(stmt));
        }

        exitSymbolScope();
        return new Ast.SubroutineDef(startName, parameters, declarations, statements);
    }

    @Override
    public Ast visitFunctionDef(NottscriptParser.FunctionDefContext ctx) {
        String startName = ctx.Name(0).getText();
        String endName = ctx.Name(1).getText();

        if (!startName.equals(endName)) {
            throw new RuntimeException(
                    "Function name mismatch: 'function " + startName + "' ends with 'end function " + endName + "'"
            );
        }

        enterSymbolScope();
        List<String> parameters = new ArrayList<>();
        String returnParameter = null;
        List<Ast.Declaration> declarations = new ArrayList<>();
        List<Ast.Statement> statements = new ArrayList<>();

        if (ctx.parameterList() != null) {
            parameters = extractParameterList(ctx.parameterList());
//            parameters.forEach(this::declareVariable);
        }

        if (ctx.returnArg() != null) {
            returnParameter = ctx.returnArg().Name().getText();
        } else {
            returnParameter = startName;
        }

        for (var decl : ctx.declaration()) {
            declarations.add((Ast.Declaration) visit(decl));
        }

        for (var stmt : ctx.statement()) {
            statements.add((Ast.Statement) visit(stmt));
        }

        exitSymbolScope();
        return new Ast.FunctionDef(startName, parameters, returnParameter, declarations, statements);
    }

    @Override
    public Ast visitDerivedDef(NottscriptParser.DerivedDefContext ctx) {
        String startName = ctx.Name(0).getText();
        String endName = ctx.Name(1).getText();

        if (!startName.equals(endName)) {
            throw new RuntimeException(
                    "Derived Type name mismatch: 'type " + startName + "' ends with 'end type " + endName + "'"
            );
        }

        enterSymbolScope();
        List<Ast.Declaration> declarations = new ArrayList<>();
        for (var decl : ctx.declaration()) {
            declarations.add((Ast.Declaration) visit(decl));
        }

        exitSymbolScope();
        return new Ast.DerivedDef(startName, declarations);
    }

    // ============= Declarations =============

    @Override
    public Ast visitDeclaration(NottscriptParser.DeclarationContext ctx) {
        Ast.VarType type = (Ast.VarType) visit(ctx.varTypeName());

        if (ctx.POINTER() != null) {

            // Pointer Declaration
            if (ctx.arrayStar().isEmpty()) {
                List<String> pointerNames = extractDeclaredVariableNames(ctx);
                pointerNames.forEach(this::declareVariable);
                return new Ast.Declaration.PointerDecl(type, pointerNames);

            // Dynamic Array declaration
            } else {
                List<String> dynamicArrayNames = extractDeclaredVariableNames(ctx);
                dynamicArrayNames.forEach(this::declareVariable);
                int rank = ctx.arrayStar().size();
                return new Ast.Declaration.DynamicArrayDecl(type, rank, dynamicArrayNames);
            }

        // Fixed Array declaration
        } else if (!ctx.arrayDimension().isEmpty()) {
            List<Integer> dimensions = ctx.arrayDimension().stream()
                    .map(d -> Integer.parseInt(d.getText()))
                    .collect(Collectors.toList());

            List<String> fixedArrayNames = extractDeclaredVariableNames(ctx);
            fixedArrayNames.forEach(this::declareVariable);
            return new Ast.Declaration.FixedArrayDecl(type, dimensions, fixedArrayNames);

        // Simple declaration
        } else {
            List<String> names = extractDeclaredVariableNames(ctx);
            names.forEach(this::declareVariable);
            return new Ast.Declaration.SimpleDecl(type, names);
        }
    }

    @Override
    public Ast visitVarTypeName(NottscriptParser.VarTypeNameContext ctx) {
        if (ctx.INTEGER() != null) return new Ast.VarType.IntegerType();
        if (ctx.REAL() != null) return new Ast.VarType.RealType();
        if (ctx.CHARACTER() != null) return new Ast.VarType.CharacterType();
        if (ctx.LOGICAL() != null) return new Ast.VarType.LogicalType();
        if (ctx.TYPE() != null) {
            String typeName = ctx.Name().getText();
            return new Ast.VarType.CustomType(typeName);
        }
        throw new RuntimeException("Unknown variable type" + ctx.getText());
    }

    // ============= Statements =============

    @Override
    public Ast visitStatement(NottscriptParser.StatementContext ctx) {
        return visit(ctx.getChild(0));
    }

    @Override
    public Ast visitAssignment(NottscriptParser.AssignmentContext ctx) {
        String varName = ctx.Name(0).getText();
        Ast.Expression value = (Ast.Expression) visit(ctx.expression());
        List<Ast.Expression> arrayIndices = new ArrayList<>();

        if (ctx.FIELD_ACCESS() != null) {
            // Field assignment
            String fieldName = ctx.Name(1).getText();
            if (ctx.arrayIndex() != null) {
                arrayIndices = visitArrayIndexExpressions(ctx.arrayIndex());
            }
            return new Ast.Statement.DerivedTypeAssignment(varName, fieldName, arrayIndices, value);
        } else {
            // Regular assignment
            if (ctx.arrayIndex() != null) {
                arrayIndices = visitArrayIndexExpressions(ctx.arrayIndex());
            }
            return new Ast.Statement.Assignment(varName, arrayIndices, value);
        }
    }

    @Override
    public Ast visitSubroutineCallStatement(NottscriptParser.SubroutineCallStatementContext ctx) {
        String name = ctx.Name().getText();
        List<Ast.Expression> arguments = new ArrayList<>();
        if (ctx.argumentList() != null) {
            arguments = visitArgumentListExpressions(ctx.argumentList());
        }
        return new Ast.Statement.SubroutineCallStatement(name, arguments);
    }

    @Override
    public Ast visitIfStatement(NottscriptParser.IfStatementContext ctx) {
        Ast.Expression condition = (Ast.Expression) visit(ctx.expression());
        Ast.Statement statement = (Ast.Statement) visit(ctx.statement());
        return new Ast.Statement.IfStatement(condition, statement);
    }

    @Override
    public Ast visitIfThenElseStatement(NottscriptParser.IfThenElseStatementContext ctx) {
        Ast.Expression condition = (Ast.Expression) visit(ctx.expression());

        List<Ast.Statement> thenBlock = new ArrayList<>();
        List<Ast.Statement> elseBlock = new ArrayList<>();

        // Find the position of ELSE token in children
        int elseIndex = -1;
        if (ctx.ELSE() != null) {
            for (int i = 0; i < ctx.children.size(); i++) {
                if (ctx.children.get(i).getText().equals("else")) {
                    elseIndex = i;
                    break;
                }
            }
        }

        // Split statements based on ELSE position
        for (var stmtCtx : ctx.statement()) {
            // Find this statement's position in children
            int stmtPosition = ctx.children.indexOf(stmtCtx);

            if (elseIndex != -1 && stmtPosition > elseIndex) {
                elseBlock.add((Ast.Statement) visit(stmtCtx));
            } else {
                thenBlock.add((Ast.Statement) visit(stmtCtx));
            }
        }

        return new Ast.Statement.IfThenElseStatement(condition, thenBlock, elseBlock);
    }

    @Override
    public Ast visitDoStatement(NottscriptParser.DoStatementContext ctx) {
        String loopVar = ctx.Name().getText();

        Ast.Expression start = (Ast.Expression) visit(ctx.expression(0));
        Ast.Expression end = (Ast.Expression) visit(ctx.expression(1));
        Ast.Expression step = ctx.expression().size() > 2 ?
                (Ast.Expression) visit(ctx.expression(2)) :
                new Ast.Expression.ConstantExpr(new Ast.Constants.IntConstant(+1, Ast.IntBase.BASE10));
        List<Ast.Statement> body = new ArrayList<>();

        for (var stmt : ctx.statement()) {
            body.add((Ast.Statement) visit(stmt));
        }

        return new Ast.Statement.DoStatement(loopVar, start, end, step, body);
    }

    @Override
    public Ast visitDoWhileStatement(NottscriptParser.DoWhileStatementContext ctx) {
        Ast.Expression condition = (Ast.Expression) visit(ctx.expression());

        List<Ast.Statement> body = new ArrayList<>();

        for (var stmt : ctx.statement()) {
            body.add((Ast.Statement) visit(stmt));
        }

        return new Ast.Statement.DoWhileStatement(condition, body);
    }

    @Override
    public Ast visitWriteStatement(NottscriptParser.WriteStatementContext ctx) {

        List<Ast.Expression> expressions = new ArrayList<>();

        for (var expr : ctx.expression()) {
            expressions.add((Ast.Expression) visit(expr));
        }

        return new Ast.Statement.WriteStatement(expressions);
    }

    @Override
    public Ast visitReadStatement(NottscriptParser.ReadStatementContext ctx) {

        List<Ast.Expression> names = new ArrayList<>();

        for (var varName : ctx.expression()) {
            names.add((Ast.Expression) visit(varName));
        }

        return new Ast.Statement.ReadStatement(names);
    }

    @Override
    public Ast visitAllocStatement(NottscriptParser.AllocStatementContext ctx) {
        String name = ctx.Name(0).getText();
        Ast.AllocSize allocSize = null;

        // Second argument is a variable rather than integer
        if (ctx.Name().size() > 1) {
            allocSize = new Ast.AllocSize.VariableSize(ctx.Name(1).getText());

        // Second argument is fixed integer value
        } else if (ctx.integers() != null) {
            Ast.Constants.IntConstant constant = (Ast.Constants.IntConstant) visit(ctx.integers());
            allocSize = new Ast.AllocSize.ConstantSize(constant);
        }

        return new Ast.Statement.AllocateStatement(name, allocSize);
    }

    @Override
    public Ast visitDeallocStatement(NottscriptParser.DeallocStatementContext ctx) {
        return new Ast.Statement.DeallocateStatement(ctx.Name().getText());
    }

    // ============= Expressions =============

    @Override
    public Ast visitExpression(NottscriptParser.ExpressionContext ctx) {
        // Binary operations

        // Arithmetic operations
        if (ctx.PLUS() != null || ctx.MINUS() != null || ctx.MULT() != null || ctx.DIV() != null || ctx.POWER() != null) {
            Ast.Expression left = (Ast.Expression) visit(ctx.expression(0));
            Ast.Expression right = (Ast.Expression) visit(ctx.expression(1));
            Ast.BinaryOperator op;
            if (ctx.PLUS() != null) op = Ast.BinaryOperator.PLUS;
            else if (ctx.MINUS() != null) op = Ast.BinaryOperator.MINUS;
            else if (ctx.MULT() != null) op = Ast.BinaryOperator.MULT;
            else if (ctx.DIV() != null) op = Ast.BinaryOperator.DIV;
            else op = Ast.BinaryOperator.POWER;
            return new Ast.Expression.BinaryOp(op, left, right);
        }

        // Comparison operations
        if (ctx.EQ() != null || ctx.NEQ() != null || ctx.LT() != null || ctx.GT() != null || ctx.LE() != null || ctx.GE() != null) {
            Ast.Expression left = (Ast.Expression) visit(ctx.expression(0));
            Ast.Expression right = (Ast.Expression) visit(ctx.expression(1));
            Ast.BinaryOperator op;
            if (ctx.EQ() != null) op = Ast.BinaryOperator.EQ;
            else if (ctx.NEQ() != null) op = Ast.BinaryOperator.NEQ;
            else if (ctx.LT() != null) op = Ast.BinaryOperator.LT;
            else if (ctx.GT() != null) op = Ast.BinaryOperator.GT;
            else if (ctx.LE() != null) op = Ast.BinaryOperator.LE;
            else op = Ast.BinaryOperator.GE;
            return new Ast.Expression.BinaryOp(op, left, right);
        }

        // Logical operations
        if (ctx.AND() != null || ctx.OR() != null) {
            Ast.Expression left = (Ast.Expression) visit(ctx.expression(0));
            Ast.Expression right = (Ast.Expression) visit(ctx.expression(1));
            Ast.BinaryOperator op = ctx.AND() != null ? Ast.BinaryOperator.AND : Ast.BinaryOperator.OR;
            return new Ast.Expression.BinaryOp(op, left, right);
        }

        // Concat operations
        if (ctx.CONCAT() != null) {
            Ast.Expression left = (Ast.Expression) visit(ctx.expression(0));
            Ast.Expression right = (Ast.Expression) visit(ctx.expression(1));
            return new Ast.Expression.BinaryOp(Ast.BinaryOperator.CONCAT, left, right);
        }

        // Parenthesized expression
        if (ctx.LPAREN() != null && ctx.expression().size() == 1) {
            return (Ast.Expression) visit(ctx.expression(0));
        }

        // Constants
        if (ctx.constants() != null) {
            return (Ast.Expression) visit(ctx.constants());
        }

        // Function call and array
        if (ctx.Name() != null && ctx.LPAREN() != null) {
            String name = ctx.Name(0).getText();
            List<Ast.Expression> arguments = new ArrayList<>();

            if (ctx.argumentList() != null) {
                arguments = visitArgumentListExpressions(ctx.argumentList());
            }

            // Test for function calls and array (ambiguous grammar)
            if (isVariableDeclared(name)) {
                // If it's declared, treat as array access (variable)

                if (ctx.arrayIndex() != null) {
                    arguments = visitArrayIndexExpressions(ctx.arrayIndex());
                }
                return new Ast.Expression.Variable(name, arguments);
            } else {
                // Not declared before, assume it's a function call

                if (ctx.argumentList() != null) {
                    arguments = visitArgumentListExpressions(ctx.argumentList());
                }
                return new Ast.Expression.FunctionCall(name, arguments);
            }

        }

        // Field access
        if (ctx.FIELD_ACCESS() != null) {
            String typeName = ctx.Name(0).getText();
            String varName = ctx.Name(1).getText();
            List<Ast.Expression> arrayIndices = new ArrayList<>();
            if (ctx.arrayIndex() != null) {
                arrayIndices = visitArrayIndexExpressions(ctx.arrayIndex());
            }
            return new Ast.Expression.FieldAccess(typeName, varName, arrayIndices);
        }

        // Simple variable reference
        if (ctx.Name() != null && ctx.LPAREN() == null) {
            String name = ctx.Name(0).getText();
            return new Ast.Expression.Variable(name, new ArrayList<>());
        }

        throw new RuntimeException("Unknown expression type: " + ctx.getText());
    }

    // ============= Constants =============

    @Override
    public Ast visitConstants(NottscriptParser.ConstantsContext ctx) {
        if (ctx.integers() != null) {
            return new Ast.Expression.ConstantExpr((Ast.Constants) visit(ctx.integers()));
        }
        if (ctx.Reals() != null) {
            float value = Float.parseFloat(ctx.Reals().getText());
            return new Ast.Expression.ConstantExpr(new Ast.Constants.RealConstant(value));
        }
        if (ctx.Boolean() != null) {
            String text = ctx.Boolean().getText();
            boolean value = text.contains("true");
            return new Ast.Expression.ConstantExpr(new Ast.Constants.BooleanConstant(value));
        }
        if (ctx.String() != null) {
            String text = ctx.String().getText();
            String value = text.substring(1, text.length() - 1);
            return new Ast.Expression.ConstantExpr(new Ast.Constants.StringConstant(value));
        }
        throw new RuntimeException("Unknown constant type: " + ctx.getText());
    }

    @Override
    public Ast visitIntegers(NottscriptParser.IntegersContext ctx) {
        if (ctx.Int_base10() != null) {
            int value = Integer.parseInt(ctx.Int_base10().getText());
            return new Ast.Constants.IntConstant(value, Ast.IntBase.BASE10);
        }
        if (ctx.Int_base2() != null) {
            String text = ctx.Int_base2().getText();
            String binaryStr = text.substring(2, text.length() - 1); // Remove b" and "
            int value = Integer.parseInt(binaryStr, 2);
            return new Ast.Constants.IntConstant(value, Ast.IntBase.BASE2);
        }
        if (ctx.Int_base7() != null) {
            String text = ctx.Int_base7().getText();
            String octalStr = text.substring(2, text.length() - 1); // Remove o" and "
            int value = Integer.parseInt(octalStr, 7);
            return new Ast.Constants.IntConstant(value, Ast.IntBase.BASE7);
        }
        if (ctx.Int_base16() != null) {
            String text = ctx.Int_base16().getText();
            String hexStr = text.substring(2, text.length() - 1); // Remove z" and "
            int value = Integer.parseInt(hexStr, 16);
            return new Ast.Constants.IntConstant(value, Ast.IntBase.BASE16);
        }
        throw new RuntimeException("Unknown integer type: " + ctx.getText());
    }

    // Helper methods
    private List<String> extractParameterList(NottscriptParser.ParameterListContext ctx) {
        return ctx.Name().stream()
                .map(ParseTree::getText)
                .collect(Collectors.toList());
    }

    private List<Ast.Expression> visitArrayIndexExpressions(NottscriptParser.ArrayIndexContext ctx) {
        return ctx.expression().stream()
                .map(e -> (Ast.Expression) visit(e))
                .collect(Collectors.toList());
    }

    private List<Ast.Expression> visitArgumentListExpressions(NottscriptParser.ArgumentListContext ctx) {
        return ctx.expression().stream()
                .map(c -> (Ast.Expression) visit(c))
                .collect(Collectors.toList());
    }

    private void enterSymbolScope() {
        currentScope = new SymbolTable(currentScope);  // Create child scope
    }

    private void exitSymbolScope() {
        currentScope = currentScope.getParent();  // Return to parent
    }

    private void declareVariable(String name) {
        // Dummy declaration, actual checks happen in TypeChecker class
        currentScope.define(name, SymbolData.createVariable(name, Type.BaseType.INTEGER));
    }

    private boolean isVariableDeclared(String name) {
        return currentScope.hasBeenDefined(name);
    }

    private List<String> extractDeclaredVariableNames(NottscriptParser.DeclarationContext ctx) {
        return ctx.Name().stream()
                .map(ParseTree::getText)
                .collect(Collectors.toList());
    }

    // TODO: Remove
    public static void main(String[] args) {
        String input =
            """
                    function arr_test() result(res)
                        integer :: res
                        res = 999
                    end function arr_test
                    
                    function func1() result(out)
                        integer :: out
                        integer(5) :: arr_test
                        integer :: i
                    
                        ! Local variable arr_test (array)
                        do i = 1, 5, 1
                            arr_test(i) = i * 10
                        end do
                    
                        ! This should access LOCAL array arr_test, not the function
                        out = arr_test(3)
                    end function func1
                    
                    function func2() result(out)
                        integer :: out
                    
                        ! No local arr_test declared here
                        ! This should call the FUNCTION arr_test
                        out = arr_test()
                    end function func2
                    
                    program testprog
                        integer(3) :: arr_test
                        integer :: i, result1, result2
                    
                        ! Local variable arr_test (array)
                        arr_test(1) = 100
                        arr_test(2) = 200
                        arr_test(3) = 300
                    
                        ! This should access LOCAL array arr_test, not the function
                        write arr_test(2)
                    
                        ! Call func1 which has local arr_test array
                        result1 = func1()
                        write result1
                    
                        ! Call func2 which calls arr_test function
                        result2 = func2()
                        write result2
                    end program testprog
            """;

        // Construct parser
        NottscriptLexer lexer = new NottscriptLexer(CharStreams.fromString(input));
        TokenStream tokens = new CommonTokenStream(lexer);
        NottscriptParser parser = new NottscriptParser(tokens);

        // Parse input and build AST
        AstBuilder builder = new AstBuilder();

        Ast ast = builder.visit(parser.programUnit());

        System.out.println("AST nodes:");
        System.out.println(ast);

    }

}
