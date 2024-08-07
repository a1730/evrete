package org.evrete.runtime.rete;

import org.evrete.api.spi.MemoryScope;
import org.evrete.runtime.*;
import org.evrete.runtime.evaluation.DefaultEvaluatorHandle;

import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.StringJoiner;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;

public abstract class ReteSessionNode extends ReteNode<ReteSessionNode> {
    public static final ReteSessionNode[] EMPTY_ARRAY = new ReteSessionNode[0];
    private final AbstractRuntime<?,?> runtime;
    private final FactType[] nodeFactTypes;
    private final int[][] nodeFactTypesMapping;
    private final MapOfList<ActiveType.Idx, Integer> typeToIndices;

    public ReteSessionNode(AbstractRuntime<?,?> runtime, ReteKnowledgeNode parent, ReteSessionNode[] sourceNodes) {
        super(sourceNodes);
        this.runtime = runtime;
        this.nodeFactTypes = parent.getNodeFactTypes();
        this.nodeFactTypesMapping = parent.getNodeFactTypesMapping();
        this.typeToIndices = parent.getTypeToIndices();
    }

    public Collection<Integer> nodeIndices(ActiveType.Idx type) {
        return typeToIndices.getOrDefault(type, Collections.emptyList());
    }

    public FactType[] getNodeFactTypes() {
        return nodeFactTypes;
    }

    abstract String debugName();

    String debugName(ReteSessionNode[] nodes) {
        StringJoiner joiner = new StringJoiner(", ", "[" , "]");
        for (ReteSessionNode node : nodes) {
            joiner.add("'" + node.debugName() + "'");
        }
        return joiner.toString();
    }

    int location(int sourceIndex, int inSourceIndex) {
        return nodeFactTypesMapping[sourceIndex][inSourceIndex];
    }

    protected ExecutorService getExecutor() {
        return runtime.getService().getExecutor();
    }

    protected StoredCondition getActiveEvaluator(DefaultEvaluatorHandle handle) {
        return runtime.getEvaluatorsContext().get(handle, false);
    }

    public abstract CompletableFuture<Void> computeDeltaMemoryAsync(DeltaMemoryMode mode);

    abstract Iterator<ConditionMemory.MemoryEntry> iterator(MemoryScope scope);

}
