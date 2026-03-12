package fanstake.hotswap;

import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.nio.file.StandardWatchEventKinds;
import java.nio.file.WatchEvent;
import java.nio.file.WatchEvent.Kind;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

public class HotSwapWatcher {

  private final static Logger log = Logger.getLogger(HotSwapWatcher.class.getName());

  private final ServiceBuilder[] serviceBuilders;

  private final HotSwapClassLoader[] classLoaders;

  private final Lifecycle[] services;

  private final ClassLoader rootClassLoader;

  private Thread thread;

  private long quietcensePeriodMillis = 100;

  private final Map<WatchKey, Path> keyToPath = new HashMap<>();

  public HotSwapWatcher(ClassLoader rootClassLoader, ServiceBuilder... serviceBuilders) {
    this.rootClassLoader = rootClassLoader;
    this.serviceBuilders = serviceBuilders;
    this.classLoaders = new HotSwapClassLoader[serviceBuilders.length];
    this.services = new Lifecycle[serviceBuilders.length];
  }

  public long getQuietcensePeriodMillis() {
    return quietcensePeriodMillis;
  }

  public void setQuietcensePeriodMillis(long quietcensePeriodMillis) {
    this.quietcensePeriodMillis = quietcensePeriodMillis;
  }

  private void startFrom(int serviceIndex) {
    // initialize the classloader stack
    for (int i = serviceIndex; i < serviceBuilders.length; i++) {
      final var parentClassLoader = (i == 0) ? rootClassLoader : classLoaders[i - 1];
      final var classLoader = serviceBuilders[i].getClassLoader(parentClassLoader);
      classLoaders[i] = classLoader;
    }
    // start the services
    Thread.currentThread().setContextClassLoader(classLoaders[classLoaders.length - 1]);
    for (int i = serviceIndex; i < serviceBuilders.length; i++) {
      final var parentService = (i == 0) ? null : services[i - 1];
      final var startClassName = serviceBuilders[i].serviceStartClassname();
      final var serviceBuilderInstance = HotSwapClassLoader.<Lifecycle>newInstance(startClassName);
      services[i] = serviceBuilderInstance;
      serviceBuilderInstance.start(parentService);
    }
  }

  private void stopDownTo(int serviceIndex) {
    // stop services and clear out classloaders
    for (int i = serviceBuilders.length - 1; i >= serviceIndex; i--) {
      var parentService = (i == 0) ? null : services[i - 1];
      services[i].stop(parentService);
      services[i] = null;
      classLoaders[i] = null;
    }
  }

  public void start() {
    if (thread != null) {
      log.warning("HotSwapWatcher is already running.");
      return;
    }

    startFrom(0);

    thread = new Thread(() -> run());
    thread.setName("HotSwapWatcher " + System.currentTimeMillis());
    thread.setDaemon(true);
    thread.start();
  }

  @SuppressWarnings("UseSpecificCatch")
  private void run() {
    log.info("Starting HotSwapWatcher...");
    try {
      WatchService watcher = FileSystems.getDefault().newWatchService();
      addShutdownHook(watcher);

      for (Path path : HotSwapDirs.getWatchingPaths()) {
        if (log.isLoggable(Level.FINE)) {
          log.fine("Watching path: %s".formatted(path));
        }
        final var watchkey = path.register(watcher,
            StandardWatchEventKinds.ENTRY_DELETE,
            StandardWatchEventKinds.ENTRY_MODIFY,
            StandardWatchEventKinds.ENTRY_CREATE);
        keyToPath.put(watchkey, path);
      }

      while (!Thread.currentThread().isInterrupted()) {
        watch(watcher);
      }

      stopDownTo(0);

    } catch (Exception e) {
      log.severe("Exception occurred while running HotSwapWatcher: %s".formatted(e));
      throw new RuntimeException(e);
    }
  }

  @SuppressWarnings("UseSpecificCatch")
  private void watch(WatchService watcher) {
    try {
      var lowestMatchingIndex = -1;
      while (true) {
        // log.debug("Checking for file changes...");
        final var watchKey = watcher.poll(100, TimeUnit.MILLISECONDS);
        if (watchKey != null) {
          // log.info("File change detected: %s", watchKey.watchable().toString());
          var matchingIndex = process(watchKey);
          if (matchingIndex == 1 || matchingIndex > lowestMatchingIndex) {
            lowestMatchingIndex = matchingIndex;
          }
          watchKey.reset();
        } else {
          // if no changes detected within the polling interval, we've reached quietcense
          // so perform the corresponding restart(s)
          break;
        }
      }
      if (lowestMatchingIndex != -1) {
        stopDownTo(lowestMatchingIndex);
        startFrom(lowestMatchingIndex);
      }
    } catch (Exception e) {
      if (e instanceof InterruptedException) {
        Thread.currentThread().interrupt();
      }
    }
  }

  private int process(WatchKey watchKey) {
    final var path = keyToPath.get(watchKey);
    final List<WatchEvent<?>> watchEvents = watchKey.pollEvents();
    var lowestMatchingIndex = -1;
    // log.info("Number of file modifications:%s", watchEvents.size());
    for (final WatchEvent<?> event : watchEvents) {
      final Kind<?> kind = event.kind();
      final String fileName = event.context().toString();
      final String fullPath = path.resolve(fileName).toString();
      // log.info("event %s,%s", kind.toString(), fullPath);
      if (kind == StandardWatchEventKinds.OVERFLOW) {
        continue;
      }

      if (fullPath.endsWith(".class")) {
        if (log.isLoggable(Level.FINE)) {
          log.fine("File change detected: %s, %s".formatted(kind.toString(), fullPath));
        }
        for (int i = classLoaders.length - 1; i >= 0; i--) {
          // log.fine("Checking classloader %s for class file
          // %s".formatted(classLoaders[i].toString(), fullPath));
          if (classLoaders[i].getHotSwapResolver().isHotswapFile(fullPath)) {
            // log.info("Matched class %s to classloader %s".formatted(fullPath,
            // classLoaders[i].toString()));
            if (lowestMatchingIndex == -1 || lowestMatchingIndex > i) {
              lowestMatchingIndex = i;
            }
          }
        }
      }
    }
    return lowestMatchingIndex;
  }

  @SuppressWarnings("UseSpecificCatch")
  private void addShutdownHook(WatchService watcher) {
    @SuppressWarnings({ "CallToPrintStackTrace", "ConvertToTryWithResources" })

    final var hook = new Thread(() -> {
      try {
        log.info("HotSwapWatcher shutdown hook");
        watcher.close();
      } catch (Exception e) {
        log.severe("Error occurred while closing WatchService: %s".formatted(e.getMessage()));
        e.printStackTrace();
      }
    });
    Runtime.getRuntime().addShutdownHook(hook);
  }

  @SuppressWarnings("CallToPrintStackTrace")
  public void stop() {
    if (thread != null) {
      try {
        thread.interrupt();
      } catch (Throwable e) {
        log.severe("Error occurred while interrupting HotSwapWatcher thread: %s".formatted(e.getMessage()));
        e.printStackTrace();
      }
    }
  }

}
