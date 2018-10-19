package com.github.tarcv.ztest.plugin

import org.gradle.api.file.FileVisitDetails
import org.gradle.api.file.FileVisitor
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.SourceTask
import org.gradle.api.tasks.TaskAction

import static converter.AcsConverter.convertAcs
import static converter.DecorateConverter.convertDecorate

class GenerateTestSourcesTask extends SourceTask {
    private File generatedDir;

    @OutputDirectory
    File getGeneratedDir() {
        return generatedDir;
    }

    void setGeneratedDir(File generatedFileDir) {
        this.generatedDir = generatedFileDir;
    }

    @TaskAction
    protected void perform() {
        getSource().visit(new FileVisitor() {
            @Override
            void visitDir(FileVisitDetails dirDetails) {
            }

            @Override
            void visitFile(FileVisitDetails fileDetails) {
                File file = fileDetails.file
                String lumpName = file.getName().toUpperCase()
                if (lumpName.endsWith(".ACS")) {
                    convertAcs(file.toPath(), generatedDir.toPath())
                } else if (lumpName.startsWith("DECORATE")) {
                    convertDecorate(file.toPath(), generatedDir.toPath())
                }
            }
        })
    }
}
