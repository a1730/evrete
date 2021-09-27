package org.evrete.examples.run;

import org.evrete.KnowledgeService;
import org.evrete.api.*;

import java.util.HashMap;
import java.util.Map;

class WhoIsFritzAdvanced {
    public static void main(String[] args) {

        KnowledgeService service = new KnowledgeService();

        Knowledge knowledge = service.newKnowledge();

        Type<Subject> subjectType = knowledge.getTypeResolver().declare(Subject.class);
        knowledge.getTypeResolver().wrapType(new SubjectType(subjectType));

        StatefulSession session = knowledge
                .newRule("rule 1")
                .forEach("$s", Subject.class)
                .where("$s.croaks && $s.eatsFlies && !$s.isFrog")
                .execute(
                        ctx -> {
                            Subject $s = ctx.get("$s");
                            $s.set("isFrog");
                            ctx.update($s);
                        }
                )
                .newRule("rule 2")
                .forEach("$s", Subject.class)
                .where("$s.isFrog && !$s.green")
                .execute(
                        ctx -> {
                            Subject $s = ctx.get("$s");
                            $s.set("green");
                            ctx.update($s);
                        }
                )
                .newStatefulSession();

        // Fritz and his known properties
        Subject fritz = new Subject();
        fritz.set("eatsFlies");
        fritz.set("croaks");

        // Insert Fritz and fire all rules
        session.insertAndFire(fritz);

        // Fritz should have been identified as a green frog
        System.out.println(fritz);

        session.close();
        service.shutdown();
    }


    @SuppressWarnings("unused")
    public static class Subject {
        private final Map<String, Boolean> properties = new HashMap<>();
        void set(String prop) {
            properties.put(prop, true);
        }

        boolean isNot(String prop) {
            Boolean s = properties.get(prop);
            return !Boolean.TRUE.equals(s);
        }

        @Override
        public String toString() {
            return "Subject{" + properties +
                    '}';
        }
    }

    public static class SubjectType extends TypeWrapper<Subject> {
        SubjectType(Type<Subject> delegate) {
            super(delegate);
        }

        @Override
        public TypeField getField(String name) {
            TypeField found = super.getField(name);
            if (found == null) {
                found = declareBooleanField(name, subject -> {
                    Boolean state = subject.properties.get(name);
                    return Boolean.TRUE.equals(state);
                });

            }
            return found;
        }
    }
}