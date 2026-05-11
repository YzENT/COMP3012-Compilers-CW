package uk.ac.nott.cs.comp3012.coursework.tam;

import uk.ac.nott.cs.comp3012.coursework.symbol.SymbolData;
import uk.ac.nott.cs.comp3012.coursework.symbol.SymbolTable;
import uk.ac.nott.cs.comp3012.coursework.tac.*;
import uk.ac.nott.cs.comp3012.coursework.types.Type;

import java.util.*;

/**
 * Generates TAM (Triangle Abstract Machine) code from Three-Address Code (TAC).
 */
public class TamGenerator {

    private final SymbolTable globalScope;
    private final Map<String, SymbolTable> nestedScopes;
    private SymbolTable currentScope;

    // Maps variable names to their offsets in the current frame
    private final Map<String, Integer> variableOffsets = new HashMap<>();

    // Maps TAC temporary variables to their offsets
    private final Map<String, Integer> tempOffsets = new HashMap<>();

    // Maps TAC label UUIDs to TAM instruction addresses
    private final Map<UUID, Integer> labelAddresses = new HashMap<>();

    // List of instructions that need label resolution (address, label UUID)
    private final List<PendingLabel> pendingLabels = new ArrayList<>();

    private final Map<String, TacInstr> labelInstructions;

    private int stackBasePointer = 0;
    private int heapPointer = 65535;

    // Subroutines
    private boolean inSubroutine = false;
    private final Map<String, Integer> subroutineVarOffsets = new HashMap<>();
    private final Map<String, Integer> subroutineTempOffsets = new HashMap<>();

    // Functions
    private boolean inFunction = false;
    private final Map<String, Integer> functionVarOffsets = new HashMap<>();
    private final Map<String, Integer> functionTempOffsets = new HashMap<>();

    // Shared between functions and subroutines
    private int localBasePointer = 3; // starts with 3 to ignore 012, static link, dynamic link, ret addr

    // Register tracker
    private TamRegister currentRegisterType = TamRegister.SB;

    private static class PendingLabel {
        int instructionIndex;
        UUID labelId;

        PendingLabel(int instructionIndex, UUID labelId) {
            this.instructionIndex = instructionIndex;
            this.labelId = labelId;
        }
    }

    public TamGenerator(SymbolTable globalScope, Map<String, SymbolTable> nestedScopes, Map<String, TacInstr> labelInstructions) {
        this.globalScope = globalScope;
        this.nestedScopes = nestedScopes;
        this.currentScope = globalScope;
        this.labelInstructions = labelInstructions;
    }

    /**
     * Generate TAM code from TAC instructions
     */
    public TamInstruction.InstructionList generate(List<TacInstr> tacInstructions) {
        TamInstruction.InstructionList instructions = new TamInstruction.InstructionList();

        // Separate main program from subroutines
        List<TacInstr> mainProgramCodes = new ArrayList<>();
        Map<String, List<TacInstr>> subroutineBlocks = new HashMap<>();
        Map<String, List<TacInstr>> functionBlocks = new HashMap<>();

        separateCodeBlocks(tacInstructions, mainProgramCodes, subroutineBlocks, functionBlocks);
        generateMainProgram(instructions, mainProgramCodes);

        // Generate subroutines
        for (Map.Entry<String, List<TacInstr>> entry : subroutineBlocks.entrySet()) {
            TamInstruction.InstructionList subroutineCode = generateSubroutine(
                    entry.getKey(),
                    entry.getValue(),
                    instructions.size()  // Pass current global address
            );
            instructions.addAll(subroutineCode);
        }

        // Generation functions
        for (Map.Entry<String, List<TacInstr>> entry : functionBlocks.entrySet()) {
            TamInstruction.InstructionList functionCode = generateFunction(
                    entry.getKey(),
                    entry.getValue(),
                    instructions.size()
            );
            instructions.addAll(functionCode);
        }

        // Resolve label addresses
        resolveLabels(instructions);

        return instructions;
    }

    private void separateCodeBlocks(List<TacInstr> tacInstructions,
                                    List<TacInstr> mainProgramCodes,
                                    Map<String, List<TacInstr>> subroutineBlocks,
                                    Map<String, List<TacInstr>> functionBlocks) {
        List<TacInstr> currentBlock = mainProgramCodes;

        for (TacInstr instr : tacInstructions) {
            if (instr.op() == TacOp.Label) {
                String subrName = findSubroutineForLabel(instr.id());
                String funcName = findFunctionForLabel(instr.id());
                String progName = findProgramForLabel(instr.id());

                if (subrName != null) {
                    currentBlock = new ArrayList<>();
                    subroutineBlocks.put(subrName, currentBlock);
                } else if (funcName != null) {
                    currentBlock = new ArrayList<>();
                    functionBlocks.put(funcName, currentBlock);
                } else if (progName != null) {
                    currentBlock = mainProgramCodes;
                }
            }
            currentBlock.add(instr);
        }
    }

    private String findSubroutineForLabel(UUID labelId) {
        return findLabelWithPrefix(labelId, "SUBR_");
    }

    private String findFunctionForLabel(UUID labelId) {
        return findLabelWithPrefix(labelId, "FUNC_");
    }

    private String findProgramForLabel(UUID labelId) {
        return findLabelWithPrefix(labelId, "PROG_");
    }

    private String findLabelWithPrefix(UUID labelId, String prefix) {
        for (Map.Entry<String, TacInstr> entry : labelInstructions.entrySet()) {
            if (entry.getKey().startsWith(prefix) && entry.getValue().id().equals(labelId)) {
                return entry.getKey().substring(prefix.length());
            }
        }
        return null;
    }

    private void generateMainProgram(TamInstruction.InstructionList instructions,
                                     List<TacInstr> mainProgramCodes) {

        // Main program uses SB register
        inSubroutine = false;
        inFunction = false;
        currentScope = globalScope;
        currentRegisterType = TamRegister.SB;

        collectVariables(mainProgramCodes);

        if (stackBasePointer > 0) {
            instructions.add(new TamInstruction.Instruction(
                    TamOpcode.PUSH, null, 0, stackBasePointer
            ));
        }

        for (TacInstr instr : mainProgramCodes) {
            if (instr.op() == TacOp.Label) {
                labelAddresses.put(instr.id(), instructions.size());
            }
            List<TamInstruction.Instruction> tamInstrs = generateInstruction(instr, instructions.size());
            instructions.addAll(tamInstrs);
        }

        instructions.add(new TamInstruction.Instruction(
                TamOpcode.HALT, null, 0, 0
        ));
    }

    private TamInstruction.InstructionList generateSubroutine(String name, List<TacInstr> tacInstructions, int startAddress) {
        TamInstruction.InstructionList instructions = new TamInstruction.InstructionList();

        // Setup
        SymbolTable subScope = nestedScopes.get("SUBR_" + name);
        if (subScope == null) {
            throw new RuntimeException("No scope found for function: " + name);
        }

        SymbolTable prevScope = currentScope;
        currentScope = subScope;
        inSubroutine = true;
        currentRegisterType = TamRegister.LB;

        // Reset
        subroutineVarOffsets.clear();
        subroutineTempOffsets.clear();
        localBasePointer = 3;

        Optional<SymbolData> subData = prevScope.lookup("SUBR_" + name);
        if (subData.isEmpty()) {
            throw new RuntimeException("Subroutine not found in symbol table: " + name);
        }

        List<String> params = subData.get().getFunctionParameters();
        if (params == null) {
            params = new ArrayList<>();
        }

        for (String param : params) {
            subroutineVarOffsets.put(param, localBasePointer);
            int varSize = getVariableSize(param);
            localBasePointer = localBasePointer + varSize;
        }

        // Argument size of this subroutine
        int argumentSize = localBasePointer - 3;

        Set<String> localVars = new LinkedHashSet<>();
        Set<String> temps = new LinkedHashSet<>();

        for (TacInstr instr : tacInstructions) {
            if (instr.op() == TacOp.Label || instr.op() == TacOp.ReturnVoid) {
                continue;
            }
            collectParamVariables(instr.dst(), localVars, temps);
            collectParamVariables(instr.src1(), localVars, temps);
            collectParamVariables(instr.src2(), localVars, temps);
        }

        for (String var : localVars) {
            if (!subroutineVarOffsets.containsKey(var)) {
                subroutineVarOffsets.put(var, localBasePointer);
                int varSize = getVariableSize(var);
                localBasePointer = localBasePointer + varSize;
            }
        }

        for (String temp : temps) {
            subroutineTempOffsets.put(temp, localBasePointer++);
        }

        int totalLocalVariableCount = subroutineVarOffsets.size() + subroutineTempOffsets.size();

        System.out.println("=== Subroutine: " + name + " ===");
        System.out.println("Parameters: " + params);
        System.out.println("Subroutine offsets: " + subroutineVarOffsets);
        System.out.println("Subroutine temp offsets: " + subroutineTempOffsets);
        System.out.println("Total locals size: " + totalLocalVariableCount);

        // Generate TAM instructions
        boolean allocatedVariableSpace = false;
        boolean storedArgumentsValues = false;

        for (TacInstr instr : tacInstructions) {
            if (instr.op() == TacOp.Label) {
                // Record label at GLOBAL address (startAddress + local offset)
                labelAddresses.put(instr.id(), startAddress + instructions.size());
                continue;
            }

            if (!allocatedVariableSpace && totalLocalVariableCount > 0) {
                instructions.add(new TamInstruction.Instruction(
                        TamOpcode.PUSH, null, 0, localBasePointer - 3 // culprit
                ));
                allocatedVariableSpace = true;
            }

            // store the passed arguments into local base
            if (!storedArgumentsValues) {
                for (int i = 0; i < params.size(); i++) {
                    int negLBOffset = -argumentSize + i;

                    instructions.add(new TamInstruction.Instruction(
                            TamOpcode.LOAD, TamRegister.LB, 1, negLBOffset
                    ));
                    instructions.add(new TamInstruction.Instruction(
                            TamOpcode.STORE, TamRegister.LB, 1, subroutineVarOffsets.get(params.get(i))
                    ));
                }

                storedArgumentsValues = true;
            }

            if (instr.op() == TacOp.ReturnVoid) {
                int paramSize = 0;
                for (String param : params) {
                    paramSize += getVariableSize(param);
                }

                int wordsToPop = paramSize;

                instructions.add(new TamInstruction.Instruction(
                        TamOpcode.RETURN, null, 0, wordsToPop
                ));
            } else {
                // Pass the global current address when generating instructions
                List<TamInstruction.Instruction> generated = generateInstruction(
                        instr,
                        startAddress + instructions.size()
                );
                instructions.addAll(generated);
            }
        }

        currentScope = prevScope;
        inSubroutine = false;

        return instructions;
    }

    private TamInstruction.InstructionList generateFunction(String name, List<TacInstr> tacInstructions, int startAddress) {
        TamInstruction.InstructionList instructions = new TamInstruction.InstructionList();

        // Setup
        SymbolTable funcScope = nestedScopes.get("FUNC_" + name);
        if (funcScope == null) {
            throw new RuntimeException("No scope found for function: " + name);
        }

        SymbolTable prevScope = currentScope;
        currentScope = funcScope;
        inFunction = true;
        currentRegisterType = TamRegister.LB;

        // State reset
        functionVarOffsets.clear();
        functionTempOffsets.clear();
        localBasePointer = 3;

        Optional<SymbolData> funcData = prevScope.lookup("FUNC_" + name);
        if (funcData.isEmpty()) {
            throw new RuntimeException("Function not found in symbol table: " + name);
        }

        List<String> params = funcData.get().getFunctionParameters();
        if (params == null) {
            params = new ArrayList<>();
        }

        // Same parameter handling as subroutines
        for (String param : params) {
            functionVarOffsets.put(param, localBasePointer);
            int varSize = getVariableSize(param);
            localBasePointer = localBasePointer + varSize;
        }

        int argumentSize = localBasePointer - 3;

        // Collect local variables
        Set<String> localVars = new LinkedHashSet<>();
        Set<String> temps = new LinkedHashSet<>();

        for (TacInstr instr : tacInstructions) {
            if (instr.op() == TacOp.Label || instr.op() == TacOp.Return) {
                continue;
            }
            collectParamVariables(instr.dst(), localVars, temps);
            collectParamVariables(instr.src1(), localVars, temps);
            collectParamVariables(instr.src2(), localVars, temps);
        }

        for (String var : localVars) {
            if (!functionVarOffsets.containsKey(var)) {
                functionVarOffsets.put(var, localBasePointer);
                int varSize = getVariableSize(var);
                localBasePointer = localBasePointer + varSize;
            }
        }

        for (String temp : temps) {
            functionTempOffsets.put(temp, localBasePointer++);
        }

        int totalLocalVariableCount = functionVarOffsets.size() + functionTempOffsets.size();

        System.out.println("=== Function: " + name + " ===");
        System.out.println("Parameters: " + params);
        System.out.println("Function offsets: " + functionVarOffsets);
        System.out.println("Function temp offsets: " + functionTempOffsets);
        System.out.println("Total locals size: " + totalLocalVariableCount);

        // Generate TAM instructions
        boolean allocatedVariableSpace = false;
        boolean storedArgumentsValues = false;

        for (TacInstr instr : tacInstructions) {
            if (instr.op() == TacOp.Label) {
                labelAddresses.put(instr.id(), startAddress + instructions.size());
                continue;
            }

            if (!allocatedVariableSpace && totalLocalVariableCount > 0) {
                instructions.add(new TamInstruction.Instruction(
                        TamOpcode.PUSH, null, 0, localBasePointer - 3
                ));
                allocatedVariableSpace = true;
            }

            // Copy parameters (same as subroutines)
            if (!storedArgumentsValues) {
                for (int i = 0; i < params.size(); i++) {
                    int negLBOffset = -argumentSize + i;
                    instructions.add(new TamInstruction.Instruction(
                            TamOpcode.LOAD, TamRegister.LB, 1, negLBOffset
                    ));
                    instructions.add(new TamInstruction.Instruction(
                            TamOpcode.STORE, TamRegister.LB, 1, functionVarOffsets.get(params.get(i))
                    ));
                }
                storedArgumentsValues = true;
            }

            // key diff: Handle Return with value
            if (instr.op() == TacOp.Return) {
                // Load the return value onto stack
                loadParam(instructions, instr.src1());
                instructions.add(new TamInstruction.Instruction(
                        TamOpcode.RETURN, null, 1, argumentSize
                ));
            } else {
                List<TamInstruction.Instruction> generated = generateInstruction(
                        instr,
                        startAddress + instructions.size()
                );
                instructions.addAll(generated);
            }
        }

        currentScope = prevScope;
        inFunction = false;

        return instructions;
    }

    /**
     * Collect all variables and assign offsets
     */
    private void collectVariables(List<TacInstr> tacInstructions) {
        Set<String> variables = new LinkedHashSet<>();
        Set<String> temps = new LinkedHashSet<>();

        for (TacInstr instr : tacInstructions) {
            // Skip labels and control flow
            if (instr.op() == TacOp.Label || instr.op() == TacOp.Goto ||
                    instr.op() == TacOp.GotoIf || instr.op() == TacOp.GotoIfFalse) {
                continue;
            }

            collectParamVariables(instr.dst(), variables, temps);
            collectParamVariables(instr.src1(), variables, temps);
            collectParamVariables(instr.src2(), variables, temps);
        }

        // Assign offsets to variables
        for (String var : variables) {
            variableOffsets.put(var, stackBasePointer);
            int varSize = getVariableSize(var);
            stackBasePointer = stackBasePointer + varSize;
        }

        // Assign offsets to temporaries (each one is size = 1)
        for (String temp : temps) {
            tempOffsets.put(temp, stackBasePointer++);
        }
    }

    private void collectParamVariables(TacParam param, Set<String> variables, Set<String> temps) {
        if (param instanceof TacParam.Variable var) {
            variables.add(var.name());
        } else if (param instanceof TacParam.Temp temp) {
            temps.add("t" + temp.id());
        }
    }

    private int getVariableSize(String varName) {
        Optional<SymbolData> symData = currentScope.lookup(varName);

        Type varType = symData.get().getVarType();

        if (varType instanceof Type.FixedArrayType arrayType) {
            int totalSize = 1;
            for (int dim : arrayType.dimensions()) {
                totalSize *= dim;
            }
            return totalSize;
        } else if (varType instanceof Type.DynamicArrayType) {
            throw new RuntimeException("DynamicArrayType allocation should be in heap.");
        } else if (varType == Type.BaseType.CHARACTER) {
            return 1; // CHARACTER type to store relative address in heap, need 1
        }else {
            return 1;
        }
    }

    /**
     * Generate TAM instructions for a single TAC instruction
     */
    private List<TamInstruction.Instruction> generateInstruction(TacInstr instr, int currentAddress) {
        List<TamInstruction.Instruction> instructions = new ArrayList<>();

        if (inFunction || inSubroutine) {
            currentRegisterType = TamRegister.LB;
        }

        switch (instr.op()) {

            // Arithmetic operations
            case Add:
                // dst = src1 + src2
                loadParam(instructions, instr.src1());
                loadParam(instructions, instr.src2());
                instructions.add(new TamInstruction.Instruction(
                        TamOpcode.CALL, TamRegister.PB, 0, TamPrimitives.add.value
                ));
                storeVariable(instructions, instr.dst());
                break;

            case Sub:
                // dst = src1 - src2
                loadParam(instructions, instr.src1());
                loadParam(instructions, instr.src2());
                instructions.add(new TamInstruction.Instruction(
                        TamOpcode.CALL, TamRegister.PB, 0, TamPrimitives.sub.value
                ));
                storeVariable(instructions, instr.dst());
                break;

            case Mul:
                // dst = src1 * src2
                loadParam(instructions, instr.src1());
                loadParam(instructions, instr.src2());
                instructions.add(new TamInstruction.Instruction(
                        TamOpcode.CALL, TamRegister.PB, 0, TamPrimitives.mult.value
                ));
                storeVariable(instructions, instr.dst());
                break;

            case Div:
                // dst = src1 / src2
                loadParam(instructions, instr.src1());
                loadParam(instructions, instr.src2());
                instructions.add(new TamInstruction.Instruction(
                        TamOpcode.CALL, TamRegister.PB, 0, TamPrimitives.div.value
                ));
                storeVariable(instructions, instr.dst());
                break;

            case Exp:
                // result = 1; for i = 0 to src2-1: result *= src1
                // Allocate space for temp result and temp loop counter
                instructions.add(new TamInstruction.Instruction(
                        TamOpcode.PUSH, null, 0, 2
                ));

                // Load the exponent (src2) to check if it's 0
                loadParam(instructions, instr.src2());

                // Store exponent in a temp location for the loop counter
                int expCounterOffset;
                if (inSubroutine || inFunction) {
                    expCounterOffset = localBasePointer++;
                } else {
                    expCounterOffset = stackBasePointer++;
                }

                instructions.add(new TamInstruction.Instruction(
                        TamOpcode.STORE, currentRegisterType, 1, expCounterOffset
                ));

                // Initialize result to 1
                int resultOffset;
                if (inSubroutine || inFunction) {
                    resultOffset = localBasePointer++;
                } else {
                    resultOffset = stackBasePointer++;
                }

                instructions.add(new TamInstruction.Instruction(
                        TamOpcode.LOADL, null, 0, 1
                ));
                instructions.add(new TamInstruction.Instruction(
                        TamOpcode.STORE, currentRegisterType, 1, resultOffset
                ));

                // Create loop start label
                int loopStartExponential = currentAddress + instructions.size();

                // Check if counter > 0
                instructions.add(new TamInstruction.Instruction(
                        TamOpcode.LOAD, currentRegisterType, 1, expCounterOffset
                ));

                instructions.add(new TamInstruction.Instruction(
                        TamOpcode.LOADL, null, 0, 0
                ));
                instructions.add(new TamInstruction.Instruction(
                        TamOpcode.CALL, TamRegister.PB, 0, TamPrimitives.gt.value
                ));

                // Jump to end if counter <= 0
                int loopEndJumpIndexExponential = currentAddress + instructions.size();
                instructions.add(new TamInstruction.Instruction(
                        TamOpcode.JUMPIF, TamRegister.CB, 0, 0  // Will be patched
                ));

                // Loop body: result = result * src1
                instructions.add(new TamInstruction.Instruction(
                        TamOpcode.LOAD, currentRegisterType, 1, resultOffset
                ));
                loadParam(instructions, instr.src1());
                instructions.add(new TamInstruction.Instruction(
                        TamOpcode.CALL, TamRegister.PB, 0, TamPrimitives.mult.value
                ));
                instructions.add(new TamInstruction.Instruction(
                        TamOpcode.STORE, currentRegisterType, 1, resultOffset
                ));

                // Decrement counter
                instructions.add(new TamInstruction.Instruction(
                        TamOpcode.LOAD, currentRegisterType, 1, expCounterOffset
                ));
                instructions.add(new TamInstruction.Instruction(
                        TamOpcode.LOADL, null, 0, 1
                ));
                instructions.add(new TamInstruction.Instruction(
                        TamOpcode.CALL, TamRegister.PB, 0, TamPrimitives.sub.value
                ));
                instructions.add(new TamInstruction.Instruction(
                        TamOpcode.STORE, currentRegisterType, 1, expCounterOffset
                ));

                // Jump back to loop start
                instructions.add(new TamInstruction.Instruction(
                        TamOpcode.JUMP, TamRegister.CB, 0, loopStartExponential
                ));

                // Loop end - patch the jump instruction
                int loopEndExponential = currentAddress + instructions.size();
                instructions.set(loopEndJumpIndexExponential - currentAddress,
                        new TamInstruction.Instruction(TamOpcode.JUMPIF, TamRegister.CB, 0, loopEndExponential));

                // Load result and store to destination
                instructions.add(new TamInstruction.Instruction(
                        TamOpcode.LOAD, currentRegisterType, 1, resultOffset
                ));
                storeVariable(instructions, instr.dst());
                break;

            // Relational operators
            case Eql, Neq:
                // dst = src1 == src2
                // stack bottom [[ src1 src2 cmpSize, only CALL eq/ne
                Type cmpType = currentScope.lookup(instr.src1().toString()).get().getVarType();
                int cmpSize;

                if (cmpType == Type.BaseType.INTEGER || cmpType == Type.BaseType.LOGICAL) {
                    cmpSize = 1;
                } else if (cmpType instanceof Type.FixedArrayType) {
                    int cmpBaseSize = 1;

                    for (int dim : ((Type.FixedArrayType) cmpType).dimensions()) {
                        cmpBaseSize *= dim;
                    }
                    cmpSize = cmpBaseSize;
                }
                else {
                    throw new RuntimeException("TAM Error: Cannot determine eq compare size for: " + instr.src1());
                }

                loadParam(instructions, instr.src1());
                loadParam(instructions, instr.src2());

                instructions.add(new TamInstruction.Instruction(
                        TamOpcode.LOADL, null, 0, cmpSize
                ));

                if (instr.op() == TacOp.Eql) {
                    instructions.add(new TamInstruction.Instruction(
                            TamOpcode.CALL, TamRegister.PB, 0, TamPrimitives.eq.value
                    ));
                } else {
                    instructions.add(new TamInstruction.Instruction(
                            TamOpcode.CALL, TamRegister.PB, 0, TamPrimitives.ne.value
                    ));
                }

                storeVariable(instructions, instr.dst());
                break;

            case Lss, Grt, Leq, Geq:
                loadParam(instructions, instr.src1());
                loadParam(instructions, instr.src2());

                if (instr.op() == TacOp.Lss) {
                    // dst = src1 < src2
                    instructions.add(new TamInstruction.Instruction(
                            TamOpcode.CALL, TamRegister.PB, 0, TamPrimitives.lt.value
                    ));
                } else if (instr.op() == TacOp.Grt) {
                    // dst = src1 > src2
                    instructions.add(new TamInstruction.Instruction(
                            TamOpcode.CALL, TamRegister.PB, 0, TamPrimitives.gt.value
                    ));
                } else if (instr.op() == TacOp.Leq) {
                    // dst = src1 <= src2
                    instructions.add(new TamInstruction.Instruction(
                            TamOpcode.CALL, TamRegister.PB, 0, TamPrimitives.le.value
                    ));
                } else {
                    // TacOp.Geq
                    // dst = src1 >= src2
                    instructions.add(new TamInstruction.Instruction(
                            TamOpcode.CALL, TamRegister.PB, 0, TamPrimitives.ge.value
                    ));
                }

                storeVariable(instructions, instr.dst());
                break;

            case And, Or:
                loadParam(instructions, instr.src1());
                loadParam(instructions, instr.src2());

                if (instr.op() == TacOp.And) {
                    instructions.add(new TamInstruction.Instruction(
                            TamOpcode.CALL, TamRegister.PB, 0, TamPrimitives.and.value
                    ));
                } else {
                    instructions.add(new TamInstruction.Instruction(
                            TamOpcode.CALL, TamRegister.PB, 0, TamPrimitives.or.value
                    ));
                }
                storeVariable(instructions, instr.dst());

                break;

            // TODO: Concat is possible

            case Assign:
                // dst = src1

                if (instr.dst() instanceof TacParam.Variable var && instr.src1() instanceof TacParam.StringValue strVal) {

                    Optional<SymbolData> symData = currentScope.lookup(var.name());
                    if (symData.isPresent() && symData.get().getVarType() == Type.BaseType.CHARACTER) {
                        // Allocate string on heap
                        String str = strVal.toString().substring(1, strVal.toString().length() - 1);

                        // Call NEW primitive to allocate heap space
                        instructions.add(new TamInstruction.Instruction(
                                TamOpcode.LOADL, null, 0, str.length() + 1  // +1 for null terminator
                        ));
                        instructions.add(new TamInstruction.Instruction(
                                TamOpcode.CALL, TamRegister.PB, 0, TamPrimitives.NEW.value
                        ));

                        // NEW returns heap address on stack top, save it to the relevant variable offset
                        int heapAddress = heapPointer - str.length();

                        if (inSubroutine) {
                            instructions.add(new TamInstruction.Instruction(
                                    TamOpcode.STORE, TamRegister.LB, 1, subroutineVarOffsets.get(instr.dst().toString())
                            ));
                        } else if  (inFunction) {
                            instructions.add(new TamInstruction.Instruction(
                                    TamOpcode.STORE, TamRegister.LB, 1, functionVarOffsets.get(instr.dst().toString())
                            ));
                        } else {
                            instructions.add(new TamInstruction.Instruction(
                                    TamOpcode.STORE, TamRegister.SB, 1, variableOffsets.get(instr.dst().toString())
                            ));
                        }

                        // Write characters to heap
                        for (int i = 0; i < str.length(); i++) {
                            instructions.add(new TamInstruction.Instruction(
                                    TamOpcode.LOADL, null, 0, str.charAt(i)
                            ));
                            instructions.add(new TamInstruction.Instruction(
                                    TamOpcode.LOADL, null, 0, heapAddress + i
                            ));
                            // STOREI to write character to heap
                            instructions.add(new TamInstruction.Instruction(
                                    TamOpcode.STOREI, null, 1, 0
                            ));
                        }

                        // Write null terminator '\0'
                        instructions.add(new TamInstruction.Instruction(
                                TamOpcode.LOADL, null, 0, 0
                        ));
                        instructions.add(new TamInstruction.Instruction(
                                TamOpcode.LOADL, null, 0, heapAddress + str.length()
                        ));
                        instructions.add(new TamInstruction.Instruction(
                                TamOpcode.STOREI, null, 1, 0
                        ));

                        heapPointer = heapPointer - str.length() - 1;
                        break;
                    }
                } else {
                    // Normal assignment
                    loadParam(instructions, instr.src1());
                    storeVariable(instructions, instr.dst());
                }

                break;

            case Goto:
                // Unconditional jump
                if (instr.dst() instanceof TacParam.Label label) {
                    int jumpInstrIndex = currentAddress + instructions.size();
                    pendingLabels.add(new PendingLabel(jumpInstrIndex, label.target()));
                    instructions.add(new TamInstruction.Instruction(
                            TamOpcode.JUMP, TamRegister.CB, 0, 0 // d=0 resolved later
                    ));
                }
                break;

            // not sure if needed
//            case GotoIf:
//                break;

            case GotoIfFalse:
                // if !src1 goto dst (jump if condition is 0/false)
                loadParam(instructions, instr.src1());

                if (instr.dst() instanceof TacParam.Label label) {
                    int jumpInstrIndex = currentAddress + instructions.size();
                    pendingLabels.add(new PendingLabel(jumpInstrIndex, label.target()));
                    instructions.add(new TamInstruction.Instruction(
                            TamOpcode.JUMPIF, TamRegister.CB, 0, 0 // n=0 for jump if false ; d=0 resolved later
                    ));
                }
                break;

            case Label:
                // Labels don't generate code, just mark positions
                break;

            // function/subroutine calls here
            // return and returnVoid are handled specially, since they need to know argSize

            case Call:
                // Function call (has return value)
                if (instr.src1() instanceof TacParam.Label label) {
                    int callInstrIndex = currentAddress + instructions.size();
                    pendingLabels.add(new PendingLabel(callInstrIndex, label.target()));
                    instructions.add(new TamInstruction.Instruction(
                            TamOpcode.CALL, TamRegister.CB, 0, 0  // d will be resolved later
                    ));

                    // After CALL returns, result is on top of stack
                    // Store it to the destination
                    storeVariable(instructions, instr.dst());
                }
                break;

            case CallVoid:
                // Subroutine call (no return value)
                if (instr.src1() instanceof TacParam.Label label) {
                    int callInstrIndex = currentAddress + instructions.size();
                    pendingLabels.add(new PendingLabel(callInstrIndex, label.target()));
                    instructions.add(new TamInstruction.Instruction(
                            TamOpcode.CALL, TamRegister.CB, 0, 0  // d will be resolved later
                    ));
                }
                break;

            case PushParam:
                // Push parameter onto stack for upcoming call
                loadParam(instructions, instr.src1());
                // Value stays on stack for the CALL instruction
                break;

            // doesn't work properly when it goes out of bounds
            case ArrayStore:
                // dst[src1] = src2
                Type arrayType = currentScope.lookup(instr.dst().toString()).get().getVarType();
                if (arrayType instanceof Type.FixedArrayType var) {

                    int baseOffset;
                    if (inSubroutine) {
                        baseOffset = subroutineVarOffsets.getOrDefault(instr.dst().toString(), 0) + stackBasePointer;
                    } else if (inFunction){
                        baseOffset = functionVarOffsets.getOrDefault(instr.dst().toString(), 0) + stackBasePointer;
                    } else {
                        baseOffset = variableOffsets.getOrDefault(instr.dst().toString(), 0);
                    }

                    // Load value to store
                    loadParam(instructions, instr.src2());

                    // Load offset (index)
                    loadParam(instructions, instr.src1());

                    // actual address = base + offset
                    instructions.add(new TamInstruction.Instruction(
                            TamOpcode.LOADL, null, 0, baseOffset
                    ));
                    instructions.add(new TamInstruction.Instruction(
                            TamOpcode.CALL, TamRegister.PB, 0, TamPrimitives.add.value
                    ));

                    // After calling add, relative address should be top of stack
                    // Pop 1 word (address to store at stack)
                    // then pop another word (n-sized), for the actual value to be stored
                    instructions.add(new TamInstruction.Instruction(
                            TamOpcode.STOREI, null, 1, 0
                    ));
                } else {
                    throw new RuntimeException("Probably performing arrayStore on dynamic array, implementation incomplete");
                }
                break;

            // doesn't work properly when it goes out of bounds
            case ArrayLoad:
                // dst = src1[src2]
                // Load value from array at given index

                if (instr.src1() instanceof TacParam.Variable var) {
                    int baseOffset;
                    if (inSubroutine) {
                        baseOffset = subroutineVarOffsets.getOrDefault(var.name(), 0) + stackBasePointer;
                    } else if (inFunction){
                        baseOffset = functionVarOffsets.getOrDefault(var.name(), 0) + stackBasePointer;
                    } else {
                        baseOffset = variableOffsets.getOrDefault(var.name(), 0);
                    }

                    // Load offset (index)
                    loadParam(instructions, instr.src2());

                    // Compute actual address: base + offset
                    instructions.add(new TamInstruction.Instruction(
                            TamOpcode.LOADL, null, 0, baseOffset
                    ));
                    instructions.add(new TamInstruction.Instruction(
                            TamOpcode.CALL, TamRegister.PB, 0, TamPrimitives.add.value
                    ));

                    // LOADI of size 1
                    instructions.add(new TamInstruction.Instruction(
                            TamOpcode.LOADI, null, 1, 0
                    ));

                    // Store result to destination
                    storeVariable(instructions, instr.dst());
                }
                break;

            case WriteCharacter:
                // Write src1
                // if src1 is type string, call TamPrimitives.put repeatedly
                if (instr.src1() instanceof TacParam.StringValue str) {
                    String strNew = str.toString().substring(1 ,str.toString().length() - 1); // Removes the "" at start and back
                    for (int i = 0; i < strNew.length(); i++) {
                        instructions.add(new TamInstruction.Instruction(
                                TamOpcode.LOADL, null, 0, strNew.charAt(i)
                        ));
                        instructions.add(new TamInstruction.Instruction(
                                TamOpcode.CALL, TamRegister.PB, 0, TamPrimitives.put.value
                        ));
                    }
                    instructions.add(new TamInstruction.Instruction(
                            TamOpcode.CALL, TamRegister.PB, 0, TamPrimitives.puteol.value
                    ));
                    // if src1 is variable, load from heap and print until null terminator
                } else if (instr.src1() instanceof TacParam.Variable var) {

                    int varOffset;
                    int loopCounterOffset;
                    if (inSubroutine) {
                        varOffset = subroutineVarOffsets.get(var.name());
                        loopCounterOffset = localBasePointer++;
                    } else if (inFunction) {
                        varOffset = functionVarOffsets.get(var.name());
                        loopCounterOffset = localBasePointer++;
                    } else {
                        varOffset = variableOffsets.get(var.name());
                        loopCounterOffset = stackBasePointer++;
                    }

                    // Allocate 1 space on stack for the temp var
                    instructions.add(new TamInstruction.Instruction(
                            TamOpcode.PUSH, null, 0, 1
                    ));

                    // Initialize loop counter to 0
                    instructions.add(new TamInstruction.Instruction(
                            TamOpcode.LOADL, null, 0, 0
                    ));
                    instructions.add(new TamInstruction.Instruction(
                            TamOpcode.STORE, currentRegisterType, 1, loopCounterOffset
                    ));

                    // Loop start label
                    int loopStartWriteChar = currentAddress + instructions.size();

                    // Load base address of string variable
                    instructions.add(new TamInstruction.Instruction(
                            TamOpcode.LOAD, currentRegisterType, 1, varOffset
                    ));

                    // Load loop counter (current index)
                    instructions.add(new TamInstruction.Instruction(
                            TamOpcode.LOAD, currentRegisterType, 1, loopCounterOffset
                    ));

                    // Add index to base address
                    instructions.add(new TamInstruction.Instruction(
                            TamOpcode.CALL, TamRegister.PB, 0, TamPrimitives.add.value
                    ));

                    // Load character from heap at (base + index)
                    instructions.add(new TamInstruction.Instruction(
                            TamOpcode.LOADI, null, 1, 0
                    ));

                    // Check if character is null terminator (0)
                    instructions.add(new TamInstruction.Instruction(
                            TamOpcode.LOADL, null, 0, 0
                    ));
                    // Compare size
                    instructions.add(new TamInstruction.Instruction(
                            TamOpcode.LOADL, null, 0, 1
                    ));
                    instructions.add(new TamInstruction.Instruction(
                            TamOpcode.CALL, TamRegister.PB, 0, TamPrimitives.eq.value
                    ));

                    // If equal to 0, exit loop
                    int loopEndJumpIndexWriteChar = currentAddress + instructions.size();
                    instructions.add(new TamInstruction.Instruction(
                            TamOpcode.JUMPIF, TamRegister.CB, 1, 0  // Jump if true (character == 0)
                    ));

                    // If not null, print the character
                    // Reload the character (we consumed it in the comparison)
                    instructions.add(new TamInstruction.Instruction(
                            TamOpcode.LOAD, currentRegisterType, 1, varOffset
                    ));
                    instructions.add(new TamInstruction.Instruction(
                            TamOpcode.LOAD, currentRegisterType, 1, loopCounterOffset
                    ));
                    instructions.add(new TamInstruction.Instruction(
                            TamOpcode.CALL, TamRegister.PB, 0, TamPrimitives.add.value
                    ));
                    instructions.add(new TamInstruction.Instruction(
                            TamOpcode.LOADI, null, 1, 0
                    ));

                    // Print character
                    instructions.add(new TamInstruction.Instruction(
                            TamOpcode.CALL, TamRegister.PB, 0, TamPrimitives.put.value
                    ));

                    // Increment loop counter
                    instructions.add(new TamInstruction.Instruction(
                            TamOpcode.LOAD, currentRegisterType, 1, loopCounterOffset
                    ));
                    instructions.add(new TamInstruction.Instruction(
                            TamOpcode.LOADL, null, 0, 1
                    ));
                    instructions.add(new TamInstruction.Instruction(
                            TamOpcode.CALL, TamRegister.PB, 0, TamPrimitives.add.value
                    ));
                    instructions.add(new TamInstruction.Instruction(
                            TamOpcode.STORE, currentRegisterType, 1, loopCounterOffset
                    ));

                    // Jump back to loop start
                    instructions.add(new TamInstruction.Instruction(
                            TamOpcode.JUMP, TamRegister.CB, 0, loopStartWriteChar
                    ));

                    // Loop end - patch the conditional jump
                    int loopEndWriteChar = currentAddress + instructions.size();
                    instructions.set(loopEndJumpIndexWriteChar - currentAddress,
                            new TamInstruction.Instruction(TamOpcode.JUMPIF, TamRegister.CB, 1, loopEndWriteChar));

                    // Print newline after the string
                    instructions.add(new TamInstruction.Instruction(
                            TamOpcode.CALL, TamRegister.PB, 0, TamPrimitives.puteol.value
                    ));
                } else {
                    throw new RuntimeException("WriteCharacter cannot be to: " + instr.src1());
                }
                break;

            case ReadInteger:
                // Read into dst
                loadAddress(instructions, instr.dst());
                instructions.add(new TamInstruction.Instruction(
                        TamOpcode.CALL, TamRegister.PB, 0, TamPrimitives.getint.value
                ));
                break;

            case WriteInteger:
                // Write src1
                loadParam(instructions, instr.src1());
                instructions.add(new TamInstruction.Instruction(
                        TamOpcode.CALL, TamRegister.PB, 0, TamPrimitives.putint.value
                ));
                instructions.add(new TamInstruction.Instruction(
                        TamOpcode.CALL, TamRegister.PB, 0, TamPrimitives.puteol.value
                ));
                break;

            default:
                throw new UnsupportedOperationException(instr.op() + " not implemented.");
        }

        return instructions;
    }

    /**
     * Load a parameter onto the stack
     */

    private void loadParam(List<TamInstruction.Instruction> instructions, TacParam param) {
        if (param instanceof TacParam.Value val) {
            // Literal values are loaded the same way regardless of context
            instructions.add(new TamInstruction.Instruction(
                    TamOpcode.LOADL, null, 0, val.value()
            ));
        } else if (param instanceof TacParam.Temp temp) {
            String tempName = "t" + temp.id();

            int offset;
            if (inSubroutine) {
                offset = subroutineTempOffsets.getOrDefault(tempName, 0);
            } else if (inFunction){
                offset = functionTempOffsets.getOrDefault(tempName, 0);
            } else {
                offset = tempOffsets.getOrDefault(tempName, 0);
            }

            instructions.add(new TamInstruction.Instruction(
                    TamOpcode.LOAD, currentRegisterType, 1, offset
            ));
        } else if (param instanceof TacParam.BoolValue bool) {
            // Boolean values loaded the same way regardless of context
            instructions.add(new TamInstruction.Instruction(
                    TamOpcode.LOADL, null, 0, bool.value() ? 1 : 0
            ));
        } else if (param instanceof TacParam.Variable var) {
            // Variables: choose offset map based on context
            int offset;
            if (inSubroutine) {
                offset = subroutineVarOffsets.getOrDefault(var.name(), 0);
            } else if (inFunction){
                offset = functionVarOffsets.getOrDefault(var.name(), 0);
            } else {
                offset = variableOffsets.getOrDefault(var.name(), 0);
            }

            Type varType = currentScope.lookup(var.name()).get().getVarType();

            if (varType == Type.BaseType.INTEGER || varType == Type.BaseType.LOGICAL || varType == Type.BaseType.CHARACTER) {
                instructions.add(new TamInstruction.Instruction(
                        TamOpcode.LOAD, currentRegisterType, 1, offset
                ));
            } else if (varType instanceof Type.FixedArrayType) {
                int baseDim = 1;
                for (int dim: ((Type.FixedArrayType) varType).dimensions()) {
                    baseDim *= dim;
                }
                instructions.add(new TamInstruction.Instruction(
                        TamOpcode.LOAD, currentRegisterType, baseDim, offset
                ));
            } else {
                throw new RuntimeException("TAM Error: Unable to determine load size for variable: " + var.name());
            }
        } else {
            throw new RuntimeException(param + " not implemented.");
        }
    }

    /**
     * Load address of a variable
     */

    private void loadAddress(List<TamInstruction.Instruction> instructions, TacParam param) {
        if (param instanceof TacParam.Variable var) {
            // Choose offset map based on context
            int offset;
            if (inSubroutine) {
                offset = subroutineVarOffsets.getOrDefault(var.name(), 0);
            } else if (inFunction){
                offset = functionVarOffsets.getOrDefault(var.name(), 0);
            } else {
                offset = variableOffsets.getOrDefault(var.name(), 0);
            }

            instructions.add(new TamInstruction.Instruction(
                    TamOpcode.LOADA, currentRegisterType, 0, offset
            ));
        } else if (param instanceof TacParam.Temp temp) {
            String tempName = "t" + temp.id();
            int offset;
            if (inSubroutine) {
                offset = subroutineTempOffsets.getOrDefault(tempName, 0);
            } else if (inFunction){
                offset = functionTempOffsets.getOrDefault(tempName, 0);
            } else {
                offset = tempOffsets.getOrDefault(tempName, 0);
            }

            instructions.add(new TamInstruction.Instruction(
                    TamOpcode.LOADA, currentRegisterType, 0, offset
            ));
        }
    }

    /**
     * Store top of stack to a variable
     */

    private void storeVariable(List<TamInstruction.Instruction> instructions, TacParam param) {
        if (param instanceof TacParam.Variable var) {
            // Choose offset map based on context
            int offset;
            if (inSubroutine) {
                offset = subroutineVarOffsets.getOrDefault(var.name(), 0);
            } else if (inFunction){
                offset = functionVarOffsets.getOrDefault(var.name(), 0);
            } else {
                offset = variableOffsets.getOrDefault(var.name(), 0);
            }

            instructions.add(new TamInstruction.Instruction(
                    TamOpcode.STORE, currentRegisterType, 1, offset
            ));
        } else if (param instanceof TacParam.Temp temp) {
            String tempName = "t" + temp.id();
            int offset;
            if (inSubroutine) {
                offset = subroutineTempOffsets.getOrDefault(tempName, 0);
            } else if (inFunction){
                offset = functionTempOffsets.getOrDefault(tempName, 0);
            } else {
                offset = tempOffsets.getOrDefault(tempName, 0);
            }

            instructions.add(new TamInstruction.Instruction(
                    TamOpcode.STORE, currentRegisterType, 1, offset
            ));
        }
    }

    /**
     * Resolve all pending label references
     */
    private void resolveLabels(TamInstruction.InstructionList instructions) {
        for (PendingLabel pending : pendingLabels) {
            Integer targetAddress = labelAddresses.get(pending.labelId);
            if (targetAddress != null) {
                // Update the instruction with the correct address
                TamInstruction.Instruction oldInstr = instructions.get(pending.instructionIndex);
                TamInstruction.Instruction newInstr = new TamInstruction.Instruction(
                        oldInstr.op(), oldInstr.r(), oldInstr.n(), targetAddress
                );
                instructions.set(pending.instructionIndex, newInstr);
            } else {
                throw new RuntimeException("TAM Error: Unresolved label " + pending.labelId.toString().substring(0, 8));
            }
        }
    }

    /**
     * Pretty print TAM instructions for debugging
     */

    public static void printTam(TamInstruction.InstructionList instructions) {
        System.out.println("=== Generated TAM Code ===");

        Map<Integer, String> addressToLabel = new HashMap<>();
        int labelCounter = 0;

        // First pass: identify jump targets AND call targets
        Set<Integer> jumpTargets = new HashSet<>();
        for (TamInstruction.Instruction instr : instructions) {
            if (instr.op() == TamOpcode.JUMP || instr.op() == TamOpcode.JUMPI ||
                    instr.op() == TamOpcode.JUMPIF) {
                jumpTargets.add(instr.d());
            }
            // Add CALL targets too (for subroutine/function calls)
            if (instr.op() == TamOpcode.CALL && instr.r() == TamRegister.CB) {
                jumpTargets.add(instr.d());
            }
        }

        // Assign label names to jump targets
        for (Integer target : jumpTargets) {
            addressToLabel.put(target, "l" + labelCounter++);
        }

        // Second pass: print with labels
        int address = 0;
        for (TamInstruction.Instruction instr : instructions) {
            // Print label if this address is a jump target
            if (addressToLabel.containsKey(address)) {
                System.out.println(addressToLabel.get(address) + ":");
            }

            System.out.print("  ");

            // Format instruction based on opcode
            switch (instr.op()) {
                case PUSH:
                    System.out.println("PUSH " + instr.d());
                    break;

                case LOAD:
                    System.out.println(String.format("LOAD(%d) %d[%s]", instr.n(), instr.d(), instr.r()));
                    break;

                case LOADA:
                    System.out.println(String.format("LOADA %d[%s]", instr.d(), instr.r()));
                    break;

                case LOADI:
                    System.out.println(String.format("LOADI(%d)", instr.n()));
                    break;

                case LOADL:
                    System.out.println(String.format("LOADL %d", instr.d()));
                    break;

                case STORE:
                    System.out.println(String.format("STORE(%d) %d[%s]", instr.n(), instr.d(), instr.r()));
                    break;

                case STOREI:
                    System.out.println(String.format("STOREI(%d)", instr.n()));
                    break;

                case CALL:
                    if (instr.r() == TamRegister.PB) {
                        // Primitive call
                        String primName = String.valueOf(instr.d());
                        for (TamPrimitives prim : TamPrimitives.values()) {
                            if (prim.value == instr.d()) {
                                primName = prim.name();
                                break;
                            }
                        }
                        System.out.println(String.format("CALL %s", primName.toLowerCase()));
                    } else if (instr.r() == TamRegister.CB) {
                        // Subroutine/function call - show as label
                        if (addressToLabel.containsKey(instr.d())) {
                            System.out.println("CALL " + addressToLabel.get(instr.d()));
                        } else {
                            System.out.println(String.format("CALL %d[CB]", instr.d()));
                        }
                    } else {
                        System.out.println(String.format("CALL %d[%s]", instr.d(), instr.r()));
                    }
                    break;

                case CALLI:
                    System.out.println("CALLI");
                    break;

                case RETURN:
                    System.out.println(String.format("RETURN(%d) %d", instr.n(), instr.d()));
                    break;

                case JUMP:
                    if (addressToLabel.containsKey(instr.d())) {
                        System.out.println("JUMP " + addressToLabel.get(instr.d()));
                    } else {
                        System.out.println(String.format("JUMP %d[%s]", instr.d(), instr.r()));
                    }
                    break;

                case JUMPI:
                    System.out.println("JUMPI");
                    break;

                case JUMPIF:
                    if (addressToLabel.containsKey(instr.d())) {
                        System.out.println(String.format("JUMPIF(%d) %s", instr.n(), addressToLabel.get(instr.d())));
                    } else {
                        System.out.println(String.format("JUMPIF(%d) %d[%s]", instr.n(), instr.d(), instr.r()));
                    }
                    break;

                case HALT:
                    System.out.println("HALT");
                    break;

                default:
                    System.out.println(String.format("%s %s %d %d", instr.op(), instr.r(), instr.n(), instr.d()));
            }

            address++;
        }
    }

}