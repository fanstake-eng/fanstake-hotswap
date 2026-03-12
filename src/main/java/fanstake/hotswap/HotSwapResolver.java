package fanstake.hotswap;

import java.util.ArrayList;
import java.util.Collections;
import java.util.logging.Logger;

public class HotSwapResolver {
  private static final Logger log = Logger.getLogger(HotSwapResolver.class.getName());

  protected String[] systemClassPrefix = { //
      "java.", "javax.", "sun.", "com.sun.", };

  protected String[] hotSwapClassPrefix = new String[] {};

  protected String[] excludedHotSwapPrefixes = new String[] { //
      "fanstake.hotswap." };

  public HotSwapResolver() {
  }

  public boolean isSystemClass(String className) {
    for (final String s : systemClassPrefix) {
      if (className.startsWith(s)) {
        return true;
      }
    }
    return false;
  }

  public boolean isHotswapFile(String classFilename) {
    if (hotSwapClassPrefix.length == 0) {
      return false;
    }

    for (String hotSwapClassPrefix1 : hotSwapClassPrefix) {
      int pos = classFilename.indexOf(hotSwapClassPrefix1.replace('.', '/'));
      if (pos != -1) {
        var className = classFilename.substring(pos, classFilename.length() - 6).replace('/', '.');
        log.fine("className %s match %s ?".formatted(className, hotSwapClassPrefix1));
        if (isHotSwapClass(className)) {
          return true;
        }
      }
    }
    return false;
  }

  public boolean isHotSwapClass(String className) {
    // exclusions
    for (final String excludedPrefix : excludedHotSwapPrefixes) {
      if (className.startsWith(excludedPrefix)) {
        return false;
      }
    }
    // inclusions
    for (final String s : hotSwapClassPrefix) {
      if (className.startsWith(s)) {
        return true;
      }
    }
    // default: do not hot swap
    return false;
  }

  public synchronized void addSystemClassPrefix(String... prefixes) {
    final var list = new ArrayList<String>();
    Collections.addAll(list, systemClassPrefix);
    for (final String prefix : prefixes) {
      list.add(prefix.trim());
    }
    systemClassPrefix = list.toArray(String[]::new);
  }

  public synchronized void addHotSwapClassPrefix(String... prefixes) {
    final var list = new ArrayList<String>();
    Collections.addAll(list, hotSwapClassPrefix);
    for (final String prefix : prefixes) {
      list.add(prefix.trim());
    }
    hotSwapClassPrefix = list.toArray(String[]::new);
  }

  public synchronized void addExcludedHotSwapPrefix(String... prefixs) {
    final var list = new ArrayList<String>();
    Collections.addAll(list, excludedHotSwapPrefixes);
    for (final String prefix : prefixs) {
      list.add(prefix.trim());
    }
    excludedHotSwapPrefixes = list.toArray(String[]::new);
  }
}
