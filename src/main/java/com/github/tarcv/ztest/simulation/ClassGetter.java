package com.github.tarcv.ztest.simulation;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;

public class ClassGetter {
    private final List<String> packages;

    ClassGetter() {
        packages = Arrays.asList(
                "com.github.tarcv.ztest.simulation.builtin",
                "zdoom"
        );
    }

    Class<?> forSimpleName(String name) throws ClassNotFoundException {
        Object[] candidates = packages.stream()
                .map(p -> {
                    try {
                        return Class.forName(String.format("%s.%s", p, name));
                    } catch (ClassNotFoundException e) {
                        // intentionally no reporting
                        return null;
                    }
                })
                .filter(Objects::nonNull)
                .toArray();
        if (candidates.length == 0) {
            throw new ClassNotFoundException(String.format("No classes found by simple name %s", name));
        } else if (candidates.length > 1) {
            throw new AssertionError("Too many classes found");
        }
        return (Class<?>) candidates[0];
    }
}
