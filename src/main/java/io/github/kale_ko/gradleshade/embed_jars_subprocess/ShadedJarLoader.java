package io.github.kale_ko.gradleshade.embed_jars_subprocess;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.jar.JarEntry;
import java.util.jar.JarInputStream;
import org.jetbrains.annotations.NotNull;

public class ShadedJarLoader {
    protected final @NotNull Path jarPath;

    protected final boolean recursiveExtract;

    public ShadedJarLoader(@NotNull Path jarPath, boolean recursiveExtract) {
        this.jarPath = jarPath;

        this.recursiveExtract = recursiveExtract;
    }

    public @NotNull Path getJarPath() {
        return this.jarPath;
    }

    protected boolean cataloged = false;
    protected List<Path> jars = new ArrayList<>();

    protected synchronized void catalogAll() throws IOException {
        if (this.cataloged) {
            return;
        }
        this.cataloged = true;

        Path tempTempDir = Files.createTempDirectory("gradleshade-temp-");
        Path extractDir = tempTempDir.getParent().resolve("gradleshade-" + Files.getLastModifiedTime(this.jarPath).toMillis());
        Files.delete(tempTempDir);

        if (!Files.exists(extractDir)) {
            this.catalog(this.jarPath, extractDir, 0);

            this.saveCatalog(extractDir);
        } else {
            this.loadCatalog(extractDir);
        }
    }

    private synchronized void catalog(Path parentJar, Path extractDir, int depth) throws IOException {
        List<Path> found = new ArrayList<>();

        Path localExtractDir = extractDir.resolve(parentJar.getFileName());

        try (JarInputStream jarInputStream = new JarInputStream(new BufferedInputStream(Files.newInputStream(parentJar)))) {
            JarEntry jarEntry;
            while ((jarEntry = jarInputStream.getNextJarEntry()) != null) {
                if (!jarEntry.isDirectory()) {
                    if (jarEntry.getName().toLowerCase().endsWith(".jar")) {
                        Path file = localExtractDir.resolve(jarEntry.getName());
                        if (!Files.exists(file.getParent())) {
                            Files.createDirectories(file.getParent());
                        }

                        found.add(file);

                        try (OutputStream fileOutputStream = new BufferedOutputStream(Files.newOutputStream(file))) {
                            int read;
                            byte[] buf = new byte[4096];
                            while ((read = jarInputStream.read(buf)) != -1) {
                                fileOutputStream.write(buf, 0, read);
                            }
                        }
                    }
                }
            }
        }

        this.jars.addAll(found);

        if (this.recursiveExtract) {
            for (Path jar : found) {
                this.catalog(jar, extractDir, depth + 1);
            }
        }
    }

    private synchronized void loadCatalog(Path extractDir) throws IOException {
        Path resourceSaveFile = extractDir.resolve("resources.map");

        try (InputStream resourceInputStream = new BufferedInputStream(Files.newInputStream(resourceSaveFile)); DataInputStream resourceDataInputStream = new DataInputStream(resourceInputStream)) {
            int entryCount = resourceDataInputStream.readInt();
            for (int i = 0; i < entryCount; i++) {
                String key = resourceDataInputStream.readUTF();

                this.jars.add(Path.of(key));
            }
        }
    }

    private synchronized void saveCatalog(Path extractDir) throws IOException {
        Path resourceSaveFile = extractDir.resolve("resources.map");

        try (OutputStream resourceOutputStream = new BufferedOutputStream(Files.newOutputStream(resourceSaveFile)); DataOutputStream resourceDataOutputStream = new DataOutputStream(resourceOutputStream)) {
            resourceDataOutputStream.writeInt(this.jars.size());

            for (Path entry : this.jars) {
                resourceDataOutputStream.writeUTF(entry.toString());
            }
        }
    }
}