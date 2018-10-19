package com.github.tarcv.ztest.plugin

import org.gradle.api.Action
import org.gradle.api.file.SourceDirectorySet
import org.gradle.api.internal.file.SourceDirectorySetFactory
import org.gradle.api.reflect.HasPublicType
import org.gradle.api.reflect.TypeOf
import org.jetbrains.annotations.Nullable

import static org.gradle.api.reflect.TypeOf.typeOf
import static org.gradle.util.ConfigureUtil.configure

class DefaultZdoomSourceSet implements ZdoomSourceSet, HasPublicType {
    private final SourceDirectorySet zdoom;
    private final SourceDirectorySet allZdoom;

    DefaultZdoomSourceSet(String displayName, SourceDirectorySetFactory sourceDirectorySetFactory) {
        zdoom = sourceDirectorySetFactory.create(displayName +  " ZDoom mod source")
        zdoom.getFilter().include("**/*")
        allZdoom = sourceDirectorySetFactory.create(displayName + " ZDoom mod source")
        allZdoom.source(zdoom)
        allZdoom.getFilter().include("**/*")
    }

    @Override
    SourceDirectorySet getZdoom() {
        return zdoom
    }

    @Override
    SourceDirectorySet getAllZdoom() {
        return allZdoom
    }

    @Override
    ZdoomSourceSet zdoom(@Nullable Closure configureClosure) {
        configure(configureClosure, getZdoom())
        return this
    }

    @Override
    ZdoomSourceSet zdoom(Action<? super SourceDirectorySet> configureAction) {
        configureAction.execute(getZdoom())
        return null
    }

    @Override
    TypeOf<?> getPublicType() {
        return typeOf(ZdoomSourceSet.class)
    }
}
