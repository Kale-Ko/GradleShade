package io.github.kale_ko.gradleshade.embed_jars_classloader;

import java.io.*;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.jar.JarEntry;
import java.util.jar.JarInputStream;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class ShadedClassLoader extends ClassLoader {
    protected final @NotNull Path jarPath;

    protected final boolean recursiveExtract;
    protected final boolean returnDirectUri;

    public ShadedClassLoader(@NotNull Path jarPath, boolean recursiveExtract, boolean returnDirectUri) {
        super(null);

        this.jarPath = jarPath;

        this.recursiveExtract = recursiveExtract;
        this.returnDirectUri = returnDirectUri;
    }

    public @NotNull Path getJarPath() {
        return this.jarPath;
    }

    protected boolean cataloged = false;
    protected Map<String, List<Path>> resourceCatalog = new HashMap<>();
    protected Map<String, List<Path>> extractedResourceCatalog = new HashMap<>();

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
        List<Path> jars = new ArrayList<>();

        Path localExtractDir = extractDir.resolve(parentJar.getFileName());

        try (JarInputStream jarInputStream = new JarInputStream(new BufferedInputStream(Files.newInputStream(parentJar)))) {
            JarEntry jarEntry;
            while ((jarEntry = jarInputStream.getNextJarEntry()) != null) {
                if (!jarEntry.isDirectory()) {
                    Path file = localExtractDir.resolve(jarEntry.getName());
                    if (!Files.exists(file.getParent())) {
                        Files.createDirectories(file.getParent());
                    }

                    if (jarEntry.getName().toLowerCase().endsWith(".jar")) {
                        jars.add(file);
                    }

                    try (OutputStream fileOutputStream = new BufferedOutputStream(Files.newOutputStream(file))) {
                        int read;
                        byte[] buf = new byte[4096];
                        while ((read = jarInputStream.read(buf)) != -1) {
                            fileOutputStream.write(buf, 0, read);
                        }
                    }

                    if (!this.resourceCatalog.containsKey(jarEntry.getName())) {
                        this.resourceCatalog.put(jarEntry.getName(), new ArrayList<>());
                    }
                    this.resourceCatalog.get(jarEntry.getName()).add(parentJar);

                    if (!this.extractedResourceCatalog.containsKey(jarEntry.getName())) {
                        this.extractedResourceCatalog.put(jarEntry.getName(), new ArrayList<>());
                    }
                    this.extractedResourceCatalog.get(jarEntry.getName()).add(file);
                }
            }
        }

        if (this.recursiveExtract || depth == 0) {
            for (Path jar : jars) {
                this.catalog(jar, extractDir, depth + 1);
            }
        }
    }

    private synchronized void loadCatalog(Path extractDir) throws IOException {
        Path resourceSaveFile = extractDir.resolve("resources.map");
        Path extractedResourceSaveFile = extractDir.resolve("extractedResources.map");

        try (InputStream resourceInputStream = new BufferedInputStream(Files.newInputStream(resourceSaveFile)); DataInputStream resourceDataInputStream = new DataInputStream(resourceInputStream)) {
            int entryCount = resourceDataInputStream.readInt();
            for (int i = 0; i < entryCount; i++) {
                String key = resourceDataInputStream.readUTF();

                List<Path> subEntries = new ArrayList<>();
                this.resourceCatalog.put(key, subEntries);

                int subEntryCount = resourceDataInputStream.readInt();
                for (int i2 = 0; i2 < subEntryCount; i2++) {
                    String subEntry = resourceDataInputStream.readUTF();

                    subEntries.add(Path.of(subEntry));
                }
            }
        }
        try (InputStream extractedResourceInputStream = new BufferedInputStream(Files.newInputStream(extractedResourceSaveFile)); DataInputStream extractedResourceDataInputStream = new DataInputStream(extractedResourceInputStream)) {
            int entryCount = extractedResourceDataInputStream.readInt();
            for (int i = 0; i < entryCount; i++) {
                String key = extractedResourceDataInputStream.readUTF();

                List<Path> subEntries = new ArrayList<>();
                this.extractedResourceCatalog.put(key, subEntries);

                int subEntryCount = extractedResourceDataInputStream.readInt();
                for (int i2 = 0; i2 < subEntryCount; i2++) {
                    String subEntry = extractedResourceDataInputStream.readUTF();

                    subEntries.add(Path.of(subEntry));
                }
            }
        }
    }

    private synchronized void saveCatalog(Path extractDir) throws IOException {
        Path resourceSaveFile = extractDir.resolve("resources.map");
        Path extractedResourceSaveFile = extractDir.resolve("extractedResources.map");

        try (OutputStream resourceOutputStream = new BufferedOutputStream(Files.newOutputStream(resourceSaveFile)); DataOutputStream resourceDataOutputStream = new DataOutputStream(resourceOutputStream)) {
            resourceDataOutputStream.writeInt(this.resourceCatalog.size());

            for (Map.Entry<String, List<Path>> entry : this.resourceCatalog.entrySet()) {
                resourceDataOutputStream.writeUTF(entry.getKey());

                resourceDataOutputStream.writeInt(entry.getValue().size());
                for (Path subEntry : entry.getValue()) {
                    resourceDataOutputStream.writeUTF(subEntry.toString());
                }
            }
        }
        try (OutputStream extractedResourceOutputStream = new BufferedOutputStream(Files.newOutputStream(extractedResourceSaveFile)); DataOutputStream extractedResourceDataOutputStream = new DataOutputStream(extractedResourceOutputStream)) {
            extractedResourceDataOutputStream.writeInt(this.extractedResourceCatalog.size());

            for (Map.Entry<String, List<Path>> entry : this.extractedResourceCatalog.entrySet()) {
                extractedResourceDataOutputStream.writeUTF(entry.getKey());

                extractedResourceDataOutputStream.writeInt(entry.getValue().size());
                for (Path subEntry : entry.getValue()) {
                    extractedResourceDataOutputStream.writeUTF(subEntry.toString());
                }
            }
        }
    }

    @Override
    protected Class<?> findClass(String name) throws ClassNotFoundException {
        try {
            if (getParent() != null) {
                return getParent().loadClass(name);
            }
        } catch (ClassNotFoundException ignored) {
        }

        String fileName = name.replace(".", "/") + ".class"; // TODO Check if this meets specs

        if (!(this.extractedResourceCatalog.containsKey(fileName) && !this.extractedResourceCatalog.get(fileName).isEmpty())) {
            throw new ClassNotFoundException(name);
        }

        try (InputStream jarInputStream = new BufferedInputStream(Files.newInputStream(this.extractedResourceCatalog.get(fileName).get(0)))) {
            try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
                int read;
                byte[] buf = new byte[4096];
                while ((read = jarInputStream.read(buf)) != -1) {
                    outputStream.write(buf, 0, read);
                }

                return defineClass(name, outputStream.toByteArray(), 0, outputStream.size());
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    protected @Nullable URL findResource(String name) {
        {
            if (getParent() != null) {
                URL resource = getParent().getResource(name);
                if (resource != null) {
                    return resource;
                }
            }
        }

        if (!this.returnDirectUri) {
            if (!(this.resourceCatalog.containsKey(name) && !this.resourceCatalog.get(name).isEmpty())) {
                return null;
            }

            try {
                Path path = this.resourceCatalog.get(name).get(0);

                return new URL("jar", "", -1, path.toUri().toURL() + "!/" + name);
            } catch (MalformedURLException e) {
                throw new RuntimeException(e);
            }
        } else {
            if (!(this.extractedResourceCatalog.containsKey(name) && !this.extractedResourceCatalog.get(name).isEmpty())) {
                return null;
            }

            try {
                Path path = this.extractedResourceCatalog.get(name).get(0);

                return path.toUri().toURL();
            } catch (MalformedURLException e) {
                throw new RuntimeException(e);
            }
        }
    }

    @Override
    protected @NotNull Enumeration<URL> findResources(String name) {
        try {
            if (getParent() != null) {
                Enumeration<URL> resources = getParent().getResources(name);
                if (resources != null && resources.hasMoreElements()) {
                    return resources;
                }
            }
        } catch (IOException ignored) {
        }

        if (!this.returnDirectUri) {
            if (!(this.resourceCatalog.containsKey(name) && !this.resourceCatalog.get(name).isEmpty())) {
                return Collections.emptyEnumeration();
            }

            try {
                List<URL> resources = new ArrayList<>();

                for (Path path : this.resourceCatalog.get(name)) {
                    resources.add(new URL("jar", "", -1, path.toUri().toURL() + "!/" + name));
                }

                return Collections.enumeration(resources);
            } catch (MalformedURLException e) {
                throw new RuntimeException(e);
            }
        } else {
            if (!(this.extractedResourceCatalog.containsKey(name) && !this.extractedResourceCatalog.get(name).isEmpty())) {
                return Collections.emptyEnumeration();
            }

            try {
                List<URL> resources = new ArrayList<>();

                for (Path path : this.extractedResourceCatalog.get(name)) {
                    resources.add(path.toUri().toURL());
                }

                return Collections.enumeration(resources);
            } catch (MalformedURLException e) {
                throw new RuntimeException(e);
            }
        }
    }
}