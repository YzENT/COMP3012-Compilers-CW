package uk.ac.nott.cs.comp3012.coursework.tac;

import java.util.UUID;

/**
 * Parameters for Three-Address Code instructions.
 * Can represent variables, constants, temporaries, or labels.
 */
public sealed interface TacParam {

    /**
     * Integer constant value
     */
    record Value(int value) implements TacParam {
        @Override
        public String toString() {
            return String.valueOf(value);
        }
    }

    /**
     * Variable name (user-defined or parameter)
     */
    record Variable(String name) implements TacParam {
        @Override
        public String toString() {
            return name;
        }
    }

    /**
     * Temporary variable (compiler-generated)
     */
    record Temp(int id) implements TacParam {
        @Override
        public String toString() {
            return "t" + id;
        }
    }

    /**
     * Label reference (points to instruction ID for jumps)
     */
    record Label(UUID target) implements TacParam {
        @Override
        public String toString() {
            return target.toString().substring(0, 8);
        }
    }

    /**
     * Real/float constant value
     */
    record RealValue(float value) implements TacParam {
        @Override
        public String toString() {
            return String.valueOf(value);
        }
    }

    /**
     * Boolean constant value
     */
    record BoolValue(boolean value) implements TacParam {
        @Override
        public String toString() {
            return String.valueOf(value);
        }
    }

    /**
     * String constant value
     */
    record StringValue(String value) implements TacParam {
        @Override
        public String toString() {
            return "\"" + value + "\"";
        }
    }

    /**
     * Field name for derived type access
     * TODO: derived types implementation
     */
    record FieldName(String name) implements TacParam {
        @Override
        public String toString() {
            return "." + name;
        }
    }
}