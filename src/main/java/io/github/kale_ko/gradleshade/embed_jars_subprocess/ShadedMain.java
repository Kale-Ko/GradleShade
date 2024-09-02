package io.github.kale_ko.gradleshade.embed_jars_subprocess;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.lang.management.ManagementFactory;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;
import java.util.jar.Manifest;

public class ShadedMain {
    public static void main(String[] args) throws IOException, InterruptedException {
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

        List<String> javaArgs = new ArrayList<>();

        javaArgs.add(System.getProperty("java.home") + "/bin/java");

        javaArgs.addAll(ManagementFactory.getRuntimeMXBean().getInputArguments()); // TODO Add backup in case this doesn't work on some systems

        {
            List<String> classPath = new ArrayList<>();
            classPath.addAll(Arrays.asList(ManagementFactory.getRuntimeMXBean().getClassPath().split(File.pathSeparator)));

            {
                Path jarPath = new File(ShadedMain.class.getProtectionDomain().getCodeSource().getLocation().getFile()).toPath();

                ShadedJarLoader shadedJarLoader = new ShadedJarLoader(jarPath, Boolean.parseBoolean(properties.getProperty("recursiveExtract")));
                shadedJarLoader.catalogAll();
                for (Path jar : shadedJarLoader.jars) {
                    classPath.add(jar.toString());
                }
            }

            javaArgs.add("-cp");
            javaArgs.add(String.join(File.pathSeparator, classPath));
        }

        javaArgs.add(mainClazzName);

        ProcessBuilder processBuilder = new ProcessBuilder().command(javaArgs).directory(null).inheritIO();
        processBuilder.environment().clear();
        processBuilder.environment().putAll(System.getenv());
        Process process = processBuilder.start();

        System.exit(process.waitFor());
    }
}