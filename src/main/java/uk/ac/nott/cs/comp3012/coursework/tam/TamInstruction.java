package uk.ac.nott.cs.comp3012.coursework.tam;

import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.Collection;

public interface TamInstruction {

    /**
     * Represent this instruction as an array of bytes in TAM bytecode format.
     *
     * @return the bytes
     */
    byte[] toByteArray();


    /**
     * Gemini generated for clearance while development
     * Represents a single 32-bit instruction for the Triangle Abstract Machine (TAM).
     * <p>
     * The instruction is composed of four fields:
     * <ul>
     *   <li><b>Op (4 bits):</b> The operation code, represented by {@link OpCode}.</li>
     *   <li><b>R (4 bits):</b> A register specifier, represented by {@link Register}.</li>
     *   <li><b>N (8 bits):</b> The size of the data to be processed.</li>
     *   <li><b>D (16 bits):</b> A signed value used for various purposes, such as an address offset or a constant.</li>
     * </ul>
     * The complete instruction word can be constructed from these parts.
     */
    record Instruction(TamOpcode op, TamRegister r, int n, int d) implements TamInstruction {

        public byte[] toByteArray() {
            int regValue = (r != null) ? r.value : 0; // new
            return new byte[]{(byte) ((op.value << 4) | regValue), (byte) n,
                    (byte) ((d & 0xff00) >>> 8), (byte) (d & 0xff),};
        }
    }

    final class InstructionList extends ArrayList<Instruction> implements TamInstruction {

        public InstructionList() {
            super();
        }

        public InstructionList(Collection<Instruction> elems) {
            super(elems);
        }

        @Override
        public byte[] toByteArray() {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            for (Instruction instr : this) {
                out.writeBytes(instr.toByteArray());
            }
            return out.toByteArray();
        }
    }

}
