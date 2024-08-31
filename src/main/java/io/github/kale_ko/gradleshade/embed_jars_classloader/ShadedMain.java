package io.github.kale_ko.gradleshade.embed_jars_classloader;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;
import java.util.jar.Manifest;

public class ShadedMain {
    public static void main(String[] args) throws IOException, ClassNotFoundException, NoSuchMethodException, InvocationTargetException, IllegalAccessException {
        System.out.println("Loading libraries...");

        Manifest manifest = new Manifest();
        try (InputStream manifestInputStream = ShadedMain.class.getResourceAsStream("/META-INF/SHADED-MANIFEST.MF")) {
            manifest.read(manifestInputStream);
        }

        String mainClazzName = manifest.getMainAttributes().getValue("Main-Class");

        Properties properties = new Properties();
        try (InputStream manifestInputStream = ShadedMain.class.getResourceAsStream("/META-INF/shade.properties")) {
            properties.load(manifestInputStream);
        }

        ClassLoader classLoader;
        Path jarPath = new File(ShadedMain.class.getProtectionDomain().getCodeSource().getLocation().getFile()).toPath();
        if (!Files.isDirectory(jarPath)) {
            classLoader = new ShadedClassLoader(jarPath, Boolean.parseBoolean(properties.getProperty("recursiveExtract")), Boolean.parseBoolean(properties.getProperty("recursiveExtract")));
            ((ShadedClassLoader) classLoader).catalogAll();
        } else {
            System.err.println("Development environment in use, not loading shaded jars.");
            classLoader = ShadedMain.class.getClassLoader();
        }

        Class<?> mainClazz = classLoader.loadClass(mainClazzName);

        Method mainMethod = mainClazz.getDeclaredMethod("main", String[].class);
        mainMethod.invoke(null, (Object) args);
    }
}