package uk.ac.nott.cs.comp3012.coursework;

import java.io.BufferedOutputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import org.antlr.v4.runtime.CharStream;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;

import uk.ac.nott.cs.comp3012.coursework.ast.Ast;
import uk.ac.nott.cs.comp3012.coursework.ast.AstBuilder;
import uk.ac.nott.cs.comp3012.coursework.symbol.SymbolTable;
import uk.ac.nott.cs.comp3012.coursework.tac.TacGenerator;
import uk.ac.nott.cs.comp3012.coursework.tac.TacInstr;
import uk.ac.nott.cs.comp3012.coursework.tam.TamGenerator;
import uk.ac.nott.cs.comp3012.coursework.tam.TamInstruction;
import uk.ac.nott.cs.comp3012.coursework.types.TypeChecker;

/**
 * Complete Fortran compiler pipeline:
 * Source → Parse → Type Check → TAC → TAM → Bytecode
 */
public class Compiler {

    private final Frontend frontend;
    private final Backend backend;

    public Compiler(Frontend frontend, Backend backend) {
        this.frontend = frontend;
        this.backend = backend;
    }

    public static void main(String[] args) throws IOException {
//        if (args.length < 2) {
//            System.err.println("Usage: java Compiler <input.f90> <output.tam>");
//            System.exit(1);
//        }

//        String inputFile = args[0];
//        String outputFile = args[1];

        String inputFile = "C:\\Nottingham\\Year 3\\COMP3012 Compilers\\Coursework\\source.txt";
        String outputFile = "C:\\Nottingham\\Year 3\\COMP3012 Compilers\\Coursework\\out2.dat";

        // Create the complete compiler pipeline
        Frontend frontend = new CompleteFrontend();
        Backend backend = new CompleteBackend();

        Compiler compiler = new Compiler(frontend, backend);
        compiler.runCompiler(inputFile, outputFile);

        System.out.println("✓ Compilation successful!");
        System.out.println("  Input:  " + inputFile);
        System.out.println("  Output: " + outputFile);
    }

    public void runCompiler(String inputFile, String outputFile) throws IOException {
        // Read source file
        StringBuilder programText = new StringBuilder();
        Files.readAllLines(Path.of(inputFile)).forEach(line -> {
            programText.append(line).append("\n");
        });

        // Run frontend (parsing + type checking)
        Ast program = frontend.runFrontend(programText.toString());

        // Run backend (TAC + TAM + bytecode)
        byte[] code = backend.runBackend(program);

        // Write bytecode to file
        try (BufferedOutputStream out = new BufferedOutputStream(
                new FileOutputStream(outputFile))) {
            out.write(code);
        }
    }

    @FunctionalInterface
    public interface Frontend {
        Ast runFrontend(String programText);
    }

    @FunctionalInterface
    public interface Backend {
        byte[] runBackend(Ast program);
    }

    /**
     * Complete frontend: Lexing → Parsing → Type Checking
     */
    public static class CompleteFrontend implements Frontend {
        @Override
        public Ast runFrontend(String programText) {
            try {
                // Lex and parse
                CharStream input = CharStreams.fromString(programText);
                NottscriptLexer lexer = new NottscriptLexer(input);
                CommonTokenStream tokens = new CommonTokenStream(lexer);
                NottscriptParser parser = new NottscriptParser(tokens);

                // Build AST
                AstBuilder builder = new AstBuilder();
                Ast ast = builder.visit(parser.programUnit());

                // Type check
                TypeChecker typeChecker = new TypeChecker();
                typeChecker.visit(ast);

                if (typeChecker.hasErrors()) {
                    System.err.println("Type checking failed:");
                    for (String error : typeChecker.getErrors()) {
                        System.err.println("- " + error);
                    }
                    throw new RuntimeException("Type checking failed with " +
                            typeChecker.getErrors().size() + " error(s)");
                }

                System.out.println("✓ Type checking passed");

                return ast;

            } catch (Exception e) {
                throw new RuntimeException("Frontend failed: " + e.getMessage(), e);
            }
        }
    }

    /**
     * Complete backend: TAC Generation → TAM Generation → Bytecode Encoding
     */
    public static class CompleteBackend implements Backend {
        @Override
        public byte[] runBackend(Ast program) {
            try {
                // Re-run type checker to get symbol tables
                // (In a real compiler, you'd pass this through a context object)
                TypeChecker typeChecker = new TypeChecker();
                typeChecker.visit(program);

                SymbolTable globalScope = typeChecker.getCurrentScope();
                Map<String, SymbolTable> nestedScopes = typeChecker.getNestedScopes();

                // Generate TAC
                System.out.println("Generating TAC...");
                TacGenerator tacGenerator = new TacGenerator(globalScope, nestedScopes);
                List<TacInstr> tacInstructions = tacGenerator.visit(program);
                System.out.println("✓ Generated " + tacInstructions.size() + " TAC instructions");

                // print TAC for debugging
                TacGenerator.printTac(tacInstructions);

                // Generate TAM
                System.out.println("Generating TAM...");
                TamGenerator tamGenerator = new TamGenerator(globalScope, nestedScopes, tacGenerator.getLabelInstructions());
                TamInstruction.InstructionList tamInstructions = tamGenerator.generate(tacInstructions);
                System.out.println("✓ Generated " + tamInstructions.size() + " TAM instructions");

                // print TAM for debugging
                TamGenerator.printTam(tamInstructions);

                // Encode to bytecode using built-in toByteArray()
                System.out.println("Encoding bytecode...");
                byte[] bytecode = tamInstructions.toByteArray();
                System.out.println("✓ Generated " + bytecode.length + " bytes");

                return bytecode;

            } catch (Exception e) {
                throw new RuntimeException("Backend failed: " + e.getMessage(), e);
            }
        }
    }
}