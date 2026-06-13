package mainCodingFiles;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class extractJAR17022026 {

    public static List<File> extractScalaFiles(File jarFile) throws IOException {
        List<File> scalaFiles = new ArrayList<>();
        File tempDir = Files.createTempDirectory("scalaExtract").toFile();

        try (JarFile jar = new JarFile(jarFile)) {
            Enumeration<JarEntry> entries = jar.entries();

            while (entries.hasMoreElements()) {
                JarEntry entry = entries.nextElement();
                if (!entry.isDirectory() && entry.getName().endsWith(".scala")) {
                    File outFile = new File(tempDir, new File(entry.getName()).getName());
                    try (InputStream is = jar.getInputStream(entry);
                         FileOutputStream fos = new FileOutputStream(outFile)) {
                        byte[] buffer = new byte[4096];
                        int len;
                        while ((len = is.read(buffer)) > 0) {
                            fos.write(buffer, 0, len);
                        }
                    }
                    scalaFiles.add(outFile);
                }
            }
        }

        return scalaFiles; // <-- can now be assigned to selectedFiles for xtractNgenerate()
    }
}

