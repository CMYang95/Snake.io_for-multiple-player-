# SnakeChat 學習日誌：把貪食蛇與多人聊天合在同一視窗


---

## 0. 一句話先講清楚

| 元件 | 跑在哪 | 做什麼 | 走不走網路 |
|------|--------|--------|------------|
| `ChatServer` | 一台機器上的伺服器行程 | 接受連線、廣播文字 | 是（TCP） |
| `GamePanel` | 每個玩家自己的客戶端 | 畫蛇、吃蘋果、計分 | **否**（本機 Timer） |
| `ChatPanel` | 客戶端右側 | 連線、收發聊天訊息 | 是（TCP） |
| `SnakeChatApp` | 客戶端主視窗 | 把遊戲與聊天排在一起 | 組裝用 |

**核心設計決策：**  
方案 1 **不**把蛇的座標同步到伺服器。多人只共享「文字訊息」；每人玩自己的蛇。這樣協定簡單、除錯容易，是學習網路層最好的第一站。

---

## 1. 專案從哪裡來、合了什麼

原本有兩個獨立小專案：

```
多人聊天室/          SNAKE/
  ChatServer.java      Snake.java
  javaclinet.java      GameFrame.java
                       GamePanel.java
```

- 聊天：Socket + `DataInputStream` / `DataOutputStream` + UTF 字串廣播  
- 蛇：Swing `Timer` 驅動的單機遊戲  

整合後目錄：

```
Snake_Chat/
├── README.md                 ← 你正在讀的這份
├── server/
│   └── ChatServer.java       ← 聊天伺服器（先啟動）
├── client/
│   ├── SnakeChatApp.java     ← 客戶端入口（遊戲 + 聊天）
│   ├── ChatPanel.java        ← 聊天 UI + 網路收發
│   └── GamePanel.java        ← 本機貪食蛇
├── 多人聊天室/               ← 原始參考（可保留）
└── SNAKE/                    ← 原始參考（可保留）
```

---

## 2. 執行方式（先會跑，再談原理）

需要已安裝 JDK（`javac` / `java`）。

### 步驟 A：開伺服器（開一個終端）

```bash
cd server
javac ChatServer.java
java ChatServer
```

看到視窗寫著 `Server started on port 54321` 就代表在聽連線。

### 步驟 B：開客戶端（再開一個或多個終端）

```bash
cd client
javac *.java
java SnakeChatApp
```

1. 右側填 Host（本機用 `localhost`）、Port `54321`、Name  
2. 按 **Connect**  
3. 左側點一下遊戲區開始玩；方向鍵移動，空白鍵暫停  
4. Game Over 時，若已連線，會自動送出類似：  
   `[Snake] Player scored 7 points!`

多開幾個 `SnakeChatApp`，就能看到「各自玩蛇、同一聊天室」。

---

## 3. 整體架構圖（方案 1）

```
                 ┌──────────────────────────────┐
                 │         ChatServer           │
                 │  ServerSocket(54321)         │
                 │  Map<Socket, DataOutputStream>│
                 │  每連線一個 ClientHandler    │
                 └──────────────▲───────────────┘
                                │
                     TCP / writeUTF / readUTF
                     （純文字協定）
                                │
        ┌───────────────────────┴───────────────────────┐
        │                                               │
┌───────┴────────────────┐                 ┌────────────┴───────────┐
│   SnakeChatApp #1      │                 │   SnakeChatApp #2      │
│ ┌──────────┬─────────┐ │                 │ ┌──────────┬─────────┐ │
│ │GamePanel │ChatPanel│ │                 │ │GamePanel │ChatPanel│ │
│ │本機Timer │Socket   │ │                 │ │本機Timer │Socket   │ │
│ │不互相同步│收發文字 │ │                 │ │不互相同步│收發文字 │ │
│ └──────────┴─────────┘ │                 │ └──────────┴─────────┘ │
└────────────────────────┘                 └────────────────────────┘
```

重點：

- **水平方向（左↔右）**：同一個客戶端裡，UI 組裝關係。  
- **垂直方向（上↔下）**：Client ↔ Server 的網路關係。  
- GamePanel **沒有箭頭連到 Server**——這就是方案 1 與「多人對戰蛇」的分界線。

---

## 4. UI 怎麼合在一起（方案 1 本體）

`SnakeChatApp` 用 `BorderLayout`：

```
┌─────────────────────────────────────────────┐
│  SnakeChat — Play & Chat                    │
├──────────────────────────┬──────────────────┤
│  Snake（左側）            │  Chat Room       │
│  ┌────────────────────┐  │  Host / Port     │
│  │                    │  │  Name / Connect  │
│  │    GamePanel       │  │  ┌────────────┐  │
│  │    520 × 520       │  │  │ 訊息紀錄   │  │
│  │                    │  │  └────────────┘  │
│  └────────────────────┘  │  [輸入] [Send]   │
└──────────────────────────┴──────────────────┘
```

耦合點只有一個回呼：

```java
gamePanel.setOnGameOver(score ->
    chatPanel.sendSystemNote("scored " + score + " points!"));
```

也就是：**遊戲結束 → 若已連線 → 送一條聊天文字**。  
遊戲過程中的每一幀、每一個方向鍵，都 **不** 上網路。

---

## 5. 網路層教學（從 Socket 到廣播）

### 5.1 我們用什麼協定？

| 項目 | 選擇 | 為什麼適合教學 |
|------|------|----------------|
| 傳輸 | TCP (`ServerSocket` / `Socket`) | 保證順序、不會默默丟字元 |
| 訊息格式 | `DataOutputStream.writeUTF` / `readUTF` | 自帶長度前綴，不用自己切行 |
| 語意 | 任意 UTF-8 字串 | 聊天足夠；之後要升級再改 JSON |

一則訊息在線上的樣子（概念上）：

```
[2 bytes 長度][UTF 字節...]
例如內容："Alice: hello"
```

你 **不必** 手寫長度；`writeUTF` / `readUTF` 幫你處理了。這是學習階段很好的起點。

### 5.2 伺服器生命週期

```
main
 └─ new ChatServer()
     ├─ 顯示 log 視窗
     ├─ new ServerSocket(54321)
     └─ while (true)
           socket = accept()          ← 有人連進來才往下走（阻塞）
           把 socket 的 OutputStream 放進 Map
           pool.execute(ClientHandler)← 每個 client 一條讀取執行緒
```

`ClientHandler` 做的事很單純：

```
while (true) {
    msg = in.readUTF();   // 阻塞等這個人說話
    broadcast(msg);       // 轉發給所有人（含自己）
}
```

`broadcast` 走遍 `Map<Socket, DataOutputStream>`，對每個 client `writeUTF`。  
某個人斷線就從 Map 拿掉，避免「死人連線」一直噴錯。

### 5.3 客戶端網路生命週期（ChatPanel）

```
按 Connect
 ├─ new Socket(host, port)
 ├─ 取得 fromServer / toServer
 ├─ 開 daemon 執行緒 readLoop:
 │     while (connected) append(readUTF())
 └─ UI 進入 online

按 Send（或 Enter）
 └─ toServer.writeUTF(name + ": " + text)

斷線 / 伺服器關掉
 └─ readUTF 丟 IOException → disconnect() → UI 回到 offline
```

### 5.4 為什麼讀訊息一定要「背景執行緒」？

Swing 的畫面更新與按鈕事件跑在 **Event Dispatch Thread (EDT)**。  
如果在 EDT 上呼叫 `readUTF()`：

- 沒訊息時整個視窗會卡住（連按鈕都按不了）  
- 遊戲的 `Timer` 也可能感覺卡頓  

所以正確拆法是：

| 執行緒 | 負責 |
|--------|------|
| EDT | 按鈕、繪圖、`append` 到文字區（用 `SwingUtilities.invokeLater`） |
| chat-reader | 阻塞 `readUTF` |
| Swing Timer | 蛇的移動與 `repaint`（本機，與網路無關） |

伺服器端同理：`accept` 與每個 `readUTF` 都不能堵在「只剩一個執行緒」上，所以用 `ExecutorService`（執行緒池）為每個 client 開 handler。

### 5.5 執行緒安全的兩個小細節

1. **更新 Swing 元件**必須回到 EDT：  
   `SwingUtilities.invokeLater(() -> messageArea.append(...))`

2. **多人同時廣播時改 Map** 要用同步：  
   伺服器對 `clients` 的增刪與 iterate 放在 `synchronized (clients)` 裡，避免 ConcurrentModification。

---

## 6. 遊戲層（為什麼方案 1 不把蛇上網）

`GamePanel` 使用 `javax.swing.Timer`：

```
每 DELAY 毫秒
  → move()
  → checkApple()
  → checkCollisions()
  → repaint()
```

狀態（蛇身座標、蘋果位置、分數）全部活在 **這個 JVM 的記憶體**。  
另一個玩家的 `GamePanel` 是另一份記憶體——彼此看不見。

若要做到「同一條蛇、同一個蘋果」，你需要：

1. 定義二進位或 JSON 協定（方向輸入、狀態快照）  
2. 伺服器變成權威狀態機（權威伺服器）  
3. Client 只送輸入、收狀態再繪圖  
4. 處理延遲、預測、斷線重連……

那是方案 3。方案 1 刻意不做，好讓你先把 **聊天網路層** 練熟。

---

## 7. 資料流總表（對照著 code 看）

| 事件 | 路徑 | 經過的類別 |
|------|------|------------|
| 玩家連線 | Client → Server | `ChatPanel.connect` → `ServerSocket.accept` |
| 玩家說話 | Client → Server → 所有 Client | `writeUTF` → `broadcast` → 各端 `readUTF` |
| 蛇移動 | 只在本機 | `Timer` → `GamePanel.move` |
| Game Over 分享分數 | 本機回呼 → 若已連線則走聊天 | `onGameOver` → `sendSystemNote` → `writeUTF` |

---

## 8. 與原始兩個資料夾的對照

| 原始 | 整合後 | 變化 |
|------|--------|------|
| `ChatServer.java` | `server/ChatServer.java` | 同步保護 Map、log 用 `invokeLater`、視窗標題 |
| `javaclinet.java` | `client/ChatPanel.java` | 改成可嵌入的 `JPanel`；可設 host/port/暱稱；可 Disconnect |
| `GamePanel.java` | `client/GamePanel.java` | 縮小畫布以配合並排；可重開一局；Game Over 回呼 |
| `GameFrame` + `Snake` | `SnakeChatApp` | 一個視窗同時裝遊戲與聊天 |

原始資料夾可當「改之前」對照；日常請跑 `server/` + `client/`。

---

## 9. 常見問題排解

| 現象 | 可能原因 | 怎麼查 |
|------|----------|--------|
| Connect failed | 伺服器沒開，或 port 錯 | 先確認 Server 視窗在、埠口是 54321 |
| 能連但不能聊天 | 沒按 Send / 輸入空白 | 看 Server log 有沒有收到字串 |
| 按方向鍵沒反應 | 焦點在聊天輸入框 | 滑鼠點一下左側遊戲區 |
| 分數沒出現在聊天 | 還沒 Connect 就 Game Over | 先連線再玩；或再死一次觸發分享 |
| `Address already in use` | 舊的 Server 還佔著埠口 | 關掉舊 Server 視窗再開 |

---

## 10. 學習路線圖（建議你自己動手改）

照這個順序改，每次只動一個概念：

1. **改歡迎訊息**：連上線後 Server 主動 `writeUTF("Welcome!")`  
2. **私訊指令**：例如 `/w Bob hi` 只送給 Bob（要先做「暱稱 ↔ Socket」對照表）  
3. **聊天紀錄檔**：Server 把每則訊息 append 到 `chat.log`  
4. **方案 2 加強**：不只 Game Over，連「吃到蘋果」也可選擇性廣播  
5. **方案 3 挑戰**：設計 `DIR:L` / `STATE:...` 協定，做雙人同場蛇  

---

## 11. 設計原則回顧（面試也可以這樣講）

1. **關注點分離**：網路（Chat）與模擬（Game）分開；用回呼輕度膠水，而不是互相 import 一堆內部狀態。  
2. **先做能跑的薄協定**：UTF 字串廣播 → 再考慮 JSON / protobuf。  
3. **阻塞 I/O 離開 UI 執行緒**：`readUTF` / `accept` 永遠不要堵在 EDT。  
4. **伺服器持有連線表**：誰連著、怎麼寫回去，是 Server 的職責；Client 只負責自己的一條 Socket。  
5. **方案分級演進**：並排 UI（方案 1）→ 分數進聊天（本專案已做輕量版）→ 狀態同步多人遊戲（未來）。

---

## 12. 授權與用途

本專案由課堂／練習用的聊天室與貪食蛇整合而來，適合作為：

- Socket 程式設計第一次作業的延伸  
- Swing 多面板組裝示範  
- 「為什麼遊戲同步很難」的對照起點  

祝學習順利。先讓 Server 亮起來，再讓兩台 Client 互丟一句話——網路層你就摸到門了。
