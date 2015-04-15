// Copyright © 2015 HSL

package fi.hsl.parkandride.core.domain;

import com.google.common.base.MoreObjects;
import com.google.common.base.Predicate;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;

import javax.annotation.Nullable;
import javax.validation.ConstraintViolation;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static com.google.common.collect.Maps.filterKeys;
import static fi.hsl.parkandride.core.domain.PropertyPathTranslator.translate;
import static java.util.stream.Collectors.toList;

public class Violation {

    private static final Set<String> EXCLUDED_ARGUMENTS = ImmutableSet.of("message", "groups", "payload");

    public final String type;

    public final Map<String, Object> args;

    public final String path;

    public final String message;

    public Violation(String type) {
        this(type, ImmutableMap.of(), "", type);
    }

    public Violation(String type, String path, String message) {
        this(type, ImmutableMap.of(), path, message);
    }

    public Violation(String type, Map<String, Object> args, String path, String message) {
        this.type = type;
        this.args = ImmutableMap.copyOf(args);
        this.path = path;
        this.message = message;
    }

    public Violation(ConstraintViolation cv) {
        this(getType(cv), getArgs(cv), getPath(cv), cv.getMessage());
    }

    private static Map<String, Object> getArgs(ConstraintViolation<?> cv) {
        // NOTE: This supports only simple types, not annotation parameters!
        return filterKeys(cv.getConstraintDescriptor().getAttributes(), new Predicate<String>() {
            @Override
            public boolean apply(@Nullable String input) {
                return !EXCLUDED_ARGUMENTS.contains(input);
            }
        });
    }

    private static String getPath(ConstraintViolation cv) {
        return translate(cv.getPropertyPath().toString());
    }

    private static String getType(ConstraintViolation constraintViolation) {
        return constraintViolation.getConstraintDescriptor().getAnnotation().annotationType().getSimpleName();
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(getClass())
                .add("type", type)
                .add("args", args)
                .add("path", path)
                .add("message", message)
                .toString();
    }

    public static List<Violation> withPathPrefix(String pathPrefix, List<Violation> violations) {
        return violations.stream()
                .map(v -> new Violation(v.type, v.args, pathPrefix + v.path, v.message))
                .collect(toList());
    }
}
