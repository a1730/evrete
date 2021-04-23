package org.evrete.runtime;

import org.evrete.api.KeyMode;
import org.evrete.api.MemoryKey;
import org.evrete.api.ReIterator;
import org.evrete.api.ValueHandle;
import org.evrete.collections.CollectionReIterator;

import java.util.ArrayList;
import java.util.Collection;

//TODO !!!! use RuntimeAware as parent class
public class RhsFactGroupAlpha implements RhsFactGroup {
    private static final VR KEY_MAIN = new VR(KeyMode.MAIN.ordinal());
    private static final VR KEY_DELTA = new VR(KeyMode.KNOWN_UNKNOWN.ordinal());
    private final RuntimeFactType[] types;
    private final ReIterator<MemoryKey> deltaKeyIterator;
    private final ReIterator<MemoryKey> mainKeyIterator;

    RhsFactGroupAlpha(RuntimeRuleImpl rule, RhsFactGroupDescriptor descriptor) {
        this.types = rule.asRuntimeTypes(descriptor.getTypes());
        assert types.length > 0;
        if (types.length > 24)
            throw new UnsupportedOperationException("Too many alpha nodes, another implementation required");

        // Main dummy iterator
        Collection<MemoryKey> mainCollection = new ArrayList<>();
        for (int i = 0; i < types.length; i++) {
            mainCollection.add(KEY_MAIN);
        }
        this.mainKeyIterator = new CollectionReIterator<>(mainCollection);


        // Delta dummy iterator
        Collection<MemoryKey> deltaCollection = new ArrayList<>();
        for (int i = 1; i < (1 << types.length); i++) {
            for (int bit = 0; bit < types.length; bit++) {
                if ((i & (1 << bit)) == 0) {
                    deltaCollection.add(KEY_MAIN);
                } else {
                    deltaCollection.add(KEY_DELTA);
                }
            }
        }
        this.deltaKeyIterator = new CollectionReIterator<>(deltaCollection);
    }

    @Override
    public RuntimeFactType[] types() {
        return types;
    }

    @Override
    public ReIterator<MemoryKey> keyIterator(boolean delta) {
        return delta ? deltaKeyIterator : mainKeyIterator;
    }

    private static class VR implements MemoryKey {
        private final int transientValue;

        VR(int transientValue) {
            this.transientValue = transientValue;
        }

        @Override
        public ValueHandle get(int fieldIndex) {
            throw new UnsupportedOperationException();
        }

        @Override
        public int getMetaValue() {
            return transientValue;
        }

        @Override
        public void setMetaValue(int i) {
            throw new UnsupportedOperationException();
        }

        @Override
        public String toString() {
            return transientValue == 0 ? "MAIN" : "DELTA";
        }
    }
}
