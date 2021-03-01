package org.evrete.api;

/**
 * <p>
 * A wrapper for TypeField that will actually be in use by the runtime. Declared, but unused, fields will not get
 * wrapped, thus avoiding unnecessary value reads.
 * </p>
 */
public interface ActiveField extends TypeField {
    ActiveField[] ZERO_ARRAY = new ActiveField[0];

    /**
     * @return index under which the value of this field is stored during insert/update in an Object[] array.
     * @see FieldToValueHandle
     */
    int getValueIndex();
}
