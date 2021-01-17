package org.evrete.runtime;

import org.evrete.api.*;
import org.evrete.collections.AbstractLinearHashMap;
import org.evrete.collections.LinearHashMap;
import org.evrete.runtime.async.Completer;
import org.evrete.runtime.async.ForkJoinExecutor;
import org.evrete.runtime.async.RuleHotDeploymentTask;
import org.evrete.runtime.async.RuleMemoryInsertTask;
import org.evrete.runtime.evaluation.AlphaBucketMeta;
import org.evrete.runtime.evaluation.AlphaDelta;

import java.util.*;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.logging.Logger;

public class SessionMemory extends AbstractRuntime<StatefulSession> implements WorkingMemory, Iterable<TypeMemory> {
    private static final Logger LOGGER = Logger.getLogger(SessionMemory.class.getName());
    private final RuntimeRules ruleStorage;
    private final LinearHashMap<Type<?>, TypeMemory> typedMemories;
    private static final Function<AbstractLinearHashMap.Entry<Type<?>, TypeMemory>, TypeMemory> TYPE_MEMORY_MAPPING = AbstractLinearHashMap.Entry::getValue;

    protected SessionMemory(KnowledgeImpl parent) {
        super(parent);
        this.ruleStorage = new RuntimeRules(this);
        this.typedMemories = new LinearHashMap<>(getTypeResolver().getKnownTypes().size());
        // Deploy existing rules
        for (RuleDescriptor descriptor : getRuleDescriptors()) {
            deployRule(descriptor, false);
        }
    }



    @Override
    protected void addListenerToRules(EvaluationListener listener) {
        this.ruleStorage.addListener(listener);
    }

    @Override
    protected void removeListenerFromRules(EvaluationListener listener) {
        this.ruleStorage.removeListener(listener);
    }

    @Override
    public Iterator<TypeMemory> iterator() {
        return typedMemories.iterator(TYPE_MEMORY_MAPPING);
    }

    ReIterator<TypeMemory> typeMemories() {
        return typedMemories.valueIterator();
    }

    private void reSortRules() {
        ruleStorage.sort(getRuleComparator());
    }

    @Override
    public void setRuleComparator(Comparator<Rule> ruleComparator) {
        super.setRuleComparator(ruleComparator);
        reSortRules();
    }

    @Override
    protected TypeResolver newTypeResolver() {
        return getParentContext().getTypeResolver().copyOf();
    }

    @Override
    public final Kind getKind() {
        return Kind.SESSION;
    }

    @Override
    public RuntimeRule deployRule(RuleDescriptor descriptor) {
        return deployRule(descriptor, true);
    }

    private synchronized RuntimeRuleImpl deployRule(RuleDescriptor descriptor, boolean hotDeployment) {
        for (FactType factType : descriptor.getLhs().getAllFactTypes()) {
            touchMemory(factType.getFields(), factType.getAlphaMask());
        }
        RuntimeRuleImpl rule = ruleStorage.addRule(descriptor);
        if (hotDeployment) {
            getExecutor().invoke(new RuleHotDeploymentTask(rule));
        }
        reSortRules();
        return rule;
    }

    private void touchMemory(FieldsKey key, AlphaBucketMeta alphaMeta) {
        Type<?> t = key.getType();
        typedMemories
                .computeIfAbsent(t, k -> new TypeMemory(this, t))
                .touchMemory(key, alphaMeta);
    }

    @Override
    public void clear() {
        typedMemories.forEachValue(TypeMemory::clear);
        for (RuntimeRuleImpl rule : ruleStorage) {
            rule.clear();
        }
    }

    @Override
    public void insert(Object fact) {
        memoryAction(Action.INSERT, fact);
    }

    @Override
    public void insert(String factType, Object fact) {
        memoryAction(Action.INSERT, getTypeResolver().getType(factType), fact);
    }

    List<RuntimeRule> propagateInsertChanges() {
        List<RuntimeRule> affectedRules = new LinkedList<>();
        Set<BetaEndNode> affectedEndNodes = new HashSet<>();
        // Scanning rules first because they are sorted by salience
        for (RuntimeRuleImpl rule : ruleStorage) {
            rule.mergeNodeDeltas();
            boolean ruleAdded = false;

            for (TypeMemory tm : this) {
                Type<?> t = tm.getType();
                if (!ruleAdded && rule.dependsOn(t)) {
                    affectedRules.add(rule);
                    ruleAdded = true;
                }

                for (BetaEndNode endNode : rule.getLhs().getAllBetaEndNodes()) {
                    if (endNode.dependsOn(t)) {
                        affectedEndNodes.add(endNode);
                    }
                }
            }
        }

        // Ordered task 1 - process beta nodes, i.e. evaluate conditions
        List<Completer> tasks = new LinkedList<>();
        if (!affectedEndNodes.isEmpty()) {
            tasks.add(new RuleMemoryInsertTask(affectedEndNodes, true));
        }

        if (tasks.size() > 0) {
            ForkJoinExecutor executor = getExecutor();
            for (Completer task : tasks) {
                executor.invoke(task);
            }
        }
        return affectedRules;
    }

    void processDeleteBuffer() {
        for (TypeMemory tm : this) {
            tm.processDeleteBuffer();
        }
    }

    void processInsertBuffer() {
        for (TypeMemory tm : this) {
            tm.processInsertBuffer();
        }
    }

    @Override
    protected synchronized void onNewActiveField(ActiveField newField) {
        Type<?> t = newField.getDeclaringType();
        TypeMemory tm = typedMemories.get(t);
        if (tm == null) {
            tm = new TypeMemory(this, t);
            typedMemories.put(t, tm);
        }
        tm.onNewActiveField(newField);
    }

    @Override
    protected void onNewAlphaBucket(AlphaDelta delta) {
        Type<?> t = delta.getKey().getType();
        TypeMemory tm = typedMemories.get(t);
        if (tm == null) {
            tm = new TypeMemory(this, t);
            typedMemories.put(t, tm);
        } else {
            tm.onNewAlphaBucket(delta);
        }
    }

    SharedBetaFactStorage getBetaFactStorage(FactType factType) {
        Type<?> t = factType.getType();
        FieldsKey fields = factType.getFields();
        AlphaBucketMeta mask = factType.getAlphaMask();

        return get(t).get(fields).get(mask);
    }

    void destroy() {
        typedMemories.clear();
    }

    void memoryAction(Action action, Object o) {
        memoryAction(action, getTypeResolver().resolve(o), o);
    }

    private void memoryAction(Action action, Type<?> t, Object o) {
        if (t == null) {
            LOGGER.warning("Unknown object type of " + o + ", action " + action + "  skipped");
        } else {
            get(t).memoryAction(action, o);
        }
    }

    @Override
    public <T> void forEachMemoryObject(String type, Consumer<T> consumer) {
        Type<?> t = getTypeResolver().getType(type);
        if (t != null) {
            TypeMemory tm = typedMemories.get(t);
            tm.forEachMemoryObject(consumer);
        }
    }

    @Override
    public void forEachMemoryObject(Consumer<Object> consumer) {
        typedMemories.forEachValue(tm -> tm.forEachObjectUnchecked(consumer));
    }

    public List<RuntimeRule> getRules() {
        return ruleStorage.asList();
    }

    public TypeMemory get(Type<?> t) {
        TypeMemory m = typedMemories.get(t);
        if (m == null) {
            // TODO !!!! touch TypeMemory if a corresponding type has been explicitly declared in TypeResolver
            throw new IllegalArgumentException("No type memory created for " + t);
        } else {
            return m;
        }
    }

    @Override
    public void update(Object fact) {
        memoryAction(Action.UPDATE, fact);
    }

    @Override
    public void delete(Object fact) {
        memoryAction(Action.RETRACT, fact);
    }
}