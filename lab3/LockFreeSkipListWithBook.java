import java.util.concurrent.atomic.AtomicMarkableReference;
import java.util.concurrent.locks.ReentrantLock;
import java.util.Random;

public final class LockFreeSkipListWithBook<T> {
	/* Number of levels */
	private static final int MAX_LEVEL = 16;

	/* RNG for randomLevel() function */
	/* Random is thread safe! :) */
	private static final Random rng = new Random();

	private final Node<T> head = new Node<T>(Integer.MIN_VALUE);
	private final Node<T> tail = new Node<T>(Integer.MAX_VALUE);

	public ReentrantLock bookMutex = new ReentrantLock();
	public LockFreeSkipListRecordBook<T> book = new LockFreeSkipListRecordBook<T>();

	public LockFreeSkipListWithBook() {
		for (int i = 0; i < head.next.length; i++) {
			head.next[i] = new AtomicMarkableReference<LockFreeSkipListWithBook.Node<T>>(tail, false);
		}
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
			// LINEARIZED: If found existing node,
			// it will eventually return false.
			bookMutex.lock();
			boolean found = find(x, preds, succs);
			if (found)
				book.record(1, x, false);
			bookMutex.unlock();

			if (found) {
				return false;
			}

			Node<T> newNode = new Node<T>(x, topLevel);

			for (int level = bottomLevel; level <= topLevel; level++) {
				Node<T> succ = succs[level];
				newNode.next[level].set(succ, false);
			}

			Node<T> pred = preds[bottomLevel];
			Node<T> succ = succs[bottomLevel];

			// NOTE: If other thread gets ahead of this thread
			// need to retry since the link is not updated.
			// LINEARIZED: Become linearized if success as new node
			// is successfully added to the abstract set.
			bookMutex.lock();

			try {
				if (!pred.next[bottomLevel].compareAndSet(succ, newNode, false, false))
					continue;
				book.record(1, x, true, "@" + topLevel + " " + pred.key);
			} finally {
				bookMutex.unlock();
			}

			// NOTE: Progressively creating the link
			// from the second level to top.
			for (int level = bottomLevel + 1; level <= topLevel; level++) {
				while (true) {
					pred = preds[level];
					succ = succs[level];

					if (pred.next[level].compareAndSet(succ, newNode, false, false))
						break;

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
						book.record(2, x, false, "not existed");
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
			int count = 0;
			while (true) {
				// POTENTIALLY
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
							book.record(2, x, true, "own " + count);
						}

						return true;
					} else if (marked[0]) {
						// return false because there is a process
						// that already delete this node before us

						// LINEARIZED:
						// The target node is removed by the other thread
						// who was one-step ahead of me.
						if (book != null) {
							book.record(2, x, false, "other " + count);
						}

						return false;
					}
					// otherwise, a node is added after the target node,
					// and causes succ to change

					count++;
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
		Node<T> pred = head;
		Node<T> curr = null;
		Node<T> succ = null;

		boolean found = false;
		for (int level = MAX_LEVEL; level >= bottomLevel; level--) {
			// LINEARIZE: If the next node happens to be
			// the unmarked target node at the botom level.
			// The function is linearize, yet subsequent checks
			// is required, therefore the lock.
			bookMutex.lock(); 
			curr = pred.next[level].getReference();

			// PROBLEM: In some rare cases, the following can happens
			// * Initial state, 21 - 24 - 27
			// - Remove(24)	- true
			// - Add(26) 		- true
			// - Contains(26) - false
			// REASON: Contain's pred get stuck at 24 before it reache
			// bottom round. Then, another node remove 24 making it a ghost. 
			// Then, another node add 26 after 21 instead, making it 
			// invisible to the threads that call contains().
			// This signal that the function can sometimes 
			// be linearized because of other threads. In this case, 
			// when the other thread remove the node pred is pointing to. 
			while (true) {
				// LINEARIZE: If the next current happens
				// to be the target node and unmarked, the
				// the function linearize.
				// Only if at the bottom level.
				succ = curr.next[level].get(marked);

				// NOTE: Need to additionally check the node,
				// because there could be only two nodes in the level:
				// HEAD and TAIL. The node could be null.
				if (!found && !marked[0]) {
					if (curr.key == v) {
						book.record(0, x, true, "found @" + level + " " + pred.key);
						found = true;
					} else if (level == bottomLevel && curr.key > v) {
						book.record(0, x, false, "not found @" + level + " " + pred.key + " -> " + curr.key);
						found = true;
					}
				}

				while (marked[0]) {
					curr = succ;
					succ = curr.next[level].get(marked);
				}

				if (curr.key < v) {
					pred = curr;
					curr = succ;
				} else {
					break;
				}
			}
			
			bookMutex.unlock();
		}

		return (curr.key == v);
	}
}
