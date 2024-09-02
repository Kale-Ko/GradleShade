package io.github.kale_ko.gradleshade;

import org.gradle.api.provider.Property;
import org.jetbrains.annotations.NotNull;

public interface ShadeExtension {
    public @NotNull Property<ShadeMode> getMode();

    public @NotNull Property<Boolean> getRecursiveExtract();

    public @NotNull Property<Boolean> getReturnDirectUri();
}