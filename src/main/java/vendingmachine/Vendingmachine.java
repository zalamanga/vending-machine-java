package vendingmachine;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;

public final class Vendingmachine {
  private Vendingmachine() {}

  public static void main(String[] args) {
    PrintWriter output =
        new PrintWriter(new OutputStreamWriter(System.out, StandardCharsets.UTF_8), true);
    try {
      var store = new ProductStore(DatabaseConfig.fromEnvironment(System.getenv()));
      new ConsoleApp(
              store,
              new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8)),
              output)
          .run();
    } catch (IllegalArgumentException failure) {
      output.println(failure.getMessage());
    } catch (IOException failure) {
      output.println("Masukan terminal tidak dapat dibaca. Program dihentikan.");
    }
  }
}
