package fanstake.hotswap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.nio.file.StandardWatchEventKinds;
import java.nio.file.WatchEvent;
import java.nio.file.WatchKey;
import java.nio.file.Watchable;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class HotSwapWatcherTest {

  private static final List<String> events = new ArrayList<>();

  @BeforeEach
  void clearEvents() {
    events.clear();
  }

  @Test
  void rejectsNonPositiveQuietcensePeriod() {
    final var watcher = new HotSwapWatcher(getClass().getClassLoader());

    assertThrows(IllegalArgumentException.class, () -> watcher.setQuietcensePeriodMillis(0));
    assertThrows(IllegalArgumentException.class, () -> watcher.setQuietcensePeriodMillis(-1));
  }

  @Test
  void acceptsPositiveQuietcensePeriod() {
    final var watcher = new HotSwapWatcher(getClass().getClassLoader());

    watcher.setQuietcensePeriodMillis(25);

    assertEquals(25, watcher.getQuietcensePeriodMillis());
  }

  @Test
  void startsServicesFromRequestedIndexAndStopsDownToRequestedIndex() {
    final var watcher = new HotSwapWatcher(
        getClass().getClassLoader(),
        serviceBuilder("first", RecordingLifecycle.class.getName()),
        serviceBuilder("second", SecondRecordingLifecycle.class.getName()));

    watcher.startFrom(0);
    watcher.stopDownTo(1);
    watcher.startFrom(1);
    watcher.stopDownTo(0);

    assertEquals(
        List.of(
            "first:start:null",
            "second:start:RecordingLifecycle",
            "second:stop:RecordingLifecycle",
            "second:start:RecordingLifecycle",
            "second:stop:RecordingLifecycle",
            "first:stop:null"),
        events);
  }

  @Test
  void processReturnsMinusOneForUnknownWatchKey() {
    final var watcher = new HotSwapWatcher(getClass().getClassLoader());

    assertEquals(-1, watcher.process(new FakeWatchKey(List.of())));
  }

  @Test
  void processIgnoresOverflowAndNonClassEvents() {
    final var watcher = new HotSwapWatcher(
        getClass().getClassLoader(),
        serviceBuilder("first", RecordingLifecycle.class.getName()));
    watcher.startFrom(0);
    final var key = new FakeWatchKey(List.of(
        new FakeWatchEvent<>(StandardWatchEventKinds.OVERFLOW, Path.of("ignored.class")),
        new FakeWatchEvent<>(StandardWatchEventKinds.ENTRY_MODIFY, Path.of("notes.txt"))));
    watcher.registerWatchKey(key, Path.of("/tmp/classes"));

    assertEquals(-1, watcher.process(key));

    watcher.stopDownTo(0);
  }

  @Test
  void processReturnsLowestMatchingClassLoaderIndex() {
    final var watcher = new HotSwapWatcher(
        getClass().getClassLoader(),
        serviceBuilder("first", RecordingLifecycle.class.getName(), "com.example.parent."),
        serviceBuilder("second", SecondRecordingLifecycle.class.getName(), "com.example.child."));
    watcher.startFrom(0);
    final var key = new FakeWatchKey(List.of(
        new FakeWatchEvent<>(StandardWatchEventKinds.ENTRY_MODIFY, Path.of("Child.class")),
        new FakeWatchEvent<>(StandardWatchEventKinds.ENTRY_MODIFY, Path.of("Parent.class"))));
    watcher.registerWatchKey(key, Path.of("/tmp/classes/com/example/child"));

    assertEquals(1, watcher.process(key));

    watcher.stopDownTo(0);
  }

  @Test
  void processReturnsParentIndexWhenParentLayerMatches() {
    final var watcher = new HotSwapWatcher(
        getClass().getClassLoader(),
        serviceBuilder("first", RecordingLifecycle.class.getName(), "com.example.parent."),
        serviceBuilder("second", SecondRecordingLifecycle.class.getName(), "com.example.child."));
    watcher.startFrom(0);
    final var key = new FakeWatchKey(List.of(
        new FakeWatchEvent<>(StandardWatchEventKinds.ENTRY_MODIFY, Path.of("Parent.class"))));
    watcher.registerWatchKey(key, Path.of("/tmp/classes/com/example/parent"));

    assertEquals(0, watcher.process(key));

    watcher.stopDownTo(0);
  }

  private ServiceBuilder serviceBuilder(String name, String startClassName, String... hotSwapPrefixes) {
    return new ServiceBuilder() {
      @Override
      public String serviceStartClassname() {
        return startClassName;
      }

      @Override
      public HotSwapClassLoader getClassLoader(ClassLoader parent) {
        final var resolver = new HotSwapResolver();
        resolver.addHotSwapClassPrefix(hotSwapPrefixes);
        return new HotSwapClassLoader(name, resolver, parent);
      }
    };
  }

  public static class RecordingLifecycle implements Lifecycle {
    @Override
    public void start(Object parentService) {
      events.add("first:start:" + parentName(parentService));
    }

    @Override
    public void stop(Object parentService) {
      events.add("first:stop:" + parentName(parentService));
    }
  }

  public static class SecondRecordingLifecycle implements Lifecycle {
    @Override
    public void start(Object parentService) {
      events.add("second:start:" + parentName(parentService));
    }

    @Override
    public void stop(Object parentService) {
      events.add("second:stop:" + parentName(parentService));
    }
  }

  private static String parentName(Object parentService) {
    return parentService == null ? "null" : parentService.getClass().getSimpleName();
  }

  private static class FakeWatchKey implements WatchKey {
    private final List<WatchEvent<?>> events;
    private boolean valid = true;

    FakeWatchKey(List<WatchEvent<?>> events) {
      this.events = events;
    }

    @Override
    public boolean isValid() {
      return valid;
    }

    @Override
    public List<WatchEvent<?>> pollEvents() {
      return events;
    }

    @Override
    public boolean reset() {
      return valid;
    }

    @Override
    public void cancel() {
      valid = false;
    }

    @Override
    public Watchable watchable() {
      return null;
    }
  }

  private record FakeWatchEvent<T>(Kind<T> kind, T context) implements WatchEvent<T> {
    @Override
    public int count() {
      return 1;
    }
  }
}
