package com.wokdsem.kommander;

class RunnableAction<T> implements Runnable {

	private enum RunnableState {
		NEW,
		RUNNING,
		COMPLETED,
		CANCELED
	}

	private final Deliverer deliverer;
	private Thread executor;
	private RunnableState state;
	private Action<T> action;
	private Response.OnError onError;
	private Response.OnCompleted<T> onCompleted;

	RunnableAction(RunnableActionBundle<T> bundle) {
		this.deliverer = bundle.deliverer;
		this.action = bundle.action;
		this.onError = bundle.onError;
		this.onCompleted = bundle.onCompleted;
		this.state = RunnableState.NEW;
	}

	@Override
	public final void run() {
		Action<T> runnableAction;
		synchronized (this) {
			if (state != RunnableState.NEW) {
				return;
			}
			runnableAction = action;
			executor = Thread.currentThread();
			state = RunnableState.RUNNING;
		}
		try {
			T result = runnableAction.act();
			deliverResponse(result);
		} catch (Throwable e) {
			deliverError(e);
		} finally {
			synchronized (this) {
				action = null;
				executor = null;
			}
		}
	}

	private void deliverResponse(final T response) {
		deliverer.deliver(new Runnable() {
			@Override
			public void run() {
				synchronized (RunnableAction.this) {
					if (state != RunnableState.CANCELED) {
						onError = null;
						if (onCompleted != null) {
							onCompleted.onCompleted(response);
							onCompleted = null;
						}
						afterExecuted();
					}
				}
			}
		});
	}

	private void deliverError(final Throwable e) {
		deliverer.deliver(new Runnable() {
			@Override
			public void run() {
				synchronized (RunnableAction.this) {
					if (state != RunnableState.CANCELED) {
						onCompleted = null;
						if (onError != null) {
							onError.onError(e);
							onError = null;
						}
						afterExecuted();
					}
				}
			}
		});
	}

	private void afterExecuted() {
		state = RunnableState.COMPLETED;
	}

	synchronized void cancel() {
		if (state == RunnableState.NEW || state == RunnableState.RUNNING) {
			if (executor != null) {
				executor.interrupt();
			}
			action = null;
			onCompleted = null;
			onError = null;
			state = RunnableState.CANCELED;
		}
	}

}
