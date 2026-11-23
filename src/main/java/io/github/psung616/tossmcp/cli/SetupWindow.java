package io.github.psung616.tossmcp.cli;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import javax.swing.UIManager;
import javax.swing.WindowConstants;

/**
 * 더블클릭으로 실행했을 때 뜨는 설정 창. 터미널을 모르는 사용자도 키를 붙여넣고 [저장]만 누르면 된다.
 */
final class SetupWindow {

    private final SetupSteps steps;
    private final Optional<Path> claude;

    private final JTextField clientId = new JTextField(28);
    private final JPasswordField clientSecret = new JPasswordField(28);
    private final JCheckBox registerClaude = new JCheckBox("Claude Code 에 자동 등록");
    private final JButton saveButton = new JButton("저장하고 연결 테스트");
    private final JTextArea log = new JTextArea(9, 46);

    private SetupWindow(SetupSteps steps) {
        this.steps = steps;
        this.claude = steps.findClaudeCode();
    }

    static void open() {
        SetupSteps steps = new SetupSteps();
        SwingUtilities.invokeLater(() -> new SetupWindow(steps).show());
    }

    private void show() {
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception ignored) {
            // 기본 룩앤필 사용
        }
        JFrame frame = new JFrame("tossinvest-mcp 설정");
        frame.setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);

        JPanel form = new JPanel(new GridBagLayout());
        form.setBorder(BorderFactory.createEmptyBorder(16, 16, 8, 16));
        GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(4, 4, 4, 4);
        c.anchor = GridBagConstraints.WEST;

        c.gridx = 0;
        c.gridy = 0;
        c.gridwidth = 2;
        form.add(
                new JLabel("<html><b>토스증권 Open API 키를 붙여넣으세요</b><br>"
                        + "토스증권 PC 웹(WTS) &gt; 설정 &gt; Open API 에서 발급할 수 있습니다.<br>"
                        + "키는 이 PC 의 " + SetupSteps.keyringName() + "에만 저장됩니다.</html>"),
                c);

        c.gridwidth = 1;
        c.gridy = 1;
        form.add(new JLabel("Client ID"), c);
        c.gridx = 1;
        form.add(clientId, c);

        c.gridx = 0;
        c.gridy = 2;
        form.add(new JLabel("Client Secret"), c);
        c.gridx = 1;
        form.add(clientSecret, c);

        c.gridx = 1;
        c.gridy = 3;
        registerClaude.setSelected(claude.isPresent());
        registerClaude.setEnabled(claude.isPresent());
        if (claude.isEmpty()) {
            registerClaude.setText("Claude Code 에 자동 등록 (설치되어 있지 않음)");
        }
        form.add(registerClaude, c);

        c.gridy = 4;
        form.add(saveButton, c);

        log.setEditable(false);
        log.setLineWrap(true);
        log.setWrapStyleWord(true);
        steps.savedClientIdMasked()
                .ifPresent(id -> log.setText("이미 저장된 키가 있습니다 (Client ID " + id + "). 새로 입력하면 덮어씁니다.\n"));
        JScrollPane scroll = new JScrollPane(log);
        scroll.setBorder(BorderFactory.createTitledBorder("진행 상황"));
        scroll.setPreferredSize(new Dimension(520, 180));

        JPanel root = new JPanel(new BorderLayout());
        root.add(form, BorderLayout.NORTH);
        JPanel logPanel = new JPanel(new BorderLayout());
        logPanel.setBorder(BorderFactory.createEmptyBorder(0, 16, 16, 16));
        logPanel.add(scroll);
        root.add(logPanel, BorderLayout.CENTER);

        saveButton.addActionListener(e -> runSetup());
        frame.getRootPane().setDefaultButton(saveButton);
        frame.setContentPane(root);
        frame.pack();
        frame.setLocationRelativeTo(null);
        frame.setVisible(true);
    }

    private void runSetup() {
        String id = clientId.getText();
        char[] secret = clientSecret.getPassword();
        clientSecret.setText("");
        boolean register = registerClaude.isSelected();
        saveButton.setEnabled(false);

        new SwingWorker<Void, String>() {
            @Override
            protected Void doInBackground() {
                List<SetupSteps.Outcome> results = new ArrayList<>();
                try {
                    if (!id.isBlank() || secret.length > 0) {
                        SetupSteps.Outcome saved = steps.save(id, secret);
                        publish(line(saved));
                        if (!saved.ok()) {
                            return null;
                        }
                    }
                } finally {
                    Arrays.fill(secret, '\0');
                }
                publish("연결 테스트 중...");
                publish(line(steps.testConnection()));
                if (register && claude.isPresent()) {
                    publish("Claude Code 에 등록 중... (10초 정도 걸릴 수 있습니다)");
                    SetupSteps.Outcome r = steps.registerClaudeCode(claude.get());
                    results.add(r);
                    publish(line(r));
                }
                publish(
                        results.stream().allMatch(SetupSteps.Outcome::ok)
                                ? "\n완료! Claude 에서 \"삼성전자 현재가 알려줘\" 라고 물어보세요. 이 창은 닫아도 됩니다."
                                : "\n일부 단계가 실패했습니다. 위 안내를 확인하세요.");
                return null;
            }

            @Override
            protected void process(List<String> lines) {
                lines.forEach(l -> log.append(l + "\n"));
            }

            @Override
            protected void done() {
                saveButton.setEnabled(true);
            }
        }.execute();
    }

    private static String line(SetupSteps.Outcome outcome) {
        return (outcome.ok() ? "✔ " : "✖ ") + outcome.message();
    }
}
