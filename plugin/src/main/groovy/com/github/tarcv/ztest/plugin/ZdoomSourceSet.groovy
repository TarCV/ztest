package com.github.tarcv.ztest.plugin

import org.gradle.api.Action
import org.gradle.api.file.SourceDirectorySet
import org.jetbrains.annotations.NotNull
import org.jetbrains.annotations.Nullable

interface ZdoomSourceSet {
    @NotNull SourceDirectorySet getZdoom()

    ZdoomSourceSet zdoom(@Nullable Closure configureClosure)

    ZdoomSourceSet zdoom(Action<? super SourceDirectorySet> configureAction);

    SourceDirectorySet getAllZdoom();
}