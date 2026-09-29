import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.Socket;

import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import javax.swing.border.EmptyBorder;

/**
 * 聊天 UI 面板（可嵌進主視窗）
 *
 * 網路流程：
 * Connect → new Socket(host, port)
 *        → 背景執行緒 while(readUTF) 更新訊息區
 * Send    → writeUTF(暱稱 + 訊息)
 */
public class ChatPanel extends JPanel {

    private final JTextField hostField = new JTextField("localhost", 10);
    private final JTextField portField = new JTextField(String.valueOf(54321), 5);
    private final JTextField nameField = new JTextField("Player", 8);
    private final JTextArea messageArea = new JTextArea();
    private final JTextField inputField = new JTextField();
    private final JButton connectBtn = new JButton("Connect");
    private final JButton sendBtn = new JButton("Send");
    private final JLabel statusLabel = new JLabel("Status: offline");

    private Socket socket;
    private DataInputStream fromServer;
    private DataOutputStream toServer;
    private volatile boolean connected = false;
    private Thread readerThread;

    public ChatPanel() {
        setLayout(new BorderLayout(8, 8));
        setBorder(new EmptyBorder(8, 8, 8, 8));
        setPreferredSize(new Dimension(320, 520));

        // 上方：連線設定
        JPanel top = new JPanel(new BorderLayout(4, 4));
        JPanel row1 = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 2));
        row1.add(new JLabel("Host"));
        row1.add(hostField);
        row1.add(new JLabel("Port"));
        row1.add(portField);

        JPanel row2 = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 2));
        row2.add(new JLabel("Name"));
        row2.add(nameField);
        row2.add(connectBtn);

        top.add(row1, BorderLayout.NORTH);
        top.add(row2, BorderLayout.CENTER);
        top.add(statusLabel, BorderLayout.SOUTH);

        // 中間：訊息區
        messageArea.setEditable(false);
        messageArea.setLineWrap(true);
        messageArea.setWrapStyleWord(true);
        messageArea.setFont(new Font("SansSerif", Font.PLAIN, 13));

        // 下方：輸入列
        JPanel bottom = new JPanel(new BorderLayout(4, 4));
        bottom.add(inputField, BorderLayout.CENTER);
        bottom.add(sendBtn, BorderLayout.EAST);

        add(top, BorderLayout.NORTH);
        add(new JScrollPane(messageArea), BorderLayout.CENTER);
        add(bottom, BorderLayout.SOUTH);

        connectBtn.addActionListener(e -> toggleConnect());
        sendBtn.addActionListener(e -> sendMessage());
        inputField.addActionListener(e -> sendMessage());

        setChatEnabled(false);
    }

    private void setChatEnabled(boolean on) {
        sendBtn.setEnabled(on);
        inputField.setEnabled(on);
        hostField.setEnabled(!on);
        portField.setEnabled(!on);
        nameField.setEnabled(!on);
        connectBtn.setText(on ? "Disconnect" : "Connect");
        statusLabel.setText(on ? "Status: online" : "Status: offline");
    }

    private void toggleConnect() {
        if (connected) {
            disconnect();
        } else {
            connect();
        }
    }

    private void connect() {
        String host = hostField.getText().trim();
        int port;
        try {
            port = Integer.parseInt(portField.getText().trim());
        } catch (NumberFormatException ex) {
            appendSystem("Port must be a number.");
            return;
        }

        try {
            socket = new Socket(host, port);
            fromServer = new DataInputStream(socket.getInputStream());
            toServer = new DataOutputStream(socket.getOutputStream());
            connected = true;
            setChatEnabled(true);
            appendSystem("Connected to " + host + ":" + port);

            readerThread = new Thread(this::readLoop, "chat-reader");
            readerThread.setDaemon(true);
            readerThread.start();
        } catch (IOException e) {
            appendSystem("Connect failed: " + e.getMessage());
            disconnect();
        }
    }

    private void readLoop() {
        try {
            while (connected) {
                String msg = fromServer.readUTF();
                appendMessage(msg);
            }
        } catch (IOException e) {
            if (connected) {
                appendSystem("Connection closed by server.");
            }
            SwingUtilities.invokeLater(this::disconnect);
        }
    }

    private void sendMessage() {
        if (!connected || toServer == null) {
            appendSystem("Not connected.");
            return;
        }
        String text = inputField.getText().trim();
        if (text.isEmpty()) {
            return;
        }
        String name = nameField.getText().trim();
        if (name.isEmpty()) {
            name = "Anonymous";
        }
        String payload = name + ": " + text;
        try {
            toServer.writeUTF(payload);
            toServer.flush();
            inputField.setText("");
        } catch (IOException e) {
            appendSystem("Send failed: " + e.getMessage());
            disconnect();
        }
    }

    /**
     * 給遊戲結束時呼叫：把分數丟進聊天室（方案 1 的輕量整合點）
     */
    public void sendSystemNote(String note) {
        if (!connected || toServer == null) {
            return;
        }
        String name = nameField.getText().trim();
        if (name.isEmpty()) {
            name = "Anonymous";
        }
        String payload = "[Snake] " + name + " " + note;
        try {
            toServer.writeUTF(payload);
            toServer.flush();
        } catch (IOException e) {
            appendSystem("Score share failed.");
        }
    }

    private void disconnect() {
        connected = false;
        try {
            if (socket != null) {
                socket.close();
            }
        } catch (IOException ignored) {
        }
        socket = null;
        fromServer = null;
        toServer = null;
        setChatEnabled(false);
        appendSystem("Disconnected.");
    }

    private void appendMessage(String msg) {
        SwingUtilities.invokeLater(() -> {
            messageArea.append(msg + "\n");
            messageArea.setCaretPosition(messageArea.getDocument().getLength());
        });
    }

    private void appendSystem(String msg) {
        appendMessage("* " + msg);
    }
}
