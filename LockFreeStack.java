import java.util.concurrent.atomic.AtomicReference;


public class LockFreeStack<T> implements Main.Stack<T> {
    private static class Node<T> {
        final T value;
        Node<T> next;

        Node(T value) {
            this.value = value;
            this.next = null;
        }
    }

    private final AtomicReference<Node<T>> top = new AtomicReference<>(null);

    @Override
    public void push(T value) {
        Node<T> newNode = new Node<>(value);
        while (true) {
            Node<T> currentTop = top.get();
            newNode.next = currentTop;
            if (top.compareAndSet(currentTop, newNode)) {
                break; // Successfully pushed the node
            }
        }
    }

    @Override
    public T pop() throws Exception {
        while (true) {
            Node<T> currentTop = top.get();
            if (currentTop == null) {
                throw new Exception("Stack is empty");
            }
            Node<T> newTop = currentTop.next;
            if (top.compareAndSet(currentTop, newTop)) {
                return currentTop.value; // Successfully popped the node
            }
        }
    }
}
