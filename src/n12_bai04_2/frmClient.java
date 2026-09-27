package n12_bai04_2;

import javax.swing.*;
import java.io.*;
import java.net.*;
import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * Giao diện Máy Khách (Client) - Quản lý kết nối mạng
 * Hỗ trợ Gửi tin nhắn mã hóa AES-128, Gửi File dung lượng lớn & Nhận kết quả phản hồi thời gian thực
 */
public class frmClient extends javax.swing.JFrame {

    private Socket socket;
    private DataInputStream dis;
    private DataOutputStream dos;
    private boolean isConnected = false;
    private Thread listenThread;
    private final SimpleDateFormat dateFormat = new SimpleDateFormat("HH:mm:ss");

    /**
     * Creates new form frmClient
     */
    public frmClient() {
        initComponents();
        initCustomEvents();
    }

    private void initCustomEvents() {
        btnConnect.addActionListener(e -> connectToServer());
        btnDisconnect.addActionListener(e -> disconnectFromServer());
        btnSendMsg.addActionListener(e -> sendMessage());
        btnBrowse.addActionListener(e -> browseFile());
        btnSendFile.addActionListener(e -> sendFile());
    }

    private void logClient(String msg) {
        String time = dateFormat.format(new Date());
        SwingUtilities.invokeLater(() -> txaLog.append("[" + time + "] " + msg + "\n"));
    }

    private void connectToServer() {
        try {
            String ip = txtIP.getText().trim();
            int port = Integer.parseInt(txtPort.getText().trim());
            String clientName = txtClientName.getText().trim();

            if (clientName.isEmpty()) {
                JOptionPane.showMessageDialog(this, "Vui lòng nhập Tên Client!", "Cảnh báo", JOptionPane.WARNING_MESSAGE);
                return;
            }

            socket = new Socket(ip, port);
            dis = new DataInputStream(new BufferedInputStream(socket.getInputStream()));
            dos = new DataOutputStream(new BufferedOutputStream(socket.getOutputStream()));
            isConnected = true;

            // Đăng ký tên Client với Server
            dos.writeUTF("REGISTER:" + clientName);
            dos.flush();

            btnConnect.setEnabled(false);
            btnDisconnect.setEnabled(true);
            txtIP.setEnabled(false);
            txtPort.setEnabled(false);
            txtClientName.setEnabled(false);
            lblStatus.setText("Trạng thái: ĐÃ KẾT NỐI thành công đến Server (" + ip + ":" + port + ")");

            logClient(">>> ĐÃ KẾT NỐI ĐẾN MÁY CHỦ (" + ip + ":" + port + ") VỚI TÊN [" + clientName + "] <<<");

            // Khởi chạy tiến trình lắng nghe dữ liệu trả về từ Server thời gian thực
            listenThread = new Thread(this::listenForServerData);
            listenThread.start();

        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, "Không thể kết nối đến Server: " + ex.getMessage(), "Lỗi Kết Nối", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void listenForServerData() {
        try {
            while (isConnected) {
                String response = dis.readUTF();

                if (response.startsWith("MSG_CHAT:")) {
                    // MSG_CHAT:<Sender>:<Receiver>:<EncryptedMsg>
                    String[] parts = response.split(":", 4);
                    if (parts.length == 4) {
                        String sender = parts[1];
                        String encryptedMsg = parts[3];
                        String decryptedMsg = SecurityUtil.decrypt(encryptedMsg);
                        logClient("📩 [Tin nhắn từ " + sender + "]: " + decryptedMsg + " (Mã hóa: " + encryptedMsg + ")");
                    }

                } else if (response.startsWith("MSG_ACK:") || response.startsWith("FILE_ACK:")) {
                    // Nhận kết quả phản hồi xác nhận từ Server/Client khác
                    logClient("✅ [KẾT QUẢ PHẢN HỒI]: " + response.substring(response.indexOf(":") + 1));

                } else if (response.startsWith("SERVER_NOTIF:")) {
                    logClient("ℹ️ [Hệ Thống]: " + response.substring(13));

                } else if (response.startsWith("FILE_HEADER:")) {
                    // FILE_HEADER:<Sender>:<Receiver>:<FileName>:<FileSize>
                    String[] parts = response.split(":", 5);
                    if (parts.length >= 5) {
                        String sender = parts[1];
                        String fileName = parts[3];
                        long fileSize = Long.parseLong(parts[4]);

                        logClient("📥 Đang nhận tập tin từ [" + sender + "]: " + fileName + " (" + fileSize + " bytes)...");

                        File dir = new File("client_downloads");
                        if (!dir.exists()) dir.mkdirs();
                        File file = new File(dir, "Received_" + System.currentTimeMillis() + "_" + fileName);

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

                        logClient("🎉 ĐÃ NHẬN FILE THÀNH CÔNG! Đã lưu tại: " + file.getAbsolutePath());
                    }
                }
            }
        } catch (Exception ex) {
            if (isConnected) {
                logClient("Đã mất kết nối đến Server.");
                disconnectFromServer();
            }
        }
    }

    private void sendMessage() {
        if (!isConnected) {
            JOptionPane.showMessageDialog(this, "Bạn chưa kết nối đến Server!", "Thông báo", JOptionPane.WARNING_MESSAGE);
            return;
        }

        String msg = txtMessage.getText().trim();
        if (msg.isEmpty()) return;

        try {
            String clientName = txtClientName.getText().trim();
            // Mã hóa tin nhắn trước khi gửi bằng AES-128
            String encryptedMsg = SecurityUtil.encrypt(msg);

            // Gửi tin nhắn cho tất cả hoặc cá nhân (Default: ALL)
            dos.writeUTF("MSG_CHAT:" + clientName + ":ALL:" + encryptedMsg);
            dos.flush();

            logClient("📤 [Đã gửi tin nhắn (Mã hóa AES)]: " + msg);
            txtMessage.setText("");

        } catch (Exception ex) {
            logClient("Lỗi gửi tin nhắn: " + ex.getMessage());
        }
    }

    private void browseFile() {
        JFileChooser fileChooser = new JFileChooser();
        int result = fileChooser.showOpenDialog(this);
        if (result == JFileChooser.APPROVE_OPTION) {
            File selectedFile = fileChooser.getSelectedFile();
            txtFilePath.setText(selectedFile.getAbsolutePath());
        }
    }

    private void sendFile() {
        if (!isConnected) {
            JOptionPane.showMessageDialog(this, "Bạn chưa kết nối đến Server!", "Thông báo", JOptionPane.WARNING_MESSAGE);
            return;
        }

        String filePath = txtFilePath.getText().trim();
        if (filePath.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Vui lòng chọn tập tin cần gửi!", "Thông báo", JOptionPane.WARNING_MESSAGE);
            return;
        }

        File file = new File(filePath);
        if (!file.exists()) {
            JOptionPane.showMessageDialog(this, "File không tồn tại!", "Lỗi", JOptionPane.ERROR_MESSAGE);
            return;
        }

        new Thread(() -> {
            try {
                String sender = txtClientName.getText().trim();
                String fileName = file.getName();
                long fileSize = file.length();

                logClient("📤 Đang gửi File [" + fileName + "] (" + fileSize + " bytes)...");

                // Gửi header file trước
                dos.writeUTF("FILE_HEADER:" + sender + ":ALL:" + fileName + ":" + fileSize);
                dos.flush();

                // Đọc file theo từng Buffer Chunk (8192 bytes) để đảm bảo không mất thông tin và chịu tải file lớn
                try (FileInputStream fis = new FileInputStream(file);
                     BufferedInputStream bis = new BufferedInputStream(fis)) {
                    byte[] buffer = new byte[8192];
                    int bytesRead;
                    while ((bytesRead = bis.read(buffer)) != -1) {
                        dos.write(buffer, 0, bytesRead);
                    }
                    dos.flush();
                }

                logClient("🚀 Gửi File thành công! Đang chờ kết quả phản hồi từ Server...");
                SwingUtilities.invokeLater(() -> txtFilePath.setText(""));

            } catch (Exception ex) {
                logClient("Lỗi khi gửi File: " + ex.getMessage());
            }
        }).start();
    }

    private void disconnectFromServer() {
        isConnected = false;
        try {
            if (socket != null && !socket.isClosed()) {
                socket.close();
            }
        } catch (IOException ignored) {}

        btnConnect.setEnabled(true);
        btnDisconnect.setEnabled(false);
        txtIP.setEnabled(true);
        txtPort.setEnabled(true);
        txtClientName.setEnabled(true);
        lblStatus.setText("Trạng thái: Chưa kết nối.");
        logClient(">>> ĐÃ NGẮT KẾT NỐI KHỎI SERVER <<<");
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
        lblIP = new javax.swing.JLabel();
        txtIP = new javax.swing.JTextField();
        lblPort = new javax.swing.JLabel();
        txtPort = new javax.swing.JTextField();
        lblClientName = new javax.swing.JLabel();
        txtClientName = new javax.swing.JTextField();
        btnConnect = new javax.swing.JButton();
        btnDisconnect = new javax.swing.JButton();
        lblStatus = new javax.swing.JLabel();
        lblMessage = new javax.swing.JLabel();
        txtMessage = new javax.swing.JTextField();
        btnSendMsg = new javax.swing.JButton();
        lblFilePath = new javax.swing.JLabel();
        txtFilePath = new javax.swing.JTextField();
        btnBrowse = new javax.swing.JButton();
        btnSendFile = new javax.swing.JButton();
        lblLog = new javax.swing.JLabel();
        spLog = new javax.swing.JScrollPane();
        txaLog = new javax.swing.JTextArea();

        setDefaultCloseOperation(javax.swing.WindowConstants.EXIT_ON_CLOSE);
        setTitle("MÁY KHÁCH (CLIENT) - BAI04 NETWORK MANAGEMENT");

        lblTitle.setFont(new java.awt.Font("Segoe UI", 1, 18)); // NOI18N
        lblTitle.setHorizontalAlignment(javax.swing.SwingConstants.CENTER);
        lblTitle.setText("CHƯƠNG TRÌNH MÁY KHÁCH (CLIENT)");

        lblIP.setFont(new java.awt.Font("Segoe UI", 1, 12)); // NOI18N
        lblIP.setText("IP Server:");

        txtIP.setText("127.0.0.1");

        lblPort.setFont(new java.awt.Font("Segoe UI", 1, 12)); // NOI18N
        lblPort.setText("Cổng:");

        txtPort.setText("8888");

        lblClientName.setFont(new java.awt.Font("Segoe UI", 1, 12)); // NOI18N
        lblClientName.setText("Tên Client:");

        txtClientName.setText("ClientA");

        btnConnect.setFont(new java.awt.Font("Segoe UI", 1, 12)); // NOI18N
        btnConnect.setText("Kết Nối");

        btnDisconnect.setFont(new java.awt.Font("Segoe UI", 1, 12)); // NOI18N
        btnDisconnect.setText("Ngắt Kết Nối");
        btnDisconnect.setEnabled(false);

        lblStatus.setFont(new java.awt.Font("Segoe UI", 2, 12)); // NOI18N
        lblStatus.setText("Trạng thái: Chưa kết nối.");

        lblMessage.setFont(new java.awt.Font("Segoe UI", 1, 12)); // NOI18N
        lblMessage.setText("Tin nhắn:");

        btnSendMsg.setFont(new java.awt.Font("Segoe UI", 1, 12)); // NOI18N
        btnSendMsg.setText("Gửi Tin Nhắn");

        lblFilePath.setFont(new java.awt.Font("Segoe UI", 1, 12)); // NOI18N
        lblFilePath.setText("Chọn File:");

        btnBrowse.setText("Chọn File...");

        btnSendFile.setFont(new java.awt.Font("Segoe UI", 1, 12)); // NOI18N
        btnSendFile.setText("Gửi File");

        lblLog.setFont(new java.awt.Font("Segoe UI", 1, 12)); // NOI18N
        lblLog.setText("Lịch sử giao tiếp & Phản hồi kết quả:");

        txaLog.setEditable(false);
        txaLog.setColumns(20);
        txaLog.setRows(5);
        spLog.setViewportView(txaLog);

        javax.swing.GroupLayout layout = new javax.swing.GroupLayout(getContentPane());
        getContentPane().setLayout(layout);
        layout.setHorizontalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(layout.createSequentialGroup()
                .addGap(20, 20, 20)
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(lblTitle, javax.swing.GroupLayout.DEFAULT_SIZE, 580, Short.MAX_VALUE)
                    .addComponent(lblStatus, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .addComponent(spLog)
                    .addGroup(layout.createSequentialGroup()
                        .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addComponent(lblLog)
                            .addGroup(layout.createSequentialGroup()
                                .addComponent(lblIP)
                                .addGap(5, 5, 5)
                                .addComponent(txtIP, javax.swing.GroupLayout.PREFERRED_SIZE, 110, javax.swing.GroupLayout.PREFERRED_SIZE)
                                .addGap(10, 10, 10)
                                .addComponent(lblPort)
                                .addGap(5, 5, 5)
                                .addComponent(txtPort, javax.swing.GroupLayout.PREFERRED_SIZE, 65, javax.swing.GroupLayout.PREFERRED_SIZE)
                                .addGap(10, 10, 10)
                                .addComponent(lblClientName)
                                .addGap(5, 5, 5)
                                .addComponent(txtClientName, javax.swing.GroupLayout.PREFERRED_SIZE, 90, javax.swing.GroupLayout.PREFERRED_SIZE))
                            .addGroup(layout.createSequentialGroup()
                                .addComponent(btnConnect, javax.swing.GroupLayout.PREFERRED_SIZE, 120, javax.swing.GroupLayout.PREFERRED_SIZE)
                                .addGap(10, 10, 10)
                                .addComponent(btnDisconnect, javax.swing.GroupLayout.PREFERRED_SIZE, 120, javax.swing.GroupLayout.PREFERRED_SIZE))
                            .addGroup(layout.createSequentialGroup()
                                .addComponent(lblMessage)
                                .addGap(10, 10, 10)
                                .addComponent(txtMessage, javax.swing.GroupLayout.PREFERRED_SIZE, 380, javax.swing.GroupLayout.PREFERRED_SIZE)
                                .addGap(10, 10, 10)
                                .addComponent(btnSendMsg, javax.swing.GroupLayout.PREFERRED_SIZE, 110, javax.swing.GroupLayout.PREFERRED_SIZE))
                            .addGroup(layout.createSequentialGroup()
                                .addComponent(lblFilePath)
                                .addGap(10, 10, 10)
                                .addComponent(txtFilePath, javax.swing.GroupLayout.PREFERRED_SIZE, 270, javax.swing.GroupLayout.PREFERRED_SIZE)
                                .addGap(10, 10, 10)
                                .addComponent(btnBrowse, javax.swing.GroupLayout.PREFERRED_SIZE, 100, javax.swing.GroupLayout.PREFERRED_SIZE)
                                .addGap(10, 10, 10)
                                .addComponent(btnSendFile, javax.swing.GroupLayout.PREFERRED_SIZE, 110, javax.swing.GroupLayout.PREFERRED_SIZE)))
                        .addGap(0, 0, Short.MAX_VALUE)))
                .addGap(20, 20, 20))
        );
        layout.setVerticalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(layout.createSequentialGroup()
                .addGap(15, 15, 15)
                .addComponent(lblTitle)
                .addGap(15, 15, 15)
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(lblIP)
                    .addComponent(txtIP, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(lblPort)
                    .addComponent(txtPort, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(lblClientName)
                    .addComponent(txtClientName, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addGap(10, 10, 10)
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(btnConnect)
                    .addComponent(btnDisconnect))
                .addGap(10, 10, 10)
                .addComponent(lblStatus)
                .addGap(15, 15, 15)
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(lblMessage)
                    .addComponent(txtMessage, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(btnSendMsg))
                .addGap(10, 10, 10)
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(lblFilePath)
                    .addComponent(txtFilePath, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(btnBrowse)
                    .addComponent(btnSendFile))
                .addGap(15, 15, 15)
                .addComponent(lblLog)
                .addGap(5, 5, 5)
                .addComponent(spLog, javax.swing.GroupLayout.DEFAULT_SIZE, 190, Short.MAX_VALUE)
                .addGap(20, 20, 20))
        );

        pack();
        setLocationRelativeTo(null);
    }// </editor-fold>//GEN-END:initComponents

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
            java.util.logging.Logger.getLogger(frmClient.class.getName()).log(java.util.logging.Level.SEVERE, null, ex);
        } catch (InstantiationException ex) {
            java.util.logging.Logger.getLogger(frmClient.class.getName()).log(java.util.logging.Level.SEVERE, null, ex);
        } catch (IllegalAccessException ex) {
            java.util.logging.Logger.getLogger(frmClient.class.getName()).log(java.util.logging.Level.SEVERE, null, ex);
        } catch (javax.swing.UnsupportedLookAndFeelException ex) {
            java.util.logging.Logger.getLogger(frmClient.class.getName()).log(java.util.logging.Level.SEVERE, null, ex);
        }
        //</editor-fold>

        /* Create and display the form */
        java.awt.EventQueue.invokeLater(new Runnable() {
            public void run() {
                new frmClient().setVisible(true);
            }
        });
    }

    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JButton btnBrowse;
    private javax.swing.JButton btnConnect;
    private javax.swing.JButton btnDisconnect;
    private javax.swing.JButton btnSendFile;
    private javax.swing.JButton btnSendMsg;
    private javax.swing.JLabel lblClientName;
    private javax.swing.JLabel lblFilePath;
    private javax.swing.JLabel lblIP;
    private javax.swing.JLabel lblLog;
    private javax.swing.JLabel lblMessage;
    private javax.swing.JLabel lblPort;
    private javax.swing.JLabel lblStatus;
    private javax.swing.JLabel lblTitle;
    private javax.swing.JScrollPane spLog;
    private javax.swing.JTextArea txaLog;
    private javax.swing.JTextField txtClientName;
    private javax.swing.JTextField txtFilePath;
    private javax.swing.JTextField txtIP;
    private javax.swing.JTextField txtMessage;
    private javax.swing.JTextField txtPort;
    // End of variables declaration//GEN-END:variables
}
