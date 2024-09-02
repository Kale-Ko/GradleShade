package io.github.kale_ko.gradleshade;

import java.util.List;
import org.jetbrains.annotations.NotNull;

public enum ShadeMode {
    EMBED_JARS_CLASSLOADER(List.of("embed_jars_classloader/ShadedMain.class", "embed_jars_classloader/ShadedClassLoader.class")),
    EMBED_JARS_SUBPROCESS(List.of("embed_jars_subprocess/ShadedMain.class", "embed_jars_subprocess/ShadedJarLoader.class"));

    private final @NotNull List<String> files;

    private ShadeMode(@NotNull List<String> files) {
        this.files = files;
    }

    @NotNull List<String> getFiles() {
        return this.files;
    }
}