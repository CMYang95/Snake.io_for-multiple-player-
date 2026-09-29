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
 * 本機貪食蛇面板（方案 1：遊戲不走網路）
 *
 * 透過 onGameOver 回呼，讓主程式可選擇把分數送到聊天室。
 */
@SuppressWarnings("serial")
public class GamePanel extends JPanel implements ActionListener {

    static final int SCREEN_WIDTH = 520;
    static final int SCREEN_HEIGHT = 520;
    static final int UNIT_SIZE = 20;
    static final int GAME_UNITS = (SCREEN_WIDTH * SCREEN_HEIGHT) / (UNIT_SIZE * UNIT_SIZE);
    static final int DELAY = 70;

    private final int[] x = new int[GAME_UNITS];
    private final int[] y = new int[GAME_UNITS];
    private int bodyParts = 3;
    private int applesEaten;
    private int appleX;
    private int appleY;

    private char direction = 'R';
    private boolean running = false;
    private boolean waitingToStart = true;
    private boolean paused = false;

    private Timer timer;
    private final Random random = new Random();
    private IntConsumer onGameOver;

    public GamePanel() {
        setPreferredSize(new Dimension(SCREEN_WIDTH, SCREEN_HEIGHT));
        setBackground(new Color(18, 22, 28));
        setFocusable(true);
        addKeyListener(new MyKeyAdapter());
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

    /** 註冊 Game Over 回呼（參數為最終分數） */
    public void setOnGameOver(IntConsumer onGameOver) {
        this.onGameOver = onGameOver;
    }

    private void startFreshGame() {
        bodyParts = 3;
        applesEaten = 0;
        direction = 'R';
        paused = false;
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
        timer = new Timer(DELAY, this);
        timer.start();
        repaint();
    }

    private void newApple() {
        appleX = random.nextInt(SCREEN_WIDTH / UNIT_SIZE) * UNIT_SIZE;
        appleY = random.nextInt(SCREEN_HEIGHT / UNIT_SIZE) * UNIT_SIZE;
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        if (running && !paused) {
            move();
            checkApple();
            checkCollisions();
        }
        repaint();
    }

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

    private void checkApple() {
        if (x[0] == appleX && y[0] == appleY) {
            bodyParts++;
            applesEaten++;
            newApple();
        }
    }

    private void checkCollisions() {
        for (int i = bodyParts; i > 0; i--) {
            if (x[0] == x[i] && y[0] == y[i]) {
                running = false;
            }
        }
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

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        draw(g);
    }

    private void draw(Graphics g) {
        if (waitingToStart) {
            drawCentered(g, Color.ORANGE, 28, "Click to start Snake", SCREEN_HEIGHT / 2 - 20);
            drawCentered(g, Color.LIGHT_GRAY, 16, "Arrows = Move | Space = Pause", SCREEN_HEIGHT / 2 + 20);
            return;
        }

        if (running) {
            g.setColor(new Color(220, 60, 60));
            g.fillOval(appleX, appleY, UNIT_SIZE, UNIT_SIZE);

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

    private void drawCentered(Graphics g, Color color, int size, String text, int y) {
        g.setColor(color);
        g.setFont(new Font("SansSerif", Font.BOLD, size));
        FontMetrics metrics = g.getFontMetrics();
        int x = (SCREEN_WIDTH - metrics.stringWidth(text)) / 2;
        g.drawString(text, x, y);
    }

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
