package org.evrete.spi.minimal;

import org.evrete.api.*;
import org.evrete.api.annotations.NonNull;
import org.evrete.api.spi.LiteralSourceCompiler;
import org.evrete.util.CompilationException;

import java.lang.invoke.MethodHandle;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import static org.evrete.Configuration.RULE_BASE_CLASS;
import static org.evrete.Configuration.SPI_LHS_STRIP_WHITESPACES;

public class DefaultLiteralSourceCompiler extends LeastImportantServiceProvider implements LiteralSourceCompiler {
    private static final String TAB = "  ";
    private static final String RHS_CLASS_NAME = "Rhs";
    private static final String RHS_INSTANCE_VAR = "ACTION";

    private static final AtomicInteger classCounter = new AtomicInteger(0);
    static final String CLASS_PACKAGE = DefaultLiteralSourceCompiler.class.getPackage().getName() + ".compiled";

    @Override
    public <S extends RuleLiteralData<R>, R extends Rule> Collection<RuleCompiledSources<S, R>> compile(RuntimeContext<?> context, Collection<S> sources) throws CompilationException {
        // Return if there's nothing to compile
        if (sources.isEmpty()) {
            return Collections.emptyList();
        }

        String stripFlag = context.getConfiguration().getProperty(SPI_LHS_STRIP_WHITESPACES);

        if (stripFlag == null) {
            try {
                // Try compiling with stripped whitespaces
                return compile(context, sources, true);
            } catch (CompilationException e) {
                // Compile literals as-is
                return compile(context, sources, false);
            }
        } else {
            return compile(context, sources, Boolean.parseBoolean(stripFlag));
        }
    }

    private  <S extends RuleLiteralData<R>, R extends Rule> Collection<RuleCompiledSources<S, R>> compile(RuntimeContext<?> context, Collection<S> sources, boolean stripWhitespaces) throws CompilationException {
        JavaSourceCompiler compiler = context.getSourceCompiler();

        Collection<RuleSource<S, R>> javaSources = sources.stream()
                .map(o -> new RuleSource<>(o, context, stripWhitespaces))
                .collect(Collectors.toList());

        Collection<JavaSourceCompiler.Result<RuleSource<S, R>>> result = compiler.compile(javaSources);

        return result
                    .stream()
                    .map(compiledSource -> {
                        Class<?> ruleClass = compiledSource.getCompiledClass();
                        return new RuleCompiledSourcesImpl<>(ruleClass, compiledSource.getSource());
                    })
                    .collect(Collectors.toList());
    }

    static class RuleSource<S extends RuleLiteralData<R>, R extends Rule> implements JavaSourceCompiler.ClassSource {
        private final String className;
        private final String classSimpleName;

        private final S delegate;
        private final Imports imports;
        private final RhsSource rhsSource;

        private final String javaSource;
        private final Collection<ConditionSource> conditionSources;

        RuleSource(S delegate, RuntimeContext<?> context, boolean stripWhitespaces) {
            this.delegate = delegate;
            this.imports = context.getImports();
            this.classSimpleName = "Rule" + classCounter.incrementAndGet();
            this.className = CLASS_PACKAGE + "." + classSimpleName;

            AtomicInteger conditionCounter = new AtomicInteger();
            this.conditionSources = delegate.conditions()
                    .stream()
                    .map(s -> new ConditionSource(delegate.getRule(), "condition" + conditionCounter.incrementAndGet(), this.classSimpleName, s, context, stripWhitespaces))
                    .collect(Collectors.toList());

            String rhs = delegate.rhs();
            this.rhsSource = rhs == null ? null : new RhsSource(delegate.getRule(), rhs);
            this.javaSource = this.buildSource();
        }

        private String buildSource() {
            StringBuilder sb = new StringBuilder(4096);
            // Class header
            appendHeader(sb);

            // Class RHS body
            if (this.rhsSource != null) {
                this.rhsSource.appendClassVar(sb);
            }

            // Class conditions declarations
            for (ConditionSource source : this.conditionSources) {
                sb.append(TAB);
                source.appendDeclaration(sb);
            }

            // Class conditions definitions
            if (!this.conditionSources.isEmpty()) {
                sb.append("\n").append(TAB).append("static {\n");
                sb.append(TAB).append(TAB).append("try {\n");
                for (ConditionSource source : this.conditionSources) {
                    sb.append(TAB);
                    sb.append(TAB);
                    sb.append(TAB);
                    source.appendDefinition(sb);
                }
                sb.append(TAB).append(TAB).append("} catch (Exception e) {\n");
                sb.append(TAB).append(TAB).append(TAB).append("throw new IllegalStateException(e);\n");
                sb.append(TAB).append(TAB).append("}\n");
                sb.append(TAB).append("}\n");
            }

            // Class conditions methods
            for (ConditionSource source : this.conditionSources) {
                source.appendHandleMethod(sb);
                source.appendInnerMethod(sb);
                sb.append("\n");
            }

            // Class RHS body
            if (this.rhsSource != null) {
                this.rhsSource.appendClassBody(sb);
            }

            // Class footer
            appendFooter(sb);
            return sb.toString();
        }

        @Override
        public String binaryName() {
            return className;
        }

        @Override
        public String getSource() {
            return javaSource;
        }

        private void appendHeader(StringBuilder target) {
            // Declare package
            target.append("package ").append(CLASS_PACKAGE).append(";\n\n");

            // Declare imports
            imports.asJavaImportStatements(target);

            // Declare class
            String baseClassName = delegate.getRule().get(RULE_BASE_CLASS, BaseRuleClass.class.getName());

            target.append("public final class ")
                    .append(classSimpleName)
                    .append(" extends ")
                    .append(baseClassName)
                    .append(" {\n");

        }

        private void appendFooter(StringBuilder target) {
            target.append("\n}\n");
        }
    }

    private static class ConditionSource {
        private static final String DECLARATION_TEMPLATE =
                "public static final java.lang.invoke.MethodHandle %s;\n";
        private static final String DEFINITION_TEMPLATE =
                "%s = java.lang.invoke.MethodHandles.lookup().findStatic(%s.class, \"%s\", java.lang.invoke.MethodType.methodType(boolean.class, %s));\n";
        private static final String INNER_METHOD_TEMPLATE = "\n" +
                "  private static boolean %sInner(%s) {\n" +
                "    return %s;\n" +
                "  }\n";
        private static final String HANDLE_METHOD_TEMPLATE = "\n" +
                "  public static boolean %s(%s) {\n" +
                "    return %sInner(%s);\n" +
                "  }\n";

        final String source;
        final String methodName;
        final String handleName;
        final String className;
        private final String replaced;
        private final StringJoiner methodArgs;
        private final StringJoiner argCasts;
        private final FieldReference[] descriptor;

        public ConditionSource(Rule rule, String name, String className, String source, RuntimeContext<?> context, boolean stripWhitespaces) {
            this.className = className;
            this.source = source;
            this.methodName = name;
            this.handleName = name.toUpperCase() + "_HANDLE";
            StringLiteralEncoder encoder = StringLiteralEncoder.of(source, stripWhitespaces);

            ExpressionResolver resolver = context.getExpressionResolver();

            final List<ConditionStringTerm> terms = ConditionStringTerm.resolveTerms(encoder.getEncoded(), s -> resolver.resolve(s, rule));

            List<ConditionStringTerm> uniqueReferences = new ArrayList<>();
            List<FieldReference> descriptorBuilder = new ArrayList<>();

            String encodedExpression = encoder.getEncoded().value;
            int accumulatedShift = 0;
            int castVarIndex = 0;
            this.argCasts = new StringJoiner(", ");
            this.methodArgs = new StringJoiner(", ");
            for (ConditionStringTerm term : terms) {
                String original = encodedExpression.substring(term.start + accumulatedShift, term.end + accumulatedShift);
                String javaArgVar = term.varName;
                String before = encodedExpression.substring(0, term.start + accumulatedShift);
                String after = encodedExpression.substring(term.end + accumulatedShift);
                encodedExpression = before + javaArgVar + after;
                accumulatedShift += javaArgVar.length() - original.length();

                if (!uniqueReferences.contains(term)) {
                    //Build the reference
                    descriptorBuilder.add(term);
                    //Prepare the corresponding source code vars
                    Class<?> fieldType = term.field().getValueType();

                    //argTypes.add(term.type().getType().getName() + "/" + term.field().getName());
                    argCasts.add("(" + fieldType.getCanonicalName() + ") values.apply(" + castVarIndex + ")");
                    methodArgs.add(fieldType.getCanonicalName() + " " + javaArgVar);
                    castVarIndex++;
                    // Mark as processed
                    uniqueReferences.add(term);
                }
            }

            this.replaced = encoder.unwrapLiterals(encodedExpression);
            this.descriptor = descriptorBuilder.toArray(FieldReference.ZERO_ARRAY);
        }

        void appendDeclaration(StringBuilder target) {
            target.append(String.format(DECLARATION_TEMPLATE, handleName));
        }

        void appendHandleMethod(StringBuilder target) {
            target.append(String.format(HANDLE_METHOD_TEMPLATE, methodName, IntToValue.class.getName() + " values", methodName, argCasts));
        }

        void appendInnerMethod(StringBuilder target) {
            target.append(String.format(INNER_METHOD_TEMPLATE, methodName, methodArgs, replaced));
        }

        void appendDefinition(StringBuilder target) {
            target.append(String.format(DEFINITION_TEMPLATE, handleName, className, methodName, IntToValue.class.getName() + ".class"));
        }
    }

    private static class RhsSource {
        @NonNull
        final String rhs;
        final Rule rule;
        final StringJoiner methodArgs;
        final StringJoiner args;

        RhsSource(Rule rule, @NonNull String rhs) {
            this.rule = rule;
            this.rhs = rhs;
            this.methodArgs = new StringJoiner(", ");
            this.args = new StringJoiner(", ");
            for (NamedType t : rule.getDeclaredFactTypes()) {
                methodArgs.add(t.getType().getJavaType() + " " + t.getName());
                args.add(t.getName());
            }
        }

        void appendClassVar(StringBuilder target) {
            target
                    .append(TAB)
                    .append("public static final " + RHS_CLASS_NAME + " ")
                    .append(RHS_INSTANCE_VAR + " = new " + RHS_CLASS_NAME + "();")
                    .append("\n")
            ;
        }

        void appendClassBody(StringBuilder target) {
            target
                    .append("\n")
                    .append(TAB)
                    .append("public static class " + RHS_CLASS_NAME + " extends ")
                    .append(AbstractLiteralRhs.class.getName())
                    .append(" {\n\n")
                    .append(TAB).append(TAB)
                    .append("@Override\n")
                    .append(TAB).append(TAB)
                    .append("protected final void doRhs() {\n")
            ;

            // Assign vars
            for (NamedType t : rule.getDeclaredFactTypes()) {
                target
                        .append(TAB).append(TAB).append(TAB)
                        .append(t.getType().getJavaType()).append(" ")
                        .append(t.getName())
                        .append(" = ")
                        .append("get(\"")
                        .append(t.getName())
                        .append("\");\n");
            }

            // Inner method call
            target
                    .append(TAB).append(TAB).append(TAB)
                    .append("this.doRhs(")
                    .append(this.args)
                    .append(");\n")
                    .append(TAB).append(TAB)
                    .append("}\n\n")
            ;

            // Inner method declaration
            target.append(TAB).append(TAB)
                    .append("private void doRhs(")
                    .append(this.methodArgs)
                    .append(") {\n")
            ;

            String source = "/***** Start RHS source *****/\n" + this.rhs + "\n" + "/****** End RHS source ******/";
            String[] lines = source.split("\n");
            for (String line : lines) {
                target
                        .append(TAB)
                        .append(TAB)
                        .append(TAB)
                        .append(line)
                        .append("\n");
            }

            // end of the class
            target.append(TAB).append(TAB)
                    .append("}\n")
                    .append(TAB)
                    .append("}")
            ;
        }

    }

    private static class RuleCompiledSourcesImpl<S extends RuleLiteralData<R>, R extends Rule> implements RuleCompiledSources<S, R> {

        private final RuleSource<S, R> source;
        private final Collection<LiteralEvaluator> conditions;
        private final Consumer<RhsContext> rhs;

        public RuleCompiledSourcesImpl(Class<?> ruleClass, RuleSource<S, R> source) {
            this.source = source;

            Map<String, ConditionSource> compiledConditions = new IdentityHashMap<>();
            for (ConditionSource conditionSource : source.conditionSources) {
                compiledConditions.put(conditionSource.source, conditionSource);
            }

            Collection<String> originalConditions = source.delegate.conditions();
            this.conditions = new ArrayList<>(originalConditions.size());

            for (String condition : originalConditions) {
                ConditionSource compiled = compiledConditions.get(condition);
                if (compiled == null) {
                    throw new IllegalStateException("Condition not found or not compiled");
                } else {
                    assert compiled.source.equals(condition);
                    this.conditions.add(new LiteralEvaluatorImpl(getSources().getRule(), compiled, ruleClass));
                }
            }

            // Define RHS if present
            if (source.delegate.rhs() == null) {
                this.rhs = null;
            } else {
                this.rhs = fromClass(ruleClass);
            }
        }

        @SuppressWarnings("unchecked")
        private static Consumer<RhsContext> fromClass(Class<?> ruleClass) {
            try {
                return (Consumer<RhsContext>) ruleClass.getDeclaredField(RHS_INSTANCE_VAR).get(null);
            } catch (IllegalAccessException | NoSuchFieldException e) {
                throw new IllegalStateException("RHS source provided but not compiled");
            }
        }

        @NonNull
        @Override
        public S getSources() {
            return source.delegate;
        }

        @NonNull
        @Override
        public Collection<LiteralEvaluator> conditions() {
            return conditions;
        }

        @Override
        public Consumer<RhsContext> rhs() {
            return this.rhs;
        }
    }

    private static class LiteralEvaluatorImpl implements LiteralEvaluator {
        private final FieldReference[] descriptor;
        private final String source;
        private final MethodHandle handle;
        private final LiteralExpression sourceExpression;

        public LiteralEvaluatorImpl(Rule rule, ConditionSource compiled, Class<?> ruleClass) {
            this.descriptor = compiled.descriptor;
            this.source = compiled.source;
            this.handle = getHandle(ruleClass, compiled.handleName);
            this.sourceExpression = LiteralExpression.of(this.source, rule);
        }

        @Override
        public FieldReference[] descriptor() {
            return descriptor;
        }

        @Override
        public LiteralExpression getSource() {
            return sourceExpression;
        }

        @Override
        public boolean test(IntToValue values) {
            try {
                return (boolean) handle.invoke(values);
            } catch (Throwable t) {
                Object[] args = new Object[descriptor.length];
                for (int i = 0; i < args.length; i++) {
                    args[i] = values.apply(i);
                }
                throw new IllegalStateException("Evaluation exception at '" + source + "', arguments: " + Arrays.toString(descriptor) + " -> " + Arrays.toString(args), t);
            }
        }

        static MethodHandle getHandle(Class<?> compiledClass, String name) {
            try {
                return (MethodHandle) compiledClass.getDeclaredField(name).get(null);
            } catch (IllegalAccessException | NoSuchFieldException e) {
                throw new IllegalStateException("Handle not found", e);
            }
        }
    }
}
