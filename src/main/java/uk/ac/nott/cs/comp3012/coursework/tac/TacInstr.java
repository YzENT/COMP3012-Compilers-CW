package uk.ac.nott.cs.comp3012.coursework.tac;

import java.util.UUID;

/**
 * Represents a single three-address code instruction.
 * <p>
 * Use the constructor without an id parameter to automatically generate a unique ID for this node.
 * It is really quite wasteful to use a full UUID for each node, but it was simple to implement.
 * You are most welcome to change or improve this if you wish.
 *
 * @param id   unique ID of this instruction
 * @param op   operator
 * @param dst  destination
 * @param src1 first argument
 * @param src2 second argument (may be {@code null})
 */
public record TacInstr(UUID id, TacOp op, TacParam dst, TacParam src1, TacParam src2) {

    public TacInstr(TacOp op, TacParam dst, TacParam src1, TacParam src2) {
        this(UUID.randomUUID(), op, dst, src1, src2);
    }
}
