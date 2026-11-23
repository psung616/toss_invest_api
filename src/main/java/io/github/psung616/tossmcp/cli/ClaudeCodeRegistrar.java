package io.github.psung616.tossmcp.cli;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * Claude Code 에 이 서버를 사용자 범위(user scope)로 등록한다. 키는 OS 자격 증명 저장소에 있으므로
 * 등록 명령에는 키가 들어가지 않는다 (저장소의 .mcp.json 에 키가 섞일 위험도 없음).
 */
final class ClaudeCodeRegistrar {

    static final String SERVER_NAME = "tossinvest";

    private static final boolean WINDOWS =
            System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win");

    private ClaudeCodeRegistrar() {}

    record Result(boolean ok, String message) {}

    /** PATH 에서 claude 실행 파일을 찾는다. */
    static Optional<Path> findClaude() {
        try {
            Process p = new ProcessBuilder(WINDOWS ? List.of("where", "claude") : List.of("which", "claude"))
                    .redirectErrorStream(true)
                    .start();
            String out = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            if (!p.waitFor(10, TimeUnit.SECONDS) || p.exitValue() != 0) {
                return Optional.empty();
            }
            List<Path> found = out.lines()
                    .map(String::strip)
                    .filter(s -> !s.isEmpty())
                    .map(Path::of)
                    .filter(Files::isRegularFile)
                    .toList();
            if (WINDOWS) {
                // npm 설치는 확장자 없는 sh 스크립트와 claude.cmd 가 함께 있다. 실행 가능한 것만 고른다.
                return found.stream()
                        .filter(f -> name(f).endsWith(".exe"))
                        .findFirst()
                        .or(() -> found.stream()
                                .filter(f -> name(f).endsWith(".cmd"))
                                .findFirst());
            }
            return found.stream().findFirst();
        } catch (IOException e) {
            return Optional.empty();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return Optional.empty();
        }
    }

    static Result register(Path claude) {
        return register(claude, SERVER_NAME);
    }

    static Result register(Path claude, String serverName) {
        // 이미 등록되어 있으면 지우고 다시 등록한다 (jar 위치가 바뀐 경우 대비). 실패해도 무시.
        run(claude, List.of("mcp", "remove", "--scope", "user", serverName));

        List<String> args =
                new ArrayList<>(List.of("mcp", "add", "--transport", "stdio", "--scope", "user", serverName, "--"));
        args.addAll(serverCommand());
        ProcessOutput out = run(claude, args);
        if (out.exitCode == 0) {
            return new Result(true, "Claude Code 에 '" + serverName + "' 로 등록했습니다.");
        }
        return new Result(false, "Claude Code 등록에 실패했습니다: " + out.text.strip());
    }

    static Result unregister(Path claude, String serverName) {
        ProcessOutput out = run(claude, List.of("mcp", "remove", "--scope", "user", serverName));
        return new Result(out.exitCode == 0, out.text.strip());
    }

    /**
     * 지금 실행 중인 프로그램을 serve 모드로 다시 실행하는 명령.
     * java -jar 로 실행 중이면 [java, -jar, jar경로, serve], jpackage 실행 파일이면 [exe경로, serve].
     */
    static List<String> serverCommand() {
        String self = ProcessHandle.current().info().command().orElse("java");
        String exeName = Path.of(self).getFileName().toString().toLowerCase(Locale.ROOT);
        if (exeName.startsWith("java")) {
            // 더블클릭(javaw)으로 실행됐어도 등록은 콘솔용 java 로 한다.
            Path java = Path.of(self).resolveSibling(WINDOWS ? "java.exe" : "java");
            String classPath = System.getProperty("java.class.path", "");
            if (!classPath.toLowerCase(Locale.ROOT).endsWith(".jar") || classPath.contains(File.pathSeparator)) {
                throw new IllegalStateException("jar 파일로 실행했을 때만 Claude Code 에 등록할 수 있습니다.");
            }
            String jar = Path.of(classPath).toAbsolutePath().toString();
            return List.of(Files.exists(java) ? java.toString() : self, "-jar", jar, "serve");
        }
        return List.of(self, "serve");
    }

    private record ProcessOutput(int exitCode, String text) {}

    private static ProcessOutput run(Path claude, List<String> args) {
        List<String> command = new ArrayList<>();
        if (WINDOWS && name(claude).endsWith(".cmd")) {
            // .cmd 는 cmd.exe 로 실행해야 한다. /s /c "..." 는 바깥 따옴표만 벗기고 안쪽 따옴표는 유지한다.
            String line = quote(claude.toString()) + " "
                    + args.stream().map(ClaudeCodeRegistrar::quote).collect(Collectors.joining(" "));
            command.addAll(List.of("cmd.exe", "/d", "/s", "/c", "\"" + line + "\""));
        } else {
            command.add(claude.toString());
            command.addAll(args);
        }
        try {
            Process p = new ProcessBuilder(command).redirectErrorStream(true).start();
            p.getOutputStream().close();
            String text = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            if (!p.waitFor(90, TimeUnit.SECONDS)) {
                p.destroyForcibly();
                return new ProcessOutput(-1, "claude 명령이 응답하지 않습니다.");
            }
            return new ProcessOutput(p.exitValue(), text);
        } catch (IOException e) {
            return new ProcessOutput(-1, e.getMessage());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return new ProcessOutput(-1, "중단되었습니다.");
        }
    }

    private static String quote(String s) {
        return s.isEmpty() || s.chars().anyMatch(c -> c == ' ' || c == '&' || c == '(' || c == ')')
                ? "\"" + s + "\""
                : s;
    }

    private static String name(Path p) {
        return p.getFileName().toString().toLowerCase(Locale.ROOT);
    }
}
