import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
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
 * ============================================================
 * ChatPanel —— 客戶端「聊天」區塊（可嵌進主視窗右側）
 * ============================================================
 *
 * 【網路流程（對照程式閱讀）】
 *  1. 使用者按 Connect
 *       → new Socket(host, port) 連上 ChatServer
 *       → 取得 fromServer / toServer 兩條串流
 *       → 開背景執行緒 readLoop() 持續 readUTF
 *  2. 使用者按 Send（或在輸入框按 Enter）
 *       → toServer.writeUTF("暱稱: 訊息")
 *  3. Server 廣播後，本機 readLoop 會讀到字串並顯示在 messageArea
 *
 * 【為什麼讀訊息要開新執行緒？】
 *  readUTF() 會阻塞。若在 Swing 主執行緒（EDT）上呼叫，
 *  整個視窗會卡住（按鈕按不了、畫面不更新）。
 *  所以「收訊息」放背景執行緒，「改 UI」再用 invokeLater 回到 EDT。
 */
public class ChatPanel extends JPanel {

    // ---------- UI 元件 ----------
    private final JTextField hostField = new JTextField("localhost", 10);
    private final JTextField portField = new JTextField(String.valueOf(54321), 5);
    private final JTextField nameField = new JTextField("Player", 8);
    private final JTextArea messageArea = new JTextArea();
    private final JTextField inputField = new JTextField();
    private final JButton connectBtn = new JButton("Connect");
    private final JButton sendBtn = new JButton("Send");
    private final JLabel statusLabel = new JLabel("Status: offline");

    // ---------- 網路狀態 ----------
    private Socket socket;                 // 與 Server 的 TCP 連線
    private DataInputStream fromServer;    // 讀：Server → 我
    private DataOutputStream toServer;     // 寫：我 → Server
    /**
     * volatile：讓「UI 執行緒」與「讀取執行緒」看到同一個最新值。
     * connected=false 時，readLoop 應停止；disconnect 也靠它判斷狀態。
     */
    private volatile boolean connected = false;
    private Thread readerThread;

    public ChatPanel() {
        setLayout(new BorderLayout(8, 8));
        setBorder(new EmptyBorder(8, 8, 8, 8));
        setPreferredSize(new Dimension(320, 520));

        // ===== 上方：連線設定列 =====
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

        // ===== 中間：聊天紀錄（唯讀）=====
        messageArea.setEditable(false);
        messageArea.setLineWrap(true);
        messageArea.setWrapStyleWord(true);
        messageArea.setFont(new Font("SansSerif", Font.PLAIN, 13));

        // ===== 下方：輸入 + 送出 =====
        JPanel bottom = new JPanel(new BorderLayout(4, 4));
        bottom.add(inputField, BorderLayout.CENTER);
        bottom.add(sendBtn, BorderLayout.EAST);

        add(top, BorderLayout.NORTH);
        add(new JScrollPane(messageArea), BorderLayout.CENTER);
        add(bottom, BorderLayout.SOUTH);

        // 事件綁定：Connect 切換連線；Send / Enter 送訊息
        connectBtn.addActionListener(e -> toggleConnect());
        sendBtn.addActionListener(e -> sendMessage());
        inputField.addActionListener(e -> sendMessage()); // 按 Enter 也送出

        setChatEnabled(false); // 尚未連線：不能送訊息
    }

    /**
     * 依連線狀態啟用／停用相關元件。
     * 連上後鎖住 Host/Port/Name，避免連線中途亂改造成誤解。
     */
    private void setChatEnabled(boolean on) {
        sendBtn.setEnabled(on);
        inputField.setEnabled(on);
        hostField.setEnabled(!on);
        portField.setEnabled(!on);
        nameField.setEnabled(!on);
        connectBtn.setText(on ? "Disconnect" : "Connect");
        statusLabel.setText(on ? "Status: online" : "Status: offline");
    }

    /** Connect / Disconnect 同一個按鈕，依目前狀態切換 */
    private void toggleConnect() {
        if (connected) {
            disconnect();
        } else {
            connect();
        }
    }

    /**
     * 建立 TCP 連線，並啟動背景讀取執行緒。
     * 失敗時會顯示原因並回到 offline。
     */
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
            // 1) 連上 Server（Server 必須已在該 host:port listen）
            socket = new Socket(host, port);
            // 2) 包成 Data*Stream，之後用 writeUTF / readUTF 傳字串
            fromServer = new DataInputStream(socket.getInputStream());
            toServer = new DataOutputStream(socket.getOutputStream());
            connected = true;
            setChatEnabled(true);
            appendSystem("Connected to " + host + ":" + port);

            // 3) 背景執行緒專門阻塞讀訊息；daemon=true → 主程式關了它也會結束
            readerThread = new Thread(this::readLoop, "chat-reader");
            readerThread.setDaemon(true);
            readerThread.start();
        } catch (IOException e) {
            appendSystem("Connect failed: " + e.getMessage());
            disconnect();
        }
    }

    /**
     * 背景迴圈：一直 readUTF，讀到就顯示。
     * 當 Server 關閉或本機 disconnect 關閉 Socket 時，
     * readUTF 會拋 IOException，進而清理連線狀態。
     */
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
            // disconnect 會改 UI，必須排回 EDT
            SwingUtilities.invokeLater(this::disconnect);
        }
    }

    /**
     * 送出一則聊天訊息。
     * 實際協定內容就是一個字串："暱稱: 內容"
     * Server 收到後原樣廣播，不做額外解析。
     */
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
     * 給遊戲層呼叫的「輕量整合點」。
     * Game Over 時 SnakeChatApp 會傳入分數說明，
     * 這裡組成 "[Snake] 暱稱 ..." 再走同一條 writeUTF 通道。
     * （若尚未連線，安靜略過，不影響本機遊戲）
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

    /** 關閉 Socket、清狀態、UI 回到 offline */
    private void disconnect() {
        connected = false;
        try {
            if (socket != null) {
                socket.close(); // 會讓卡在 readUTF 的執行緒跳出
            }
        } catch (IOException ignored) {
        }
        socket = null;
        fromServer = null;
        toServer = null;
        setChatEnabled(false);
        appendSystem("Disconnected.");
    }

    /** 把訊息加到文字區；一定要在 EDT 上改 Swing 元件 */
    private void appendMessage(String msg) {
        SwingUtilities.invokeLater(() -> {
            messageArea.append(msg + "\n");
            // 自動捲到最底，方便看最新訊息
            messageArea.setCaretPosition(messageArea.getDocument().getLength());
        });
    }

    /** 系統提示（連線成功／失敗等），前面加 * 與一般聊天區隔 */
    private void appendSystem(String msg) {
        appendMessage("* " + msg);
    }
}
