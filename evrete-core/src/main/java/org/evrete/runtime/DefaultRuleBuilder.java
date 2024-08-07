package org.evrete.runtime;

import org.evrete.api.FactBuilder;
import org.evrete.api.NamedType;
import org.evrete.api.RhsContext;
import org.evrete.api.RuntimeContext;
import org.evrete.api.annotations.NonNull;
import org.evrete.api.builders.RuleBuilder;

import java.util.Collection;
import java.util.function.Consumer;

class DefaultRuleBuilder<C extends RuntimeContext<C>> extends AbstractRule implements RuleBuilder<C> {
    private final DefaultLhsBuilder<C> lhsBuilder;

    private final DefaultRuleSetBuilder<C> ruleSetBuilder;


    DefaultRuleBuilder(DefaultRuleSetBuilder<C> ruleSetBuilder, String name) {
        super(ruleSetBuilder, name);
        this.ruleSetBuilder = ruleSetBuilder;
        this.lhsBuilder = new DefaultLhsBuilder<>(this);
    }

    String literalRhs() {
        return super.getLiteralRhs();
    }

    @Override
    public DefaultConditionManager getConditionManager() {
        return this.lhsBuilder.getConditionManager();
    }

    @Override
    public DefaultRuleSetBuilder<C> execute() {
        return this.ruleSetBuilder;
    }

    @Override
    public DefaultRuleSetBuilder<C> execute(Consumer<RhsContext> consumer) {
        setRhs(consumer);
        return this.ruleSetBuilder;
    }

    @Override
    public DefaultRuleSetBuilder<C> execute(String literalRhs) {
        setRhs(literalRhs);
        return this.ruleSetBuilder;
    }

    @Override
    public Collection<NamedType> getDeclaredFactTypes() {
        return lhsBuilder.getDeclaredFactTypes();
    }

    @Override
    public DefaultRuleBuilder<C> set(String property, Object value) {
        super.set(property, value);
        return this;
    }

    @Override
    public DefaultRuleBuilder<C> salience(int salience) {
        setSalience(salience);
        return this;
    }

    //@Override
    @NonNull
    public NamedType resolve(@NonNull String var) {
        return lhsBuilder.resolve(var);
    }

    @Override
    public DefaultLhsBuilder<C> getLhs() {
        return lhsBuilder;
    }

    @SuppressWarnings("unchecked")
    @Override
    public C getRuntime() {
        return (C) runtime();
    }


    @Override
    public DefaultLhsBuilder<C> forEach(Collection<FactBuilder> facts) {
        return lhsBuilder.buildLhs(facts);
    }

    AbstractRuntime<?, C> runtime() {
        return ruleSetBuilder.getRuntime();
    }
}
