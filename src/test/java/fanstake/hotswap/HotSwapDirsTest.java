package fanstake.hotswap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertIterableEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class HotSwapDirsTest {

  @TempDir
  Path tempDir;

  @Test
  void discoversDirectoriesRecursivelyAndSkipsMetaInf() throws Exception {
    final var root = Files.createDirectories(tempDir.resolve("classes"));
    final var packageDir = Files.createDirectories(root.resolve("com/example"));
    Files.createDirectories(root.resolve("META-INF/services"));
    Files.writeString(root.resolve("com/example/Service.class"), "compiled");

    final var paths = HotSwapDirs.buildWatchingPaths(root.toString());

    assertTrue(paths.contains(root));
    assertTrue(paths.contains(root.resolve("com")));
    assertTrue(paths.contains(packageDir));
    assertFalse(paths.contains(root.resolve("META-INF")));
    assertFalse(paths.contains(root.resolve("META-INF/services")));
  }

  @Test
  void skipsDirectoriesWhoseNamesContainMetaInf() throws Exception {
    final var root = Files.createDirectories(tempDir.resolve("classes"));
    final var metaInfNamedDir = Files.createDirectories(root.resolve("not-META-INF-but-contains-it"));
    Files.createDirectories(metaInfNamedDir.resolve("nested"));

    final var paths = HotSwapDirs.buildWatchingPaths(root.toString());

    assertTrue(paths.contains(root));
    assertFalse(paths.contains(metaInfNamedDir));
    assertFalse(paths.contains(metaInfNamedDir.resolve("nested")));
  }

  @Test
  void sortsAndDeduplicatesDiscoveredDirectories() throws Exception {
    final var b = Files.createDirectories(tempDir.resolve("b"));
    final var a = Files.createDirectories(tempDir.resolve("a"));
    final var classPath = b + File.pathSeparator + a + File.pathSeparator + b;

    final var paths = HotSwapDirs.buildWatchingPaths(classPath);

    assertIterableEquals(
        java.util.List.of(a, b),
        paths);
  }

  @Test
  void ignoresClasspathEntriesThatAreFiles() throws Exception {
    final var jar = Files.writeString(tempDir.resolve("library.jar"), "not a directory");

    assertEquals(java.util.List.of(), HotSwapDirs.buildWatchingPaths(jar.toString()));
  }

  @Test
  void watchingPathUrlsMatchDiscoveredWatchingPaths() throws Exception {
    final var paths = HotSwapDirs.getWatchingPaths();
    final var urls = HotSwapDirs.buildWatchingPathsAsUrls();

    assertEquals(paths.size(), urls.length);
    for (int i = 0; i < paths.size(); i++) {
      assertEquals(paths.get(i).toUri().toURL(), urls[i]);
    }
  }

  @Test
  void buildDirsHandlesUnreadableOrVanishedDirectoryListings() throws Exception {
    final var classFile = Files.writeString(tempDir.resolve("Service.class"), "compiled");
    final var watchingDirs = new HashSet<String>();

    HotSwapDirs.buildDirs(classFile.toFile(), watchingDirs);

    assertTrue(watchingDirs.isEmpty());
  }
}
