package org.evrete.collections;

import org.evrete.api.IntObjectPredicate;
import org.evrete.api.ReIterable;
import org.evrete.api.ReIterator;
import org.evrete.api.annotations.NonNull;
import org.evrete.util.CollectionUtils;

import java.util.Arrays;
import java.util.NoSuchElementException;
import java.util.StringJoiner;
import java.util.function.*;
import java.util.stream.Stream;

// TODO !!!! rewrite the whole concept

/**
 * A simple implementation of a linear probing hash table, devoid of fail-fast bells and whistles and similar features.
 * Contrary to Java's standard HashMap, this implementation can reduce its bucket table size when required,
 * preserving the true O(N) scan complexity.
 *
 * @param <E> Entry type
 */
public abstract class AbstractLinearHash<E> implements ReIterable<E> {
    static final ToIntFunction<Object> DEFAULT_HASH = Object::hashCode;
    static final BiPredicate<Object, Object> DEFAULT_EQUALS = Object::equals;
    private static final IntObjectPredicate<Object> NULL_BIN_PREDICATE = (i, o) -> o == null;

    private static final float loadFactor = 0.75f;
    private static final int MAXIMUM_CAPACITY = 1 << 30;
    private static final int MINIMUM_CAPACITY = 1 << 1;
    private static final int NULL_VALUE = -1;
    private final int minDataSize;
    int size = 0;
    private Object[] data;
    private boolean[] deletedIndices;
    private int deletes = 0;
    private int currentInsertIndex;
    private int[] unsignedIndices;

    protected AbstractLinearHash(int minCapacity) {
        int capacity = tableSizeFor(minCapacity);
        this.unsignedIndices = new int[capacity];
        CollectionUtils.systemFill(this.unsignedIndices, NULL_VALUE);
        this.currentInsertIndex = 0;

        this.minDataSize = capacity;
        this.data = new Object[capacity];
        this.deletedIndices = new boolean[capacity];
    }

    static long location(int hash, int binIndex) {
        return ((long) hash << 32) | (binIndex & 0xFFFFFFFFL);
    }


    @SuppressWarnings("unchecked")
    private static <Z> int findBinIndex0(int hash, Object[] scope, IntObjectPredicate<Z> stopSearchPredicate) {
        int mask = scope.length - 1, counter = 0, pos = hash & mask;
        while (!stopSearchPredicate.test(pos, (Z) scope[pos])) {
            if (counter++ == scope.length) {
                throw new IllegalStateException("Low-level implementation error, please submit a bug.");
            } else {
                pos = (pos + 1) & mask;
            }
        }
        return pos;
    }

    private static int findBinIndexForZ1(Object key, int hash, Object[] destination, BiPredicate<Object, Object> eqTest) {
        int mask = destination.length - 1;
        int pos = hash & mask;
        Object found;
        while ((found = destination[pos]) != null) {
            if (eqTest.test(key, found)) {
                return pos;
            } else {
                pos = (pos + 1) & mask;
            }
        }
        return pos;
    }

    /**
     * Returns a power of two size for the given target capacity.
     */
    private static int tableSizeFor(int dataSize) {
        int capacity = (int) (dataSize / loadFactor);
        int cap = Math.max(capacity, MINIMUM_CAPACITY);
        int n = -1 >>> Integer.numberOfLeadingZeros(cap - 1);
        int ret = n + 1;
        assert ret >= capacity;
        if (ret > MAXIMUM_CAPACITY) throw new OutOfMemoryError();
        return ret;
    }

    private int findBinIndex0(int hash, IntObjectPredicate<E> stopSearchPredicate) {
        return findBinIndex0(hash, data, stopSearchPredicate);
    }

    public void apply(int hash, Predicate<E> predicate, ObjIntConsumer<E> consumer) {
        apply(hash, binPredicate2(predicate), consumer);
    }

    public void apply(int hash, IntObjectPredicate<E> predicate, ObjIntConsumer<E> consumer) {
        int pos = findBinIndex0(hash, this.data, predicate);
        consumer.accept(get(pos), pos);
    }

    private IntObjectPredicate<E> binPredicate2(Predicate<E> predicate) {
        return (i, o) -> o == null || predicate.test(o);
    }

    static int hash(long location) {
        return (int) (location >> 32);
    }

    static int binIndex(long location) {
        return (int) location;
    }

    // Method to extract two int values from a long
    public static int[] extractInts(long value) {
        int value1 = (int) (value >> 32);
        int value2 = (int) value;

        return new int[] { value1, value2 };
    }

    /**
     * <p>
     * Returns the encoded location of a given argument in the hash table. The location is simply
     * the concatenated hash and bin index of where the argument would be placed based on its hash and equality test.
     * This function returns a long to minimize the overhead of Object creation and garbage collection.
     * </p>
     *
     * @param arg           The argument to compute the location from.
     * @param matchFunction The function to test objects with equal hash.
     * @param <K>           The type of the argument.
     * @return The location of the argument in the hash table.
     */
    public <K> long locationFor(@NonNull K arg, BiPredicate<? super E, K> matchFunction) {
        int hash = arg.hashCode();
        int binIndex = findBinIndex(arg, hash, matchFunction);
        return location(hash, binIndex);
    }

    /**
     * Computes the value associated with the given key if it is absent in the hash table. If the value is absent, the supplied producer function is called to generate the value.
     * If the value is present, the supplied action function is called with the existing value as an argument.
     *
     * @param key            The key used to compute the value.
     * @param searchFunction A function to test if the existing value matches the key.
     * @param producer       A function to generate the value if it is absent.
     * @param action         A consumer function to perform an action on the existing value.
     * @param <K>            The type of the key.
     * @return true if the value was absent and added, false if the value was present.
     */
    public <K> boolean computeIfAbsent(K key, BiPredicate<E, K> searchFunction, Function<K, E> producer, Consumer<E> action) {
        this.resize();
        int hash = key.hashCode();
        int binIndex = this.findBinIndex(key, hash, searchFunction);
        E existingAction = this.get(binIndex);
        if (existingAction == null) {
            this.saveDirect(producer.apply(key), binIndex);
            return true;
        } else {
            action.accept(existingAction);
            return false;
        }
    }

    /**
     * Returns the value associated with the given key.
     * If the value is absent, the supplied producer function is called to generate the value and update the hash table.
     *
     * @param <K>            The type of the key.
     * @param key            The key used to compute the value.
     * @param searchFunction A function to test if the existing value matches the key.
     * @param producer       A function to generate the value if it is absent.
     * @return The computed value.
     */
    public <K> E computeIfAbsent(K key, BiPredicate<E, K> searchFunction, Function<K, E> producer) {
        this.resize();
        int hash = key.hashCode();
        int binIndex = this.findBinIndex(key, hash, searchFunction);
        E result = this.get(binIndex);
        if (result == null) {
            result = producer.apply(key);
            this.saveDirect(result, binIndex);
        }
        return result;
    }

    //TODO optimize, no extra jobs must be done
    public <K> boolean exists(K key, BiPredicate<E, K> searchFunction) {
        int hash = key.hashCode();
        int binIndex = this.findBinIndex(key, hash, searchFunction);
        return this.get(binIndex) != null;
    }

    //TODO optimize, no extra jobs must be done
    public <K> E get(K key, BiPredicate<E, K> searchFunction) {
        int hash = key.hashCode();
        int binIndex = this.findBinIndex(key, hash, searchFunction);
        return this.get(binIndex);
    }

    @SuppressWarnings("unchecked")
    // TODO !!!! analyze/rewrite
    public E get(int pos) {
        return deletedIndices[pos] ? null : (E) data[pos];
    }

    public <K> E getByKey(@NonNull K key, BiPredicate<? super E, K> matchFunction) {
        int hash = key.hashCode();
        int binIndex = this.findBinIndex(key, hash, matchFunction);
        return this.get(binIndex);
    }


    private int findBinIndexForZ1(Object key, int hash, BiPredicate<Object, Object> eqTest) {
        return findBinIndexForZ1(key, hash, data, eqTest);
    }

    public <K> int findBinIndex(K key, int hash, BiPredicate<? super E, K> eqTest) {
        IntObjectPredicate<E> mainPredicate = binPredicate2(e -> eqTest.test(e, key));
        return findBinIndex0(hash, mainPredicate);
    }

    public final boolean addVerbose(E element) {
        resize();
        BiPredicate<Object, Object> eq = getEqualsPredicate();
        int hash = getHashFunction().applyAsInt(element);
        int pos = findBinIndexForZ1(element, hash, eq);
        E old = saveDirect(element, pos);
        return old == null || !eq.test(element, old);
    }

    public final void addSilent(E element) {
        resize();
        addNoResize(element);
    }

    private void addNoResize(E element) {
        int hash = getHashFunction().applyAsInt(element);
        int pos = findBinIndexForZ1(element, hash, getEqualsPredicate());
        saveDirect(element, pos);
    }

    @SuppressWarnings("unchecked")
    // TODO !!!! analyze/rewrite
    // TODO in some calls, this method is doing unnecessary job
    public final E saveDirect(E element, int pos) {
        Object old = data[pos];
        data[pos] = element;
        if (old == null) {
            unsignedIndices[currentInsertIndex++] = pos;
            size++;
        } else {
            if (deletedIndices[pos]) {
                deletedIndices[pos] = false;
                deletes--;
                size++;
            }
        }
        return (E) old;
    }

    protected abstract ToIntFunction<Object> getHashFunction();

    protected abstract BiPredicate<Object, Object> getEqualsPredicate();

    public final int size() {
        return size;
    }

    final void deleteEntries(Predicate<E> predicate) {
        int initialDeletes = this.deletes;
        forEachDataEntry((e, i) -> {
            if (predicate.test(e)) {
                markDeleted(i);
            }
        });
        if (initialDeletes != this.deletes) {
            resize();
        }
    }

    public void forEachDataEntry(Consumer<E> consumer) {
        E obj;
        for (int i = 0; i < currentInsertIndex; i++) {
            if ((obj = get(unsignedIndices[i])) != null) {
                consumer.accept(obj);
            }
        }
    }

    private void forEachDataEntry(ObjIntConsumer<E> consumer) {
        int i, idx;
        E obj;
        for (i = 0; i < currentInsertIndex; i++) {
            idx = unsignedIndices[i];
            if ((obj = get(idx)) != null) {
                consumer.accept(obj, idx);
            }
        }
    }

    protected void markDeleted(int pos) {
        if (!deletedIndices[pos]) {
            deletedIndices[pos] = true;
            deletes++;
            size--;
        }
    }

    @Override
    public String toString() {
        StringJoiner joiner = new StringJoiner(", ", "[", "]");
        forEachDataEntry(k -> joiner.add(k.toString()));
        return joiner.toString();
    }

    public void clear() {
        CollectionUtils.systemFill(this.data, null);
        CollectionUtils.systemFill(this.deletedIndices, false);
        CollectionUtils.systemFill(this.unsignedIndices, NULL_VALUE);
        this.currentInsertIndex = 0;
        this.size = 0;
        this.deletes = 0;
    }

    // TODO !!!! analyze/rewrite
    boolean containsEntry(E e) {
        int pos = findBinIndexForZ1(e, getHashFunction().applyAsInt(e), getEqualsPredicate());
        return data[pos] != null && !deletedIndices[pos];
    }

    boolean removeEntry(Object e) {
        int pos = findBinIndexForZ1(e, getHashFunction().applyAsInt(e), getEqualsPredicate());
        return removeEntry(pos);
    }

    private boolean removeEntry(int pos) {
        if (data[pos] == null) {
            // Nothing to delete
            return false;
        } else {
            if (deletedIndices[pos]) {
                // Nothing to delete
                return false;
            } else {
                markDeleted(pos);
                return true;
            }
        }
    }

    @Override
    public ReIterator<E> iterator() {
        return new It();
    }

    @SuppressWarnings("unchecked")
    //TODO !!!! analyze usage / rewrite
    public Stream<E> stream() {
        return Arrays.stream(unsignedIndices, 0, currentInsertIndex)
                .filter(i -> !deletedIndices[i])
                .mapToObj(value -> (E) data[value]);
    }

    public void resize() {
        int upperBound = (int) (data.length * loadFactor);
        int lowerBound = (int) (data.length * loadFactor / 4);
        if (size > upperBound) {
            // Resize up
            rebuild(data.length * 2);
            return;
        }

        if (size < lowerBound) {
            // Resize down
            int newDataSize = Math.max(minDataSize, tableSizeFor(lowerBound * 2));
            if (newDataSize != data.length) {
                rebuild(newDataSize);
                return;
            }
        }

        if (deletes > size && size > MINIMUM_CAPACITY) {
            // Purge deleted data and indices
            rebuild(this.data.length);
        }
    }

    private void rebuild(int newArrSize) {
        Object[] newData = new Object[newArrSize];
        int[] newUnsignedIndices = new int[newArrSize];
        int newCurrentInsertIndex = 0;
        ToIntFunction<Object> hashFunction = getHashFunction();
        E obj;
        for (int i = 0; i < currentInsertIndex; i++) {
            if ((obj = get(unsignedIndices[i])) != null) {
                int pos = findBinIndex0(hashFunction.applyAsInt(obj), newData, NULL_BIN_PREDICATE);
                newData[pos] = obj;
                newUnsignedIndices[newCurrentInsertIndex++] = pos;
            }
        }
        this.data = newData;
        this.deletes = 0;
        this.deletedIndices = new boolean[newArrSize];
        this.currentInsertIndex = newCurrentInsertIndex;
        this.unsignedIndices = newUnsignedIndices;
    }

    void assertStructure() {
        int indices = currentInsertIndex;
        int deletes = this.deletes;
        assert indices == size + deletes : "indices: " + indices + " size: " + size + ", deletes: " + deletes;
        assert this.data.length >= minDataSize;
    }

    private final class It implements ReIterator<E> {
        int pos = 0;
        int nextIndex;
        int currentIndex = -1;

        private It() {
            // Initial advance
            nextIndex = computeNextIndex();
        }

        @Override
        public long reset() {
            pos = 0;
            nextIndex = computeNextIndex();
            return size;
        }

        @Override
        public boolean hasNext() {
            return nextIndex >= 0;
        }

        private int computeNextIndex() {
            while (pos < currentInsertIndex) {
                int idx = unsignedIndices[pos];
                if (deletedIndices[idx]) {
                    pos++;
                } else {
                    return idx;
                }
            }
            return -1;
        }


        @Override
        @SuppressWarnings("unchecked")
        public E next() {
            if (nextIndex < 0) {
                throw new NoSuchElementException();
            } else {
                pos++;
                currentIndex = nextIndex;
                nextIndex = computeNextIndex();
                return (E) data[currentIndex];
            }
        }

        @Override
        public void remove() {
            if (currentIndex < 0) {
                throw new NoSuchElementException();
            } else {
                markDeleted(currentIndex);
            }
        }
    }
}
