package com.github.tarcv.ztest.plugin


import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.file.FileTreeElement
import org.gradle.api.internal.classpath.ModuleRegistry
import org.gradle.api.internal.file.SourceDirectorySetFactory
import org.gradle.api.internal.plugins.DslObject
import org.gradle.api.internal.tasks.DefaultSourceSet
import org.gradle.api.plugins.JavaBasePlugin
import org.gradle.api.plugins.JavaPluginConvention
import org.gradle.api.specs.Spec
import org.gradle.api.tasks.SourceSet
import org.gradle.api.tasks.SourceSetContainer

import javax.inject.Inject
import java.nio.file.Paths

class ZdoomBuildPlugin implements Plugin<Project> {
    private final SourceDirectorySetFactory sourceDirectorySetFactory;
    private final ModuleRegistry moduleRegistry;

    private Project project;

    @Inject
    ZdoomBuildPlugin(SourceDirectorySetFactory sourceDirectorySetFactory, ModuleRegistry moduleRegistry) {
        this.sourceDirectorySetFactory = sourceDirectorySetFactory;
        this.moduleRegistry = moduleRegistry;
    }

    void apply(Project project) {
        this.project = project

        project.getPluginManager().apply(JavaBasePlugin.class)

        setupConversionTask(project)
    }

    private void setupConversionTask(Project project) {
        SourceSetContainer sourceSets = project.getConvention().getPlugin(JavaPluginConvention.class).getSourceSets()
        SourceSet sourceSet = sourceSets.main

        final DefaultZdoomSourceSet zdoomSourceSet = new DefaultZdoomSourceSet(((DefaultSourceSet) sourceSet).getDisplayName(), sourceDirectorySetFactory)
        new DslObject(sourceSet).getConvention().getPlugins().put("zdoom", zdoomSourceSet)
        zdoomSourceSet.getZdoom().srcDir("src/" + sourceSet.getName() + "/zdoom")
        sourceSet.getResources().getFilter().exclude(new Spec<FileTreeElement>() {
            boolean isSatisfiedBy(FileTreeElement element) {
                return zdoomSourceSet.getZdoom().contains(element.getFile());
            }
        })

        String taskName = sourceSet.getTaskName("convertZdoom", "forTest")
        GenerateTestSourcesTask task = project.getTasks().create(taskName, GenerateTestSourcesTask.class)
        task.setDescription("Compiles the " + sourceSet.getName() + " ZDoom mod source.")
        task.setSource(zdoomSourceSet.getZdoom())
        task.setGeneratedDir(Paths.get(project.buildDir.path, 'generated', 'zdoomForTest').toFile())

        SourceSet testSourceSet = sourceSets.test
        testSourceSet.java.srcDir task.generatedDir

        project.getTasks().getByName(testSourceSet.getCompileTaskName("java")).dependsOn(taskName)
    }

}