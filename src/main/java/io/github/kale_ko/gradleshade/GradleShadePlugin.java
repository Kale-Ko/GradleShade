package io.github.kale_ko.gradleshade;

import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.plugins.JavaPlugin;
import org.gradle.api.tasks.TaskProvider;
import org.jetbrains.annotations.NotNull;

public class GradleShadePlugin implements Plugin<Project> {
    @Override
    public void apply(@NotNull Project project) {
        if (project.getPluginManager().hasPlugin("java")) {
            project.getPluginManager().apply(JavaPlugin.class);
        } else {
            throw new RuntimeException("Plugin \"java\" must be applied before \"gradleshade\"");
        }

        ShadeExtension shadeExtension = project.getExtensions().create("shade", ShadeExtension.class);
        if (!shadeExtension.getMode().isPresent()) {
            shadeExtension.getMode().set(ShadeMode.EMBED_JARS_CLASSLOADER);
        }
        if (!shadeExtension.getRecursiveExtract().isPresent()) {
            shadeExtension.getRecursiveExtract().set(true);
        }
        if (!shadeExtension.getReturnDirectUri().isPresent()) {
            shadeExtension.getReturnDirectUri().set(false);
        }

        TaskProvider<ShadedJar> shadedJarTaskProvider = project.getTasks().register("shadedJar", ShadedJar.class);
        project.getTasks().named("build").get().dependsOn(shadedJarTaskProvider);
    }
}