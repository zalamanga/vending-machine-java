# Alur kode Vending Machine

Menu terminal memisahkan input dari transaksi JDBC. Stok dan catatan pembelian disimpan bersama; pesan sukses dicetak setelah `commit` mengembalikan hasil.

```mermaid
flowchart TD
  A["Vendingmachine.main: environment koneksi"] --> B["ConsoleApp: muat daftar produk"]
  B --> C["Baca ID dan uang contoh"]
  C -->|Batal| B
  C -->|Keluar atau EOF| X["Selesai"]
  C --> D["ProductStore.purchase: mulai transaksi"]
  D --> E["SELECT produk FOR UPDATE"]
  E --> F{"Produk ada, stok cukup, uang cukup"}
  F -->|Tidak| G["Rollback dan PurchaseRejected"]
  G --> B
  F -->|Ya| H["Kurangi stok dan INSERT purchases"]
  H --> I["Commit"]
  I --> J["Receipt: ID, produk, harga, kembalian"]
  J --> K["Cetak sukses lalu muat daftar terbaru"]
  K --> B
  H -->|SQL gagal| L["Rollback; jangan klaim sukses"]
  I -->|Konfirmasi terputus| L
  L --> B
```

Kunci baris mencegah dua koneksi membeli unit terakhir yang sama. Catatan `purchases` menyimpan snapshot nama produk dan angka transaksi. Kegagalan sebelum commit membatalkan perubahan. Jika server sudah commit tetapi koneksi putus sebelum konfirmasi diterima, program tidak dapat menjanjikan hasilnya belum tersimpan. Ia tidak mengirim ulang operasi otomatis.

| Bagian | Sumber |
| --- | --- |
| Entry point dan environment | [Vendingmachine.java](../src/main/java/vendingmachine/Vendingmachine.java), [DatabaseConfig.java](../src/main/java/vendingmachine/DatabaseConfig.java) |
| Menu, validasi dan pembatalan | [ConsoleApp.java](../src/main/java/vendingmachine/ConsoleApp.java) |
| Daftar produk dan transaksi pembelian | [ProductStore.java](../src/main/java/vendingmachine/ProductStore.java) |
| Tabel produk/pembelian dan seed | [001-schema.sql](../database/001-schema.sql) |
| Stok, rollback, konkurensi, commit dan EOF | [ProductStoreTest.java](../src/test/java/vendingmachine/ProductStoreTest.java) |

Uang, kembalian, dan penyerahan produk hanya simulasi. Tidak ada perangkat, uang nyata, refund, atau pengiriman barang. Ikuti [README](../README.md) untuk setup dan pemeriksaan sebelum mengulang transaksi yang konfirmasinya terputus.
