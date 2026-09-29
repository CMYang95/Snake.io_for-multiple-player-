import java.awt.BorderLayout;
import java.awt.Color;

import javax.swing.BorderFactory;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import javax.swing.border.EmptyBorder;

/**
 * SnakeChat 客戶端主程式（方案 1）
 *
 * 同一個視窗：
 *   左側 = 本機貪食蛇（GamePanel）
 *   右側 = 多人聊天（ChatPanel）
 *
 * 兩邊只在「Game Over 分享分數」時輕度耦合，網路層仍只傳文字。
 */
public class SnakeChatApp extends JFrame {

    public SnakeChatApp() {
        setTitle("SnakeChat — Play & Chat");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLayout(new BorderLayout(0, 0));

        GamePanel gamePanel = new GamePanel();
        ChatPanel chatPanel = new ChatPanel();

        // 遊戲結束 → 若已連線，自動把分數送到聊天室
        gamePanel.setOnGameOver(score ->
                chatPanel.sendSystemNote("scored " + score + " points!"));

        JPanel left = new JPanel(new BorderLayout());
        left.setBackground(new Color(18, 22, 28));
        left.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 4));
        JLabel gameTitle = new JLabel("Snake");
        gameTitle.setForeground(Color.WHITE);
        gameTitle.setBorder(new EmptyBorder(0, 4, 6, 0));
        left.add(gameTitle, BorderLayout.NORTH);
        left.add(gamePanel, BorderLayout.CENTER);

        JPanel right = new JPanel(new BorderLayout());
        right.setBorder(BorderFactory.createMatteBorder(0, 1, 0, 0, new Color(60, 70, 80)));
        JLabel chatTitle = new JLabel("  Chat Room");
        chatTitle.setBorder(new EmptyBorder(8, 4, 0, 0));
        right.add(chatTitle, BorderLayout.NORTH);
        right.add(chatPanel, BorderLayout.CENTER);

        add(left, BorderLayout.CENTER);
        add(right, BorderLayout.EAST);

        pack();
        setLocationRelativeTo(null);
        setResizable(false);
        setVisible(true);

        // 啟動後讓遊戲區先拿到鍵盤焦點
        SwingUtilities.invokeLater(gamePanel::requestFocusInWindow);
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(SnakeChatApp::new);
    }
}
