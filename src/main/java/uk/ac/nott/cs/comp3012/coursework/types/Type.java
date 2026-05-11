package uk.ac.nott.cs.comp3012.coursework.types;

import java.util.List;

public sealed interface Type {

    enum BaseType implements Type {
        INTEGER,    // integer type
        REAL,       // real (floating point) type
        CHARACTER,  // character type
        LOGICAL     // logical (boolean) type
    }

    // integer(5,5) == ArrayType(INTEGER, [5, 5])
    record FixedArrayType(Type baseType, List<Integer> dimensions) implements Type {}

    // integer pointer == PointerType(INTEGER)
    record PointerType(Type targetType) implements Type {}

    // integer(*,*) pointer == ArrayPointerType(INTEGER, 2)
    record DynamicArrayType(Type baseType, int rank) implements Type {}

    /**
     * Custom derived type defined by the user.
     * For example: type(Point) where Point is a user-defined type
     */
    record DerivedType(String typeName) implements Type {}

    // Callables
    record SubroutineType(List<Type> parameterTypes) implements Type {}
    record FunctionType(List<Type> parameterTypes, Type returnType) implements Type {}

}
