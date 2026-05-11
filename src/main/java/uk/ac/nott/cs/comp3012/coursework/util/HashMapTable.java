package uk.ac.nott.cs.comp3012.coursework.util;

import java.util.HashMap;
import java.util.Map;

/**
 * A table implementation using {@link HashMap}s to represent rows and columns.
 */
public class HashMapTable<K1, K2, V> implements Table<K1, K2, V> {

    private final Map<K1, Map<K2, V>> tableCells = new HashMap<>();
    private int size = 0;

    @Override
    public int size() {
        return size;
    }

    @Override
    public V put(K1 row, K2 col, V value) {
        if (!tableCells.containsKey(row)) {
            tableCells.put(row, new HashMap<>());
        }
        Map<K2, V> rowMap = tableCells.get(row);

        V oldValue = rowMap.put(col, value);
        if (oldValue == null) {
            size++;
        }
        return oldValue;
    }

    @Override
    public V get(K1 row, K2 col) {
        if (!tableCells.containsKey(row)) {
            return null;
        }
        return tableCells.get(row).get(col);
    }
}
