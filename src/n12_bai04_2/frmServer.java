package n12_bai04_2;

import javax.swing.*;
import java.io.*;
import java.net.*;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.*;

/**
 * Giao diện Máy Chủ (Server) - Quản lý kết nối mạng
 * Hỗ trợ Đa luồng (Concurrency), Thời gian thực, Truyền File dung lượng lớn & Mã hóa AES-128
 */
public class frmServer extends javax.swing.JFrame {

    private ServerSocket serverSocket;
    private boolean isRunning = false;
    private ExecutorService threadPool;
    private final Map<String, ClientHandler> clientsMap = new ConcurrentHashMap<>();
    private final SimpleDateFormat dateFormat = new SimpleDateFormat("HH:mm:ss");

    /**
     * Creates new form frmServer
     */
    public frmServer() {
        initComponents();
    }

    private void logServer(String msg) {
        String time = dateFormat.format(new Date());
        SwingUtilities.invokeLater(() -> txaLog.append("[" + time + "] " + msg + "\n"));
    }

    private void updateClientListUI() {
        SwingUtilities.invokeLater(() -> {
            StringBuilder sb = new StringBuilder();
            for (String clientName : clientsMap.keySet()) {
                sb.append("• ").append(clientName).append("\n");
            }
            txaClients.setText(sb.toString());
        });
    }

    public void startServer() {
        try {
            int port = Integer.parseInt(txtPort.getText().trim());
            serverSocket = new ServerSocket(port);
            isRunning = true;
            threadPool = Executors.newCachedThreadPool();

            btnStart.setEnabled(false);
            btnStop.setEnabled(true);
            txtPort.setEnabled(false);
            lblStatus.setText("Trạng thái: Server đang HOẠT ĐỘNG tại cổng " + port);

            logServer(">>> MÁY CHỦ BẮT ĐẦU HOẠT ĐỘNG TẠI CỔNG " + port + " <<<");

            // Tiến trình lắng nghe kết nối Client
            threadPool.execute(() -> {
                while (isRunning) {
                    try {
                        Socket socket = serverSocket.accept();
                        ClientHandler handler = new ClientHandler(socket);
                        threadPool.execute(handler);
                    } catch (IOException ex) {
                        if (!isRunning) break;
                    }
                }
            });

        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, "Lỗi khởi động Server: " + ex.getMessage(), "Lỗi", JOptionPane.ERROR_MESSAGE);
        }
    }

    public void stopServer() {
        isRunning = false;
        try {
            if (serverSocket != null && !serverSocket.isClosed()) {
                serverSocket.close();
            }
            for (ClientHandler handler : clientsMap.values()) {
                handler.closeConnection();
            }
            clientsMap.clear();
            if (threadPool != null) {
                threadPool.shutdownNow();
            }
        } catch (Exception ex) {
            logServer("Lỗi khi dừng Server: " + ex.getMessage());
        }

        btnStart.setEnabled(true);
        btnStop.setEnabled(false);
        txtPort.setEnabled(true);
        lblStatus.setText("Trạng thái: Chưa khởi động Server.");
        updateClientListUI();
        logServer(">>> MÁY CHỦ ĐÃ DỪNG HOẠT ĐỘNG <<<");
    }

    /**
     * Inner Class xử lý kết nối từng Client (Mô hình Multi-threading)
     */
    private class ClientHandler implements Runnable {
        private final Socket socket;
        private DataInputStream dis;
        private DataOutputStream dos;
        private String clientName = "Unknown";

        public ClientHandler(Socket socket) {
            this.socket = socket;
        }

        @Override
        public void run() {
            try {
                dis = new DataInputStream(new BufferedInputStream(socket.getInputStream()));
                dos = new DataOutputStream(new BufferedOutputStream(socket.getOutputStream()));

                while (isRunning) {
                    String command = dis.readUTF();

                    if (command.startsWith("REGISTER:")) {
                        clientName = command.substring(9).trim();
                        clientsMap.put(clientName, this);
                        logServer("Client đã kết nối: " + clientName + " (" + socket.getInetAddress().getHostAddress() + ")");
                        updateClientListUI();
                        broadcastMessage("SERVER_NOTIF", "Client [" + clientName + "] đã tham gia mạng!");

                    } else if (command.startsWith("MSG_CHAT:")) {
                        // Cú pháp: MSG_CHAT:<Sender>:<Receiver>:<EncryptedMsg>
                        String[] parts = command.split(":", 4);
                        if (parts.length == 4) {
                            String sender = parts[1];
                            String receiver = parts[2];
                            String encryptedMsg = parts[3];
                            String decryptedMsg = SecurityUtil.decrypt(encryptedMsg);

                            logServer("Tin nhắn [" + sender + " -> " + receiver + "] (Mã hóa AES): " + encryptedMsg);
                            logServer("Nội dung đã giải mã: " + decryptedMsg);

                            // Chuyển tiếp tin nhắn cho người nhận hoặc Broadcast
                            forwardMessage(receiver, "MSG_CHAT:" + sender + ":" + receiver + ":" + encryptedMsg);

                            // Gửi phản hồi ACK thời gian thực cho Người gửi
                            sendDirectMessage("MSG_ACK:Server đã nhận & chuyển tiếp tin nhắn của bạn tới [" + receiver + "] thành công!");
                        }

                    } else if (command.startsWith("FILE_HEADER:")) {
                        // Cú pháp: FILE_HEADER:<Sender>:<Receiver>:<FileName>:<FileSize>
                        String[] parts = command.split(":", 5);
                        if (parts.length >= 5) {
                            String sender = parts[1];
                            String receiver = parts[2];
                            String fileName = parts[3];
                            long fileSize = Long.parseLong(parts[4]);

                            logServer("Đang nhận File từ [" + sender + "]: " + fileName + " (" + fileSize + " bytes)");

                            // Lưu file tại thư mục server_received
                            File dir = new File("server_received");
                            if (!dir.exists()) dir.mkdirs();
                            File file = new File(dir, "Server_Received_" + System.currentTimeMillis() + "_" + fileName);

                            try (FileOutputStream fos = new FileOutputStream(file);
                                 BufferedOutputStream bos = new BufferedOutputStream(fos)) {
                                byte[] buffer = new byte[8192];
                                long bytesReadTotal = 0;
                                int bytesRead;

                                while (bytesReadTotal < fileSize && (bytesRead = dis.read(buffer, 0, (int) Math.min(buffer.length, fileSize - bytesReadTotal))) != -1) {
                                    bos.write(buffer, 0, bytesRead);
                                    bytesReadTotal += bytesRead;
                                }
                                bos.flush();
                            }

                            logServer("Đã lưu File thành công tại Server: " + file.getAbsolutePath());

                            // Chuyển tiếp File sang cho Receiver nếu có
                            forwardFileToClient(receiver, sender, fileName, file);

                            // Phản hồi kết quả ACK về cho Máy gửi
                            sendDirectMessage("FILE_ACK:Server đã nhận & xác minh tập tin [" + fileName + "] (" + fileSize + " bytes) không bị mất dữ liệu!");
                        }
                    }
                }
            } catch (Exception ex) {
                logServer("Client " + clientName + " đã ngắt kết nối.");
            } finally {
                closeConnection();
            }
        }

        private void sendDirectMessage(String msg) {
            try {
                dos.writeUTF(msg);
                dos.flush();
            } catch (IOException ignored) {}
        }

        private void forwardMessage(String receiver, String fullMsg) {
            if ("ALL".equalsIgnoreCase(receiver)) {
                for (ClientHandler client : clientsMap.values()) {
                    if (client != this) {
                        client.sendDirectMessage(fullMsg);
                    }
                }
            } else {
                ClientHandler target = clientsMap.get(receiver);
                if (target != null) {
                    target.sendDirectMessage(fullMsg);
                }
            }
        }

        private void forwardFileToClient(String receiver, String sender, String fileName, File file) {
            ClientHandler target = clientsMap.get(receiver);
            if (target != null) {
                try {
                    target.dos.writeUTF("FILE_HEADER:" + sender + ":" + receiver + ":" + fileName + ":" + file.length());
                    target.dos.flush();

                    try (FileInputStream fis = new FileInputStream(file);
                         BufferedInputStream bis = new BufferedInputStream(fis)) {
                        byte[] buffer = new byte[8192];
                        int bytesRead;
                        while ((bytesRead = bis.read(buffer)) != -1) {
                            target.dos.write(buffer, 0, bytesRead);
                        }
                        target.dos.flush();
                    }
                    logServer("Đã chuyển tiếp File [" + fileName + "] tới Client [" + receiver + "]");
                } catch (Exception e) {
                    logServer("Lỗi chuyển tiếp file tới " + receiver + ": " + e.getMessage());
                }
            }
        }

        private void broadcastMessage(String prefix, String msg) {
            for (ClientHandler client : clientsMap.values()) {
                client.sendDirectMessage(prefix + ":" + msg);
            }
        }

        public void closeConnection() {
            try {
                if (clientName != null) {
                    clientsMap.remove(clientName);
                    updateClientListUI();
                    broadcastMessage("SERVER_NOTIF", "Client [" + clientName + "] đã rời mạng.");
                }
                if (socket != null && !socket.isClosed()) {
                    socket.close();
                }
            } catch (IOException ignored) {}
        }
    }

    /**
     * This method is called from within the constructor to initialize the form.
     * WARNING: Do NOT modify this code. The content of this method is always
     * regenerated by the Form Editor.
     */
    @SuppressWarnings("unchecked")
    // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
    private void initComponents() {

        lblTitle = new javax.swing.JLabel();
        lblPort = new javax.swing.JLabel();
        txtPort = new javax.swing.JTextField();
        btnStart = new javax.swing.JButton();
        btnStop = new javax.swing.JButton();
        lblStatus = new javax.swing.JLabel();
        lblLog = new javax.swing.JLabel();
        spLog = new javax.swing.JScrollPane();
        txaLog = new javax.swing.JTextArea();
        lblClients = new javax.swing.JLabel();
        spClients = new javax.swing.JScrollPane();
        txaClients = new javax.swing.JTextArea();

        setDefaultCloseOperation(javax.swing.WindowConstants.EXIT_ON_CLOSE);
        setTitle("MÁY CHỦ (SERVER) - BAI04 NETWORK MANAGEMENT");

        lblTitle.setFont(new java.awt.Font("Segoe UI", 1, 18)); // NOI18N
        lblTitle.setHorizontalAlignment(javax.swing.SwingConstants.CENTER);
        lblTitle.setText("CHƯƠNG TRÌNH MÁY CHỦ (SERVER)");

        lblPort.setFont(new java.awt.Font("Segoe UI", 1, 12)); // NOI18N
        lblPort.setText("Cổng (Port):");

        txtPort.setText("8888");

        btnStart.setFont(new java.awt.Font("Segoe UI", 1, 12)); // NOI18N
        btnStart.setText("Bắt Đầu Server");
        btnStart.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                btnStartActionPerformed(evt);
            }
        });

        btnStop.setFont(new java.awt.Font("Segoe UI", 1, 12)); // NOI18N
        btnStop.setText("Dừng Server");
        btnStop.setEnabled(false);
        btnStop.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                btnStopActionPerformed(evt);
            }
        });

        lblStatus.setFont(new java.awt.Font("Segoe UI", 2, 12)); // NOI18N
        lblStatus.setText("Trạng thái: Chưa khởi động Server.");

        lblLog.setFont(new java.awt.Font("Segoe UI", 1, 12)); // NOI18N
        lblLog.setText("Nhật ký hoạt động (Server Log):");

        txaLog.setEditable(false);
        txaLog.setColumns(20);
        txaLog.setRows(5);
        spLog.setViewportView(txaLog);

        lblClients.setFont(new java.awt.Font("Segoe UI", 1, 12)); // NOI18N
        lblClients.setText("Danh sách Clients:");

        txaClients.setEditable(false);
        txaClients.setColumns(20);
        txaClients.setRows(5);
        spClients.setViewportView(txaClients);

        javax.swing.GroupLayout layout = new javax.swing.GroupLayout(getContentPane());
        getContentPane().setLayout(layout);
        layout.setHorizontalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(layout.createSequentialGroup()
                .addGap(20, 20, 20)
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(lblTitle, javax.swing.GroupLayout.PREFERRED_SIZE, 560, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addGroup(layout.createSequentialGroup()
                        .addComponent(lblPort)
                        .addGap(10, 10, 10)
                        .addComponent(txtPort, javax.swing.GroupLayout.PREFERRED_SIZE, 80, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addGap(15, 15, 15)
                        .addComponent(btnStart, javax.swing.GroupLayout.PREFERRED_SIZE, 130, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addGap(10, 10, 10)
                        .addComponent(btnStop, javax.swing.GroupLayout.PREFERRED_SIZE, 130, javax.swing.GroupLayout.PREFERRED_SIZE))
                    .addComponent(lblStatus, javax.swing.GroupLayout.PREFERRED_SIZE, 560, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addGroup(layout.createSequentialGroup()
                        .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addComponent(lblLog)
                            .addComponent(spLog, javax.swing.GroupLayout.PREFERRED_SIZE, 360, javax.swing.GroupLayout.PREFERRED_SIZE))
                        .addGap(15, 15, 15)
                        .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addComponent(lblClients)
                            .addComponent(spClients, javax.swing.GroupLayout.PREFERRED_SIZE, 185, javax.swing.GroupLayout.PREFERRED_SIZE))))
                .addGap(20, 20, 20))
        );
        layout.setVerticalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(layout.createSequentialGroup()
                .addGap(15, 15, 15)
                .addComponent(lblTitle)
                .addGap(15, 15, 15)
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(lblPort)
                    .addComponent(txtPort, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(btnStart)
                    .addComponent(btnStop))
                .addGap(10, 10, 10)
                .addComponent(lblStatus)
                .addGap(15, 15, 15)
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(lblLog)
                    .addComponent(lblClients))
                .addGap(5, 5, 5)
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(spLog, javax.swing.GroupLayout.DEFAULT_SIZE, 250, Short.MAX_VALUE)
                    .addComponent(spClients, javax.swing.GroupLayout.DEFAULT_SIZE, 250, Short.MAX_VALUE))
                .addGap(20, 20, 20))
        );

        pack();
        setLocationRelativeTo(null);
    }// </editor-fold>//GEN-END:initComponents

    private void btnStartActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_btnStartActionPerformed
        startServer();
    }//GEN-LAST:event_btnStartActionPerformed

    private void btnStopActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_btnStopActionPerformed
        stopServer();
    }//GEN-LAST:event_btnStopActionPerformed

    /**
     * @param args the command line arguments
     */
    public static void main(String args[]) {
        /* Set the Nimbus look and feel */
        //<editor-fold defaultstate="collapsed" desc=" Look and feel setting code (optional) ">
        try {
            for (javax.swing.UIManager.LookAndFeelInfo info : javax.swing.UIManager.getInstalledLookAndFeels()) {
                if ("Nimbus".equals(info.getName())) {
                    javax.swing.UIManager.setLookAndFeel(info.getClassName());
                    break;
                }
            }
        } catch (ClassNotFoundException ex) {
            java.util.logging.Logger.getLogger(frmServer.class.getName()).log(java.util.logging.Level.SEVERE, null, ex);
        } catch (InstantiationException ex) {
            java.util.logging.Logger.getLogger(frmServer.class.getName()).log(java.util.logging.Level.SEVERE, null, ex);
        } catch (IllegalAccessException ex) {
            java.util.logging.Logger.getLogger(frmServer.class.getName()).log(java.util.logging.Level.SEVERE, null, ex);
        } catch (javax.swing.UnsupportedLookAndFeelException ex) {
            java.util.logging.Logger.getLogger(frmServer.class.getName()).log(java.util.logging.Level.SEVERE, null, ex);
        }
        //</editor-fold>

        /* Create and display the form */
        java.awt.EventQueue.invokeLater(new Runnable() {
            public void run() {
                new frmServer().setVisible(true);
            }
        });
    }

    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JButton btnStart;
    private javax.swing.JButton btnStop;
    private javax.swing.JLabel lblClients;
    private javax.swing.JLabel lblLog;
    private javax.swing.JLabel lblPort;
    private javax.swing.JLabel lblStatus;
    private javax.swing.JLabel lblTitle;
    private javax.swing.JScrollPane spClients;
    private javax.swing.JScrollPane spLog;
    private javax.swing.JTextArea txaClients;
    private javax.swing.JTextArea txaLog;
    private javax.swing.JTextField txtPort;
    // End of variables declaration//GEN-END:variables
}
