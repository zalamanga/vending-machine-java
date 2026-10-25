package vendingmachine;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

public final class ProductStore {
  public record Product(int id, String name, int price, int stock) {}

  public record Receipt(long id, String productName, int price, int money, int change) {}

  public enum Rejection {
    NOT_FOUND,
    SOLD_OUT,
    INSUFFICIENT_MONEY
  }

  public static final class PurchaseRejected extends Exception {
    private final Rejection reason;

    public PurchaseRejected(Rejection reason) {
      this.reason = reason;
    }

    public Rejection reason() {
      return reason;
    }
  }

  private final ConnectionFactory connections;

  public ProductStore(ConnectionFactory connections) {
    this.connections = connections;
  }

  public List<Product> products() throws SQLException {
    try (Connection connection = connections.open();
        PreparedStatement statement =
            connection.prepareStatement("SELECT id, name, price, stock FROM products ORDER BY id");
        ResultSet rows = statement.executeQuery()) {
      List<Product> products = new ArrayList<>();
      while (rows.next()) {
        products.add(
            new Product(rows.getInt(1), rows.getString(2), rows.getInt(3), rows.getInt(4)));
      }
      return List.copyOf(products);
    }
  }

  public Receipt purchase(int productId, int money) throws SQLException, PurchaseRejected {
    if (productId <= 0 || money < 0) {
      throw new IllegalArgumentException("ID harus positif dan uang tidak boleh negatif.");
    }
    try (Connection connection = connections.open()) {
      connection.setAutoCommit(false);
      try {
        Product product;
        try (PreparedStatement statement =
            connection.prepareStatement(
                "SELECT id, name, price, stock FROM products WHERE id = ? FOR UPDATE")) {
          statement.setInt(1, productId);
          try (ResultSet rows = statement.executeQuery()) {
            if (!rows.next()) throw new PurchaseRejected(Rejection.NOT_FOUND);
            product =
                new Product(rows.getInt(1), rows.getString(2), rows.getInt(3), rows.getInt(4));
          }
        }
        if (product.stock() <= 0) throw new PurchaseRejected(Rejection.SOLD_OUT);
        if (money < product.price()) throw new PurchaseRejected(Rejection.INSUFFICIENT_MONEY);
        try (PreparedStatement statement =
            connection.prepareStatement(
                "UPDATE products SET stock = stock - 1 WHERE id = ? AND stock > 0")) {
          statement.setInt(1, productId);
          if (statement.executeUpdate() != 1) throw new SQLException("Stock update rejected.");
        }
        long receiptId;
        try (PreparedStatement statement =
            connection.prepareStatement(
                "INSERT INTO purchases (product_id, product_name, price, money, change_amount) VALUES (?, ?, ?, ?, ?)",
                Statement.RETURN_GENERATED_KEYS)) {
          statement.setInt(1, productId);
          statement.setString(2, product.name());
          statement.setInt(3, product.price());
          statement.setInt(4, money);
          statement.setInt(5, money - product.price());
          if (statement.executeUpdate() != 1) throw new SQLException("Receipt insert rejected.");
          try (ResultSet keys = statement.getGeneratedKeys()) {
            if (!keys.next()) throw new SQLException("Receipt ID missing.");
            receiptId = keys.getLong(1);
          }
        }
        connection.commit();
        return new Receipt(
            receiptId, product.name(), product.price(), money, money - product.price());
      } catch (SQLException | PurchaseRejected | RuntimeException failure) {
        try {
          connection.rollback();
        } catch (SQLException rollbackFailure) {
          failure.addSuppressed(rollbackFailure);
        }
        throw failure;
      }
    }
  }
}
