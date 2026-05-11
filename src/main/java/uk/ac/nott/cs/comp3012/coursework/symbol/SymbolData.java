package uk.ac.nott.cs.comp3012.coursework.symbol;

import uk.ac.nott.cs.comp3012.coursework.ast.Ast;
import uk.ac.nott.cs.comp3012.coursework.types.Type;
import java.util.*;

public class SymbolData {

    private final String varName;
    private final Type varType;

    // For functions
    private final List<String> functionParameters;
    private final String functionReturnParameter;

    // For derived types
    private final Map<String, SymbolData> fields;

    private SymbolData(String varName,
                       Type varType,
                       List<String> functionParameters,
                       String functionReturnParameter,
                       Map<String, SymbolData> fields
                    ) {
        this.varName = varName;
        this.varType = varType;
        this.functionParameters = functionParameters != null ? new ArrayList<>(functionParameters) : null;
        this.functionReturnParameter = functionReturnParameter != null ? functionReturnParameter : null;
        this.fields = fields != null ? new HashMap<>(fields) : null;
    }

    public static SymbolData createVariable(String name, Type varType) {
        return new SymbolData(name, varType, null, null, null);
    }

    // varType == FunctionType
    public static SymbolData createFunction(String name, Type varType,
                                            List<String> functionParameters,
                                            String functionReturnParameter) {
        return new SymbolData(name, varType,
                functionParameters, functionReturnParameter, null);
    }

    // varType == VoidType
    public static SymbolData createSubroutine(String name, Type varType, List<String> functionParameters) {
        return new SymbolData(name, varType,
                functionParameters, null, null);
    }

    public static SymbolData createDerivedType(String name, Map<String, SymbolData> fields) {
        Type derivedType = new Type.DerivedType(name);
        return new SymbolData(name, derivedType, null, null, fields);
    }

    // Getters
    public String getVarName() {
        return varName;
    }

    public Type getVarType() {
        return varType;
    }

    public List<String> getFunctionParameters() {
        return functionParameters != null ? new ArrayList<>(functionParameters) : null;
    }

    public String getFunctionReturnParameter() {
        return functionReturnParameter;
    }

    public Map<String, SymbolData> getFields() {
        return fields != null ? new HashMap<>(fields) : null;
    }

    public int getArrayDimensionSize() {
        if (varType instanceof Type.FixedArrayType arrayData) {
            return arrayData.dimensions().size();
        }
        if (varType instanceof Type.DynamicArrayType dynArray) {
            return dynArray.rank();
        }
        throw new IllegalStateException( varName + " not an array type");
    }

    // Utility methods
    public boolean isVariable() {
        return varType instanceof Type.BaseType;
    }

    public boolean isFixedArray() {
        return varType instanceof Type.FixedArrayType;
    }

    public boolean isDynamicArray() {
        return varType instanceof Type.DynamicArrayType;
    }

    public boolean isPointer() {
        return varType instanceof Type.PointerType || varType instanceof Type.DynamicArrayType;
    }

    public boolean isFunction() {
        return varType instanceof Type.FunctionType;
    }

    public boolean isSubroutine() {
        return varType instanceof Type.SubroutineType;
    }

    public boolean isCallable() {
        return varType instanceof Type.FunctionType || varType instanceof Type.SubroutineType;
    }

    public boolean isDerivedType() {
        return varType instanceof Type.DerivedType;
    }

}