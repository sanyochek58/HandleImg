package com.example.HanldeImg.scripts;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.Comparator;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class ApkScripts {

    private static final Logger log = LoggerFactory.getLogger(ApkScripts.class);
    private static final String APKTOOL = "/usr/local/bin/apktool";

    public static void decodeApksToProjectRoot(Path projectDir) {
        if (projectDir == null || !Files.isDirectory(projectDir)) {
            throw new RuntimeException("NOT FOUND DIRECTORY: " + projectDir);
        }

        Path systemDir = projectDir.resolve("system");
        Path vendorDir = projectDir.resolve("vendor");

        if (!Files.isDirectory(systemDir) && !Files.isDirectory(vendorDir)) {
            throw new RuntimeException("No system/ or vendor/ directories in: " + projectDir);
        }

        Pattern pkgPattern = Pattern.compile("package\\s*=\\s*\"([^\"]+)\"");

        try (Stream<Path> walk = Files.walk(projectDir)) {

            var apks = walk
                    .filter(Files::isRegularFile)
                    .filter(p -> p.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".apk"))
                    .filter(p -> p.startsWith(systemDir) || p.startsWith(vendorDir))
                    .sorted(Comparator.comparing(Path::toString))
                    .collect(Collectors.toList());

            log.info("Найдено APK в system/vendor: {}", apks.size());

            for (Path apk : apks) {
                String apkFileName = apk.getFileName().toString();
                String baseName = apkFileName;
                int dot = baseName.lastIndexOf('.');
                if (dot > 0) baseName = baseName.substring(0, dot);

                Path tmpOut = projectDir.resolve("_tmp_decode_" + baseName + "_" + Math.abs(apk.toAbsolutePath().toString().hashCode()));

                if (Files.exists(tmpOut)) {
                    try (Stream<Path> del = Files.walk(tmpOut).sorted(Comparator.reverseOrder())) {
                        del.forEach(p -> {
                            try { Files.deleteIfExists(p); } catch (Exception ignored) {}
                        });
                    }
                }

                log.info("apktool decode (tmp): {} -> {}", apk.toAbsolutePath(), tmpOut.toAbsolutePath());

                ProcessBuilder decodePb = new ProcessBuilder(
                        APKTOOL, "d",
                        "-f",
                        "-o", tmpOut.toAbsolutePath().toString(),
                        apk.toAbsolutePath().toString()
                );
                decodePb.redirectErrorStream(true);
                Process decodeProc = decodePb.start();

                StringBuilder apktoolOut = new StringBuilder();
                try (BufferedReader br = new BufferedReader(new InputStreamReader(decodeProc.getInputStream(), StandardCharsets.UTF_8))) {
                    String line;
                    while ((line = br.readLine()) != null) {
                        apktoolOut.append(line).append("\n");
                        log.info("[apktool] {}", line);
                    }
                }

                int decodeCode = decodeProc.waitFor();
                if (decodeCode != 0) {
                    throw new RuntimeException(
                            "apktool failed (" + decodeCode + ") for " + apk + "\n" + apktoolOut
                    );
                }

                Path manifest = tmpOut.resolve("AndroidManifest.xml");
                if (!Files.exists(manifest)) {
                    throw new RuntimeException("AndroidManifest.xml not found after decode: " + manifest);
                }

                String manifestText = Files.readString(manifest, StandardCharsets.UTF_8);
                Matcher m = pkgPattern.matcher(manifestText);
                String pkg = m.find() ? m.group(1) : "unknown.package";

                String safePkg = pkg.replaceAll("[\\\\/\\s:]+", "_");
                String outName = safePkg + "_" + baseName;

                Path finalOut = projectDir.resolve(outName);

                if (Files.exists(finalOut)) {
                    try (Stream<Path> del = Files.walk(finalOut).sorted(Comparator.reverseOrder())) {
                        del.forEach(p -> {
                            try { Files.deleteIfExists(p); } catch (Exception ignored) {}
                        });
                    }
                }
                try {
                    Files.move(tmpOut, finalOut, StandardCopyOption.ATOMIC_MOVE);
                } catch (AtomicMoveNotSupportedException ex) {
                    Files.move(tmpOut, finalOut);
                }

                log.info("Готово: {} -> {}", apkFileName, finalOut.toAbsolutePath());
            }

        } catch (Exception e) {
            throw new RuntimeException("ERROR WHILE DECODING APKs IN: " + projectDir, e);
        }
    }
}

