package io.github.kale_ko.gradleshade.embed_jars_classloader;

import io.github.kale_ko.gradleshade.ShadeExtension;
import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Properties;
import org.gradle.api.Project;
import org.gradle.api.file.CopySpec;
import org.gradle.api.file.FileTree;
import org.gradle.api.internal.file.FileCollectionFactory;
import org.gradle.api.java.archives.Manifest;
import org.gradle.api.java.archives.internal.CustomManifestInternalWrapper;
import org.gradle.api.java.archives.internal.DefaultManifest;
import org.gradle.api.java.archives.internal.ManifestInternal;
import org.gradle.api.plugins.JavaPlugin;
import org.gradle.api.plugins.JavaPluginExtension;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.SourceSetContainer;
import org.gradle.api.tasks.TaskProvider;
import org.gradle.internal.execution.OutputChangeListener;
import org.gradle.internal.serialization.Cached;
import org.gradle.jvm.tasks.Jar;
import org.jetbrains.annotations.NotNull;

public abstract class ShadedJar extends Jar {
    @Internal private final @NotNull Manifest shadedManifest;

    public ShadedJar() {
        super();

        Project project = this.getProject();
        TaskProvider<Jar> jarTaskProvider = project.getTasks().named(JavaPlugin.JAR_TASK_NAME, Jar.class);
        this.dependsOn(jarTaskProvider);
        Jar jarTask = jarTaskProvider.get();

        this.shadedManifest = new DefaultManifest(this.getFileResolver());
        this.shadedManifest.attributes(jarTask.getManifest().getAttributes());

        this.getManifest().attributes(jarTask.getManifest().getAttributes());
        this.getManifest().getAttributes().put("Main-Class", "io.github.kale_ko.gradleshade.embed_jars_classloader.ShadedMain");

        this.getDestinationDirectory().set(jarTask.getDestinationDirectory().getOrNull());

        this.getArchiveBaseName().set(jarTask.getArchiveBaseName().getOrNull());
        this.getArchiveAppendix().set(jarTask.getArchiveAppendix().getOrNull());
        this.getArchiveClassifier().set("shaded");
        this.getArchiveVersion().set(jarTask.getArchiveVersion().getOrNull());
        this.getArchiveExtension().set(jarTask.getArchiveExtension().getOrNull());

        this.setDuplicatesStrategy(jarTask.getDuplicatesStrategy());

        SourceSetContainer sourceSets = project.getExtensions().getByType(JavaPluginExtension.class).getSourceSets();
        this.from(sourceSets.named("main").get().getOutput().getFiles());

        this.getMetaInf().from(this.shadedManifestFileTree());
        this.getMetaInf().from(this.shadedPropertiesFileTree());

        CopySpec codeCopy = this.getRootSpec().addFirst().into("io/github/kale_ko/gradleshade");
        codeCopy.from(this.classFilesFileTree());

        this.from(project.getConfigurations().named(JavaPlugin.RUNTIME_CLASSPATH_CONFIGURATION_NAME).get().getResolvedConfiguration().getFiles());
    }

    private @NotNull ShadeExtension getSettings() {
        return this.getProject().getExtensions().getByType(ShadeExtension.class);
    }

    public @NotNull Manifest getShadedManifest() {
        return this.shadedManifest;
    }

    private @NotNull ManifestInternal getShadedManifestInternal() {
        Manifest manifest = this.getShadedManifest();

        Object manifestInternal;
        if (manifest instanceof ManifestInternal) {
            manifestInternal = manifest;
        } else {
            manifestInternal = new CustomManifestInternalWrapper(manifest);
        }

        ((ManifestInternal) manifestInternal).setContentCharset(this.getManifestContentCharset());
        return (ManifestInternal) manifestInternal;
    }

    private @NotNull FileTree shadedManifestFileTree() {
        Cached<ManifestInternal> cachedShadedManifest = Cached.of(this::getShadedManifestInternal);
        OutputChangeListener outputChangeListener = this.getServices().get(OutputChangeListener.class);

        return this.getServices().get(FileCollectionFactory.class).generated(this.getTemporaryDirFactory(), "SHADED-MANIFEST.MF", (file) -> {
            outputChangeListener.invalidateCachesFor(List.of(file.getAbsolutePath()));
        }, (outputStream) -> {
            cachedShadedManifest.get().writeTo(outputStream);
        });
    }

    private @NotNull FileTree shadedPropertiesFileTree() {
        OutputChangeListener outputChangeListener = this.getServices().get(OutputChangeListener.class);

        return this.getServices().get(FileCollectionFactory.class).generated(this.getTemporaryDirFactory(), "shade.properties", (file) -> {
            outputChangeListener.invalidateCachesFor(List.of(file.getAbsolutePath()));
        }, (outputStream) -> {
            Properties properties = new Properties();
            properties.setProperty("mode", this.getSettings().getMode().get().toString());
            properties.setProperty("recursiveExtract", Boolean.TRUE.equals(this.getSettings().getRecursiveExtract().get()) ? "true" : "false");
            properties.setProperty("returnDirectUri", Boolean.TRUE.equals(this.getSettings().getReturnDirectUri().get()) ? "true" : "false");
            try {
                properties.store(outputStream, null);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        });
    }

    private @NotNull FileTree classFilesFileTree() {
        FileTree tree = this.getServices().get(FileCollectionFactory.class).treeOf(List.of());

        for (String clazz : this.getSettings().getMode().get().getFiles()) {
            tree = tree.plus(this.classFileFileTree(clazz));
        }

        return tree;
    }

    private @NotNull FileTree classFileFileTree(String clazz) {
        return this.getServices().get(FileCollectionFactory.class).generated(this.getTemporaryDirFactory(), clazz, (file) -> {
        }, (outputStream) -> {
            try (InputStream rawInputStream = this.getClass().getResourceAsStream("/io/github/kale_ko/gradleshade/" + clazz)) {
                if (rawInputStream == null) {
                    throw new RuntimeException("Missing resource " + clazz);
                }

                try (InputStream inputStream = new BufferedInputStream(rawInputStream)) {
                    int read;
                    byte[] buf = new byte[4096];
                    while ((read = inputStream.read(buf)) != -1) {
                        outputStream.write(buf, 0, read);
                    }
                }
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        });
    }
}