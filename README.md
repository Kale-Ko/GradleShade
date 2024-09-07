# GradleShade

## About

GradleShade is a Gradle extension that automatically shades/shadows dependencies into a jar for standalone execution using a few different methods.

## Example

```groovy
// build.gradle
plugins {
    id "java"
    id "io.github.kale_ko.gradleshade" version "1.2.1"
}

...

import io.github.kale_ko.gradleshade.ShadeMode

shade {
    mode = ShadeMode.EMBED_JARS_CLASSLOADER
}
```

```groovy
// settings.gradle
pluginManagement {
    repositories {
        maven {
            url "https://maven.kaleko.dev/public-snapshot/"
        }
        gradlePluginPortal()
    }
}
```

## Modes

GradleShade can use one of three modes.

### Embed_Jars_Classloader

This mode embeds all dependency jars into the shaded jar for later use.\
When run GradleShade's main method is called which loads the jars using a custom classloader and then runs your program.

### Embed_Jars_Subprocess

This mode embeds all dependency jars into the shaded jar for later use.\
When run GradleShade's main method is called which starts a new Java process will all the dependencies on the passed classpath.

This mode is faster than Embed_Jars_Classloader for initial loading of the jars and also for fetching classes (uses java native). \
The disadvantage is mainly that you must start a separate process. Any args you pass to the JVM should be passed to the subprocess but some may be missed (The used API is a little ambiguous on this).

### Embed_Classes

This mode embeds all the classes and resources of dependencies into the shaded jar. (The same thing other shade/shadow implementations do)

This mode is ok for simple dependencies and is very fast, but it will not work for things like services that use the same file in different jars.
