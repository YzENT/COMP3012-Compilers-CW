package uk.ac.nott.cs.comp3012.coursework.util;

/**
 * A {@code Table} represents a traditional two-dimensional table where keys are indexed by column
 * and row.
 *
 * @param <K1> key used for rows
 * @param <K2> key used for columns
 * @param <V>  type of values
 */
public interface Table<K1, K2, V> {

    /**
     * Return the number of filled cells in this table.
     *
     * @return the size
     */
    int size();

    /**
     * Insert a new value or update an existing value.
     *
     * @param row   row index
     * @param col   column index
     * @param value value to insert or update
     * @return the previous cell value, or {@code null} if none existed
     */
    V put(K1 row, K2 col, V value);

    /**
     * Get an item from the table.
     *
     * @param row row index
     * @param col column index
     * @return cell value, or {@code null} if the cell is empty
     */
    V get(K1 row, K2 col);
}
