package n12_bai04_2;

import javax.swing.SwingUtilities;

/**
 * Lớp khởi chạy hệ thống (Main Launcher)
 */
public class Main {
    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            // Khởi chạy Server
            new frmServer().setVisible(true);

            // Khởi chạy Client A
            frmClient clientA = new frmClient();
            clientA.setTitle("MÁY KHÁCH (CLIENT A)");
            clientA.setVisible(true);

            // Khởi chạy Client B
            frmClient clientB = new frmClient();
            clientB.setTitle("MÁY KHÁCH (CLIENT B)");
            clientB.setVisible(true);
        });
    }
}
