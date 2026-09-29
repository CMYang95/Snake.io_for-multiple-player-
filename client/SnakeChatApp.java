import java.awt.BorderLayout;
import java.awt.Color;

import javax.swing.BorderFactory;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import javax.swing.border.EmptyBorder;

/**
 * ============================================================
 * SnakeChatApp —— 客戶端主程式（方案 1：同一視窗邊玩邊聊）
 * ============================================================
 *
 * 【畫面配置】
 *  ┌─────────────────────┬──────────────────┐
 *  │  左側 GamePanel      │  右側 ChatPanel   │
 *  │  本機貪食蛇          │  TCP 多人聊天     │
 *  └─────────────────────┴──────────────────┘
 *
 * 【兩邊怎麼「接」在一起？】
 *  只有一條膠水：Game Over 時呼叫 chatPanel.sendSystemNote(...)
 *  遊戲過程的每一幀、每一次按鍵，都「不會」上網路。
 *
 * 【啟動順序】
 *  1. 先執行 server/ChatServer
 *  2. 再執行本類別（可開多個視窗模擬多人）
 *  3. 右側按 Connect → 左側點擊開始玩
 */
public class SnakeChatApp extends JFrame {

    public SnakeChatApp() {
        setTitle("SnakeChat — Play & Chat");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLayout(new BorderLayout(0, 0));

        // 建立兩個子面板：遊戲本機、聊天走網路
        GamePanel gamePanel = new GamePanel();
        ChatPanel chatPanel = new ChatPanel();

        /*
         * 輕度耦合（方案 1 的整合點）：
         * 遊戲結束 → 把分數組成文字 → 若已連線就送進聊天室。
         * 尚未 Connect 時 sendSystemNote 會安靜略過，不影響單機遊玩。
         */
        gamePanel.setOnGameOver(score ->
                chatPanel.sendSystemNote("scored " + score + " points!"));

        // ===== 左側：遊戲區 =====
        JPanel left = new JPanel(new BorderLayout());
        left.setBackground(new Color(18, 22, 28));
        left.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 4));
        JLabel gameTitle = new JLabel("Snake");
        gameTitle.setForeground(Color.WHITE);
        gameTitle.setBorder(new EmptyBorder(0, 4, 6, 0));
        left.add(gameTitle, BorderLayout.NORTH);
        left.add(gamePanel, BorderLayout.CENTER);

        // ===== 右側：聊天區 =====
        JPanel right = new JPanel(new BorderLayout());
        right.setBorder(BorderFactory.createMatteBorder(0, 1, 0, 0, new Color(60, 70, 80)));
        JLabel chatTitle = new JLabel("  Chat Room");
        chatTitle.setBorder(new EmptyBorder(8, 4, 0, 0));
        right.add(chatTitle, BorderLayout.NORTH);
        right.add(chatPanel, BorderLayout.CENTER);

        // CENTER 吃掉剩餘空間給遊戲；EAST 依 ChatPanel 偏好寬度擺聊天
        add(left, BorderLayout.CENTER);
        add(right, BorderLayout.EAST);

        pack(); // 依子元件 preferredSize 算出視窗大小
        setLocationRelativeTo(null); // 螢幕置中
        setResizable(false);
        setVisible(true);

        // 視窗顯示後，把鍵盤焦點給遊戲區（否則方向鍵可能沒反應）
        SwingUtilities.invokeLater(gamePanel::requestFocusInWindow);
    }

    /**
     * 進入點：務必在 EDT 上建立 Swing 視窗。
     * invokeLater 可避免偶發的執行緒安全問題。
     */
    public static void main(String[] args) {
        SwingUtilities.invokeLater(SnakeChatApp::new);
    }
}
