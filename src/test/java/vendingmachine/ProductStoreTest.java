package vendingmachine;

import static org.junit.jupiter.api.Assertions.*;

import java.io.BufferedReader;
import java.io.StringReader;
import java.io.StringWriter;
import java.io.PrintWriter;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.h2.tools.RunScript;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ProductStoreTest {
  private ConnectionFactory connections;
  private ProductStore store;

  @BeforeEach
  void database() throws Exception {
    String url =
        "jdbc:h2:mem:" + UUID.randomUUID() + ";MODE=MySQL;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=5000";
    connections = () -> DriverManager.getConnection(url);
    try (var connection = connections.open()) {
      String schema =
          Files.readString(Path.of("database/001-schema.sql")).replace("ENGINE=InnoDB", "");
      RunScript.execute(connection, new StringReader(schema));
    }
    store = new ProductStore(connections);
  }

  @Test
  void purchaseCommitsStockAndReceiptWithExactChange() throws Exception {
    var receipt = store.purchase(1, 10000);
    assertTrue(receipt.id() > 0);
    assertEquals(6000, receipt.change());
    assertEquals(9, scalar("SELECT stock FROM products WHERE id=1"));
    assertEquals(
        1,
        scalar(
            "SELECT COUNT(*) FROM purchases WHERE price=4000 AND money=10000 AND change_amount=6000"));
  }

  @Test
  void rejectedPurchasesLeaveStockAndReceiptsUnchanged() throws Exception {
    assertEquals(
        ProductStore.Rejection.INSUFFICIENT_MONEY,
        assertThrows(ProductStore.PurchaseRejected.class, () -> store.purchase(1, 1)).reason());
    assertEquals(
        ProductStore.Rejection.NOT_FOUND,
        assertThrows(ProductStore.PurchaseRejected.class, () -> store.purchase(999, 10000))
            .reason());
    execute("UPDATE products SET stock=0 WHERE id=2");
    assertEquals(
        ProductStore.Rejection.SOLD_OUT,
        assertThrows(ProductStore.PurchaseRejected.class, () -> store.purchase(2, 10000)).reason());
    assertEquals(10, scalar("SELECT stock FROM products WHERE id=1"));
    assertEquals(0, scalar("SELECT COUNT(*) FROM purchases"));
    assertThrows(IllegalArgumentException.class, () -> store.purchase(0, 100));
    assertThrows(IllegalArgumentException.class, () -> store.purchase(1, -1));
  }

  @Test
  void receiptFailureRollsBackTheAlreadyExecutedStockUpdate() throws Exception {
    execute("ALTER TABLE purchases ADD CONSTRAINT reject_receipt CHECK (money < 0)");
    assertThrows(SQLException.class, () -> store.purchase(1, 10000));
    assertEquals(10, scalar("SELECT stock FROM products WHERE id=1"));
    assertEquals(0, scalar("SELECT COUNT(*) FROM purchases"));
  }

  @Test
  void twoConnectionsCannotBuyTheSameLastUnit() throws Exception {
    execute("UPDATE products SET stock=1 WHERE id=1");
    var pool = Executors.newFixedThreadPool(2);
    var start = new CountDownLatch(1);
    try {
      var first = pool.submit(() -> attempt(start));
      var second = pool.submit(() -> attempt(start));
      start.countDown();
      assertEquals(1, first.get(10, TimeUnit.SECONDS) + second.get(10, TimeUnit.SECONDS));
      assertEquals(0, scalar("SELECT stock FROM products WHERE id=1"));
      assertEquals(1, scalar("SELECT COUNT(*) FROM purchases"));
    } finally {
      pool.shutdownNow();
    }
  }

  @Test
  void failureBeforeCommitRollsBackAndPrintsNoSuccess() throws Exception {
    String output = runConsole(failingCommit(false), "1\n10000\n0\n");
    assertFalse(output.contains("Pembelian contoh tersimpan"));
    assertTrue(output.contains("belum dapat dikonfirmasi"));
    assertEquals(10, scalar("SELECT stock FROM products WHERE id=1"));
    assertEquals(0, scalar("SELECT COUNT(*) FROM purchases"));
  }

  @Test
  void lostCommitAcknowledgementIsHonestAndDoesNotReplayThePurchase() throws Exception {
    String output = runConsole(failingCommit(true), "1\n10000\n0\n");
    assertFalse(output.contains("Pembelian contoh tersimpan"));
    assertTrue(output.contains("koneksi dapat putus saat commit"));
    assertEquals(9, scalar("SELECT stock FROM products WHERE id=1"));
    assertEquals(1, scalar("SELECT COUNT(*) FROM purchases"));
  }

  @Test
  void invalidInputCancellationAndEofDoNotWrite() throws Exception {
    String output = runConsole(store, "abc\n-9\n2147483648\n-1\n1\n-1\n");
    assertTrue(output.contains("Masukkan bilangan bulat"));
    assertTrue(output.contains("Transaksi dibatalkan"));
    assertTrue(output.contains("Masukan berakhir"));
    assertEquals(0, scalar("SELECT COUNT(*) FROM purchases"));
  }

  @Test
  void configurationRequiresEnvironmentInsteadOfAnEmbeddedPassword() {
    assertThrows(IllegalArgumentException.class, () -> DatabaseConfig.fromEnvironment(Map.of()));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            DatabaseConfig.fromEnvironment(
                Map.of(
                    "DB_URL", "jdbc:h2:mem:test", "DB_USER", "demo", "DB_PASSWORD", "synthetic")));
  }

  private int attempt(CountDownLatch start) throws Exception {
    start.await();
    try {
      store.purchase(1, 10000);
      return 1;
    } catch (ProductStore.PurchaseRejected failure) {
      assertEquals(ProductStore.Rejection.SOLD_OUT, failure.reason());
      return 0;
    }
  }

  private ProductStore failingCommit(boolean commitBeforeFailure) {
    return new ProductStore(
        () -> {
          Connection delegate = connections.open();
          return (Connection)
              Proxy.newProxyInstance(
                  Connection.class.getClassLoader(),
                  new Class<?>[] {Connection.class},
                  (proxy, method, args) -> {
                    if (method.getName().equals("commit")) {
                      if (commitBeforeFailure) delegate.commit();
                      throw new SQLException("Simulated lost connection", "08006");
                    }
                    try {
                      return method.invoke(delegate, args);
                    } catch (InvocationTargetException failure) {
                      throw failure.getCause();
                    }
                  });
        });
  }

  private String runConsole(ProductStore selectedStore, String input) throws Exception {
    var output = new StringWriter();
    new ConsoleApp(
            selectedStore,
            new BufferedReader(new StringReader(input)),
            new PrintWriter(output, true))
        .run();
    return output.toString();
  }

  private int scalar(String sql) throws SQLException {
    try (var connection = connections.open();
        var statement = connection.createStatement();
        var rows = statement.executeQuery(sql)) {
      rows.next();
      return rows.getInt(1);
    }
  }

  private void execute(String sql) throws SQLException {
    try (var connection = connections.open();
        var statement = connection.createStatement()) {
      statement.execute(sql);
    }
  }
}
