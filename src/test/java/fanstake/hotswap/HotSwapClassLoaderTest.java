package fanstake.hotswap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class HotSwapClassLoaderTest {

  @Test
  void delegatesSystemClassesToParent() throws Exception {
    final var resolver = new HotSwapResolver();
    final var loader = new HotSwapClassLoader("test", resolver, getClass().getClassLoader());

    assertSame(String.class, loader.loadClass("java.lang.String"));
  }

  @Test
  void loadsConfiguredHotswapClassesThroughChildLoader() throws Exception {
    final var resolver = new HotSwapResolver();
    resolver.addHotSwapClassPrefix("fanstake.testapp.child.");
    final var loader = new HotSwapClassLoader("child", resolver, getClass().getClassLoader());

    final var loadedClass = loader.loadClass("fanstake.testapp.child.Child");

    assertEquals(loader, loadedClass.getClassLoader());
  }

  @Test
  void excludedHotswapClassesDelegateToParent() throws Exception {
    final var resolver = new HotSwapResolver();
    resolver.addHotSwapClassPrefix("fanstake.testapp.");
    resolver.addExcludedHotSwapPrefix("fanstake.testapp.child.");
    final var loader = new HotSwapClassLoader("child", resolver, getClass().getClassLoader());

    final var loadedClass = loader.loadClass("fanstake.testapp.child.Child");

    assertSame(getClass().getClassLoader(), loadedClass.getClassLoader());
  }

  @Test
  void withParentPreservesNameAndResolver() {
    final var resolver = new HotSwapResolver();
    final var first = new HotSwapClassLoader("original", resolver, getClass().getClassLoader());
    final var replacementParent = ClassLoader.getPlatformClassLoader();

    final var second = first.withParent(replacementParent);

    assertNotSame(first, second);
    assertSame(replacementParent, second.getParent());
    assertSame(resolver, second.getHotSwapResolver());
    assertTrue(second.toString().startsWith("original#"));
  }

  @Test
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
  void newInstanceWrapsFailures() {
    final var failure = assertThrows(
        RuntimeException.class,
        () -> HotSwapClassLoader.newInstance("does.not.Exist"));

    assertTrue(failure.getMessage().contains("Failed to start child"));
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
}
