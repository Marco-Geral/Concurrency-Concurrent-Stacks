import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

public class EliminationBackoffStack<T> implements Main.Stack<T> {
    private static class Node<T> {
        final T value;
        Node<T> next;

        Node(T value) {
            this.value = value;
            this.next = null;
        }
    }

    private final AtomicReference<Node<T>> top = new AtomicReference<>(null);
    private static final int ELIMINATION_ARRAY_SIZE = 8;
    private final EliminationArray<T> eliminationArray;

    public EliminationBackoffStack() {
        eliminationArray = new EliminationArray<>(ELIMINATION_ARRAY_SIZE);
    }

    @Override
    public void push(T value) throws Exception {
        Node<T> newNode = new Node<>(value);
        int backoff = 1;
        while (true) {
            if (tryPush(newNode)) {
                return;  // Successfully pushed the node
            } else {
                try {
                    // Attempt elimination before backing off
                    T otherValue = eliminationArray.visit(value, backoff);
                    if (otherValue == null) {
                        return;  // Successfully exchanged with a pop
                    }
                } catch (TimeoutException e) {
                    // Backoff and retry
                    backoff = backoff * 2;
                    if (backoff > 1024) {
                        throw new Exception("Push operation failed due to high contention");
                    }
                    Thread.sleep(backoff);  // Delay before retrying
                }
            }
        }
    }

    private boolean tryPush(Node<T> newNode) {
        Node<T> currentTop = top.get();
        newNode.next = currentTop;
        return top.compareAndSet(currentTop, newNode);
    }

    @Override
    public T pop() throws Exception {
        int backoff = 1;
        while (true) {
            Node<T> currentTop = top.get();
            if (currentTop != null) {
                Node<T> newTop = currentTop.next;
                if (top.compareAndSet(currentTop, newTop)) {
                    return currentTop.value;
                }
            } else {
                try {
                    // Try to eliminate pop
                    T value = eliminationArray.visit(null, backoff);
                    if (value != null) {
                        return value;  // Successfully exchanged with a push
                    }
                } catch (TimeoutException e) {
                    backoff = backoff * 2;
                    if (backoff > 1024) {
                        throw new Exception("Pop operation failed due to high contention");
                    }
                    Thread.sleep(backoff);  // Delay before retrying
                }
            }
        }
    }

    // Helper class for Elimination Array
    class EliminationArray<T> {
        private static final int DURATION = 10;  // In milliseconds
        private final LockFreeExchanger<T>[] exchanger;

        public EliminationArray(int capacity) {
            exchanger = (LockFreeExchanger<T>[]) new LockFreeExchanger[capacity];
            for (int i = 0; i < capacity; i++) {
                exchanger[i] = new LockFreeExchanger<>();
            }
        }

        public T visit(T value, int range) throws TimeoutException, InterruptedException {
            int slot = ThreadLocalRandom.current().nextInt(range);
            return exchanger[slot].exchange(value, DURATION, TimeUnit.MILLISECONDS);
        }
    }

    // LockFreeExchanger with Timeout
    class LockFreeExchanger<T> {
        private final AtomicReference<T> slot = new AtomicReference<>(null);

        public T exchange(T value, long timeout, TimeUnit unit) throws TimeoutException, InterruptedException {
            long nanos = unit.toNanos(timeout);
            long timeBound = System.nanoTime() + nanos;
            T currentValue = slot.get();
            if (slot.compareAndSet(currentValue, value)) {
                while (System.nanoTime() < timeBound) {
                    T otherValue = slot.get();
                    if (otherValue != null) {
                        slot.set(null);  // Reset after exchange
                        return otherValue;
                    }
                }
                throw new TimeoutException("Exchange timed out");
            } else {
                return slot.get();  // Successful exchange
            }
        }
    }
}
