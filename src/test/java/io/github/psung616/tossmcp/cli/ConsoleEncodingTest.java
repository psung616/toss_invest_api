package io.github.psung616.tossmcp.cli;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.FileDescriptor;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

/** 파이프 출력일 때만 UTF-8 (Git Bash·Claude Code `!` 한글 깨짐 대응). */
class ConsoleEncodingTest {

    @Test
    void forcesUtf8OnlyWithoutConsole() {
        assertThat(ConsoleEncoding.shouldForceUtf8(false)).isTrue();
        assertThat(ConsoleEncoding.shouldForceUtf8(true)).isFalse();
    }

    @Test
    void utf8StreamUsesUtf8() {
        PrintStream stream = ConsoleEncoding.utf8(FileDescriptor.out);
        assertThat(stream.charset()).isEqualTo(StandardCharsets.UTF_8);
    }
}
