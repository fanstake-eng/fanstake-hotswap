package fanstake.testapp;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

import fanstake.hotswap.Lifecycle;
import fanstake.testapp.parent.DoSomething;
import fanstake.testapp.parent.Parent;

public class Server implements Lifecycle {

  private static final Logger log = Logger.getLogger(Server.class.getName());

  private final Thread thread;

  private final CountDownLatch startupLatch = new CountDownLatch(1);
  private final CountDownLatch shutdownLatch = new CountDownLatch(1);

  private Parent parent;

  public Server() {
    log.info("Starting server");
    thread = new Thread(() -> {
      run();
    }, "Server Main Thread");
  }

  private void run() {
    parent = new Parent();
    startupLatch.countDown();
    log.info("Server thread started");

    while (!Thread.currentThread().isInterrupted()) {
      parent.doSomething();
      try {
        if (shutdownLatch.await(2000, TimeUnit.MILLISECONDS)) {
          break;
        }
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
      }
    }
  }

  @Override
  public void start(Object parentService) {
    thread.start();
    try {
      startupLatch.await();
      log.info("Server started");
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }
  }

  @Override
  public void stop(Object parentService) {
    log.info("Stopping server");
    thread.interrupt();
    if (shutdownLatch != null) {
      shutdownLatch.countDown();
    }
    try {
      thread.join();
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }
    log.info("Server stopped");
  }

  public void setChild(DoSomething child) {
    parent.setChild(child);
  }
}
