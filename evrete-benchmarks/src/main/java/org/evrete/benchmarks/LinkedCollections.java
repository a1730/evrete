package org.evrete.benchmarks;

import org.evrete.benchmarks.jmh.ListCollections;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;
import org.openjdk.jmh.runner.options.TimeValue;


public class LinkedCollections {

    public static void main(String[] args) throws RunnerException {
        TimeValue duration = TimeValue.milliseconds(1000L);
        int iterations = 10;
        Options opt = new OptionsBuilder()
                .include(ListCollections.class.getSimpleName())
                .mode(Mode.Throughput)
                .warmupIterations(iterations)
                .warmupTime(duration)
                .measurementIterations(iterations)
                .measurementTime(duration)
                .build();

        new Runner(opt).run();
    }
}
