package converter;

import java.io.IOException;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static java.lang.System.lineSeparator;
import static java.util.regex.Pattern.CASE_INSENSITIVE;

public class AcsConverter {

    private static final String CREATE_MAIN_SCRIPT_CONTEXT = "public ScriptContext createMainScriptContext(Simulation<T> simulation) {\n" +
            "    Scripts mapContext = new Scripts(simulation, null);\n" +
            "    simulation.registerScriptEventsListener(mapContext); // scriptContext must be fully constructed here\n" +
            "    setScripts(mapContext);\n" +
            "    return mapContext;\n" +
            "}".replace("\n", lineSeparator());
    private static final String SCRIPTS_BEGIN = "Scripts(Simulation<Scripts> simulation, @Nullable MapContext<Scripts> mapContext) {\n" +
            "    super(simulation, mapContext, new ScriptContextCreator<Scripts>()  {\n" +
            "        @Override\n" +
            "        public ScriptContext<Scripts> create(Simulation<Scripts> simulation, MapContext<Scripts> context) {\n" +
            "            return new Scripts(simulation, context);\n" +
            "        }\n" +
            "    }, scripts());\n" +
            "}\n" +
            "\n" +
            "@Override\n" +
            "protected NamedRunnable getScriptRunnable(Script<Scripts> script, Object[] args) {\n" +
            "    return createScriptRunnable(this, script, args);\n" +
            "}".replace("\n", lineSeparator());

    private AcsConverter() {}

    public static void convertAcs(Path file, Path outputDir) throws IOException {
        StringBuffer data = new StringBuffer(new String(Files.readAllBytes(file)));
        StringBuilder converted = new StringBuilder(data.length());

        Pattern libraryMacro = Pattern.compile("^\\s*#library\\s+\".+?\"[^\\r\\n]*", CASE_INSENSITIVE | Pattern.MULTILINE);
        Pattern ignoredInclude = Pattern.compile("^\\s*#include\\s+\"zcommon\\.acs\"[^\\r\\n]*", CASE_INSENSITIVE | Pattern.MULTILINE);
        data = ConvertUtils.removeByPattern(data, libraryMacro);
        data = ConvertUtils.removeByPattern(data, ignoredInclude);

        StringBuilder global = new StringBuilder();
        StringBuilder globalInit = new StringBuilder();
        StringBuilder globalConstants = new StringBuilder();
        StringBuilder world = new StringBuilder();
        StringBuilder worldInit = new StringBuilder();
        StringBuilder map = new StringBuilder();
        StringBuilder mapVars = new StringBuilder();
        StringBuilder scripts = new StringBuilder();
        StringBuilder additionalScriptJava = new StringBuilder();

        Pattern additionalMethod = Pattern.compile(
                "^\\s*/\\*\\*TEST_ONLY_SCRIPTS\\s+([\\S\\s]+?)\\*\\*/$",
                Pattern.MULTILINE);
        Pattern script = Pattern.compile(
                "^script\\s+(\"\\w+\"|\\w+)\\s*(?:\\((\\s*void\\s*|\\s*(?:\\/\\*\\*TEST_TYPE:\\w+\\*\\*\\/)?\\w+\\s+\\w+\\s*(?:,\\s*\\w+\\s+\\w+\\s*)*)\\))?((?:\\s+\\w+)*)\\s*\\{([^}]+)\\}",
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

        ConvertUtils.DataPair dataPair = ConvertUtils.tryParseAndRemove(new ConvertUtils.DataPair(data), additionalMethod, additionalMethodGroups -> {
            String body = additionalMethodGroups.group(1);
            if (additionalScriptJava.length() > 0) {
                additionalScriptJava.append(lineSeparator());
            }
            additionalScriptJava.append(body);
        });

        dataPair = ConvertUtils.tryParseAndRemove(dataPair, script, scriptGroups -> {
            String name = scriptGroups.group(1);
            if (name.startsWith("\"") && name.endsWith("\"")) {
                name = name.substring(1, name.length() -1);
            }

            String arguments = convertArguments(scriptGroups.group(2));
            String[] argumentArray = arguments.split(",");
            if (argumentArray.length == 1 && argumentArray[0].isEmpty()) {
                argumentArray = new String[0];
            }

            String types = scriptGroups.group(3).trim();
            String body = scriptGroups.group(4);

            StringBuilder argLambda = new StringBuilder("(t, a) -> t.")
                    .append(name).append("(");
            int argIndex = 0;
            for (String argument : argumentArray) {
                String[] argParts = argument.trim().split("\\s+");
                String type = String.join("", Arrays.copyOf(argParts, argParts.length - 1));
                if (argIndex > 0) {
                    argLambda.append(", ");
                }
                argLambda.append("(").append(type).append(") a[").append(argIndex).append("]");

                ++argIndex;
            }
            argLambda.append(")");

            if (scripts.length() > 0) {
                scripts.append(", ").append(lineSeparator());
            }
            scripts.append("\t\tnew Script<>(\"")
                    .append(name).append("\", ")
                    .append(argumentArray.length).append(", ")
                    .append(argLambda);
            if (types != null && !types.isEmpty()) {
                String[] typesArr = types.trim().toUpperCase().split("\\s");
                for (String type : typesArr) {
                    scripts.append(", ").append(type);
                }
            }
            scripts.append(")");

            if (map.length() > 0) {
                map.append(lineSeparator());
            }
            map.append("void ").append(name).append("(").append(arguments).append(") throws SuspendExecution {");
            map.append(convertScriptBody(body));
            map.append("}").append(lineSeparator());
        });

        dataPair = ConvertUtils.tryParseAndRemove(dataPair, function, functionGroups -> {
            String returnType = convertType(functionGroups.group(1));
            String name = functionGroups.group(2);

            String arguments = convertArguments(functionGroups.group(3));

            String body = functionGroups.group(4);

            if (map.length() > 0) {
                map.append(lineSeparator());
            }
            map.append(returnType).append(" ").append(name).append("(").append(arguments).append(")").append("{");
            map.append(convertScriptBody(body));
            map.append("}").append(lineSeparator());
        });

        dataPair = ConvertUtils.tryReplace(dataPair, globalVar, globalVarGroups -> {
            String scope = globalVarGroups.group(1).trim().toLowerCase();
            StringBuilder scopeBuilder;
            StringBuilder scopeInitBuilder;
            if ("global".equals(scope)) {
                scopeBuilder = global;
                scopeInitBuilder = globalInit;
            } else if ("world".equals(scope)) {
                scopeBuilder = world;
                scopeInitBuilder = worldInit;
            } else {
                return globalVarGroups.group();
            }

            String type = convertType(globalVarGroups.group(2));
            String name = globalVarGroups.group(3);
            boolean isArray = name.trim().endsWith("]");

            scopeBuilder.append("\tstatic ").append(type).append(" ").append(name);
            if (isArray) {
                int nameEnds = name.lastIndexOf("[");
                scopeInitBuilder.append(lineSeparator())
                        .append("\t\t").append(name, 0, nameEnds).append(" = new ").append(type).append("[1000];");
            } else if ("int".equals(type)) {
                scopeInitBuilder.append(lineSeparator())
                        .append("\t\t").append(name).append(" = 0;");
            }
            scopeBuilder.append(";").append(lineSeparator());

            return "";
        });

        dataPair = ConvertUtils.tryReplace(dataPair, var, varGroups -> {
            String type = convertType(varGroups.group(1));
            String name = varGroups.group(2).trim();
            String value = varGroups.group(3);
            boolean isArray = name.trim().endsWith("]");
            boolean hasValue = value != null && !value.isEmpty();
            String sizePart = "";

            mapVars.append(type);

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
                    mapVars.append("new ").append(type).append(sizePart);
                }
            }
            mapVars.append(";").append(lineSeparator());

            return "";
        });

        dataPair = ConvertUtils.tryParseAndRemove(dataPair, constant, constantGroups -> {
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
                    .append(lineSeparator())
                    .append("\tstatic final ").append(type).append(" ").append(name).append(" = ").append(value)
                    .append(";");
        });

        String safeClassName = file.getFileName().toString().replace(".", "_");
        try (Writer writer = Files.newBufferedWriter(outputDir.resolve(safeClassName + ".java"))) {

            writer.append("package zdoom;").append(lineSeparator());
            writer.append("import co.paralleluniverse.fibers.SuspendExecution;").append(lineSeparator());
            writer.append("import com.github.tarcv.ztest.simulation.*;").append(lineSeparator());
            writer.append("import com.github.tarcv.ztest.simulation.ScriptContext.Script;").append(lineSeparator());
            writer.append("import org.jetbrains.annotations.Nullable;").append(lineSeparator())
                    .append(lineSeparator());

            writer.append("import java.util.Arrays;").append(lineSeparator());
            writer.append("import java.util.List;").append(lineSeparator())
                    .append(lineSeparator());

            writer.append("import static com.github.tarcv.ztest.simulation.ScriptContext.ScriptType.*;").append(lineSeparator());
            writer.append("import static com.github.tarcv.ztest.simulation.AcsConstants.*;").append(lineSeparator());
            writer.append("import static zdoom.Global").append(safeClassName).append(".*;").append(lineSeparator());
            writer.append("import static zdoom.World").append(safeClassName).append(".*;").append(lineSeparator());
            writer.append(lineSeparator());
            writer.append("class Global").append(safeClassName).append(" {");
            writer.append(globalConstants).append(lineSeparator()).append(lineSeparator());
            if (global.length() > 0) {
                writer.append(global).append(lineSeparator()).append(lineSeparator());
            }
            writer.append("\tpublic static void reset() {");
            writer.append(globalInit).append(lineSeparator());
            writer.append("\t}").append(lineSeparator());
            writer.append("}").append(lineSeparator()).append(lineSeparator());

            writer.append("class World").append(safeClassName).append(" {").append(lineSeparator());
            if (world.length() > 0) {
                writer.append(world).append(lineSeparator()).append(lineSeparator());
            }
            writer.append("\tpublic static void reset() {");
            writer.append(worldInit).append(lineSeparator());
            writer.append("\t}").append(lineSeparator());
            writer.append("}").append(lineSeparator()).append(lineSeparator());

            writer.append("class Map").append(safeClassName)
                    .append(" extends VarContext<Map").append(safeClassName).append(".Scripts> {")
                    .append(lineSeparator());
            writer.append(mapVars).append(lineSeparator());
            writer.append(CREATE_MAIN_SCRIPT_CONTEXT.replace("<T>", "<Map" + safeClassName + ".Scripts>")).append(lineSeparator()).append(lineSeparator());
            writer.append("private static List<Script<Scripts>> scripts() {").append(lineSeparator())
                    .append("\treturn Arrays.asList(").append(lineSeparator())
                    .append(scripts).append(lineSeparator())
                    .append("\t);").append(lineSeparator())
                    .append("}").append(lineSeparator()).append(lineSeparator());
            writer.append("public class Scripts extends ScriptContext<Scripts> {").append(lineSeparator());
            writer.append(SCRIPTS_BEGIN).append(lineSeparator()).append(lineSeparator());
            writer.append(map).append(lineSeparator());
            writer.append(additionalScriptJava).append(lineSeparator());
            writer.append("}").append(lineSeparator());
            writer.append("}").append(lineSeparator());
        }

        String leftOutFinal = dataPair.data.toString();
        leftOutFinal = cleanupLeftoutData(leftOutFinal);
        if (!leftOutFinal.isEmpty() && !";".equals(leftOutFinal)) {
            throw new IllegalStateException(String.format("Didn't understood: %s%s", lineSeparator(), leftOutFinal));
        }
    }

    private static StringBuffer convertScriptBody(String body) {
        Pattern print = Pattern.compile(
                "(?<=\\W|_)(print|printbold|hudmessage|hudmessagebold)\\s*\\(([^;)]+)(?:;([^)]+))?\\)",
                CASE_INSENSITIVE);
        Pattern outputArg = Pattern.compile("\\S*([bcdfiklnsx]):([^,]+)\\S*(?:,)?");
        ConvertUtils.DataPair bodyPair = ConvertUtils.tryReplace(new ConvertUtils.DataPair(body), print, printGroups -> {
            String function = printGroups.group(1);
            String output = printGroups.group(2);
            String otherArgs = printGroups.group(3);

            StringBuilder format = new StringBuilder();
            StringBuilder outputArgs = new StringBuilder();

            ConvertUtils.tryParseAndRemove(new ConvertUtils.DataPair(output), outputArg, outputGroups -> {
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

        bodyPair = ConvertUtils.tryReplace(bodyPair, Pattern.compile("(?<=\\W|_)bool(?=\\W|_)"), groups -> "boolean");
        bodyPair = ConvertUtils.tryReplace(bodyPair, Pattern.compile("(?<=\\W|_)str(?=\\W|_)"), groups -> "String");
        bodyPair = ConvertUtils.tryReplace(bodyPair, Pattern.compile("(?<=\\W|_)terminate(?=\\W|_)"), groups -> "terminate()");
        bodyPair = ConvertUtils.tryReplace(bodyPair, Pattern.compile("(?<=\\W|_)class(?=\\W|_)"), groups -> "class__");
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
                            String name = parts[1];
                            if ("class".equals(name)) {
                                name = "class__";
                            }
                            return type + " " + name;
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
            case "/**test_type:bool**/int":
            case "bool":
                return "boolean";
            case "/**test_type:str**/int":
            case "str":
                return "String";
            default:
                return acsType.trim().toLowerCase();
        }
    }
}
