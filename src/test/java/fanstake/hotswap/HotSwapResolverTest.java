package fanstake.hotswap;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class HotSwapResolverTest {

  @Test
  void recognizesDefaultSystemClassPrefixes() {
    final var resolver = new HotSwapResolver();

    assertTrue(resolver.isSystemClass("java.lang.String"));
    assertTrue(resolver.isSystemClass("javax.sql.DataSource"));
    assertTrue(resolver.isSystemClass("sun.misc.Unsafe"));
    assertTrue(resolver.isSystemClass("com.sun.net.httpserver.HttpServer"));
    assertFalse(resolver.isSystemClass("com.example.Service"));
  }

  @Test
  void trimsAndAddsSystemClassPrefixes() {
    final var resolver = new HotSwapResolver();

    resolver.addSystemClassPrefix(" org.example. ");

    assertTrue(resolver.isSystemClass("org.example.Stable"));
  }

  @Test
  void doesNotMatchHotswapFileWithoutConfiguredPrefixes() {
    final var resolver = new HotSwapResolver();

    assertFalse(resolver.isHotswapFile("/tmp/classes/com/example/MyService.class"));
  }

  @Test
  void matchesUnixClassFilePath() {
    final var resolver = new HotSwapResolver();
    resolver.addHotSwapClassPrefix("com.example.");

    assertTrue(resolver.isHotswapFile("/tmp/classes/com/example/service/MyService.class"));
  }

  @Test
  void matchesWindowsClassFilePath() {
    final var resolver = new HotSwapResolver();
    resolver.addHotSwapClassPrefix("com.example.");

    assertTrue(resolver.isHotswapFile("C:\\project\\target\\classes\\com\\example\\service\\MyService.class"));
  }

  @Test
  void honorsExcludedPrefixes() {
    final var resolver = new HotSwapResolver();
    resolver.addHotSwapClassPrefix("com.example.");
    resolver.addExcludedHotSwapPrefix("com.example.generated.");

    assertFalse(resolver.isHotswapFile("/tmp/classes/com/example/generated/Generated.class"));
    assertTrue(resolver.isHotswapFile("/tmp/classes/com/example/runtime/Runtime.class"));
  }

  @Test
  void matchesInnerClassFiles() {
    final var resolver = new HotSwapResolver();
    resolver.addHotSwapClassPrefix("com.example.");

    assertTrue(resolver.isHotswapFile("/tmp/classes/com/example/Outer$Inner.class"));
  }

  @Test
  void packagePrefixWithTrailingDotDoesNotMatchSimilarPackageNames() {
    final var resolver = new HotSwapResolver();
    resolver.addHotSwapClassPrefix("com.example.");

    assertFalse(resolver.isHotSwapClass("com.example2.Service"));
    assertFalse(resolver.isHotswapFile("/tmp/classes/com/example2/Service.class"));
  }

  @Test
  void packagePrefixWithoutTrailingDotMatchesByRawStringPrefix() {
    final var resolver = new HotSwapResolver();
    resolver.addHotSwapClassPrefix("com.example");

    assertTrue(resolver.isHotSwapClass("com.example2.Service"));
  }

  @Test
  void defaultExclusionPreventsHotswappingLibraryClasses() {
    final var resolver = new HotSwapResolver();
    resolver.addHotSwapClassPrefix("fanstake.");

    assertFalse(resolver.isHotSwapClass("fanstake.hotswap.HotSwapWatcher"));
    assertTrue(resolver.isHotSwapClass("fanstake.testapp.Server"));
  }

  @Test
  void unrelatedClassFilesDoNotMatchConfiguredPrefixes() {
    final var resolver = new HotSwapResolver();
    resolver.addHotSwapClassPrefix("com.example.");

    assertFalse(resolver.isHotswapFile("/tmp/classes/org/example/MyService.class"));
  }

  @Test
  void trimsAndAddsMultipleHotswapPrefixes() {
    final var resolver = new HotSwapResolver();

    resolver.addHotSwapClassPrefix(" com.example. ", "org.example.");

    assertTrue(resolver.isHotSwapClass("com.example.Service"));
    assertTrue(resolver.isHotSwapClass("org.example.Service"));
    assertFalse(resolver.isHotSwapClass("net.example.Service"));
  }

  @Test
  void trimmedBlankHotswapPrefixMatchesAnyNonExcludedClass() {
    final var resolver = new HotSwapResolver();

    resolver.addHotSwapClassPrefix(" ");

    assertTrue(resolver.isHotSwapClass("com.example.Service"));
    assertFalse(resolver.isHotSwapClass("fanstake.hotswap.HotSwapWatcher"));
  }
}
