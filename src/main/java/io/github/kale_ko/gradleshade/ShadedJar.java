package io.github.kale_ko.gradleshade;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
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
        this.getManifest().getAttributes().put("Main-Class", "io.github.kale_ko.gradleshade.ShadedMain");

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

        CopySpec codeCopy = this.getRootSpec().addFirst().into("io/github/kale_ko/gradleshade");
        codeCopy.from(this.classFiles());

        this.from(project.getConfigurations().named(JavaPlugin.RUNTIME_CLASSPATH_CONFIGURATION_NAME).get().getResolvedConfiguration().getFiles());
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
        Cached<ManifestInternal> manifest = Cached.of(this::getShadedManifestInternal);
        OutputChangeListener outputChangeListener = this.getServices().get(OutputChangeListener.class);

        return this.getServices().get(FileCollectionFactory.class).generated(this.getTemporaryDirFactory(), "SHADED-MANIFEST.MF", (file) -> {
            outputChangeListener.invalidateCachesFor(List.of(file.getAbsolutePath()));
        }, (outputStream) -> {
            manifest.get().writeTo(outputStream);
        });
    }

    private @NotNull FileTree classFiles() {
        FileTree tree = this.getServices().get(FileCollectionFactory.class).treeOf(List.of());

        for (String clazz : List.of("ShadedMain.class", "ShadedClassLoader.class")) {
            tree = tree.plus(this.classFile(clazz));
        }

        return tree;
    }

    private @NotNull FileTree classFile(String clazz) {
        return this.getServices().get(FileCollectionFactory.class).generated(this.getTemporaryDirFactory(), clazz, (file) -> {
        }, (outputStream) -> {
            try (InputStream inputStream = this.getClass().getResourceAsStream("/io/github/kale_ko/gradleshade/" + clazz)) {
                if (inputStream == null) {
                    throw new RuntimeException("Missing resource " + clazz);
                }

                int read;
                byte[] buf = new byte[4096];
                while ((read = inputStream.read(buf)) != -1) {
                    outputStream.write(buf, 0, read);
                }
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        });
    }
}