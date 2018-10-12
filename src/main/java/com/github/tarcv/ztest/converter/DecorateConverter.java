package com.github.tarcv.ztest.converter;

import java.io.IOException;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.github.tarcv.ztest.converter.ConvertUtils.removeByPattern;
import static com.github.tarcv.ztest.converter.ConvertUtils.tryReplace;
import static java.util.regex.Pattern.*;

class DecorateConverter {
    private DecorateConverter() {}

    static void convertDecorate(Path file) throws IOException {
        StringBuffer data = new StringBuffer(new String(Files.readAllBytes(file)));
        StringBuilder converted = new StringBuilder(data.length());

        Pattern sectionPattern = Pattern.compile(
                "\\s*(Actor|DamageType)\\s+(\\w+)\\s+(?::\\s+(\\w+)\\s+)?(?>\\{)(.*?)\\}\\s*(?=actor|damagetype|\\Z)",
                DOTALL | CASE_INSENSITIVE);
        Pattern lineCommentPattern = Pattern.compile("\\s*\\/\\/[^\\r\\n]*", DOTALL | CASE_INSENSITIVE);

        data = removeByPattern(data, lineCommentPattern);

        StringBuffer leftOut;
        leftOut = tryReplace(data, sectionPattern, matcher -> {
            String type = matcher.group(1);
            String name = matcher.group(2);
            String parent = matcher.group(3);
            String body = matcher.group(4);

            boolean understood = true;
            StringBuffer leftOutBody = new StringBuffer(body);
            if ("ACTOR".equalsIgnoreCase(type)) {
                leftOutBody = convertActor(leftOutBody, converted, name, parent);
            } else if ("DAMAGETYPE".equalsIgnoreCase(type)) {
                leftOutBody = convertActor(leftOutBody, converted, name, "DamageType");
            } else {
                understood = false;
            }

            if (understood) {
                return leftOutBody.toString();
            } else {
                return matcher.group(0);
            }
        });

        String leftOutFinal = leftOut.toString().trim();
        if (!leftOutFinal.isEmpty()) {
            throw new IllegalStateException(String.format("Didn't understood: %s%s", System.lineSeparator(), leftOutFinal));
        }

        try (Writer writer = Files.newBufferedWriter(Paths.get(file.getFileName() + ".java"))) {
            writer.append("package zdoom;").append(System.lineSeparator());
            writer.append("import com.github.tarcv.converter.decorate.*;").append(System.lineSeparator());
            writer.append("import static Constants.*;").append(System.lineSeparator());
            writer.write(System.lineSeparator());
            writer.write(converted.toString());
        }
    }

    private static StringBuffer convertActor(StringBuffer leftOutBody, StringBuilder converted, String name, String parent) {
        String actualParent = "Actor";
        if (parent != null && !parent.isEmpty()) {
            actualParent = parent;
        }
        converted.append("class ").append(name);
        converted.append(" extends ").append(actualParent);
        converted.append(" {").append(System.lineSeparator());

        StringBuilder ctor = new StringBuilder();
        StringBuilder methods = new StringBuilder();

        Pattern addFlag = Pattern.compile("^\\s*\\+([a-z.]+)\\s*$", MULTILINE | CASE_INSENSITIVE);
        Pattern removeFlag = Pattern.compile("^\\s*\\-([a-z.]+)\\s*$", MULTILINE | CASE_INSENSITIVE);
        Pattern setFlag = Pattern.compile("^\\s*([a-z.]+)\\s*$", MULTILINE | CASE_INSENSITIVE);
        Pattern property = Pattern.compile("^\\s*([a-z.]+)\\s+(.+?)\\s*$", MULTILINE | CASE_INSENSITIVE);
        Pattern states = Pattern.compile("^\\s*States\\s+\\{([\\S\\s]+)?\\}\\s*$", MULTILINE | CASE_INSENSITIVE);
        Pattern animation = Pattern.compile("^\\s*(\\w+):((?:\\s*^\\s*\\w+(?:\\s+.+|$)(?:[\\r\\n]|$))+)$", MULTILINE);
        Pattern animationLine = Pattern.compile("^\\s*(\\w+)\\s+(\\w+)\\s+(\\d+)(?:\\s(.+))?\\s*$", MULTILINE);

        leftOutBody = tryReplace(leftOutBody, states, flagMatcher -> {
            String statesBody = flagMatcher.group(1);

            StringBuffer leftOutStates = new StringBuffer(statesBody);
            leftOutStates = tryReplace(leftOutStates, animation, animationMatcher -> {
                String animationName = animationMatcher.group(1);
                String animationBody = animationMatcher.group(2);
                StringBuilder leftOutLines = new StringBuilder();
                StringBuilder methodBody = new StringBuilder();
                for (String sl : animationBody.split("\n")) {
                    String stateLine = sl.trim();
                    Matcher animationLineMatcher = animationLine.matcher(stateLine);
                    if ("stop".equalsIgnoreCase(stateLine)) {
                        methodBody.append("\t\treturn null;").append(System.lineSeparator());
                    } else if ("loop".equalsIgnoreCase(stateLine)) {
                        methodBody.insert(0, "\t\twhile (true) {" + System.lineSeparator());
                        methodBody.append("\t\t}").append(System.lineSeparator());
                    } else if (animationLineMatcher.matches()) {
                        methodBody.append("\t\tstates(")
                                .append("\"").append(animationLineMatcher.group(1)).append("\", ")
                                .append("\"").append(animationLineMatcher.group(2)).append("\", ")
                                .append(animationLineMatcher.group(3));
                        String pointer = animationLineMatcher.group(4);
                        if (pointer != null && !pointer.isEmpty()) {
                            methodBody.append(", t -> t.").append(pointer);
                            if (!pointer.contains("(")) {
                                methodBody.append("()");
                            }
                        }
                        methodBody.append(");").append(System.lineSeparator());
                    } else {
                        leftOutLines.append(stateLine).append(System.lineSeparator());
                    }
                }
                methods.append("\t@Override public Object ").append(animationName).append("() {").append(System.lineSeparator())
                        .append(methodBody).append(System.lineSeparator())
                        .append("\t}").append(System.lineSeparator());

                return leftOutLines.toString();
            });
            return leftOutStates.toString();
        });

        leftOutBody = ConvertUtils.tryParseAndRemove(leftOutBody, addFlag, flagMatcher -> {
            String flagName = flagMatcher.group(1);
            ctor.append("\t\taddFlag(\"").append(flagName).append("\");").append(System.lineSeparator());
        });

        leftOutBody = ConvertUtils.tryParseAndRemove(leftOutBody, removeFlag, flagMatcher -> {
            String flagName = flagMatcher.group(1);
            ctor.append("\t\tremoveFlag(\"").append(flagName).append("\");").append(System.lineSeparator());
        });

        leftOutBody = ConvertUtils.tryParseAndRemove(leftOutBody, setFlag, flagSetMatcher -> {
            String setName = flagSetMatcher.group(1);
            ctor.append("\t\tsetFlag(\"").append(setName).append("\");").append(System.lineSeparator());
        });

        leftOutBody = ConvertUtils.tryParseAndRemove(leftOutBody, property, flagMatcher -> {
            String propertyName = flagMatcher.group(1);
            String propertyValue = flagMatcher.group(2);
            ctor.append("\t\tsetProperty(\"").append(propertyName).append("\", ").append(propertyValue).append(");")
                    .append(System.lineSeparator());
        });

        converted.append("\t").append(name).append("() {").append(System.lineSeparator())
                .append("\t\tsuper();").append(System.lineSeparator())
                .append(ctor)
                .append("\t}").append(System.lineSeparator());

        converted.append(methods);

        converted.append(" }").append(System.lineSeparator());
        return leftOutBody;
    }
}
