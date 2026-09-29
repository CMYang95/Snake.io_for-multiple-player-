import java.awt.BorderLayout;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import javax.swing.JFrame;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.SwingUtilities;

/**
 * ============================================================
 * ChatServer —— 聊天室伺服器（方案 1 的網路層核心）
 * ============================================================
 *
 * 【這個類別負責什麼？】
 *  1. 在固定埠口（PORT）監聽客戶端連線
 *  2. 為「每一個」連進來的 client 開一條讀取執行緒
 *  3. 收到任何一則訊息後，廣播給「所有」已連線的 client
 *
 * 【這個類別不負責什麼？】
 *  - 不跑貪食蛇邏輯（遊戲在各 client 本機執行）
 *  - 不解析複雜協定（訊息就是 UTF 字串）
 *
 * 【怎麼啟動？】
 *  javac ChatServer.java
 *  java ChatServer
 *  → 先開 Server，再開一個或多個 SnakeChatApp
 */
public class ChatServer extends JFrame {

    /** 伺服器監聽埠口；Client 連線時必須填同一個數字 */
    public static final int PORT = 54321;

    /** 伺服器視窗上的 log，方便除錯時看誰連上、誰說了什麼 */
    private final JTextArea logArea = new JTextArea("Server started on port " + PORT + "\n");

    /**
     * 連線表：Socket → 對應的輸出串流
     * - Key：某個 client 的連線
     * - Value：要寫訊息給他時用的 DataOutputStream
     * 廣播時會遍歷這張表，對每個人 writeUTF。
     */
    private final Map<Socket, DataOutputStream> clients = new LinkedHashMap<>();

    /**
     * 執行緒池：每接受一個 client，就丟一個 ClientHandler 進去跑。
     * 用 CachedThreadPool 的好處：人多就多開執行緒，人少會回收。
     */
    private final ExecutorService pool = Executors.newCachedThreadPool();

    /** 真正綁定埠口、負責 accept() 的物件 */
    private ServerSocket serverSocket;

    public ChatServer() throws IOException {
        // ---------- UI：單純顯示 log，不是聊天室介面 ----------
        setTitle("SnakeChat Server");
        setLayout(new BorderLayout());
        logArea.setEditable(false);
        add(new JScrollPane(logArea), BorderLayout.CENTER);
        setSize(520, 340);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLocationRelativeTo(null);
        setVisible(true);

        // ---------- 網路：開始聽指定埠口 ----------
        // ServerSocket 綁定 PORT 後，作業系統會把連到此埠的 TCP 連線交給我們
        serverSocket = new ServerSocket(PORT);
        appendLog("Waiting for clients...\n");

        /*
         * accept() 是「阻塞呼叫」：
         * 沒人連進來時，這一行會一直卡住等；
         * 有人連進來才回傳一個代表該連線的 Socket。
         *
         * 因此這個 while 迴圈會永遠跑下去，直到程式關閉。
         */
        while (true) {
            Socket socket = serverSocket.accept();

            // 先準備好「寫給這個 client」的串流，存進連線表
            DataOutputStream out = new DataOutputStream(socket.getOutputStream());
            synchronized (clients) {
                // synchronized：避免 accept 執行緒與 handler 執行緒同時改 Map
                clients.put(socket, out);
            }
            appendLog("Client connected: " + socket.getRemoteSocketAddress() + "\n");

            // 為這個 client 開獨立執行緒去讀他的訊息（不要堵在 accept 迴圈）
            pool.execute(new ClientHandler(socket));
        }
    }

    /**
     * 把文字加到 log 區。
     * 必須用 invokeLater：網路執行緒不能直接改 Swing 元件，
     * 要排程回「事件分派執行緒 (EDT)」再更新畫面。
     */
    private void appendLog(String text) {
        SwingUtilities.invokeLater(() -> logArea.append(text));
    }

    /**
     * 廣播：把同一則 message 寫給連線表裡的每一個人。
     * 若寫入失敗（對方已斷線），就從表中移除並關閉 Socket。
     */
    private void broadcast(String message) {
        synchronized (clients) {
            Iterator<Entry<Socket, DataOutputStream>> it = clients.entrySet().iterator();
            while (it.hasNext()) {
                Entry<Socket, DataOutputStream> entry = it.next();
                try {
                    // writeUTF：會自動帶長度前綴，對方用 readUTF 即可完整讀出一字串
                    entry.getValue().writeUTF(message);
                    entry.getValue().flush(); // 立刻送出，不要卡在緩衝區
                } catch (IOException e) {
                    // 這個 client 寫不進去 → 視為離線
                    it.remove();
                    try {
                        entry.getKey().close();
                    } catch (IOException ignored) {
                        // 關閉時的例外可忽略
                    }
                }
            }
        }
    }

    /**
     * ClientHandler：一個連線 = 一個 Runnable。
     *
     * 生命週期：
     *   讀到訊息 → 寫進 Server log → broadcast 給所有人
     *   讀取拋 IOException（對方斷線）→ 從連線表移除 → 關閉 Socket
     */
    private class ClientHandler implements Runnable {
        private final Socket socket;
        private DataInputStream in;

        ClientHandler(Socket socket) throws IOException {
            this.socket = socket;
            // 只負責「讀」這個 client 傳來的資料
            this.in = new DataInputStream(socket.getInputStream());
        }

        @Override
        public void run() {
            try {
                String msg;
                // readUTF 同樣會阻塞，直到對方送出一則完整 UTF 字串
                while ((msg = in.readUTF()) != null) {
                    appendLog("From " + socket.getPort() + ": " + msg + "\n");
                    broadcast(msg); // 轉發給所有人（含自己）
                }
            } catch (IOException e) {
                // 常見原因：Client 關閉視窗、按 Disconnect、網路中斷
                appendLog("Client disconnected: " + socket.getRemoteSocketAddress() + "\n");
            } finally {
                // 無論正常結束或例外，都要清掉連線表，避免「殭屍連線」
                synchronized (clients) {
                    clients.remove(socket);
                }
                try {
                    socket.close();
                } catch (IOException ignored) {
                }
            }
        }
    }

    /** 程式進入點：建立伺服器視窗並開始 accept 迴圈 */
    public static void main(String[] args) throws IOException {
        new ChatServer();
    }
}
