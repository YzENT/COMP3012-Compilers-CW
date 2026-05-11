package uk.ac.nott.cs.comp3012.coursework.tam;

public enum TamPrimitives {
    id(1),
    not(2),
    and(3),
    or(4),
    succ(5),
    pred(6),
    neg(7),
    add(8),
    sub(9),
    mult(10),
    div(11),
    mod(12),
    lt(13),
    le(14),
    ge(15),
    gt(16),
    eq(17),
    ne(18),
    eol(19),
    eof(20),
    get(21),
    put(22),
    geteol(23),
    puteol(24),
    getint(25),
    putint(26),
    NEW(27),
    dispose(28);

    public final int value;

    TamPrimitives(int value) {
        this.value = value;
    }
}
