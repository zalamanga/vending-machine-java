package vendingmachine;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.PrintWriter;
import java.sql.SQLException;

public final class ConsoleApp {
  private final ProductStore store;
  private final BufferedReader input;
  private final PrintWriter output;

  public ConsoleApp(ProductStore store, BufferedReader input, PrintWriter output) {
    this.store = store;
    this.input = input;
    this.output = output;
  }

  public void run() throws IOException {
    output.println("VENDING MACHINE | Simulasi lokal");
    output.println("Uang dan penyerahan produk hanya simulasi.");
    while (true) {
      output.println("\n=== Daftar Produk ===");
      try {
        var products = store.products();
        if (products.isEmpty())
          output.println("Belum ada produk. Siapkan data demo terlebih dahulu.");
        for (var product : products) {
          output.printf(
              "%d. %s - Rp %d (Stok: %d)%n",
              product.id(), product.name(), product.price(), product.stock());
        }
      } catch (SQLException failure) {
        output.println("Daftar belum dapat dimuat. Periksa database, lalu coba lagi.");
      }
      Integer productId = readInteger("Pilih ID produk (0 keluar, -1 batal): ", -1);
      if (productId == null || productId == 0) break;
      if (productId == -1) {
        output.println("Transaksi dibatalkan.");
        continue;
      }
      Integer money = readInteger("Masukkan uang contoh (rupiah bulat, -1 batal): ", -1);
      if (money == null) break;
      if (money == -1) {
        output.println("Transaksi dibatalkan.");
        continue;
      }
      output.println("Memproses pembelian contoh...");
      try {
        var receipt = store.purchase(productId, money);
        output.printf("Pembelian contoh tersimpan. Nomor: %d%n", receipt.id());
        output.printf("Anda membeli: %s seharga Rp %d.%n", receipt.productName(), receipt.price());
        output.printf("Kembalian contoh: Rp %d%n", receipt.change());
      } catch (ProductStore.PurchaseRejected failure) {
        output.println(
            switch (failure.reason()) {
              case NOT_FOUND -> "Produk tidak ditemukan.";
              case SOLD_OUT -> "Stok produk habis.";
              case INSUFFICIENT_MONEY -> "Uang contoh tidak cukup. Stok tidak berubah.";
            });
      } catch (SQLException failure) {
        output.println(
            "Pembelian belum dapat dikonfirmasi. Jangan langsung mengulang; periksa stok dan tabel purchases karena koneksi dapat putus saat commit.");
      }
    }
    output.println("Terima kasih telah menggunakan vending machine!");
    output.flush();
  }

  private Integer readInteger(String prompt, int minimum) throws IOException {
    while (true) {
      output.print(prompt);
      output.flush();
      String line = input.readLine();
      if (line == null) {
        output.println("\nMasukan berakhir.");
        return null;
      }
      try {
        int number = Integer.parseInt(line.strip());
        if (number >= minimum) return number;
      } catch (NumberFormatException ignored) {
      }
      output.println(
          "Masukkan bilangan bulat dari " + minimum + " sampai " + Integer.MAX_VALUE + ".");
    }
  }
}
