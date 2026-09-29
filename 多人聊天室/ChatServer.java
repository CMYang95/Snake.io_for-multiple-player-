import java.awt.BorderLayout;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Map.Entry;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Executor;

import javax.swing.JFrame;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;

public class ChatServer extends JFrame {
    private JTextArea txt = new JTextArea("Server on\n");
    private ServerSocket serverSocket = null;
    private static Map<Socket, DataOutputStream> clientOutputStreams = new LinkedHashMap<>();
    private ExecutorService executorService = null;

    public ChatServer() throws IOException {
        setLayout(new BorderLayout());
        this.add(new JScrollPane(txt), BorderLayout.CENTER);
        setSize(500, 300);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setVisible(true);

        serverSocket = new ServerSocket(54321);
        executorService = Executors.newCachedThreadPool();

        while (true) {
            Socket socket = null;
            socket = serverSocket.accept();
            txt.append("New client connected: " + socket.getPort() + "\n");
            DataOutputStream clientOutputStream = new DataOutputStream(socket.getOutputStream());
            clientOutputStreams.put(socket, clientOutputStream);
            executorService.execute(new Communication(socket));
        }
    }

    public class Communication implements Runnable {
        private Socket socket;
        private DataInputStream fromClient = null;
        private String msg;

        public Communication(Socket socket) throws IOException {
            this.socket = socket;
            fromClient = new DataInputStream(socket.getInputStream());
        }

        @Override
        public void run() {
            try {
                while ((msg = fromClient.readUTF()) != null) {
                    txt.append("Client " + socket.getPort() + ": " + msg + "\n");
                    sendMessageToAllClients(msg);
                }
            } catch (IOException e) {
                // Client disconnected
                txt.append("Client disconnected: " + socket.getPort() + "\n");
                clientOutputStreams.remove(socket);
                try {
                    socket.close();
                } catch (IOException ex) {
                    ex.printStackTrace();
                }
            }
        }

        public void sendMessageToAllClients(String message) {
            Iterator<Entry<Socket, DataOutputStream>> iterator = clientOutputStreams.entrySet().iterator();
            while (iterator.hasNext()) {
                Entry<Socket, DataOutputStream> entry = iterator.next();
                Socket clientSocket = entry.getKey();
                DataOutputStream clientOutputStream = entry.getValue();
                try {
                    clientOutputStream.writeUTF(message);
                    clientOutputStream.flush();
                } catch (IOException e) {
                    // Remove disconnected client
                    iterator.remove();
                    try {
                        clientSocket.close();
                    } catch (IOException ex) {
                        ex.printStackTrace();
                    }
                }
            }
        }
    }

    public static void main(String[] args) throws IOException {
        ChatServer server = new ChatServer();
    }
}
