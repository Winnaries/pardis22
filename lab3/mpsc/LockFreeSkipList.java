package mpsc; 

import java.util.concurrent.atomic.AtomicMarkableReference;
import java.util.Random;

public final class LockFreeSkipList<T> {
	/* Number of levels */
	private static final int MAX_LEVEL = 16;

	/* RNG for randomLevel() function */
	/* Random is thread safe! :) */
	private static final Random rng = new Random();

	private final Node<T> head = new Node<T>(Integer.MIN_VALUE);
	private final Node<T> tail = new Node<T>(Integer.MAX_VALUE);

	public LockFreeSkipListRecordBook<T> book = new LockFreeSkipListRecordBook<T>();

	public LockFreeSkipList() {
		for (int i = 0; i < head.next.length; i++) {
			head.next[i] = new AtomicMarkableReference<LockFreeSkipList.Node<T>>(tail, false);
		}
	}

	private static final class Node<T> {
		final int key;
		final AtomicMarkableReference<Node<T>>[] next;
		private final int topLevel;

		@SuppressWarnings("unchecked")
		public Node(int key) {
			this.key = key;
			next = (AtomicMarkableReference<Node<T>>[]) new AtomicMarkableReference[MAX_LEVEL + 1];
			for (int i = 0; i < next.length; i++) {
				next[i] = new AtomicMarkableReference<Node<T>>(null, false);
			}
			topLevel = MAX_LEVEL;
		}

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
		long start = System.nanoTime();

		int topLevel = randomLevel();
		int bottomLevel = 0;
		Node<T>[] preds = (Node<T>[]) new Node[MAX_LEVEL + 1];
		Node<T>[] succs = (Node<T>[]) new Node[MAX_LEVEL + 1];

		while (true) {
			// LINEARIZED: If found existing node,
			// it will eventually return false.
			boolean found = find(x, preds, succs);
			if (found) {
				book.record(1, x, false);
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

			if (!pred.next[bottomLevel].compareAndSet(succ, newNode, false, false))
				continue;
			book.record(1, x, true, start, "@" + topLevel + " " + pred.key);

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
		long start = System.nanoTime();

		int bottomLevel = 0;
		Node<T>[] preds = (Node<T>[]) new Node[MAX_LEVEL + 1];
		Node<T>[] succs = (Node<T>[]) new Node[MAX_LEVEL + 1];
		Node<T> succ;
		while (true) {
			boolean found = find(x, preds, succs);
			if (!found) {
				book.record(2, x, false, start, "non-existent");
				return false;
			}

			Node<T> nodeToRemove = succs[bottomLevel];
			for (int level = nodeToRemove.topLevel; level >= bottomLevel + 1; level--) {
				boolean[] marked = { false };
				succ = nodeToRemove.next[level].get(marked);

				while (!marked[0]) {
					nodeToRemove.next[level].compareAndSet(succ, succ, false, true);

					// NOTE: If delete success, proceed because marked must == false
					// otherwise, other process must have add new node after this,
					// try again one more time. Another case is when another overlapping
					// process mark the link before this process. 
					succ = nodeToRemove.next[level].get(marked);
				}
			}

			boolean[] marked = { false };
			succ = nodeToRemove.next[bottomLevel].get(marked);

			// NOTE: The following execution is possible. 
			//  A: REMOVE(31) - true
			//  A: ADD(31) - true
			//  B: REMOVE(31) - fail
			// REASON: Assume that the remove by A and B overlap. B get stuck here, 
			// during the execution. A proceed to successfully remove the node. 
			// Then, A again add the same node. B miss the update. In this case, 
			// it could be said that the linearization point of B'remove is immediately
			// after A successful remove, but before A'add. 
			while (true) {
				// LINEARIZE: The CAS operation on the next line, if success, 
				// marks the linearization point of the function. On the other hand, 
				// if it fails because the expected mark isn't matched, the other thread
				// must have already remove it before this thread. In which case, the
				// the linearization point of the function is when the other process successfully
				// remove it before this process. Otherwise, the next field is changed leading to a retry. 
				boolean iMarkedIt = nodeToRemove.next[bottomLevel].compareAndSet(succ, succ, false, true);
				succ = succs[bottomLevel].next[bottomLevel].get(marked);
				if (iMarkedIt) {
					// NOTE: This process was able to successfully
					// delete the node by itself. 
					book.record(2, x, true, start, "by itself");
					find(x, preds, succs);
					return true;
				} else if (marked[0]) {
					// NOTE: Other node get ahead of this node, 
					// and proceed to remove the node first. 
					book.record(2, x, false, start, "by others");
					return false;
				}
			}
		}
	}

	private boolean find(T x, Node<T>[] preds, Node<T>[] succs) {
		int bottomLevel = 0;
		int key = x.hashCode();
		boolean[] marked = { false };

		boolean snip;
		Node<T> pred = null;
		Node<T> curr = null;
		Node<T> succ = null;
		retry: while (true) {
			pred = head;
			for (int level = MAX_LEVEL; level >= bottomLevel; level--) {
				curr = pred.next[level].getReference();
				while (true) {
					succ = curr.next[level].get(marked);
					while (marked[0]) {
						snip = pred.next[level].compareAndSet(curr, succ, false, false);
						if (!snip)
							continue retry;
						curr = pred.next[level].getReference();
						succ = curr.next[level].get(marked);
					}

					if (curr.key < key) {
						pred = curr;
						curr = succ;
					} else {
						break;
					}
				}

				preds[level] = pred;
				succs[level] = curr;
			}
			return (curr.key == key);
		}
	}

	public boolean contains(T x) {
		long start = System.nanoTime();

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
			curr = pred.next[level].getReference();

			// PROBLEM: In some rare cases, the following can happens
			// * Initial state, 21 - 24 - 27
			// - Remove(24) - true
			// - Add(26) - true
			// - Contains(26) - false
			// REASON: Contain's pred get stuck at 24 before it reaches
			// bottom round. Then, another node remove 24 making it a ghost.
			// Then, another node add 26 after 21 instead, making it
			// invisible to the threads that call contains().
			// This signal that the function can sometimes
			// be linearized because of other threads. In this case,
			// when the other thread remove the node pred is pointing to.

			// TL;DR: In a rare case, this function can become linearized, 
			// when a node referenced by `pred` variable up to the minimum 
			// node with value more than X are deleted. The reason is simply 
			// because there is no way for the process to see changes anymore. 
			while (true) {
				// LINEARIZE: If the next current happens
				// to be the target node and unmarked, the
				// the function linearize. And only if at the bottom level.
				succ = curr.next[level].get(marked);

				// NOTE: Need to additionally check the node,
				// because there could be only two nodes in the level:
				// HEAD and TAIL. The node could be null.
				if (!found && !marked[0]) {
					if (curr.key == v) {
						book.record(0, x, true, start, "found @" + level + " " + pred.key);
						found = true;
					} else if (level == bottomLevel && curr.key > v) {
						book.record(0, x, false, start, "not found @" + level + " " + pred.key + " -> " + curr.key);
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
		}
		
		return (curr.key == v);
	}
}
