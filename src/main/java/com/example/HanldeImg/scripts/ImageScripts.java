package com.example.HanldeImg.scripts;

import org.gitlab4j.api.GitLabApi;
import org.gitlab4j.api.GitLabApiException;
import org.gitlab4j.api.models.Group;
import org.gitlab4j.api.models.Project;
import org.gitlab4j.models.Constants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

public class ImageScripts {

    private static final String DEFAULT_7Z_PATH = "/usr/local/bin/7z";
    private static final String GIT_GROUP = System.getenv("GIT_GROUP_NAME");
    private static final Logger log = LoggerFactory.getLogger(ImageScripts.class.getName());
    public static final String[] ignore = new String[] {".gitignore", ".git", ".gitattributes"};

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


        String buildVersion;

        Path buildProp = folder.resolve("system").resolve("build.prop");
        if (!Files.exists(buildProp)) {
            throw new RuntimeException("build.prop not found: " + buildProp);
        }

        try (Stream<String> lines = Files.lines(buildProp)) {
            buildVersion = lines
                    .filter(l -> !l.startsWith("#"))
                    .filter(l -> l.startsWith("ro.build.display.id="))
                    .map(l -> l.substring(l.indexOf('=') + 1))
                    .findFirst()
                    .orElseThrow(() ->
                            new RuntimeException("ro.build.display.id not found in build.prop"));
        }

        log.info("Build version for commit: {}", buildVersion);


        log.info("Коммитим изменения");
        ProcessBuilder commitPb = new ProcessBuilder("git", "commit", "--allow-empty", "-m", buildVersion);
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

    public static void ensure(String groupName, String projectName) throws GitLabApiException {
        GitLabApi api = new GitLabApi(
                System.getenv("GIT_URL"),
                Constants.TokenType.PRIVATE,
                System.getenv("GIT_TOKEN")
        );

        Group group = api.getGroupApi().getGroups(groupName).stream().
                filter(g -> g.getName().equalsIgnoreCase(groupName)).findFirst().orElseGet(() -> {
                    try{
                        return api.getGroupApi().addGroup(
                                groupName,
                                groupName.toLowerCase()
                        );
                    }catch (Exception e){
                        throw new RuntimeException(e);
                    }
                });

        String projectPath = projectName.toLowerCase();
        String fullPath = group.getFullPath() + "/" + projectName.toLowerCase();
        try{
            api.getProjectApi().getProject(fullPath);
        }catch (GitLabApiException e){
            if (e.getHttpStatus() == 404) {
                Project p = new Project()
                        .withName(projectName)
                        .withPath(projectPath)
                        .withNamespaceId(group.getId())
                        .withLfsEnabled(true);

                try{
                    api.getProjectApi().createProject(p);
                }catch(Exception ex){
                    throw new RuntimeException(ex);
                }
            }
        }
    }

    public static void updateImages(Path targetDir, List<Path> images) throws GitLabApiException, Exception {

        String repoName = targetDir.getFileName().toString();
        String gitRemote = "git@100.98.83.30:" + GIT_GROUP + "/" + repoName.toLowerCase() + ".git";

        log.info("Updating to gitlab");

        Path gitDir = targetDir.resolve(".git");

        Files.createDirectories(targetDir);

        if (!Files.exists(gitDir)) {
            log.info("Локальный репозиторий не найден — инициализируем");

            log.info("Инициализация гит проекта");
            ProcessBuilder initPb = new ProcessBuilder("git", "init");
            initPb.directory(targetDir.toFile());
            initPb.redirectErrorStream(true);
            int initCode = initPb.start().waitFor();
            if (initCode != 0) throw new RuntimeException("git init failed with code " + initCode);

            log.info("Создание ветки: main");
            ProcessBuilder branchPb = new ProcessBuilder("git", "branch", "-M", "main");
            branchPb.directory(targetDir.toFile());
            branchPb.redirectErrorStream(true);
            int branchCode = branchPb.start().waitFor();
            if (branchCode != 0) throw new RuntimeException("git branch -M main failed with code " + branchCode);

            log.info("Создание подключения к проекту в GitLab");
            ProcessBuilder remotePb = new ProcessBuilder("git", "remote", "add", "origin", gitRemote);
            remotePb.directory(targetDir.toFile());
            remotePb.redirectErrorStream(true);
            int remoteCode = remotePb.start().waitFor();
            if (remoteCode != 0) throw new RuntimeException("git remote add origin failed with code " + remoteCode);

        } else {
            log.info("Локальный репозиторий уже существует — используем существующий");

            log.info("Проверяем/обновляем origin");
            ProcessBuilder setUrlPb = new ProcessBuilder("git", "remote", "set-url", "origin", gitRemote);
            setUrlPb.directory(targetDir.toFile());
            setUrlPb.redirectErrorStream(true);
            int setUrlCode = setUrlPb.start().waitFor();
            if (setUrlCode != 0) {
                log.warn("git remote set-url origin завершился с кодом {}, продолжаем", setUrlCode);
            }
        }


        log.info("Подтягиваем изменения из origin (fetch)");
        ProcessBuilder fetchPb = new ProcessBuilder("git", "fetch", "origin", "-v");
        fetchPb.directory(targetDir.toFile());
        fetchPb.redirectErrorStream(true);
        fetchPb.environment().put("GIT_SSH_COMMAND", "ssh -oBatchMode=yes");

        Process fetchProcess = fetchPb.start();
        try (BufferedReader br = new BufferedReader(new InputStreamReader(fetchProcess.getInputStream()))) {
            String line;
            while ((line = br.readLine()) != null) {
                log.info("[git fetch] {}", line);
            }
        }
        int fetchCode = fetchProcess.waitFor();
        log.info("git fetch exit code = {}", fetchCode);
        if (fetchCode != 0) {
            throw new RuntimeException("git fetch failed with exit code " + fetchCode);
        }

        log.info("Переключаемся на main");
        ProcessBuilder checkoutPb = new ProcessBuilder("git", "checkout", "-B", "main");
        checkoutPb.directory(targetDir.toFile());
        checkoutPb.redirectErrorStream(true);
        int checkoutCode = checkoutPb.start().waitFor();
        if (checkoutCode != 0) {
            throw new RuntimeException("git checkout -B main failed with code " + checkoutCode);
        }

        log.info("Сбрасываем локальные изменения в состояние origin/main (если ветка существует)");
        ProcessBuilder resetPb = new ProcessBuilder("git", "reset", "--hard", "origin/main");
        resetPb.directory(targetDir.toFile());
        resetPb.redirectErrorStream(true);
        Process resetProcess = resetPb.start();

        StringBuilder resetOut = new StringBuilder();
        try (BufferedReader br = new BufferedReader(new InputStreamReader(resetProcess.getInputStream()))) {
            String line;
            while ((line = br.readLine()) != null) {
                resetOut.append(line).append("\n");
                log.info("[git reset] {}", line);
            }
        }
        int resetCode = resetProcess.waitFor();
        if (resetCode != 0) {
            // Это нормально, если remote пустой и ветки main ещё нет
            log.warn("git reset --hard origin/main завершился с кодом {} (возможно, remote ещё пустой). Продолжаем.\n{}",
                    resetCode, resetOut);
        }

        // ===== 3. Полная очистка папки (кроме .git и ignore) =====
        log.info("Очищаем папку проекта (кроме .git/.gitignore/.gitattributes)");
        wipeDirectoryExceptGit(targetDir);

        // ===== 4. Распаковка новых образов =====
        log.info("Распаковываем новые образы в {}", targetDir.toAbsolutePath());
        for (Path img : images) {
            if (img == null) continue;

            String fileName = img.getFileName().toString();
            String folderName = fileName.contains(".")
                    ? fileName.substring(0, fileName.lastIndexOf('.'))
                    : fileName;

            Path outDir = targetDir.resolve(folderName); // system / vendor
            log.info("Распаковка: {} -> {}", img.toAbsolutePath(), outDir.toAbsolutePath());

            extractImgWith7z(img, outDir);
        }

        ApkScripts.decodeApksToProjectRoot(targetDir);

        // ===== 5. git add =====
        log.info("Добавление файлов образа в проект");
        ProcessBuilder addPb = new ProcessBuilder("git", "add", ".");
        addPb.directory(targetDir.toFile());
        addPb.redirectErrorStream(true);
        int addCode = addPb.start().waitFor();
        if (addCode != 0) throw new RuntimeException("git add . failed with code " + addCode);

        // ===== 6. commit =====


        String buildVersion;

        Path buildProp = targetDir.resolve("system").resolve("build.prop");
        if (!Files.exists(buildProp)) {
            throw new RuntimeException("build.prop not found: " + buildProp);
        }

        try (Stream<String> lines = Files.lines(buildProp)) {
            buildVersion = lines
                    .filter(l -> !l.startsWith("#"))
                    .filter(l -> l.startsWith("ro.build.display.id="))
                    .map(l -> l.substring(l.indexOf('=') + 1))
                    .findFirst()
                    .orElseThrow(() ->
                            new RuntimeException("ro.build.display.id not found in build.prop"));
        }

        log.info("Build version for commit: {}", buildVersion);

        log.info("Коммитим изменения");
        ProcessBuilder commitPb = new ProcessBuilder("git", "commit", "--allow-empty", "-m", buildVersion);
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

        // ===== 7. pull --rebase (чтобы не словить fetch first) =====
        log.info("Делаем pull --rebase перед push (чтобы не было fetch first)");
        ProcessBuilder pullPb = new ProcessBuilder("git", "pull", "--rebase", "origin", "main", "-v");
        pullPb.directory(targetDir.toFile());
        pullPb.redirectErrorStream(true);
        pullPb.environment().put("GIT_SSH_COMMAND", "ssh -oBatchMode=yes");

        Process pullProcess = pullPb.start();
        StringBuilder pullOut = new StringBuilder();
        try (BufferedReader br = new BufferedReader(new InputStreamReader(pullProcess.getInputStream()))) {
            String line;
            while ((line = br.readLine()) != null) {
                pullOut.append(line).append("\n");
                log.info("[git pull] {}", line);
            }
        }
        int pullCode = pullProcess.waitFor();
        if (pullCode != 0) {
            // бывает, если remote пустой/нет main — не критично
            log.warn("git pull --rebase завершился с кодом {}. Продолжаем.\n{}", pullCode, pullOut);
        }

        // ===== 8. push =====
        log.info("Пушим обновления в репозиторий");
        ProcessBuilder pushPb = new ProcessBuilder("git", "push", "-u", "origin", "main", "-v");
        pushPb.directory(targetDir.toFile());
        pushPb.redirectErrorStream(true);
        pushPb.environment().put("GIT_SSH_COMMAND", "ssh -oBatchMode=yes");

        Process pushProcess = pushPb.start();
        StringBuilder pushOut = new StringBuilder();
        try (BufferedReader br = new BufferedReader(new InputStreamReader(pushProcess.getInputStream()))) {
            String line;
            while ((line = br.readLine()) != null) {
                pushOut.append(line).append("\n");
                log.info("[git push] {}", line);
            }
        }
        int pushCode = pushProcess.waitFor();
        log.info("git push exit code = {}", pushCode);

        if (pushCode != 0) {
            throw new RuntimeException("git push failed with exit code " + pushCode + "\nOutput:\n" + pushOut);
        }
    }

    private static void wipeDirectoryExceptGit(Path folder) throws IOException {
        if (!Files.exists(folder)) return;

        try (Stream<Path> walk = Files.walk(folder)) {
            walk.sorted(Comparator.reverseOrder())
                    .filter(p -> !p.equals(folder))
                    .filter(p -> {
                        String name = p.getFileName().toString();
                        // не трогаем .git и всё внутри
                        if (name.equals(".git")) return false;
                        if (p.startsWith(folder.resolve(".git"))) return false;
                        // не трогаем явные ignore-файлы в корне
                        for (String ig : ignore) {
                            if (name.equals(ig) && p.getParent() != null && p.getParent().equals(folder)) {
                                return false;
                            }
                        }
                        return true;
                    })
                    .forEach(p -> {
                        try {
                            Files.deleteIfExists(p);
                        } catch (IOException e) {
                            throw new RuntimeException("Не смог удалить: " + p, e);
                        }
                    });
        }
    }

}