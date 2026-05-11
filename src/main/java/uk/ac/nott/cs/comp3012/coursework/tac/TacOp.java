package uk.ac.nott.cs.comp3012.coursework.tac;

public enum TacOp {
    // ============= Arithmetic Operations =============
    Add,        // dst = src1 + src2
    Sub,        // dst = src1 - src2
    Mul,        // dst = src1 * src2
    Div,        // dst = src1 / src2
    Exp,        // dst = src1 ** src2 (power/exponentiation)

    // ============= Relational Operations =============
    Eql,        // dst = src1 == src2
    Neq,        // dst = src1 != src2
    Lss,        // dst = src1 < src2
    Grt,        // dst = src1 > src2
    Leq,        // dst = src1 <= src2
    Geq,        // dst = src1 >= src2

    // ============= Logical Operations =============
    And,        // dst = src1 && src2
    Or,         // dst = src1 || src2

    // ============= String Operations =============
    Concat,     // dst = src1 // src2 (string concatenation)

    // ============= Assignment & Movement =============
    Assign,     // dst = src1

    // ============= Control Flow =============
    Goto,       // goto label (unconditional jump)
    GotoIf,     // if src1 then goto label (conditional jump)
    GotoIfFalse,// if !src1 then goto label (negated conditional)
    Label,      // label to jump to

    // ============= Function/Subroutine Calls =============
    PushParam,  // push src1 onto call stack
    Call,       // dst = call src1 (function call)
    CallVoid,   // call src1 (subroutine call, no return)
    Return,     // return src1
    ReturnVoid, // return (no value)

    // ============= Array Operations =============
    ArrayLoad,  // dst = src1[src2]
    ArrayStore, // src1[src2] = dst
    ArrayAddr,  // dst = &src1[src2] (compute array element address) // FIX: Not sure

    // TODO: Check implementation of derived types
    // ============= Derived Type (Field) Operations =============
    FieldAccess,// dst = src1.field (load field)
    FieldStore, // src1.field = src2 (store to field)
    FieldAddr,  // dst = &src1.field (get field address)

    // ============= Memory Management =============
    Alloc,      // allocate(dst, src1) - allocate src1 bytes for dst
    Dealloc,    // deallocate(src1) - free memory pointed by src1

    // ============= I/O Operations =============
    ReadCharacter,       // read into dst
    WriteCharacter,      // write src1 to output
    ReadInteger,
    WriteInteger,

}