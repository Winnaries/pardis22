import java.util.concurrent.atomic.AtomicMarkableReference;
import java.util.concurrent.locks.ReentrantLock;
import java.util.Random;

public final class LockFreeSkipList<T> {
	/* Number of levels */
	private static final int MAX_LEVEL = 16;

	/* RNG for randomLevel() function */
	/* Random is thread safe! :) */
	private static final Random rng = new Random();

	private final Node<T> head = new Node<T>(Integer.MIN_VALUE);
	private final Node<T> tail = new Node<T>(Integer.MAX_VALUE);

	public ReentrantLock bookMutex = new ReentrantLock();
	public LockFreeSkipListRecordBook<T> book;

	public LockFreeSkipList() {
		for (int i = 0; i < head.next.length; i++) {
			head.next[i] = new AtomicMarkableReference<LockFreeSkipList.Node<T>>(tail, false);
		}
	}

	public boolean recordOps(LockFreeSkipListRecordBook<T> book) {
		if (this.book != null)
			return false;
		this.book = book;
		return true;
	}

	private static final class Node<T> {
		// key of this node, ordered
		final int key;

		// next node of each level.
		final AtomicMarkableReference<Node<T>>[] next;

		// top level of this node, not the skiplist.
		private final int topLevel;

		// just create a new node, and chosen level of references
		@SuppressWarnings("unchecked")
		public Node(int key) {
			this.key = key;
			next = (AtomicMarkableReference<Node<T>>[]) new AtomicMarkableReference[MAX_LEVEL + 1];
			for (int i = 0; i < next.length; i++) {
				next[i] = new AtomicMarkableReference<Node<T>>(null, false);
			}
			topLevel = MAX_LEVEL;
		}

		// similar to the previous one, but height specified
		@SuppressWarnings("unchecked")
		public Node(T x, int height) {
			// value = x;
			key = x.hashCode();
			next = (AtomicMarkableReference<Node<T>>[]) new AtomicMarkableReference[height + 1];
			for (int i = 0; i < next.length; i++) {
				next[i] = new AtomicMarkableReference<Node<T>>(null, false);
			}
			topLevel = height;
		}
	}

	/**
	 * very clever solution to level random
	 * ---
	 * Returns a level between 0 to MAX_LEVEL,
	 * P[randomLevel() = x] = 1/2^(x+1), for x < MAX_LEVEL.
	 */
	private static int randomLevel() {
		int r = rng.nextInt();
		int level = 0;
		r &= (1 << MAX_LEVEL) - 1;
		while ((r & 1) != 0) {
			r >>>= 1;
			level++;
		}
		return level;
	}

	@SuppressWarnings("unchecked")
	public boolean add(T x) {
		int topLevel = randomLevel();
		int bottomLevel = 0;
		Node<T>[] preds = (Node<T>[]) new Node[MAX_LEVEL + 1];
		Node<T>[] succs = (Node<T>[]) new Node[MAX_LEVEL + 1];
		while (true) {
			if (book != null) {
				bookMutex.lock();
			}

			try {
				boolean found = find(x, preds, succs);
				if (found) {
					// LINEARIZED:
					// Basically found an existing node with same hash
					if (book != null) {
						book.record(1, x, false);
					}

					return false;
				}
			} finally {
				if (book != null) bookMutex.unlock();
			}

			// actual add block
			// allocate a new node in memory
			Node<T> newNode = new Node<T>(x, topLevel);

			// prefill the links up to the level
			for (int level = bottomLevel; level <= topLevel; level++) {
				Node<T> succ = succs[level];
				newNode.next[level].set(succ, false);
			}

			Node<T> pred = preds[bottomLevel];
			Node<T> succ = succs[bottomLevel];

			if (book != null)
				bookMutex.lock();

			try {
				if (!pred.next[bottomLevel].compareAndSet(succ, newNode, false, false)) {
					// someone get ahead of me in the bottom level,
					// gotta retry.
					continue;
				}

				// LINEARIZED:
				// After the bottom level have been successfully linked,
				// the node is added in the abstract set.
				if (book != null) {
					book.record(1, x, true);
				}
			} finally {
				if (book != null)
					bookMutex.unlock();
			}

			for (int level = bottomLevel + 1; level <= topLevel; level++) {
				while (true) {
					pred = preds[level];
					succ = succs[level];
					if (pred.next[level].compareAndSet(succ, newNode, false, false))
						// success setting the level
						break;

					// if the link is out-of-date, need to find again
					// do this for each level
					find(x, preds, succs);
				}
			}
			return true;
		}
	}

	@SuppressWarnings("unchecked")
	public boolean remove(T x) {
		int bottomLevel = 0;
		Node<T>[] preds = (Node<T>[]) new Node[MAX_LEVEL + 1];
		Node<T>[] succs = (Node<T>[]) new Node[MAX_LEVEL + 1];
		Node<T> succ;
		while (true) {
			if (book != null) {
				bookMutex.lock();
			}

			try {
				boolean found = find(x, preds, succs);
				if (!found) {
					// LINEARIZED:
					// Basically non-existent
					if (book != null) {
						book.record(2, x, false);
					}

					return false;
				}
			} finally {
				if (book != null)
					bookMutex.unlock();
			}

			// actual removal block
			Node<T> nodeToRemove = succs[bottomLevel];

			// delete from top to bottom link, except the bottom one
			for (int level = nodeToRemove.topLevel; level >= bottomLevel + 1; level--) {
				boolean[] marked = { false };
				succ = nodeToRemove.next[level].get(marked);

				// get the next unmarked node to be replaced with
				while (!marked[0]) {
					nodeToRemove.next[level].compareAndSet(succ, succ, false, true);

					// if delete success, proceed because marked must == false
					// otherwise, other process must have add new node after this,
					// try again one more time
					//
					// EXCEPTION: another node can also attempt to mark this link too.
					succ = nodeToRemove.next[level].get(marked);
				}
			}

			boolean[] marked = { false };
			succ = nodeToRemove.next[bottomLevel].get(marked);
			while (true) {
				// POTENTIALLY AHEAD
				if (book != null)
					bookMutex.lock();

				try {
					boolean iMarkedIt = nodeToRemove.next[bottomLevel].compareAndSet(succ, succ, false, true);
					succ = succs[bottomLevel].next[bottomLevel].get(marked);
					if (iMarkedIt) {
						// proceed to physically removing them?
						// not sure why do we need this?
						find(x, preds, succs);

						// LINEARIZED:
						// The target node is removed by the thread itself.
						if (book != null) {
							book.record(2, x, true);
						}

						return true;
					} else if (marked[0]) {
						// return false because there is a process
						// that already delete this node before us

						// LINEARIZED:
						// The target node is removed by the other thread
						// who was one-step ahead of me.
						if (book != null) {
							book.record(2, x, false);
						}

						return false;
					}
					// otherwise, a node is added after the target node,
					// and causes succ to change
				} finally {
					if (book != null)
						bookMutex.unlock();
				}
			}
		}
	}

	private boolean find(T x, Node<T>[] preds, Node<T>[] succs) {
		int bottomLevel = 0;
		int key = x.hashCode();
		boolean[] marked = { false };

		// is something get invalidated
		// mid-way through the function
		boolean snip;

		// [pred] - [curr] - [succ]
		Node<T> pred = null;
		Node<T> curr = null;
		Node<T> succ = null;
		retry: while (true) {

			// HEAD - [curr] - [succ]
			pred = head;

			// Loop from top to bottom levels
			for (int level = MAX_LEVEL; level >= bottomLevel; level--) {

				// HEAD - 1st - [succ]
				curr = pred.next[level].getReference();

				while (true) {

					// HEAD - 1st - opt.2nd
					succ = curr.next[level].get(marked);
					while (marked[0]) {

						// curr is marked for removal, try delete
						snip = pred.next[level].compareAndSet(curr, succ, false, false);
						if (!snip)
							continue retry;

						// new current = prev successor
						curr = pred.next[level].getReference();
						// successor = next successor
						succ = curr.next[level].get(marked);
					}

					// HEAD - 1st - X'th
					if (curr.key < key) {
						pred = curr;
						curr = succ;
					} else {
						break;
					}
				}

				// always point to a maximum node with value < v
				preds[level] = pred;

				// always point to a minimum node with value >= v
				succs[level] = curr;
			}
			return (curr.key == key);
		}
	}

	public boolean contains(T x) {
		int bottomLevel = 0;
		int v = x.hashCode();
		boolean[] marked = { false };
		Node<T> pred = head; // [0]-null-null
		Node<T> curr = null;
		Node<T> succ = null;

		// loop from the top to bottom level eg. 3, 2, 1, and 0
		for (int level = MAX_LEVEL; level >= bottomLevel; level--) {
			// [0]-[0.next]-null
			curr = pred.next[level].getReference();
			while (true) {

				// [0]-[0.next]-[0.next.next]
				succ = curr.next[level].get(marked);

				// while current successor is marked to be deleted,
				// move to the next one
				while (marked[0]) {
					curr = succ; /* Same as, curr.next[level].getReference() */
					succ = curr.next[level].get(marked);
				}

				// stop when moving to the next node,
				// make its value more than or equal to v

				// POTENTIAL LINEARIZATION
				boolean maybe = book != null && level == bottomLevel;
				if (maybe) {
					bookMutex.lock();
				}

				try {
					if (curr.key < v) {
						// NOT YET:
						// Continue to release the lock
						// and move on the next nodes
						pred = curr;
						curr = succ;
					} else {
						// LINEARIZED:
						// If at the bottom level, the cursor
						// will no longer move, hence the result is locked.
						if (book != null && level == bottomLevel) {
							book.record(0, x, curr.key == v);
						}

						break;
					}
				} finally {
					if (maybe) {
						bookMutex.unlock();
					}
				}
			}
		}
		return (curr.key == v);
	}
}
