package fanstake.hotswap;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

@DisplayName("HotSwapResolver")
class HotSwapResolverTest {

  @ParameterizedTest
  @ValueSource(strings = { "java.lang.String", "javax.sql.DataSource", "sun.misc.Unsafe",
      "com.sun.net.httpserver.HttpServer" })
  @DisplayName("recognizes default system class prefixes")
  void recognizesDefaultSystemClassPrefixes(String className) {
    assertTrue(new HotSwapResolver().isSystemClass(className));
  }

  @Test
  @DisplayName("does not treat arbitrary classes as system classes")
  void doesNotTreatArbitraryClassesAsSystemClasses() {
    assertFalse(new HotSwapResolver().isSystemClass("com.example.Service"));
  }

  @Test
  @DisplayName("trims and adds system class prefixes")
  void trimsAndAddsSystemClassPrefixes() {
    final var resolver = new HotSwapResolver();
    resolver.addSystemClassPrefix(" org.example. ");

    assertTrue(resolver.isSystemClass("org.example.Stable"));
  }

  @Test
  @DisplayName("does not match hot-swap file without configured prefixes")
  void doesNotMatchHotswapFileWithoutConfiguredPrefixes() {
    final var resolver = new HotSwapResolver();

    assertFalse(resolver.isHotswapFile("/tmp/classes/com/example/MyService.class"));
  }

  @Test
  @DisplayName("matches Unix class file path")
  void matchesUnixClassFilePath() {
    final var resolver = new HotSwapResolver();
    resolver.addHotSwapClassPrefix("com.example.");

    assertTrue(resolver.isHotswapFile("/tmp/classes/com/example/service/MyService.class"));
  }

  @Test
  @DisplayName("matches Windows class file path")
  void matchesWindowsClassFilePath() {
    final var resolver = new HotSwapResolver();
    resolver.addHotSwapClassPrefix("com.example.");

    assertTrue(resolver.isHotswapFile("C:\\project\\target\\classes\\com\\example\\service\\MyService.class"));
  }

  @Test
  @DisplayName("honors excluded prefixes")
  void honorsExcludedPrefixes() {
    final var resolver = new HotSwapResolver();
    resolver.addHotSwapClassPrefix("com.example.");
    resolver.addExcludedHotSwapPrefix("com.example.generated.");

    assertAll(
        () -> assertFalse(resolver.isHotswapFile("/tmp/classes/com/example/generated/Generated.class")),
        () -> assertTrue(resolver.isHotswapFile("/tmp/classes/com/example/runtime/Runtime.class")));
  }

  @Test
  @DisplayName("matches inner class files")
  void matchesInnerClassFiles() {
    final var resolver = new HotSwapResolver();
    resolver.addHotSwapClassPrefix("com.example.");

    assertTrue(resolver.isHotswapFile("/tmp/classes/com/example/Outer$Inner.class"));
  }

  @Test
  @DisplayName("package prefix with trailing dot does not match similar package names")
  void packagePrefixWithTrailingDotDoesNotMatchSimilarPackageNames() {
    final var resolver = new HotSwapResolver();
    resolver.addHotSwapClassPrefix("com.example.");

    assertAll(
        () -> assertFalse(resolver.isHotSwapClass("com.example2.Service")),
        () -> assertFalse(resolver.isHotswapFile("/tmp/classes/com/example2/Service.class")));
  }

  @Test
  @DisplayName("package prefix without trailing dot matches by raw string prefix")
  void packagePrefixWithoutTrailingDotMatchesByRawStringPrefix() {
    final var resolver = new HotSwapResolver();
    resolver.addHotSwapClassPrefix("com.example");

    assertTrue(resolver.isHotSwapClass("com.example2.Service"));
  }

  @Test
  @DisplayName("default exclusion prevents hot-swapping library classes")
  void defaultExclusionPreventsHotswappingLibraryClasses() {
    final var resolver = new HotSwapResolver();
    resolver.addHotSwapClassPrefix("fanstake.");

    assertAll(
        () -> assertFalse(resolver.isHotSwapClass("fanstake.hotswap.HotSwapWatcher")),
        () -> assertTrue(resolver.isHotSwapClass("fanstake.testapp.Server")));
  }

  @Test
  @DisplayName("unrelated class files do not match configured prefixes")
  void unrelatedClassFilesDoNotMatchConfiguredPrefixes() {
    final var resolver = new HotSwapResolver();
    resolver.addHotSwapClassPrefix("com.example.");

    assertFalse(resolver.isHotswapFile("/tmp/classes/org/example/MyService.class"));
  }

  @Test
  @DisplayName("trims and adds multiple hot-swap prefixes")
  void trimsAndAddsMultipleHotswapPrefixes() {
    final var resolver = new HotSwapResolver();

    resolver.addHotSwapClassPrefix(" com.example. ", "org.example.");

    assertAll(
        () -> assertTrue(resolver.isHotSwapClass("com.example.Service")),
        () -> assertTrue(resolver.isHotSwapClass("org.example.Service")),
        () -> assertFalse(resolver.isHotSwapClass("net.example.Service")));
  }

  @ParameterizedTest
  @CsvSource({
      "' ', com.example.Service, fanstake.hotswap.HotSwapWatcher",
  })
  @DisplayName("trimmed blank hot-swap prefix matches any non-excluded class")
  void trimmedBlankHotswapPrefixMatchesAnyNonExcludedClass(String prefix, String shouldMatch, String shouldNotMatch) {
    final var resolver = new HotSwapResolver();

    resolver.addHotSwapClassPrefix(prefix);

    assertAll(
        () -> assertTrue(resolver.isHotSwapClass(shouldMatch)),
        () -> assertFalse(resolver.isHotSwapClass(shouldNotMatch)));
  }
}
