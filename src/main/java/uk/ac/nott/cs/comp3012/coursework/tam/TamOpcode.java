package uk.ac.nott.cs.comp3012.coursework.tam;

public enum TamOpcode {
    LOAD(0),
    LOADA(1),
    LOADI(2),
    LOADL(3),
    STORE(4),
    STOREI(5),
    CALL(6),
    CALLI(7),
    RETURN(8),
    PUSH(10),
    POP(11),
    JUMP(12),
    JUMPI(13),
    JUMPIF(14),
    HALT(15);

    public final int value;

    TamOpcode(int value) {
        this.value = value;
    }
}
