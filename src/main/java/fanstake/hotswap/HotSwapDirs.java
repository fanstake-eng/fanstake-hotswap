package fanstake.hotswap;

import java.io.File;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class HotSwapDirs {

  static final List<Path> watchingPaths = buildWatchingPaths();

  static final URL[] watchingPathsAsUrls = buildWatchingPathsAsUrls();

  public static List<Path> getWatchingPaths() {
    return watchingPaths;
  }

  public static URL[] getWatchingPathsAsUrls() {
    return watchingPathsAsUrls;
  }

  public static URL[] buildWatchingPathsAsUrls() {
    final var urlList = new ArrayList<URL>();
    for (Path path : watchingPaths) {
      try {
        urlList.add(path.toUri().toURL());
      } catch (MalformedURLException e) {
        throw new RuntimeException("Error converting path to URL: " + e.getMessage(), e);
      }
    }
    return urlList.toArray(URL[]::new);
  }

  private static List<Path> buildWatchingPaths() {
    final var watchingDirSet = new HashSet<String>();
    String[] classPathArray = System.getProperty("java.class.path").split(File.pathSeparator);
    for (String classPath : classPathArray) {
      buildDirs(new File(classPath.trim()), watchingDirSet);
    }

    final var dirList = new ArrayList<String>(watchingDirSet);
    Collections.sort(dirList);

    final var pathList = new ArrayList<Path>(dirList.size());
    for (String dir : dirList) {
      pathList.add(Paths.get(dir));
    }
    return pathList;
  }

  private static void buildDirs(File file, Set<String> watchingDirSet) {
    if (file.isDirectory() && !file.getName().contains("META-INF")) {
      watchingDirSet.add(file.getPath());
      for (final File f : file.listFiles()) {
        buildDirs(f, watchingDirSet);
      }
    }
  }

}
