package org.evrete.runtime.evaluation;

import org.evrete.util.Bits;

import java.util.*;

public abstract class AlphaBucketMeta implements MemoryBucket {
    private static final Set<AlphaEvaluator.Match> EMPTY_COMPONENTS = new HashSet<>();
    private final int bucketIndex;
    private final Set<AlphaEvaluator.Match> key;

    private AlphaBucketMeta(int bucketIndex, Set<AlphaEvaluator.Match> matches) {
        this.bucketIndex = bucketIndex;
        this.key = matches;
    }

    public static AlphaBucketMeta factory(int bucketIndex, Set<AlphaEvaluator.Match> matches) {
        switch (matches.size()) {
            case 0:
                return new Empty(bucketIndex);
            case 1:
                return new Single(bucketIndex, matches);
            default:
                return new Multi(bucketIndex, matches);
        }
    }

    public final boolean sameKey(Set<AlphaEvaluator.Match> other) {
        if (this.key.isEmpty() && other.isEmpty()) {
            return true;
        } else {
            return this.key.equals(other);
        }
    }

    public final boolean isEmpty() {
        return this.key.isEmpty();
    }

    @Override
    public final int getBucketIndex() {
        return bucketIndex;
    }

    private static final class Empty extends AlphaBucketMeta {

        Empty(int bucketIndex) {
            super(bucketIndex, EMPTY_COMPONENTS);
        }

        @Override
        public boolean test(Bits mask) {
            return true;
        }
    }

    private static final class Single extends AlphaBucketMeta {
        private final int bitIndex;
        private final boolean expectedValue;

        Single(int bucketIndex, Set<AlphaEvaluator.Match> matches) {
            super(bucketIndex, matches);
            AlphaEvaluator.Match match = matches.iterator().next();
            this.expectedValue = match.direct;
            this.bitIndex = match.matched.getIndex();
        }

        @Override
        public boolean test(Bits mask) {
            return mask.get(bitIndex) == expectedValue;
        }
    }

    private static final class Multi extends AlphaBucketMeta {
        private final int[] bitIndices;
        private final Bits expectedValues = new Bits();

        Multi(int bucketIndex, Set<AlphaEvaluator.Match> matches) {
            super(bucketIndex, matches);
            List<AlphaEvaluator.Match> sortedMatches = new ArrayList<>(matches);
            sortedMatches.sort(Comparator.comparingDouble(o -> o.matched.getDelegate().getComplexity()));
            this.bitIndices = new int[sortedMatches.size()];
            int i = 0;
            for (AlphaEvaluator.Match match : sortedMatches) {
                this.bitIndices[i] = match.matched.getIndex();
                if (match.direct) {
                    this.expectedValues.set(match.matched.getIndex());
                }
                i++;
            }
        }

        public boolean test(Bits mask) {
            for (int idx : bitIndices) {
                if (mask.get(idx) != expectedValues.get(idx)) {
                    return false;
                }
            }
            return true;
        }

        @Override
        public String toString() {
            return "{bucket=" + getBucketIndex() +
                    ", values=" + expectedValues +
                    '}';
        }
    }
}
