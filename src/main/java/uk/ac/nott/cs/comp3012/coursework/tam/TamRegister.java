package uk.ac.nott.cs.comp3012.coursework.tam;

public enum TamRegister {
    CB(0),
    CT(1),
    PB(2),
    PT(3),
    SB(4),
    ST(5),
    HB(6),
    HT(7),
    LB(8),
    L1(9),
    L2(10),
    L3(11),
    L4(12),
    L5(13),
    L6(14),
    CP(15);

    public final int value;

    TamRegister(int value) {
        this.value = value;
    }
}
