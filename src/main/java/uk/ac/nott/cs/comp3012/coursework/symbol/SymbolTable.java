package uk.ac.nott.cs.comp3012.coursework.symbol;

import uk.ac.nott.cs.comp3012.coursework.ast.Ast;
import uk.ac.nott.cs.comp3012.coursework.types.Type;
import java.util.*;

public class SymbolTable {

    private final String scopeName;
    private final SymbolTable parent;
    private final Map<String, SymbolData> symbols;

    public SymbolTable() {
        this.scopeName = "global";
        this.parent = null;
        this.symbols = new HashMap<>();
    }

    public SymbolTable(SymbolTable parent) {
        this.scopeName = "nested";
        this.parent = parent;
        this.symbols = new HashMap<>();
    }

    public void define(String name, SymbolData data) {
        symbols.put(name, data);
    }

    public Optional<SymbolData> lookup(String name) {
        // First check current scope
        if (symbols.containsKey(name)) {
            return Optional.of(symbols.get(name));
        }

        // If not found and we have a parent scope, search there
        if (parent != null) {
            return parent.lookup(name);
        }

        // Symbol not found in any scope
        return Optional.empty();
    }

    public boolean hasBeenDefined(String name) {
        return lookup(name).isPresent();
    }

    public SymbolTable getParent() {
        return parent;
    }

    public Map<String, SymbolData> getSymbols() {
        return new HashMap<>(symbols);
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("SymbolTable{scope='").append(scopeName).append("'");
        if (!symbols.isEmpty()) {
            sb.append(", symbols=").append(symbols.keySet());
        }
        sb.append("}");
        return sb.toString();
    }
}