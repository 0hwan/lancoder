package org.lancoder.common.pool;

import org.lancoder.common.RunnableService;

public abstract class Pooler<T> extends RunnableService implements Cleanable {

	Object monitor = new Object();

	/**
	 * Currently processed element
	 */
	protected T task;
	/**
	 * Timestamp in unix msec of last activity
	 */
	private long lastActivity;

	/**
	 * The thread used by the pooler resource
	 */
	private Thread thread;

	/**
	 * The pool to notify on task completion
	 */
	private PoolerListener pool;

	/**
	 * Constructor of a base pooler resource. Sets last activity time to current time.
	 * 
	 */
	public Pooler() {
		this.lastActivity = System.currentTimeMillis();
	}

	/**
	 * Set the parent pooler listening to this ressource.
	 * 
	 * @param pool
	 *            The parent pool waiting for this resource to perform an action
	 */
	public void setPoolerListener(PoolerListener pool) {
		this.pool = pool;
	}

	/**
	 * Allow pooler to know it's execution thread.
	 * 
	 * @param thread
	 *            The execution thread of the resource
	 */
	public void setThread(Thread thread) {
		this.thread = thread;
	}

	public Thread getThread() {
		return thread;
	}

	/**
	 * Decide if the pooler should be closed. Super implementation provides a time based decision from last activity.
	 * 
	 * @return True if current pooler should be closed
	 */
	private boolean expired() {
		return System.currentTimeMillis() - this.lastActivity > CLEAN_DELAY_MSEC;
	}

	@Override
	public boolean shouldClean() {
		return !isActive() && expired();
	}

	/**
	 * Stop and clean resource if necessary.
	 */
	public final boolean clean() {
		boolean closed = false;
		if (shouldClean()) {
			this.stop();
			closed = true;
		}
		return closed;
	}

	/**
	 * Get poolable element currently processed by pooler.
	 * 
	 * @return The element
	 */
	public synchronized T getPoolable() {
		return this.task;
	}

	/**
	 * Returns the state of the worker.
	 * 
	 * @return True is pooler is busy
	 */
	public synchronized boolean isActive() {
		return this.task != null;
	}

	/**
	 * Check if worker is empty
	 * 
	 * @return True if empty
	 */
	public synchronized boolean isFree() {
		return !isActive();
	}

	/**
	 * Add a task to the pooler.
	 * 
	 * @param request
	 *            The task to complete
	 */
	public synchronized boolean handle(T request) {
		boolean handled = false;
		synchronized (monitor) {
			if (!isActive()) {
				this.task = request;
				monitor.notifyAll();
				handled = true;
			}
		}
		return handled;
	}

	@Override
	public void stop() {
		super.stop();
		this.thread.interrupt();
	}

	public void cancelTask(Object task) {
		if (this.task != null && this.task.equals(task)) {
			throw new UnsupportedOperationException("Task cancellation is not supported for "
					+ this.getClass().getSimpleName() + " !");
		}
	}

	protected abstract void start();

	/**
	 * While the pooler thread is running start tasks when requests gets elements.
	 */
	@Override
	public final void run() {
		try {
			while (!close) {
				synchronized (monitor) {
					monitor.wait();
					start(); // pooler thread is now busy and blocks here
					this.lastActivity = System.currentTimeMillis();
					this.task = null;
					pool.completed();
				}
			}
		} catch (InterruptedException e) {
		}

	}
}
