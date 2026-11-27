package io.github.psung616.tossmcp.cli;

import java.io.FileDescriptor;
import java.io.FileOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;

/**
 * CLI 출력 인코딩. Java 21 은 출력이 콘솔이 아닌 파이프·파일로 갈 때 OS 기본 인코딩(한국어 Windows 는 MS949)을 쓴다. Git Bash·Claude Code 의
 * {@code !} 명령처럼 파이프로 받아 UTF-8 로 읽는 쪽에서는 한글이 깨지므로, <b>콘솔이 붙어 있지 않을 때만</b> stdout·stderr 를 UTF-8 로 바꾼다. 콘솔
 * 창(PowerShell·cmd)에 직접 출력할 때는 콘솔 코드 페이지를 그대로 쓴다.
 *
 * <p>{@code serve} 모드에는 쓰지 않는다 — stdout 은 MCP 프로토콜 전용이며 Spring AI 전송 계층에 맡긴다.
 */
public final class ConsoleEncoding {

    private ConsoleEncoding() {}

    public static void useUtf8WhenRedirected() {
        if (!shouldForceUtf8(System.console() != null)) {
            return;
        }
        System.setOut(utf8(FileDescriptor.out));
        System.setErr(utf8(FileDescriptor.err));
    }

    /** Java 21: System.console() 은 입출력이 리다이렉트되면 null. */
    static boolean shouldForceUtf8(boolean consoleAttached) {
        return !consoleAttached;
    }

    static PrintStream utf8(FileDescriptor fd) {
        return new PrintStream(new FileOutputStream(fd), true, StandardCharsets.UTF_8);
    }
}
