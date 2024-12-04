import java.util.Stack;
import java.util.concurrent.locks.ReentrantLock;


public class SequentialBlockingStack<T> implements Main.Stack<T> {
    private final Stack<T> stack;
    private final ReentrantLock lock;

    public SequentialBlockingStack() {
        stack = new Stack<>();
        lock = new ReentrantLock();
    }

    @Override
    public void push(T value) {
        lock.lock();
        try {
            stack.push(value);
        } finally {
            lock.unlock();
        }
    }

    @Override
    public T pop() throws Exception {
        lock.lock();
        try {
            if (stack.isEmpty()) {
                throw new Exception("Stack is empty");
            }
            return stack.pop();
        } finally {
            lock.unlock();
        }
    }
}
