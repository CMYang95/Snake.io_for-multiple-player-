import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.Random;
import java.util.function.IntConsumer;

import javax.swing.JPanel;
import javax.swing.Timer;

/**
 * ============================================================
 * GamePanel —— 本機貪食蛇（方案 1：遊戲完全不走網路）
 * ============================================================
 *
 * 【重要觀念】
 *  蛇的座標、蘋果、分數都只存在「這個 JVM 的記憶體」裡。
 *  另一個玩家的 GamePanel 是另一份狀態——彼此不同步。
 *  網路只在 Game Over 時，透過 onGameOver 回呼「選擇性」把分數丟進聊天。
 *
 * 【主迴圈怎麼轉？】
 *  javax.swing.Timer 每隔 DELAY 毫秒觸發 actionPerformed：
 *    move → checkApple → checkCollisions → repaint
 *
 * 【操作】
 *  滑鼠點擊：開始 / 再玩一局
 *  方向鍵：改變蛇頭方向（禁止 180 度掉頭）
 *  空白鍵：暫停 / 繼續
 */
@SuppressWarnings("serial")
public class GamePanel extends JPanel implements ActionListener {

    // ---------- 畫布與格子設定 ----------
    static final int SCREEN_WIDTH = 520;   // 面板寬（像素）
    static final int SCREEN_HEIGHT = 520;  // 面板高（像素）
    static final int UNIT_SIZE = 20;       // 一格多大；蛇身與蘋果都對齊格子
    /** 畫面上最多能塞多少格（用來決定座標陣列長度） */
    static final int GAME_UNITS = (SCREEN_WIDTH * SCREEN_HEIGHT) / (UNIT_SIZE * UNIT_SIZE);
    static final int DELAY = 70;           // Timer 間隔；數字越小蛇越快

    // ---------- 蛇的身體：平行陣列 ----------
    // x[0], y[0] = 蛇頭；x[1], y[1] = 第一節身體……
    private final int[] x = new int[GAME_UNITS];
    private final int[] y = new int[GAME_UNITS];
    private int bodyParts = 3;             // 目前身體節數
    private int applesEaten;               // 分數（吃到幾顆蘋果）
    private int appleX;                    // 蘋果座標
    private int appleY;

    // ---------- 狀態旗標 ----------
    private char direction = 'R';          // U/D/L/R
    private boolean running = false;       // 是否正在進行一局
    private boolean waitingToStart = true; // 是否還在開始畫面
    private boolean paused = false;        // 空白鍵暫停

    private Timer timer;                   // 驅動遊戲邏輯的計時器
    private final Random random = new Random();

    /**
     * Game Over 時呼叫的回呼（參數 = 最終分數）。
     * 由 SnakeChatApp 注入：可選擇把分數送到 ChatPanel。
     * 遊戲本身不依賴聊天，沒設回呼也能正常玩。
     */
    private IntConsumer onGameOver;

    public GamePanel() {
        setPreferredSize(new Dimension(SCREEN_WIDTH, SCREEN_HEIGHT));
        setBackground(new Color(18, 22, 28));
        setFocusable(true); // 才能收到鍵盤事件
        addKeyListener(new MyKeyAdapter());

        // 點擊面板：搶焦點 +（若沒在玩）開新一局
        addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                requestFocusInWindow();
                if (!running) {
                    startFreshGame();
                }
            }
        });
    }

    /** 讓外部（SnakeChatApp）註冊「遊戲結束要做什麼」 */
    public void setOnGameOver(IntConsumer onGameOver) {
        this.onGameOver = onGameOver;
    }

    /**
     * 重置狀態並啟動 Timer。
     * 每次點擊「開始 / 再玩」都會走這裡，避免上一局殘留座標。
     */
    private void startFreshGame() {
        bodyParts = 3;
        applesEaten = 0;
        direction = 'R';
        paused = false;

        // 初始蛇身排成水平一列（頭在右側）
        for (int i = 0; i < bodyParts; i++) {
            x[i] = UNIT_SIZE * (bodyParts - i);
            y[i] = UNIT_SIZE * 5;
        }

        newApple();
        running = true;
        waitingToStart = false;

        if (timer != null) {
            timer.stop();
        }
        // this 實作了 ActionListener → 每 DELAY ms 呼叫 actionPerformed
        timer = new Timer(DELAY, this);
        timer.start();
        repaint();
    }

    /** 在格子對齊的隨機位置生出一顆蘋果 */
    private void newApple() {
        appleX = random.nextInt(SCREEN_WIDTH / UNIT_SIZE) * UNIT_SIZE;
        appleY = random.nextInt(SCREEN_HEIGHT / UNIT_SIZE) * UNIT_SIZE;
    }

    /**
     * Timer 回呼：一「幀」的邏輯更新 + 重畫。
     * 暫停時只 repaint（仍可顯示 Paused 文字），不移動。
     */
    @Override
    public void actionPerformed(ActionEvent e) {
        if (running && !paused) {
            move();
            checkApple();
            checkCollisions();
        }
        repaint();
    }

    /**
     * 移動規則：
     *  1. 從尾巴往頭，每一節抄前一節的座標（身體跟著走）
     *  2. 再依 direction 把「頭」往前移一格
     */
    private void move() {
        for (int i = bodyParts; i > 0; i--) {
            x[i] = x[i - 1];
            y[i] = y[i - 1];
        }
        switch (direction) {
            case 'U':
                y[0] -= UNIT_SIZE;
                break;
            case 'D':
                y[0] += UNIT_SIZE;
                break;
            case 'L':
                x[0] -= UNIT_SIZE;
                break;
            case 'R':
                x[0] += UNIT_SIZE;
                break;
            default:
                break;
        }
    }

    /** 頭座標 == 蘋果座標 → 變長、加分、重生蘋果 */
    private void checkApple() {
        if (x[0] == appleX && y[0] == appleY) {
            bodyParts++;
            applesEaten++;
            newApple();
        }
    }

    /**
     * 碰撞：撞到自己的身體，或頭超出邊界 → Game Over。
     * Over 時停止 Timer，並觸發 onGameOver（若有註冊）。
     */
    private void checkCollisions() {
        // 頭 vs 身體
        for (int i = bodyParts; i > 0; i--) {
            if (x[0] == x[i] && y[0] == y[i]) {
                running = false;
            }
        }
        // 頭 vs 邊界
        if (x[0] < 0 || x[0] >= SCREEN_WIDTH || y[0] < 0 || y[0] >= SCREEN_HEIGHT) {
            running = false;
        }

        if (!running) {
            if (timer != null) {
                timer.stop();
            }
            if (onGameOver != null) {
                onGameOver.accept(applesEaten);
            }
        }
    }

    /** Swing 繪圖入口：先清背景，再畫目前畫面狀態 */
    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        draw(g);
    }

    /**
     * 依狀態畫不同畫面：
     *  waitingToStart → 提示點擊開始
     *  running        → 蘋果 + 蛇 + 分數（＋暫停字）
     *  否則           → Game Over + 分數
     */
    private void draw(Graphics g) {
        if (waitingToStart) {
            drawCentered(g, Color.ORANGE, 28, "Click to start Snake", SCREEN_HEIGHT / 2 - 20);
            drawCentered(g, Color.LIGHT_GRAY, 16, "Arrows = Move | Space = Pause", SCREEN_HEIGHT / 2 + 20);
            return;
        }

        if (running) {
            // 蘋果
            g.setColor(new Color(220, 60, 60));
            g.fillOval(appleX, appleY, UNIT_SIZE, UNIT_SIZE);

            // 蛇：頭藍色、身體綠色
            for (int i = 0; i < bodyParts; i++) {
                if (i == 0) {
                    g.setColor(new Color(70, 140, 255));
                } else {
                    g.setColor(new Color(60, 180, 90));
                }
                g.fillRect(x[i], y[i], UNIT_SIZE, UNIT_SIZE);
            }

            drawCentered(g, Color.CYAN, 22, "Score: " + applesEaten, 28);
            if (paused) {
                drawCentered(g, Color.WHITE, 28, "Paused", SCREEN_HEIGHT / 2);
            }
        } else {
            drawCentered(g, new Color(230, 70, 70), 42, "Game Over", SCREEN_HEIGHT / 2 - 20);
            drawCentered(g, Color.WHITE, 24, "Score: " + applesEaten, SCREEN_HEIGHT / 2 + 30);
            drawCentered(g, Color.LIGHT_GRAY, 16, "Click to play again", SCREEN_HEIGHT / 2 + 65);
        }
    }

    /** 水平置中畫字的小工具，避免每處重複算寬度 */
    private void drawCentered(Graphics g, Color color, int size, String text, int y) {
        g.setColor(color);
        g.setFont(new Font("SansSerif", Font.BOLD, size));
        FontMetrics metrics = g.getFontMetrics();
        int x = (SCREEN_WIDTH - metrics.stringWidth(text)) / 2;
        g.drawString(text, x, y);
    }

    /**
     * 鍵盤控制。
     * 禁止「當下往右卻立刻改往左」這類 180° 掉頭，否則會立刻撞到自己。
     */
    private class MyKeyAdapter extends KeyAdapter {
        @Override
        public void keyPressed(KeyEvent e) {
            switch (e.getKeyCode()) {
                case KeyEvent.VK_LEFT:
                    if (direction != 'R') {
                        direction = 'L';
                    }
                    break;
                case KeyEvent.VK_RIGHT:
                    if (direction != 'L') {
                        direction = 'R';
                    }
                    break;
                case KeyEvent.VK_UP:
                    if (direction != 'D') {
                        direction = 'U';
                    }
                    break;
                case KeyEvent.VK_DOWN:
                    if (direction != 'U') {
                        direction = 'D';
                    }
                    break;
                case KeyEvent.VK_SPACE:
                    if (!running) {
                        break;
                    }
                    paused = !paused;
                    if (paused) {
                        timer.stop();
                    } else {
                        timer.start();
                    }
                    repaint();
                    break;
                default:
                    break;
            }
        }
    }
}
