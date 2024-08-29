package io.github.kale_ko.gradleshade;

import java.io.*;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.jar.JarEntry;
import java.util.jar.JarInputStream;
import org.jetbrains.annotations.NotNull;

public class ShadedClassLoader extends ClassLoader {
    public static boolean FIND_RESOURCES_RETURN_DIRECT = System.getProperty("gradleshade.resourceReturnDirect") != null ? Boolean.parseBoolean(System.getProperty("gradleshade.resourceReturnDirect")) : false;
    public static boolean CATALOG_RECURSIVE = System.getProperty("gradleshade.recursive") != null ? Boolean.parseBoolean(System.getProperty("gradleshade.recursive")) : false;

    protected final @NotNull Path jarPath;

    public ShadedClassLoader(@NotNull Path jarPath) {
        super(null);

        this.jarPath = jarPath;
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

        boolean copyNeeded = !Files.exists(extractDir);
        if (!copyNeeded) {
            Files.createDirectories(extractDir);
        }

        this.catalog(this.jarPath, extractDir, copyNeeded, 0);
    }

    private synchronized void catalog(Path parentJar, Path tempDir, boolean copyNeeded, int depth) throws IOException {
        List<Path> jars = new ArrayList<>();

        try (JarInputStream jarInputStream = new JarInputStream(new BufferedInputStream(Files.newInputStream(parentJar)))) {
            JarEntry jarEntry;
            while ((jarEntry = jarInputStream.getNextJarEntry()) != null) {
                if (!jarEntry.isDirectory()) {
                    Path file = tempDir.resolve(parentJar.getFileName()).resolve(jarEntry.getName());
                    if (!Files.exists(file.getParent())) {
                        Files.createDirectories(file.getParent());
                    }

                    if (jarEntry.getName().toLowerCase().endsWith(".jar")) {
                        jars.add(file);
                    }

                    if (copyNeeded) {
                        try (OutputStream fileOutputStream = new BufferedOutputStream(Files.newOutputStream(file))) {
                            int read;
                            byte[] buf = new byte[4096];
                            while ((read = jarInputStream.read(buf)) != -1) {
                                fileOutputStream.write(buf, 0, read);
                            }
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

        if (CATALOG_RECURSIVE || depth == 0) {
            for (Path jar : jars) {
                this.catalog(jar, tempDir, copyNeeded, depth + 1);
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

    @SuppressWarnings("deprecation")
    @Override
    protected URL findResource(String name) {
        {
            if (getParent() != null) {
                URL resource = getParent().getResource(name);
                if (resource != null) {
                    return resource;
                }
            }
        }

        if (!(this.resourceCatalog.containsKey(name) && !this.resourceCatalog.get(name).isEmpty())) {
            return null;
        }

        try {
            Path path = this.resourceCatalog.get(name).get(0);

            return new URL("jar", "", -1, "file:" + path.toAbsolutePath() + "!/" + name);
        } catch (MalformedURLException e) {
            throw new RuntimeException(e);
        }
    }

    @SuppressWarnings("deprecation")
    @Override
    protected Enumeration<URL> findResources(String name) {
        try {
            if (getParent() != null) {
                Enumeration<URL> resources = getParent().getResources(name);
                if (resources != null && resources.hasMoreElements()) {
                    return resources;
                }
            }
        } catch (IOException ignored) {
        }

        if (!(this.resourceCatalog.containsKey(name) && !this.resourceCatalog.get(name).isEmpty())) {
            return null;
        }

        try {
            List<URL> resources = new ArrayList<>();

            for (Path path : this.resourceCatalog.get(name)) {
                resources.add(new URL("jar", "", -1, "file:" + path.toAbsolutePath() + "!/" + name));
            }

            return Collections.enumeration(resources);
        } catch (MalformedURLException e) {
            throw new RuntimeException(e);
        }
    }
}