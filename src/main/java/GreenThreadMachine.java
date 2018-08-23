import java.util.ArrayList;
import java.util.Collections;
import java.util.ConcurrentModificationException;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * A "virtual machine" that executes any number of "green threads". A green
 * thread is simply a {@code Callable<Boolean>} whose {@code call()} method does
 * a bit of work and then yields back to the machine by returning {@code true},
 * which means "I'm not done, call me again"; eventually it returns
 * {@code false} and is removed from the machine's collection of green threads.
 * <p>
 * The Green Thread Machine maintains a fixed pool of native threads and
 * balances their workload. When a green thread is done, the native thread that
 * ran it inspects other native threads to find one whose collection of green
 * threads is sufficiently larger than the current one's to make rebalancing
 * worthwhile. If it finds one, it "steals" one of its green threads so its
 * future execution happens on the current thread.
 * <p>
 * <strong>Note</strong>: All implementation code below is just a suggestion to
 * get you started. The contract is specified by the two public methods
 * {@code start()} and {@code addGreenThread()}.
 */

public class GreenThreadMachine {

	private final Worker[] workers;
	private final List<GreenThread> greenThreadsMaster = newGreenThreadList();
	private Map<String, List<GreenThread>> workerListMap = null;
	private static int numNativeThreads = 3;
	private static int greenThreadRatio = 3;

	/**
	 * @param numNativeThreads size of the Green Thread Machine's internal native
	 *                         thread pool.
	 */
	public GreenThreadMachine(int numNativeThreads) {

		this.workerListMap = new ConcurrentHashMap<String, List<GreenThread>>();
		this.workers = new Worker[numNativeThreads];
		for (int i = 0; i < numNativeThreads; i++) {
			this.workers[i] = new Worker();
		}
	}

	/** Starts the Green Thread Machine. */
	public void start() {
		System.out.println("Starting size of green threads list is:" + greenThreadsMaster.size());
		ExecutorService executorService = Executors.newFixedThreadPool(numNativeThreads);
		for (int i = 0; i < numNativeThreads; i++) {
			executorService.execute(workers[i]);
			System.out.println("Executed native worker thread " + i);
		}
	}

	/**
	 * Submits a new green thread to be executed on the Green Thread Machine.
	 *
	 * @param t is the green thread instance to add to machine
	 */
	public void addGreenThread(GreenThread t) {
		try {
			greenThreadsMaster.add(t);
		} catch (UnsupportedOperationException e) {
			e.printStackTrace();
		}
	}

	/**
	 * executes native threads that will run the green threads from the master list
	 */
	private class Worker implements Runnable {
		// create new list for allocating work inside current thread
		List<GreenThread> workerGTList = newGreenThreadList();
		List<GreenThread> toRemoveFromMaster = newGreenThreadList();

		@Override
		public void run() {
			while (true) {
				// for (int iloop=0; iloop < 4; iloop++) {

				try {
					synchronized (greenThreadsMaster) {
						for (int i = 0; i < greenThreadRatio; i++) { // add work to this thread based on number of
																		// threads
							if (greenThreadsMaster.size() > 0) {
								GreenThread gt = greenThreadsMaster.get(i);
								workerGTList.add(gt);
								toRemoveFromMaster.add(gt);
							}
						}
						greenThreadsMaster.removeAll(toRemoveFromMaster); // to isolate removes from iterations and adds
																			// to improve thread safety
					}
				} catch (ConcurrentModificationException e) {
					e.printStackTrace();
				}
				System.out.println(
						Thread.currentThread().getName() + " greenThreads work size is " + workerGTList.size());
				System.out.println("greenThreadsMaster size now " + greenThreadsMaster.size());

				synchronized (workerGTList) {
					Iterator<GreenThread> workListIterator = workerGTList.iterator();
					while (workListIterator.hasNext()) {
						GreenThread t = workListIterator.next();
						if (!t.call()) {
							workListIterator.remove();
							synchronized (workerListMap) {
								workerListMap.put(Thread.currentThread().getName(), workerGTList);
							}

						}
					}
				}
				stealWork();
			}
		}
	}

	/** steals work from other native threads */
	private void stealWork() {
		String currThread = Thread.currentThread().getName();
		List<GreenThread> currThreadsList = workerListMap.get(currThread);
		int currGTCount = currThreadsList.size();
		synchronized (workerListMap) {
			Set<String> threadsKeys = workerListMap.keySet();
			System.out.println("Current set of keys for map " + threadsKeys);
			for (String eachkey : threadsKeys) {
				if (eachkey != currThread) {
					int eachCount = workerListMap.get(eachkey).size();
					System.out.println("Preparing to steal work, current thread " + currThread + " worker list size is "
							+ currGTCount + " and work size of target thread " + eachkey + " is " + eachCount);
					if (currGTCount <= eachCount) {
						// Steal green thread
						System.out.println(currThread + " logic triggered to steal from " + eachkey);
						List<GreenThread> currGTWorkerList = workerListMap.get(currThread);
						List<GreenThread> targetGTWorkerList = workerListMap.get(eachkey);
						// synchronized (targetGreenThrWorker) {
						int i = targetGTWorkerList.size() - 1;
						GreenThread gt = targetGTWorkerList.get(i);
						currGTWorkerList.add(gt);
						targetGTWorkerList.remove(gt);
						workerListMap.put(currThread, currGTWorkerList);
						workerListMap.put(eachkey, targetGTWorkerList);
						// }
						System.out.println(
								"Stole work from " + eachkey + " target list size now " + targetGTWorkerList.size());
					}
				}
			}
		}
	}

	private static List<GreenThread> newGreenThreadList() {
		List<GreenThread> list = new ArrayList<GreenThread>();
		List<GreenThread> returnlist = Collections.synchronizedList(list);
		return returnlist;
	}

	/** A green thread that can be executed by the Green Thread Machine. */
	public static class GreenThread implements Callable<Boolean> {

		/**
		 * @return {@code true} if the green thread wants to be called again,
		 *         {@code false} if it's done.
		 */
		public Boolean call() {
			// A Green Thread needs to do some work, so...
			System.out.println("Green Thread executed on native thread " + Thread.currentThread().getName());
			try {
				Thread.sleep(5);
			} catch (InterruptedException e) {

				e.printStackTrace();
			}
			double x = Math.random();
			if (x > .5) {
				return true; // wants to be called again
			} else {
				return false; // doesn't want to be called again
			}
		}

	}

	public static void main(String[] args) {
		GreenThreadMachine myGTM = new GreenThreadMachine(numNativeThreads);
		int numGreenThreads = greenThreadRatio * numNativeThreads;
		for (int i = 0; i < numGreenThreads; i++) {
			// add green thread to machine
			GreenThread t = new GreenThread();
			myGTM.addGreenThread(t);
		}
		// start green thread machine
		myGTM.start();
	}

}
