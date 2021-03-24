package org.evrete.dsl;

import org.evrete.KnowledgeService;
import org.evrete.api.Knowledge;
import org.evrete.api.RuleScope;
import org.evrete.api.StatefulSession;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.FilePermission;

class JavaSourceSecurityTests extends CommonTestMethods {
    private Knowledge runtime;

    @BeforeEach
    void init() {
        KnowledgeService service = new KnowledgeService();
        runtime = service.newKnowledge();
    }

    private StatefulSession session() {
        return runtime.createSession();
    }

    @Test
    void rhsTestFail1() {
        assert System.getSecurityManager() != null;

        Assertions.assertThrows(
                SecurityException.class,
                () -> {
                    applyToRuntimeAsFile(runtime, AbstractJavaDSLProvider.PROVIDER_JAVA_S, new File("src/test/resources/java/SecurityTest1.java"));
                    StatefulSession session = session();
                    session.insert(new Object());
                    session.fire();
                }
        );
    }

    @Test
    void rhsTestFail2() {
        assert System.getSecurityManager() != null;

        runtime.getService().getSecurity().addPermission(RuleScope.LHS, new FilePermission(".", "read"));

        Assertions.assertThrows(
                SecurityException.class,
                () -> {
                    applyToRuntimeAsFile(runtime, AbstractJavaDSLProvider.PROVIDER_JAVA_S, new File("src/test/resources/java/SecurityTest1.java"));
                    StatefulSession session = session();
                    session.insert(new Object());
                    session.fire();
                }
        );
    }

    @Test
    void rhsTestFail3() {
        assert System.getSecurityManager() != null;

        runtime.getService().getSecurity().addPermission(RuleScope.RHS, new FilePermission(".", "read"));

        Assertions.assertThrows(
                SecurityException.class,
                () -> {
                    applyToRuntimeAsFile(runtime, AbstractJavaDSLProvider.PROVIDER_JAVA_S, new File("src/test/resources/java/SecurityTest1.java"));
                    StatefulSession session = session();
                    session.insert(new Object());
                    session.fire();
                }
        );
    }

    @Test
    void rhsTestPass() {
        assert System.getSecurityManager() != null;

        runtime.getService().getSecurity().addPermission(RuleScope.BOTH, new FilePermission(".", "read"));
        applyToRuntimeAsFile(runtime, AbstractJavaDSLProvider.PROVIDER_JAVA_S, new File("src/test/resources/java/SecurityTest1.java"));
        StatefulSession session = session();
        session.insert(new Object());
        session.fire();
    }


    @Test
    void lhsTestFail1() {
        assert System.getSecurityManager() != null;

        Assertions.assertThrows(
                SecurityException.class,
                () -> {
                    runtime.addImport(RuleScope.BOTH, File.class);
                    applyToRuntimeAsFile(runtime, AbstractJavaDSLProvider.PROVIDER_JAVA_S, new File("src/test/resources/java/SecurityTest2.java"));
                    StatefulSession session = session();
                    session.insert(5);
                    session.fire();
                }
        );
    }

    @Test
    void lhsTestPass() {
        assert System.getSecurityManager() != null;
        runtime.getService().getSecurity().addPermission(RuleScope.BOTH, new FilePermission("5", "read"));
        runtime.addImport(RuleScope.BOTH, File.class);
        applyToRuntimeAsFile(runtime, AbstractJavaDSLProvider.PROVIDER_JAVA_S, new File("src/test/resources/java/SecurityTest2.java"));
        StatefulSession session = session();
        session.insert(5);
        session.fire();
    }


    @Test
    void indirectAccessFail() {
        assert System.getSecurityManager() != null;

        Assertions.assertThrows(
                SecurityException.class,
                () -> {
                    applyToRuntimeAsFile(runtime, AbstractJavaDSLProvider.PROVIDER_JAVA_S, new File("src/test/resources/java/SecurityTest3.java"));
                    StatefulSession session = session();
                    session.insert(5);
                    session.fire();
                }
        );
    }

    @Test
    void indirectAccessPass() {
        assert System.getSecurityManager() != null;
        runtime.getService().getSecurity().addPermission(RuleScope.BOTH, new FilePermission("<<ALL FILES>>", "read"));
        applyToRuntimeAsFile(runtime, AbstractJavaDSLProvider.PROVIDER_JAVA_S, new File("src/test/resources/java/SecurityTest3.java"));
        StatefulSession session = session();
        session.insert(5);
        session.fire();
    }
}