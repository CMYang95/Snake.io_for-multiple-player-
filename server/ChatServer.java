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
 * 聊天室伺服器（方案 1 網路層核心）
 *
 * 職責：
 * 1. 在固定埠口監聽連線（預設 54321）
 * 2. 為每個 client 開一個執行緒讀訊息
 * 3. 把收到的訊息廣播給所有已連線 client
 *
 * 注意：遊戲邏輯不在伺服器上；本方案遊戲是本機執行，
 * 網路只負責文字聊天。
 */
public class ChatServer extends JFrame {

    public static final int PORT = 54321;

    private final JTextArea logArea = new JTextArea("Server started on port " + PORT + "\n");
    private final Map<Socket, DataOutputStream> clients = new LinkedHashMap<>();
    private final ExecutorService pool = Executors.newCachedThreadPool();

    private ServerSocket serverSocket;

    public ChatServer() throws IOException {
        setTitle("SnakeChat Server");
        setLayout(new BorderLayout());
        logArea.setEditable(false);
        add(new JScrollPane(logArea), BorderLayout.CENTER);
        setSize(520, 340);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLocationRelativeTo(null);
        setVisible(true);

        serverSocket = new ServerSocket(PORT);
        appendLog("Waiting for clients...\n");

        // accept 會阻塞，所以放在建構子迴圈裡（與原專案相同風格）
        while (true) {
            Socket socket = serverSocket.accept();
            DataOutputStream out = new DataOutputStream(socket.getOutputStream());
            synchronized (clients) {
                clients.put(socket, out);
            }
            appendLog("Client connected: " + socket.getRemoteSocketAddress() + "\n");
            pool.execute(new ClientHandler(socket));
        }
    }

    private void appendLog(String text) {
        SwingUtilities.invokeLater(() -> logArea.append(text));
    }

    private void broadcast(String message) {
        synchronized (clients) {
            Iterator<Entry<Socket, DataOutputStream>> it = clients.entrySet().iterator();
            while (it.hasNext()) {
                Entry<Socket, DataOutputStream> entry = it.next();
                try {
                    entry.getValue().writeUTF(message);
                    entry.getValue().flush();
                } catch (IOException e) {
                    it.remove();
                    try {
                        entry.getKey().close();
                    } catch (IOException ignored) {
                    }
                }
            }
        }
    }

    /**
     * 每個連線對應一個 handler：持續 readUTF，再廣播。
     */
    private class ClientHandler implements Runnable {
        private final Socket socket;
        private DataInputStream in;

        ClientHandler(Socket socket) throws IOException {
            this.socket = socket;
            this.in = new DataInputStream(socket.getInputStream());
        }

        @Override
        public void run() {
            try {
                String msg;
                while ((msg = in.readUTF()) != null) {
                    appendLog("From " + socket.getPort() + ": " + msg + "\n");
                    broadcast(msg);
                }
            } catch (IOException e) {
                appendLog("Client disconnected: " + socket.getRemoteSocketAddress() + "\n");
            } finally {
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

    public static void main(String[] args) throws IOException {
        new ChatServer();
    }
}
