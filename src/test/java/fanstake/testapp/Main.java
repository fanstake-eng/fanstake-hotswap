package fanstake.testapp;

import java.io.InputStream;
import java.util.logging.LogManager;

import fanstake.hotswap.HotSwapClassLoader;
import fanstake.hotswap.HotSwapResolver;
import fanstake.hotswap.HotSwapWatcher;
import fanstake.hotswap.ServiceBuilder;

class Main {

  // Initialize logging
  static {
    try (InputStream in = Main.class.getResourceAsStream("/logging.properties")) {
      if (in != null) {
        LogManager.getLogManager().readConfiguration(in);
      }
    } catch (Exception e) {
      System.err.println("Could not load logging.properties: " + e.getMessage());
    }
  }

  public static void main(String[] args) {
    var serverBuilder = new ServiceBuilder() {
      @Override
      public String serviceStartClassname() {
        return "fanstake.testapp.Server";
      }

      @Override
      public HotSwapClassLoader getClassLoader(ClassLoader parent) {
        final var resolver = new HotSwapResolver();
        resolver.addHotSwapClassPrefix("fanstake.testapp.");
        resolver.addExcludedHotSwapPrefix("fanstake.testapp.child.");
        return new HotSwapClassLoader(
            "Server/Parent",
            resolver,
            parent);
      }
    };

    var childBuilder = new ServiceBuilder() {
      @Override
      public String serviceStartClassname() {
        return "fanstake.testapp.child.Child";
      }

      @Override
      public HotSwapClassLoader getClassLoader(ClassLoader parent) {
        final var resolver = new HotSwapResolver();
        resolver.addHotSwapClassPrefix("fanstake.testapp.child.");
        return new HotSwapClassLoader(
            "Server/Child",
            resolver,
            parent);
      }
    };

    final var watcher = new HotSwapWatcher(Main.class.getClassLoader(), serverBuilder, childBuilder);
    watcher.start();

    System.out.println("Press enter to stop the server...");
    if (System.console() != null) {
      System.console().readLine();
    }
    watcher.stop();
  }
}
