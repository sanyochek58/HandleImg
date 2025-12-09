package com.example.HanldeImg.scripts;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static java.io.FileDescriptor.out;


public class ImageScripts {

    private static final String DEFAULT_7Z_PATH = "/usr/local/bin/7z";
    private static final String GIT_GROUP = "automobiles";
    private static final Logger log = LoggerFactory.getLogger(ImageScripts.class.getName());

    private static String findArchivator() {
        Path p = Paths.get(DEFAULT_7Z_PATH);

        if (Files.exists(p) && Files.isExecutable(p)) {
            return p.toString();
        }

        return "7z";
    }

    public static void extractImgWith7z(Path imgPath, Path targetDir) throws IOException, InterruptedException {
        Files.createDirectories(targetDir);

        ProcessBuilder pb = new ProcessBuilder(
                findArchivator(),
                "x",
                imgPath.toAbsolutePath().toString(),
                "-o" + targetDir.toAbsolutePath(),
                "-y"
        );

        pb.redirectErrorStream(true);
        Process process = pb.start();

        StringBuilder output = new StringBuilder();
        try (BufferedReader br = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
            String line;
            while ((line = br.readLine()) != null) {
                output.append(line).append("\n");
            }
        }

        int exitCode = process.waitFor();
        String out = output.toString();

        if (exitCode == 0) {
            return;
        }

        // Игнорируем ошибку из-за ссылок некоторых
        if (exitCode == 2 && out.contains("Sub items Errors")) {
            log.warn("7z завершился с предупреждениями (exit=2), но продолжаем:\n{}", out);
            return;
        }

        throw new RuntimeException("Ошибка 7z (" + exitCode + "):\n" + out);
    }


    public static void pushToGitLab(Path folder) throws Exception {

        String repoName = folder.getFileName().toString();
        String gitRemote = "git@100.98.83.30:" + GIT_GROUP + "/" + repoName.toLowerCase() + ".git";

        log.info("Pushing to GitLab at {}", gitRemote);

        Path gitDir = folder.resolve(".git");

        // ===== 1. "Очистка директории" =====
        if (Files.exists(gitDir)) {
            log.info("Очистка директории: .git уже существует, не удаляем (используем существующий репозиторий)");
        } else {
            log.info("Очистка директории: .git ещё нет, пропускаем удаление");
        }

        // ===== 2. "Инициализация гит проекта" =====
        if (!Files.exists(gitDir)) {
            log.info("Инициализация гит проекта");
            ProcessBuilder initPb = new ProcessBuilder("git", "init");
            initPb.directory(folder.toFile());
            initPb.redirectErrorStream(true);
            int initCode = initPb.start().waitFor();
            if (initCode != 0) {
                throw new RuntimeException("git init failed with code " + initCode);
            }

            // ===== 3. "Создание ветки: main" =====
            log.info("Создание ветки: main");
            ProcessBuilder branchPb = new ProcessBuilder("git", "branch", "-M", "main");
            branchPb.directory(folder.toFile());
            branchPb.redirectErrorStream(true);
            int branchCode = branchPb.start().waitFor();
            if (branchCode != 0) {
                throw new RuntimeException("git branch -M main failed with code " + branchCode);
            }

            // ===== 4. "Создание подключения к проекту в GitLab" =====
            log.info("Создание подключения к проекту в GitLab");
            ProcessBuilder remotePb = new ProcessBuilder("git", "remote", "add", "origin", gitRemote);
            remotePb.directory(folder.toFile());
            remotePb.redirectErrorStream(true);
            int remoteCode = remotePb.start().waitFor();
            if (remoteCode != 0) {
                throw new RuntimeException("git remote add origin failed with code " + remoteCode);
            }
        } else {
            log.info("Гит-проект уже инициализирован, пропускаем init/branch/remote");
        }

        // ===== 5. "Добавление файлов образа в проект" =====
        log.info("Добавление файлов образа в проект");
        ProcessBuilder addPb = new ProcessBuilder("git", "add", ".");
        addPb.directory(folder.toFile());
        addPb.redirectErrorStream(true);
        int addCode = addPb.start().waitFor();
        if (addCode != 0) {
            throw new RuntimeException("git add . failed with code " + addCode);
        }

        // ===== 6. "Коммитим изменения" =====
        log.info("Коммитим изменения");
        ProcessBuilder commitPb = new ProcessBuilder("git", "commit", "--allow-empty", "-m", "Auto-uploaded files");
        commitPb.directory(folder.toFile());
        commitPb.redirectErrorStream(true);

        Process commitProcess = commitPb.start();
        try (BufferedReader br = new BufferedReader(new InputStreamReader(commitProcess.getInputStream()))) {
            String line;
            while ((line = br.readLine()) != null) {
                log.info("[git commit] {}", line);
            }
        }
        int commitCode = commitProcess.waitFor();
        if (commitCode != 0) {
            log.warn("git commit завершился с кодом {} (возможно, нечего коммитить)", commitCode);
        }

        // ===== 7. "Пушим содержимое в репозиторий" =====
        log.info("Пушим содержимое в репозиторий");
        ProcessBuilder pushPb = new ProcessBuilder("git", "push", "-u", "origin", "main", "-v");
        pushPb.directory(folder.toFile());
        pushPb.redirectErrorStream(true);
        pushPb.environment().put("GIT_SSH_COMMAND", "ssh -oBatchMode=yes");

        Process pushProcess = pushPb.start();
        StringBuilder output = new StringBuilder();
        try (BufferedReader br = new BufferedReader(new InputStreamReader(pushProcess.getInputStream()))) {
            String line;
            while ((line = br.readLine()) != null) {
                output.append(line).append("\n");
                log.info("[git push] {}", line);
            }
        }
        int pushCode = pushProcess.waitFor();
        log.info("git push exit code = {}", pushCode);

        if (pushCode != 0) {
            throw new RuntimeException("git push failed with exit code " + pushCode + "\nOutput:\n" + output);
        }
    }

    public static void updateGitRepo(List<Path> archives, Path targetDir, String projectName) throws IOException, InterruptedException {
        if(!(Files.exists(targetDir) && Files.exists(targetDir.resolve(".git")))) {
            Files.createDirectories(targetDir.getParent());

            String gitRepo = "git@100.98.83.30:" + GIT_GROUP + "/" + projectName.toLowerCase() + ".git";

            log.info("Клонируем репозиторий {}", gitRepo);
            ProcessBuilder clonePb = new ProcessBuilder("git", "clone", gitRepo, targetDir.toString());
            clonePb.redirectErrorStream(true);
            Process process = clonePb.start();
            int cloneExitCode = process.waitFor();
            if (cloneExitCode != 0) {
                throw new RuntimeException("git clone failed with code " + cloneExitCode);
            }
        }else {
            log.info("Репозиторий {} уже существует локально, пропускаем clone", projectName);
        }

        try(Stream<Path> stream = Files.list(targetDir)){
            stream.forEach(path -> {
                String name = path.getFileName().toString();
                if(".git".equals(name)) {
                    return;
                }
                else{
                    try {
                        if(Files.isDirectory(path)) {
                            try(Stream<Path> walk = Files.walk(path)){
                                walk.sorted(Comparator.reverseOrder())
                                .forEach(p -> {
                                    try{
                                        Files.deleteIfExists(p);
                                    }catch(IOException e) {
                                        throw new RuntimeException(e);
                                    }
                                });
                            }
                        }else {
                            Files.deleteIfExists(path);
                        }
                    }catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                }
            });
        }

        log.info("Рабочая директория {} очищена (кроме .git)", targetDir);

        for(Path path : archives){
            log.info("Распаковка нового образа {} в {}", path, targetDir);
            extractImgWith7z(path, targetDir);
        }

        log.info("Добавление файлов образа в проект");
        ProcessBuilder addPb = new ProcessBuilder("git", "add", ".");
        addPb.directory(targetDir.toFile());
        addPb.redirectErrorStream(true);
        int addCode = addPb.start().waitFor();
        if (addCode != 0) {
            throw new RuntimeException("git add . failed with code " + addCode);
        }

        log.info("Коммитим изменения");
        ProcessBuilder commitPb = new ProcessBuilder("git", "commit", "--allow-empty", "-m", "UPDATED IMAGES");
        commitPb.directory(targetDir.toFile());
        commitPb.redirectErrorStream(true);

        Process commitProcess = commitPb.start();
        try (BufferedReader br = new BufferedReader(new InputStreamReader(commitProcess.getInputStream()))) {
            String line;
            while ((line = br.readLine()) != null) {
                log.info("[git commit] {}", line);
            }
        }
        int commitCode = commitProcess.waitFor();
        if (commitCode != 0) {
            log.warn("git commit завершился с кодом {} (возможно, нечего коммитить)", commitCode);
        }

        log.info("Пушим содержимое в репозиторий");
        ProcessBuilder pushPb = new ProcessBuilder("git", "push", "-u", "origin", "main", "-v");
        pushPb.directory(targetDir.toFile());
        pushPb.redirectErrorStream(true);
        pushPb.environment().put("GIT_SSH_COMMAND", "ssh -oBatchMode=yes");

        Process pushProcess = pushPb.start();
        StringBuilder output = new StringBuilder();
        try (BufferedReader br = new BufferedReader(new InputStreamReader(pushProcess.getInputStream()))) {
            String line;
            while ((line = br.readLine()) != null) {
                output.append(line).append("\n");
                log.info("[git push] {}", line);
            }
        }
        int pushCode = pushProcess.waitFor();
        log.info("git push exit code = {}", pushCode);

        if (pushCode != 0) {
            throw new RuntimeException("git push failed with exit code " + pushCode + "\nOutput:\n" + output);
        }

    }

}

