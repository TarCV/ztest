package com.github.tarcv.ztest.plugin


import org.gradle.api.file.FileVisitDetails
import org.gradle.api.file.FileVisitor
import org.gradle.api.provider.Property
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.SourceTask
import org.gradle.api.tasks.TaskAction

import static java.util.concurrent.TimeUnit.SECONDS

class CompileAcsTask extends SourceTask {
    private final Property<File> destinationDir = getProject().getObjects().property(File.class)

    @OutputDirectory
    File getDestinationDir() {
        return destinationDir.getOrNull()
    }

    void setDestinationDir(File destinationDir) {
        this.destinationDir.set(destinationDir)
    }

    void setDestinationDir(Provider<File> destinationDir) {
        this.destinationDir.set(destinationDir)
    }

    @TaskAction
    protected void compile() {
        getSource().visit(new FileVisitor() {
            @Override
            void visitDir(FileVisitDetails dirDetails) {
            }

            @Override
            void visitFile(FileVisitDetails fileDetails) {
                File file = fileDetails.file
                String lumpName = file.getName().toUpperCase()
                if (lumpName.endsWith(".ACS")) {
                    buildAcs(file)
                }
            }

            void buildAcs(File file) {
                String accBaseDir = System.getenv("ACC_HOME")
                if (accBaseDir == null || accBaseDir.isEmpty()) {
                    throw new RuntimeException("ACC_HOME env variable should contain path to ACC")
                }
                File accBin = new File(accBaseDir, "acc.exe")
                if (!accBin.exists()) {
                    accBin = new File(accBaseDir, "acc")
                }
                if (!accBin.exists()) {
                    throw new RuntimeException("acc not found in the directory specified by ACC_HOME")
                }
                File destinationFile = new File(getDestinationDir(),
                        file.name.toUpperCase().replaceAll("\\.ACS\$", ".o"));

                List<String> arguments = new ArrayList<>()
                arguments.add(accBin.path)

/*
                FileCollection classpath = getClasspath()
                if (classpath != null) {
                    File includeDir = classpath.singleFile
                    if (!includeDir.isDirectory()) {
                        throw new RuntimeException("Path $includeDir specified as a classpath should be a directory")
                    }
                    arguments.add("-i")
                    arguments.add(includeDir.path)
                }
*/

                arguments.add(file.path)
                arguments.add(destinationFile.path)
                Process accProcess = new ProcessBuilder()
                    .directory(getDestinationDir())
                    .command(arguments)
                    .inheritIO()
                    .start()

                boolean finished = accProcess.waitFor(15, SECONDS)
                if (!finished) {
                    accProcess.destroyForcibly()
                    throw new RuntimeException("Compilation process hasn't finished in 15s, killing")
                }
                def exitValue = accProcess.exitValue()
                if (exitValue != 0) {
                    throw new RuntimeException("ACC returned $exitValue")
                }
            }
        })
    }
}
