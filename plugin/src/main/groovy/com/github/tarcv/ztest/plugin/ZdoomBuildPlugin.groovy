package com.github.tarcv.ztest.plugin


import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.file.FileTreeElement
import org.gradle.api.file.SourceDirectorySet
import org.gradle.api.internal.classpath.ModuleRegistry
import org.gradle.api.internal.file.SourceDirectorySetFactory
import org.gradle.api.internal.plugins.DslObject
import org.gradle.api.internal.tasks.DefaultSourceSet
import org.gradle.api.internal.tasks.DefaultSourceSetOutput
import org.gradle.api.plugins.JavaBasePlugin
import org.gradle.api.plugins.JavaPluginConvention
import org.gradle.api.specs.Spec
import org.gradle.api.tasks.SourceSet
import org.gradle.api.tasks.SourceSetContainer
import org.gradle.api.tasks.bundling.Zip
import org.gradle.internal.Cast

import javax.inject.Inject
import java.nio.file.Paths
import java.util.concurrent.Callable

class ZdoomBuildPlugin implements Plugin<Project> {
    private final SourceDirectorySetFactory sourceDirectorySetFactory
    private final ModuleRegistry moduleRegistry

    private Project project

    @Inject
    ZdoomBuildPlugin(SourceDirectorySetFactory sourceDirectorySetFactory, ModuleRegistry moduleRegistry) {
        this.sourceDirectorySetFactory = sourceDirectorySetFactory
        this.moduleRegistry = moduleRegistry
    }

    void apply(Project project) {
        this.project = project

        project.getPluginManager().apply(JavaBasePlugin.class)

        setupTasks(project)
    }

    private void setupTasks(Project project) {
        SourceSetContainer sourceSets = project.getConvention().getPlugin(JavaPluginConvention.class).getSourceSets()
        SourceSet mainSourceSet = sourceSets.main
        DefaultZdoomSourceSet zdoomSourceSet = new DefaultZdoomSourceSet("zdoom", ((DefaultSourceSet) mainSourceSet).getDisplayName(), sourceDirectorySetFactory)

        setupConvertTask(project, sourceSets, mainSourceSet, zdoomSourceSet)

        CompileAcsTask compileTask = setupAcsCompileTask(project, mainSourceSet, zdoomSourceSet)

        setupPackageTask(project, compileTask, zdoomSourceSet)
    }

    private void setupPackageTask(Project project, CompileAcsTask compileTask, zdoomSourceSet) {
        Task packageTask = project.task(type: Zip, dependsOn: compileTask, "packagePk3") {
            archiveName = "${project.name}.pk3"
            destinationDir = new File("${project.buildDir}/dist")

            from(zdoomSourceSet.zdoom) {
            }

            from(compileTask.destinationDir) {
                into 'acs'
            }
        }
        packageTask.group = "build"
        project.getTasks().getByName("assemble").dependsOn(packageTask)
    }

    private static void setupConvertTask(Project project, SourceSetContainer sourceSets, SourceSet mainSourceSet, DefaultZdoomSourceSet zdoomSourceSet) {
        String convertTaskName = mainSourceSet.getTaskName("convertZdoom", "forTest")

        new DslObject(mainSourceSet).getConvention().getPlugins().put("zdoom", zdoomSourceSet)
        zdoomSourceSet.getZdoom().srcDir("src/" + mainSourceSet.getName() + "/zdoom")
        mainSourceSet.getResources().getFilter().exclude(new Spec<FileTreeElement>() {
            boolean isSatisfiedBy(FileTreeElement element) {
                return zdoomSourceSet.getZdoom().contains(element.getFile())
            }
        })

        GenerateTestSourcesTask convertTask = project.getTasks().create(convertTaskName, GenerateTestSourcesTask.class)
        convertTask.setDescription("Converts the ${zdoomSourceSet.getZdoom()} for use in unit tests.")
        convertTask.setSource(zdoomSourceSet.getZdoom())
        convertTask.setGeneratedDir(Paths.get(project.buildDir.path, 'generated', 'zdoomForTest').toFile())

        SourceSet testSourceSet = sourceSets.test
        testSourceSet.java.srcDir convertTask.generatedDir
        project.getTasks().getByName(testSourceSet.getCompileTaskName("java")).dependsOn(convertTaskName)
    }

    private CompileAcsTask setupAcsCompileTask(Project project, SourceSet mainSourceSet, DefaultZdoomSourceSet zdoomSourceSet) {
        String compileTaskName = mainSourceSet.getCompileTaskName("Acs")

        CompileAcsTask compileTask = project.getTasks().create(compileTaskName, CompileAcsTask.class)
        SourceDirectorySet sourceDirectorySet = zdoomSourceSet.getZdoom()

        compileTask.setDescription("Compiles the ${zdoomSourceSet.getZdoom()} (ACS only).")
        compileTask.setSource(sourceDirectorySet)
        compileTask.setDestinationDir(project.provider(new Callable<File>() {
            @Override
            File call() throws Exception {
                return zdoomSourceSet.getZdoom().getOutputDir()
            }
        }))

        String sourceSetChildPath = "acc.o/${sourceDirectorySet.getName()}/${mainSourceSet.getName()}"
        sourceDirectorySet.setOutputDir(project.provider(new Callable<File>() {
            @Override
            File call() throws Exception {
                if (mainSourceSet.getOutput().isLegacyLayout()) {
                    return mainSourceSet.getOutput().getClassesDir()
                }
                return new File(project.getBuildDir(), sourceSetChildPath)
            }
        }))

        DefaultSourceSetOutput sourceSetOutput = Cast.cast(DefaultSourceSetOutput.class, mainSourceSet.getOutput())
        sourceSetOutput.addClassesDir(new Callable<File>() {
            @Override
            File call() throws Exception {
                return sourceDirectorySet.getOutputDir()
            }
        })

        compileTask
    }

}