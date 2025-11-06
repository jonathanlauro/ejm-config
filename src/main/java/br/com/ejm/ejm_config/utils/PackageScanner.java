package br.com.ejm.ejm_config.utils;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.net.URLDecoder;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

public class PackageScanner {

    public static List<Class<?>> findInterfaces(String packageName) throws IOException, ClassNotFoundException {
        List<Class<?>> classes = new ArrayList<>();
        String path = packageName.replace('.', '/');

        ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
        Enumeration<URL> resources = classLoader.getResources(path);

        while (resources.hasMoreElements()) {
            URL resource = resources.nextElement();
            String protocol = resource.getProtocol();

            if ("file".equals(protocol)) {
                // ✅ Classes locais (diretório)
                File directory = new File(resource.getFile());
                File[] files = directory.listFiles();
                if (files == null) continue;

                for (File file : files) {
                    if (file.getName().endsWith(".class")) {
                        String className = packageName + "." + file.getName().replace(".class", "");
                        Class<?> clazz = Class.forName(className);
                        if (clazz.isInterface()) classes.add(clazz);
                    }
                }
            } else if ("jar".equals(protocol)) {
                // ✅ Classes dentro de JAR
                String jarPath = resource.getPath().substring(5, resource.getPath().indexOf("!"));
                try (JarFile jarFile = new JarFile(URLDecoder.decode(jarPath, "UTF-8"))) {
                    Enumeration<JarEntry> entries = jarFile.entries();
                    while (entries.hasMoreElements()) {
                        JarEntry entry = entries.nextElement();
                        String name = entry.getName();

                        if (name.startsWith(path) && name.endsWith(".class") && !entry.isDirectory()) {
                            String className = name.replace('/', '.').replace(".class", "");
                            Class<?> clazz = Class.forName(className);
                            if (clazz.isInterface()) classes.add(clazz);
                        }
                    }
                }
            }
        }

        return classes;
    }

}
