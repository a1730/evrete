package org.evrete.spi.minimal;

import org.evrete.api.*;
import org.evrete.util.CollectionUtils;

import java.util.function.Function;

abstract class AbstractKeyedFactStorage<T extends AbstractFactsMap<?>> implements KeyedFactStorage {
    private final T[] maps;// = new FieldsFactMap[KeyMode.values().length];

    AbstractKeyedFactStorage(Class<T> mapType, Function<KeyMode, T> mapSupplier) {
        //this.fields = fields;
        this.maps = CollectionUtils.array(mapType, KeyMode.values().length);
        for (KeyMode mode : KeyMode.values()) {
            this.maps[mode.ordinal()] = mapSupplier.apply(mode);
        }
    }

    @Override
    public final ReIterator<FactHandleVersioned> values(KeyMode mode, MemoryKey key) {
        return get(mode).values(key);
    }

    @Override
    public final void clear() {
        for (T map : maps) {
            map.clear();
        }
    }

    @Override
    public final void insert(FieldToValueHandle key, int keyHash, FactHandleVersioned value) {
        if (get(KeyMode.MAIN).hasKey(keyHash, key)) {
            // Existing key
            get(KeyMode.KNOWN_UNKNOWN).add(key, keyHash, value);
        } else {
            // New key
            get(KeyMode.UNKNOWN_UNKNOWN).add(key, keyHash, value);
        }
    }

    public final ReIterator<MemoryKey> keys(KeyMode keyMode) {
        return get(keyMode).keys();
    }

    final T get(KeyMode mode) {
        return maps[mode.ordinal()];
    }
}