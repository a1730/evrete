package org.evrete.runtime;

import org.evrete.api.ActivationMode;
import org.evrete.api.RuleSession;
import org.evrete.api.events.SessionCreatedEvent;
import org.evrete.api.events.SessionFireEvent;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Logger;

/**
 * <p>
 * Base session class with common methods
 * </p>
 *
 * @param <S> session type parameter
 */
public abstract class AbstractRuleSession<S extends RuleSession<S>> extends AbstractRuleSessionDeployment<S> {
    private static final Logger LOGGER = Logger.getLogger(AbstractRuleSession.class.getName());
    private final SessionMemory memory;

    AbstractRuleSession(KnowledgeRuntime knowledge) {
        super(knowledge);
        this.memory = new SessionMemory(this);
        // Deploy existing rules
        deployRules(knowledge.getRuleDescriptors(), false);
        // Publish the Session Created event
        broadcast(SessionCreatedEvent.class, () -> AbstractRuleSession.this);
    }

    @Override
    public final SessionMemory getMemory() {
        return memory;
    }

    void clearInner() {
        for (SessionRule rule : ruleStorage) {
            rule.clear();
        }
        memory.clear();
        //TODO !!!
        //this.getActionBuffer().clear();
    }

    final void fireInner() {
        fireInnerAsync().join();
    }

    private CompletableFuture<Void> fireInnerAsync() {
        broadcast(SessionFireEvent.class, () -> AbstractRuleSession.this);
        ActivationMode mode = getAgendaMode();
        ActivationContext context = new ActivationContext(
                this,
                ruleStorage.getList() // Current rules
        );

        WorkMemoryActionBuffer buffer = getActionBuffer();
        LOGGER.fine(() -> "Session mode: " + mode + ", buffered facts: [" + buffer.bufferedActionCount() + "]");
        return fireCycle(context, mode, buffer);
    }


    private CompletableFuture<Void> fireCycle(final ActivationContext ctx, final ActivationMode mode, final WorkMemoryActionBuffer actions) {
        if (actions.hasData()) {
            // 1. Given the buffered actions, generate the session's delta memories
            CompletableFuture<ActivationContext.Status> memoryDeltaStatus = ctx.computeDelta(actions);

            // 2. When the delta structures are computed, perform the RHS calls
            return memoryDeltaStatus
                    .thenCompose(deltaStatus -> {
                        // 3. Collect actions generated by rules' RHS calls
                        WorkMemoryActionBuffer newActions = doAgenda(ctx, deltaStatus.getAgenda(), mode);
                        // 4. Commit the delta memories and repeat until there are no actions
                        return ctx.commitMemories(deltaStatus)
                                .thenCompose(unused -> fireCycle(ctx, mode, newActions));
                    });
        } else {
            // No actions, end of the fire cycle
            return CompletableFuture.completedFuture(null);
        }
    }

    private WorkMemoryActionBuffer doAgenda(ActivationContext context, List<SessionRule> agenda, ActivationMode mode) {
        if (agenda.isEmpty()) {
            return WorkMemoryActionBuffer.EMPTY;
        } else {
            activationManager.onAgenda(context.incrementFireCount(), Collections.unmodifiableList(agenda));
            WorkMemoryActionBuffer destinationForRuleActions = new WorkMemoryActionBuffer();
            switch (mode) {
                case DEFAULT:
                    doAgendaDefault(agenda, destinationForRuleActions);
                    break;
                case CONTINUOUS:
                    doAgendaContinuous(agenda, destinationForRuleActions);
                    break;
                default:
                    throw new UnsupportedOperationException("Unsupported activation mode: " + mode);
            }
            return destinationForRuleActions;
        }
    }

    private void doAgendaDefault(List<SessionRule> agenda, WorkMemoryActionBuffer destinationForRuleActions) {
        for (SessionRule rule : agenda) {
            if (activationManager.test(rule)) {
                // The rule is allowed for activation
                // Collect the RHS actions (inserts, updates, deletes called from inside the RHS)
                long activationCount = rule.callRhs(destinationForRuleActions);
                activationManager.onActivation(rule, activationCount);
                if(destinationForRuleActions.hasData()) {
                    return;
                }
            }
        }
    }

    private void doAgendaContinuous(List<SessionRule> agenda, WorkMemoryActionBuffer destinationForRuleActions) {
        for (SessionRule rule : agenda) {
            if (activationManager.test(rule)) {
                // The rule is allowed for activation
                // Collect the RHS actions (inserts, updates, deletes called from inside the RHS)
                long activationCount = rule.callRhs(destinationForRuleActions);
                activationManager.onActivation(rule, activationCount);
            }
        }
    }


//    private void fireDefault(ActivationContext ctx) {
//        List<SessionRule> agenda;
//        while (ctx.hasPendingTasks()) {
//            // Compute rules to fire
//            agenda = ctx.computeAgenda();
//            WorkMemoryActionBuffer destinationForRuleActions = ctx.getMemoryTasks();
//            if (!agenda.isEmpty()) {
//                // Report the agenda to the activation manager
//                activationManager.onAgenda(ctx.incrementFireCount(), Collections.unmodifiableList(agenda));
//                for (SessionRule rule : agenda) {
//                    if (activationManager.test(rule)) {
//                        // The rule is allowed for activation
//                        RhsResultReducer actionResult = rule.callRhs();
//                        activationManager.onActivation(rule, actionResult.getActivationCount());
//                        // Sink the RHS actions (inserts, updates, deletes called from inside the RHS)
//                        // into pending tasks
//                        actionResult.getActionBuffer().sinkTo(destinationForRuleActions);
//                    }
//                }
//            }
//            ctx.commitDeltaMemories();
//        }
//    }
//
//    private void fireContinuous(ActivationContext ctx) {
//        List<SessionRule> agenda;
//        while (ctx.hasPendingTasks()) {
//            agenda = ctx.computeAgenda();
//            WorkMemoryActionBuffer destinationForRuleActions = new WorkMemoryActionBuffer();
//            if (!agenda.isEmpty()) {
//                activationManager.onAgenda(ctx.incrementFireCount(), Collections.unmodifiableList(agenda));
//                for (SessionRule rule : agenda) {
//                    if (activationManager.test(rule)) {
//                        RhsResultReducer actionResult = rule.callRhs();
//                        activationManager.onActivation(rule, actionResult.getActivationCount());
//                        actionResult.getActionBuffer().sinkTo(destinationForRuleActions);
//                    }
//                }
//            }
//            destinationForRuleActions.sinkTo(ctx.getMemoryTasks()); // Do we need that buff var?
//            ctx.commitDeltaMemories();
//        }
//    }
}
