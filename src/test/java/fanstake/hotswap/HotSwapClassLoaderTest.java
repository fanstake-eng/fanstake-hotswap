package fanstake.hotswap;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("HotSwapClassLoader")
class HotSwapClassLoaderTest {

  @Test
  @DisplayName("delegates system classes to parent")
  void delegatesSystemClassesToParent() throws Exception {
    final var loader = newLoader(new HotSwapResolver());

    assertSame(String.class, loader.loadClass("java.lang.String"));
  }

  @Test
  @DisplayName("loads configured hot-swap classes through child loader")
  void loadsConfiguredHotswapClassesThroughChildLoader() throws Exception {
    final var resolver = new HotSwapResolver();
    resolver.addHotSwapClassPrefix("fanstake.testapp.child.");
    final var loader = new HotSwapClassLoader("child", resolver, getClass().getClassLoader());

    final var loadedClass = loader.loadClass("fanstake.testapp.child.Child");

    assertEquals(loader, loadedClass.getClassLoader());
  }

  @Test
  @DisplayName("separate hot-swap loaders load distinct class instances")
  void separateHotswapLoadersLoadDistinctClassInstances() throws Exception {
    final var resolver = new HotSwapResolver();
    resolver.addHotSwapClassPrefix("fanstake.testapp.child.");
    final var first = new HotSwapClassLoader("first", resolver, getClass().getClassLoader());
    final var second = new HotSwapClassLoader("second", resolver, getClass().getClassLoader());

    final var firstClass = first.loadClass("fanstake.testapp.child.Child");
    final var secondClass = second.loadClass("fanstake.testapp.child.Child");

    assertAll(
        () -> assertNotSame(firstClass, secondClass),
        () -> assertSame(first, firstClass.getClassLoader()),
        () -> assertSame(second, secondClass.getClassLoader()));
  }

  @Test
  @DisplayName("delegates non-hot-swap classes to parent")
  void delegatesNonHotswapClassesToParent() throws Exception {
    final var resolver = new HotSwapResolver();
    resolver.addHotSwapClassPrefix("fanstake.testapp.child.");
    final var loader = new HotSwapClassLoader("child", resolver, getClass().getClassLoader());

    final var loadedClass = loader.loadClass("fanstake.testapp.Server");

    assertSame(getClass().getClassLoader(), loadedClass.getClassLoader());
  }

  @Test
  @DisplayName("excluded hot-swap classes delegate to parent")
  void excludedHotswapClassesDelegateToParent() throws Exception {
    final var resolver = new HotSwapResolver();
    resolver.addHotSwapClassPrefix("fanstake.testapp.");
    resolver.addExcludedHotSwapPrefix("fanstake.testapp.child.");
    final var loader = new HotSwapClassLoader("child", resolver, getClass().getClassLoader());

    final var loadedClass = loader.loadClass("fanstake.testapp.child.Child");

    assertSame(getClass().getClassLoader(), loadedClass.getClassLoader());
  }

  @Test
  @DisplayName("withParent preserves name and resolver")
  void withParentPreservesNameAndResolver() {
    final var resolver = new HotSwapResolver();
    final var first = new HotSwapClassLoader("original", resolver, getClass().getClassLoader());
    final var replacementParent = ClassLoader.getPlatformClassLoader();

    final var second = first.withParent(replacementParent);

    assertAll(
        () -> assertNotSame(first, second),
        () -> assertSame(replacementParent, second.getParent()),
        () -> assertSame(resolver, second.getHotSwapResolver()));
  }

  @Test
  @DisplayName("newInstance uses thread context class loader")
  void newInstanceUsesThreadContextClassLoader() {
    final var original = Thread.currentThread().getContextClassLoader();
    try {
      Thread.currentThread().setContextClassLoader(getClass().getClassLoader());

      final Lifecycle instance = HotSwapClassLoader.newInstance(TestLifecycle.class.getName());

      assertTrue(instance instanceof TestLifecycle);
    } finally {
      Thread.currentThread().setContextClassLoader(original);
    }
  }

  @Test
  @DisplayName("newInstance wraps class not found failures")
  void newInstanceWrapsFailures() {
    final var failure = assertThrows(
        RuntimeException.class,
        () -> HotSwapClassLoader.newInstance("does.not.Exist"));

    assertTrue(failure.getMessage().contains("Failed to start child"));
  }

  @Test
  @DisplayName("newInstance wraps missing public no-arg constructor")
  void newInstanceWrapsMissingPublicNoArgConstructor() {
    final var failure = assertThrows(
        RuntimeException.class,
        () -> HotSwapClassLoader.newInstance(NoPublicConstructorLifecycle.class.getName()));

    assertTrue(failure.getMessage().contains("Failed to start child"));
  }

  @Test
  @DisplayName("newInstance wraps constructor exceptions")
  void newInstanceWrapsConstructorExceptions() {
    final var failure = assertThrows(
        RuntimeException.class,
        () -> HotSwapClassLoader.newInstance(ThrowingConstructorLifecycle.class.getName()));

    assertTrue(failure.getMessage().contains("Failed to start child"));
  }

  @Test
  @DisplayName("returns already-loaded class on subsequent loadClass call")
  void returnsAlreadyLoadedClassOnSubsequentCall() throws Exception {
    final var resolver = new HotSwapResolver();
    resolver.addHotSwapClassPrefix("fanstake.testapp.child.");
    final var loader = new HotSwapClassLoader("test", resolver, getClass().getClassLoader());

    final var first = loader.loadClass("fanstake.testapp.child.Child");
    final var second = loader.loadClass("fanstake.testapp.child.Child");

    assertSame(first, second);
  }

  @Test
  @DisplayName("loadClass with resolve=true returns the class")
  void loadClassWithResolveTrueReturnsTheClass() throws Exception {
    final var resolver = new HotSwapResolver();
    resolver.addHotSwapClassPrefix("fanstake.testapp.child.");
    final var loader = new HotSwapClassLoader("test", resolver, getClass().getClassLoader());

    final var loadedClass = loader.loadClass("fanstake.testapp.child.Child", true);

    assertNotNull(loadedClass);
    assertSame(loader, loadedClass.getClassLoader());
  }

  private static HotSwapClassLoader newLoader(HotSwapResolver resolver) {
    return new HotSwapClassLoader("test", resolver, HotSwapClassLoaderTest.class.getClassLoader());
  }

  public static class TestLifecycle implements Lifecycle {
    public TestLifecycle() {
    }

    @Override
    public void start(Object parentService) {
    }

    @Override
    public void stop(Object parentService) {
    }
  }

  public static class NoPublicConstructorLifecycle implements Lifecycle {
    private NoPublicConstructorLifecycle() {
    }

    @Override
    public void start(Object parentService) {
    }

    @Override
    public void stop(Object parentService) {
    }
  }

  public static class ThrowingConstructorLifecycle implements Lifecycle {
    public ThrowingConstructorLifecycle() {
      throw new IllegalStateException("boom");
    }

    @Override
    public void start(Object parentService) {
    }

    @Override
    public void stop(Object parentService) {
    }
  }
}
