package org.evrete.util;

import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.function.Function;
import java.util.function.IntFunction;

/**
 * An iterator that generates all possible combinations of elements from source objects.
 *
 * @param <T> The type of the elements generated by the source iterators.
 */
public class CombinationIterator<T> implements Iterator<T[]> {
    private static final int END = -1;
    private final IntFunction<Iterator<T>> iteratorFunction;
    private final T[] combination;
    private final Iterator<T>[] iterators;
    private int readNextPosition;
    private final int size;

    /**
     * Class constructor
     *
     * @param type             type of the resulting array
     * @param sources          an array of sources
     * @param iteratorFunction a mapping function to create iterators from sources
     */
    public <S> CombinationIterator(Class<T> type, S[] sources, Function<S, Iterator<T>> iteratorFunction) {
        this(sources, CommonUtils.array(type, sources.length), iteratorFunction);
    }

    /**
     * Class constructor with shared result array that will be updated on each {@link #next()} operation
     *
     * @param sources          an array of sources
     * @param sharedResultArray      a shared result array
     * @param iteratorFunction a mapping function to create iterators from sources
     */
    public <S> CombinationIterator(S[] sources, T[] sharedResultArray, Function<S, Iterator<T>> iteratorFunction) {
        this(sharedResultArray, index -> iteratorFunction.apply(sources[index]));
    }

    /**
     * Class constructor with shared result array that will be updated on each {@link #next()} operation
     *
     * @param sharedResultArray a shared result array
     * @param iteratorFunction  a mapping function to create iterators by array index
     */
    @SuppressWarnings("unchecked")
    public CombinationIterator(T[] sharedResultArray, IntFunction<Iterator<T>> iteratorFunction) {
        this.size = sharedResultArray.length;
        this.iteratorFunction = iteratorFunction;
        this.combination = sharedResultArray;
        this.iterators = (Iterator<T>[]) new Iterator[this.size];
        initializeIterators();
    }

    private void initializeIterators() {
        for (int i = 0; i < size; i++) {
            iterators[i] = iteratorFunction.apply(i);
            if (!iterators[i].hasNext()) {
                readNextPosition = END;
                return;
            }
        }
        readNextPosition = 0;
    }

    @Override
    public boolean hasNext() {
        return readNextPosition != END;
    }

    protected T advanceIterator(int index) {
        return iterators[index].next();
    }

    @Override
    public T[] next() {
        if (hasNext()) {
            // Update the shared result
            for (int i = size - 1; i >= readNextPosition; i--) {
                combination[i] = advanceIterator(i);
            }
            // Compute next
            readNextPosition = computeNextPosition();
            return combination;
        } else {
            throw new NoSuchElementException();
        }
    }

    private int computeNextPosition() {
        int ret = END;
        for (int i = size - 1; i >= 0; i--) {
            if (iterators[i].hasNext()) {
                return i;
            } else {
                if (i > 0) {
                    iterators[i] = iteratorFunction.apply(i);
                    ret = i;
                } else {
                    ret = END;
                }
            }
        }
        return ret;
    }
}


