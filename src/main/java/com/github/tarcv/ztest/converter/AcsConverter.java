package com.github.tarcv.ztest.converter;

import com.github.tarcv.ztest.converter.ConvertUtils.DataPair;

import java.io.IOException;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.github.tarcv.ztest.converter.ConvertUtils.removeByPattern;
import static com.github.tarcv.ztest.converter.ConvertUtils.tryParseAndRemove;
import static com.github.tarcv.ztest.converter.ConvertUtils.tryReplace;
import static java.util.regex.Pattern.CASE_INSENSITIVE;

public class AcsConverter {
    private AcsConverter() {}

    static void convertAcs(Path file) throws IOException {
        StringBuffer data = new StringBuffer(new String(Files.readAllBytes(file)));
        StringBuilder converted = new StringBuilder(data.length());

        Pattern libraryMacro = Pattern.compile("^\\s*#library\\s+\".+?\"[^\\r\\n]*", CASE_INSENSITIVE | Pattern.MULTILINE);
        Pattern ignoredInclude = Pattern.compile("^\\s*#include\\s+\"zcommon\\.acs\"[^\\r\\n]*", CASE_INSENSITIVE | Pattern.MULTILINE);
        data = removeByPattern(data, libraryMacro);
        data = removeByPattern(data, ignoredInclude);

        StringBuilder global = new StringBuilder();
        StringBuilder globalConstants = new StringBuilder();
        StringBuilder world = new StringBuilder();
        StringBuilder map = new StringBuilder();
        StringBuilder mapVars = new StringBuilder();
        StringBuilder ctor = new StringBuilder();

        Pattern script = Pattern.compile(
                "^script\\s+(\"\\w+\"|\\w+)\\s*(?:\\((\\s*void\\s*|\\s*\\w+\\s+\\w+\\s*(?:,\\s*\\w+\\s+\\w+\\s*)*)\\))?((?:\\s+\\w+)*)\\s*\\{([^}]+)\\}",
                CASE_INSENSITIVE | Pattern.MULTILINE);
        Pattern function = Pattern.compile(
                "^function\\s+(\\w+)\\s+(\\w+)\\s*(?:\\((\\s*void\\s*|\\s*\\w+\\s+\\w+\\s*(?:,\\s*\\w+\\s+\\w+\\s*)*)\\))\\s*\\{([^}]+)\\}",
                CASE_INSENSITIVE | Pattern.MULTILINE);
        Pattern globalVar = Pattern.compile(
                "^(global|world)\\s+(bool|int|str)\\s+\\d+:([^\\s=;]+)\\s*;",
                CASE_INSENSITIVE | Pattern.MULTILINE);
        Pattern var = Pattern.compile(
                "^(bool|int|str)\\s+([^\\s=;]+)(?:\\s*=\\s*([^=;]+))?\\s*",
                CASE_INSENSITIVE | Pattern.MULTILINE);
        Pattern constant = Pattern.compile(
                "^\\s*#(?:lib)?define\\s+(\\w+)\\s+(.+)$",
                CASE_INSENSITIVE | Pattern.MULTILINE);

        DataPair dataPair = tryParseAndRemove(new DataPair(data), script, scriptGroups -> {
            String name = scriptGroups.group(1);
            if (name.startsWith("\"") && name.endsWith("\"")) {
                name = name.substring(1, name.length() -1);
            }

            String arguments = convertArguments(scriptGroups.group(2));

            String types = scriptGroups.group(3).trim();
            String body = scriptGroups.group(4);

            ctor.append("\t\taddScript(\"").append(name).append("\"");
            if (types != null && !types.isEmpty()) {
                String[] typesArr = types.trim().split("\\s");
                for (String type : typesArr) {
                    ctor.append(", \"").append(type).append("\"");
                }
            }
            ctor.append(");").append(System.lineSeparator());

            map.append("\tvoid Script_").append(name).append("(").append(arguments).append(")").append("{")
                    .append(System.lineSeparator());
            map.append(convertScriptBody(body));
            map.append("}").append(System.lineSeparator());
        });

        dataPair = tryParseAndRemove(dataPair, function, functionGroups -> {
            String returnType = convertType(functionGroups.group(1));
            String name = functionGroups.group(2);

            String arguments = convertArguments(functionGroups.group(3));

            String body = functionGroups.group(4);

            map.append("\t").append(returnType).append(" ").append(name).append("(").append(arguments).append(")").append("{")
                    .append(System.lineSeparator());
            map.append(convertScriptBody(body));
            map.append("}").append(System.lineSeparator());
        });

        dataPair = tryReplace(dataPair, globalVar, globalVarGroups -> {
            String scope = globalVarGroups.group(1).trim().toLowerCase();
            StringBuilder scopeBuilder;
            if ("global".equals(scope)) {
                scopeBuilder = global;
            } else if ("world".equals(scope)) {
                scopeBuilder = world;
            } else {
                return globalVarGroups.group();
            }

            String type = convertType(globalVarGroups.group(2));
            String name = globalVarGroups.group(3);
            boolean isArray = name.trim().endsWith("]");

            scopeBuilder.append("static ").append(type).append(" ").append(name);
            if (isArray) {
                scopeBuilder.append(" = new ").append(type).append("[100]");
            }
            scopeBuilder.append(";").append(System.lineSeparator());

            return "";
        });

        dataPair = tryReplace(dataPair, var, varGroups -> {
            String type = convertType(varGroups.group(1));
            String itemType = type;
            String name = varGroups.group(2).trim();
            String value = varGroups.group(3);
            boolean isArray = name.trim().endsWith("]");
            boolean hasValue = value != null && !value.isEmpty();
            String sizePart = "";

            mapVars.append(itemType);

            if (isArray) {
                int sizeStart = name.indexOf('[');
                int sizeEnd = name.lastIndexOf(']');
                sizePart = name.substring(sizeStart, sizeEnd + 1);
                name = name.substring(0, sizeStart);

                sizeStart = 0;
                while (sizeStart >= 0) {
                    mapVars.append("[]");
                    sizeStart = sizePart.indexOf('[', sizeStart + 1);
                }
            }

            mapVars.append(" ").append(name);
            if (isArray || hasValue) {
                mapVars.append(" = ");
                if (hasValue) {
                    mapVars.append(value);
                } else if (isArray) {
                    mapVars.append("new ").append(itemType).append(sizePart);
                }
            }
            mapVars.append(";").append(System.lineSeparator());

            return "";
        });

        dataPair = tryParseAndRemove(dataPair, constant, constantGroups -> {
            String name = constantGroups.group(1).trim();
            String value = constantGroups.group(2).trim();
            String type;
            if (value.startsWith("\"") && value.endsWith("\"")) {
                type = "String";
            } else if (value.contains(".")) {
                type = "float";
            } else {
                type = "int";
            }

            globalConstants
                    .append("static final ").append(type).append(" ").append(name).append(" = ").append(value)
                    .append(";").append(System.lineSeparator());
        });

        try (Writer writer = Files.newBufferedWriter(Paths.get(file.getFileName() + ".java"))) {
            String safeClassName = file.getFileName().toString().replace(".", "_");

            writer.append("package zdoom;").append(System.lineSeparator());
            writer.append("import com.github.tarcv.converter.acs.*;").append(System.lineSeparator());
            writer.append("import static Symbols.*;").append(System.lineSeparator());
            writer.append("import static zdoom.Global").append(safeClassName).append(".*;").append(System.lineSeparator());
            writer.append("import static zdoom.World").append(safeClassName).append(".*;").append(System.lineSeparator());
            writer.append(System.lineSeparator());
            writer.append("class Global").append(safeClassName).append(" {").append(System.lineSeparator());
            writer.append(globalConstants).append(System.lineSeparator());
            writer.append(global).append(System.lineSeparator());
            writer.append("}").append(System.lineSeparator()).append(System.lineSeparator());

            writer.append("class World").append(safeClassName).append(" {").append(System.lineSeparator());
            writer.append(world).append(System.lineSeparator());
            writer.append("}").append(System.lineSeparator()).append(System.lineSeparator());

            writer.append("class Map").append(safeClassName).append(" extends MapContext {").append(System.lineSeparator());
            writer.append("Map").append(safeClassName).append("() {")
                    .append("\t\tsuper();").append(System.lineSeparator())
                    .append(ctor).append(System.lineSeparator())
                    .append("}").append(System.lineSeparator());
            writer.append(mapVars).append(System.lineSeparator());
            writer.append(map).append(System.lineSeparator());
            writer.append("}").append(System.lineSeparator()).append(System.lineSeparator());
        }

        String leftOutFinal = dataPair.data.toString();
        leftOutFinal = cleanupLeftoutData(leftOutFinal);
        if (!leftOutFinal.isEmpty() && !";".equals(leftOutFinal)) {
            throw new IllegalStateException(String.format("Didn't understood: %s%s", System.lineSeparator(), leftOutFinal));
        }
    }

    private static StringBuffer convertScriptBody(String body) {
        Pattern print = Pattern.compile(
                "(?<=\\W)(print|printbold|hudmessage|hudmessagebold)\\s*\\(([^;)]+)(?:;([^)]+))?\\)",
                CASE_INSENSITIVE);
        Pattern outputArg = Pattern.compile("\\S*([bcdfiklnsx]):([^,]+)\\S*(?:,)?");
        DataPair bodyPair = tryReplace(new DataPair(body), print, printGroups -> {
            String function = printGroups.group(1);
            String output = printGroups.group(2);
            String otherArgs = printGroups.group(3);

            StringBuilder format = new StringBuilder();
            StringBuilder outputArgs = new StringBuilder();

            tryParseAndRemove(new DataPair(output), outputArg, outputGroups -> {
                String type = outputGroups.group(1).trim().toLowerCase();
                String value = outputGroups.group(2);
                if (outputArgs.length() > 0) {
                    outputArgs.append(", ");
                }
                switch (type) {
                    case "b":
                        format.append("%s");
                        outputArgs.append("Integer.toBinaryString(").append(value).append(")");
                        return;
                    case "c":
                        format.append("%c");
                        outputArgs.append(value);
                        return;
                    case "d":
                    case "i":
                        format.append("%d");
                        outputArgs.append(value);
                        return;
                    case "f":
                        format.append("%f");
                        outputArgs.append(value);
                        return;
                    case "k":
                        format.append("[%s]");
                        outputArgs.append(value);
                        return;
                    case "l":
                        format.append("<localized:%s>");
                        outputArgs.append(value);
                        return;
                    case "n":
                        format.append("<printname:%s>");
                        outputArgs.append(value);
                        return;
                    case "s":
                        format.append("%s");
                        outputArgs.append(value);
                        return;
                    case "x":
                        format.append("%x");
                        outputArgs.append(value);
                        return;
                    default:
                        throw new IllegalArgumentException(String.format("Unknown type '%s'", type));
                }
            });

            if (otherArgs != null && !otherArgs.trim().isEmpty()) {
                otherArgs += ", ";
            } else {
                otherArgs = "";
            }

            return String.format("%s(%s\"%s\", %s)", function, otherArgs, format, outputArgs);
        });
        return bodyPair.data;
    }

    private static String cleanupLeftoutData(String leftOutFinal) {
        int lastLen = -1;
        while (lastLen != leftOutFinal.length()) {
            lastLen = leftOutFinal.length();
            leftOutFinal = leftOutFinal.replaceAll(";\\s*;", ";").trim();
        }
        return leftOutFinal;
    }

    private static String convertArguments(String acsArguments) {
        String arguments = acsArguments;
        if (arguments != null) {
            if (arguments.toLowerCase().equals("void")) {
                arguments = "";
            } else {
                arguments = Stream.of(arguments.trim().split(","))
                        .map(pair -> {
                            String[] parts = pair.trim().split(" ", 2);
                            String type = convertType(parts[0]);
                            return type + " " + parts[1];
                        })
                        .collect(Collectors.joining(", "));
            }
        } else {
            arguments = "";
        }
        return arguments;
    }

    private static String convertType(String acsType) {
        switch (acsType.trim().toLowerCase()) {
            case "bool":
                return "boolean";
            case "str":
                return "String";
            default:
                return acsType.trim().toLowerCase();
        }
    }
}
