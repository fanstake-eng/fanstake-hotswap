package fanstake.hotswap;

public interface Lifecycle {

  /**
   * Start the service and any associated children processes, and return a
   * Stop instance.
   *
   * @param parentService An optional context object provided by the parent
   *                      process. May be null if there is no parent process.
   */
  public void start(Object parentService);

  /** Stop the service and any associated children processes */
  public void stop(Object parentService);

}
