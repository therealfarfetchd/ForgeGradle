package net.minecraftforge.gradle.util;

import au.com.bytecode.opencsv.CSVReader;
import net.md_5.specialsource.Jar;
import net.md_5.specialsource.JarRemapper;
import net.minecraftforge.gradle.plugin.ForgeGradlePluginInstance;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.Dependency;
import org.gradle.api.artifacts.FileCollectionDependency;
import org.gradle.api.artifacts.ModuleDependency;
import org.gradle.api.file.FileCollection;
import org.gradle.internal.impldep.com.google.gson.Gson;
import org.gradle.internal.impldep.com.google.gson.JsonObject;
import org.gradle.internal.impldep.org.apache.commons.io.IOUtils;
import org.gradle.internal.impldep.org.apache.commons.lang.ArrayUtils;
import org.gradle.internal.os.OperatingSystem;

import java.io.File;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.Reader;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

public class Util {

    /**
     * Converts a file collection into a URL array.
     */
    public static URL[] toURLs(FileCollection files) throws MalformedURLException {
        List<URL> urls = new ArrayList<>();
        for (File file : files.getFiles()) {
            urls.add(file.toURI().toURL());
        }
        return urls.toArray(new URL[urls.size()]);
    }

    /**
     * Resolves a dependency, downloading the file and its transitives
     * if not cached and returns the set of files.
     */
    public static Set<File> resolveDependency(ForgeGradlePluginInstance fg, Dependency dependency) {
        if(dependency instanceof FileCollectionDependency){
            return ((FileCollectionDependency) dependency).getFiles().getFiles();
        }
        Configuration cfg = fg.project.getConfigurations().maybeCreate("resolve_dep_" + fg.dependencyID);
        fg.dependencyID++;
        cfg.getDependencies().add(dependency);
        Set<File> files = cfg.resolve();
        fg.project.getConfigurations().remove(cfg);
        fg.dependencyID--;
        return files;
    }

    /**
     * Resolves a dependency, downloading the file and its transitives
     * if not cached and returns the set of files.
     */
    public static Set<File> resolveDependency(ForgeGradlePluginInstance fg, Object dependency) {
        return resolveDependency(fg, fg.project.getDependencies().create(dependency));
    }

    /**
     * Unzips a file into the target directory.
     */
    public static void unzip(File file, File targetDir) throws IOException {
        ZipFile zip = new ZipFile(file);
        Enumeration<? extends ZipEntry> entries = zip.entries();
        while (entries.hasMoreElements()) {
            ZipEntry entry = entries.nextElement();
            File out = new File(targetDir, entry.getName());
            if (entry.isDirectory()) {
                out.mkdirs();
            } else {
                out.getParentFile().mkdirs();
                InputStream inputStream = zip.getInputStream(entry);
                OutputStream outputStream = new FileOutputStream(out);
                IOUtils.copy(inputStream, outputStream);
                IOUtils.closeQuietly(inputStream);
                outputStream.close();
            }
        }
        zip.close();
    }

    /**
     * Unzips a file to a folder named {@code extracted} in the same directory.
     */
    public static File unzip(File file) {
        File target = new File(file.getParentFile(), "extracted");
        if (target.exists()) return target;

        try {
            Util.unzip(file, target);
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }

        return target;
    }

    /**
     * Reads a CSV file and returns a map of the two requested columns.
     */
    public static Map<String, String> readCSV(File file, String column1, String column2) throws IOException {
        CSVReader reader = new CSVReader(new FileReader(file));

        Map<String, String> mappings = new HashMap<>();

        String[] header = reader.readNext();
        int pos1 = ArrayUtils.indexOf(header, column1);
        int pos2 = ArrayUtils.indexOf(header, column2);

        for (String[] entry : reader.readAll()) {
            mappings.put(entry[pos1], entry[pos2]);
        }

        reader.close();

        return mappings;
    }

    /**
     * Remaps a file using SpecialSource and the specified remapper supplier.
     */
    public static void applySpecialSource(File input, File output, IOFunction<Jar, JarRemapper> remapperSupplier) throws IOException {
        Jar inputJar = Jar.init(input);
        remapperSupplier.apply(inputJar).remapJar(inputJar, output);
        inputJar.close();
    }

    /**
     * Sets the value of a final field (can be private).
     */
    public static void setFinal(Object target, Class<?> type, String name, Object value) {
        try {
            Field field = type.getDeclaredField(name);
            field.setAccessible(true);

            Field modifiers = Field.class.getDeclaredField("modifiers");
            modifiers.setAccessible(true);
            modifiers.setInt(field, field.getModifiers() & ~Modifier.FINAL);

            field.set(target, value);
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }
    }

    /**
     * Gets the value of a field (can be private).
     */
    public static <T> T get(Object target, Class<?> type, String name) {
        try {
            Field field = type.getDeclaredField(name);
            field.setAccessible(true);
            return (T) field.get(target);
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }
    }

    /**
     * Invokes a method (can be private).
     */
    public static <T> T invoke(Object target, Class<?> type, String name, Object... args) {
        try {
            Method method = type.getDeclaredMethod(name);
            method.setAccessible(true);
            return (T) method.invoke(target, args);
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }
    }

    /**
     * Safely creates a {@link URI} from a {@link URL}.
     */
    public static URI getURI(URL url) {
        try {
            return url.toURI();
        } catch (URISyntaxException ex) {
            throw new RuntimeException(ex);
        }
    }

    /**
     * Reads a JSON object from a URL.
     */
    public static JsonObject readJSON(String url) {
        try {
            Gson gson = new Gson();
            Reader reader = new InputStreamReader(new URL(url).openStream());
            JsonObject json = gson.fromJson(reader, JsonObject.class);
            reader.close();
            return json;
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }
    }

    /**
     * Gets the operating system string.
     */
    public static String getOS() {
        OperatingSystem os = OperatingSystem.current();
        return os == OperatingSystem.WINDOWS ? "windows" : os == OperatingSystem.MAC_OS ? "osx" :
                os == OperatingSystem.LINUX ? "linux" : null;
    }

    /**
     * Creates a copy of a dependency that has no transitive elements.
     */
    public static Dependency asNonTransitive(Dependency dependency) {
        if (dependency instanceof ModuleDependency) {
            ModuleDependency dep = ((ModuleDependency) dependency).copy();
            dep.setTransitive(false);
            return dep;
        }
        return dependency;
    }

}
