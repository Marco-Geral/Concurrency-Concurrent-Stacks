import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class Main {

    private static final int NUM_OPERATIONS = 10000;  // Total push/pop operations to test
    private static final int[] THREAD_COUNTS = {1, 2, 4, 8, 16}; // Number of threads to test with

    public static void main(String[] args) throws Exception {
        System.out.println("Starting experiments...");

        for (int numThreads : THREAD_COUNTS) {
            System.out.println("\nTesting with " + numThreads + " threads:");
            System.out.println("Sequential/Blocking Stack:");
            runTest(new SequentialBlockingStack<>(), numThreads);

            System.out.println("Lock-Free Stack:");
            runTest(new LockFreeStack<>(), numThreads);

            System.out.println("Elimination Backoff Stack:");
            runTest(new EliminationBackoffStack<>(), numThreads);
        }
    }

    // Method to run the experiment for a given stack implementation and number of threads
    private static <T> void runTest(final Stack<T> stack, int numThreads) throws InterruptedException {
        ExecutorService executor = Executors.newFixedThreadPool(numThreads);
        long startTime = System.nanoTime();

        // Phase 1: Perform push operations in parallel
        for (int i = 0; i < numThreads; i++) {
            executor.submit(() -> {
                for (int j = 0; j < NUM_OPERATIONS / numThreads; j++) {
                    try {
                        stack.push((T) Integer.valueOf(j));
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            });
        }

        // Wait for all threads to finish pushing
        executor.shutdown();
        executor.awaitTermination(10, TimeUnit.MINUTES);

        // Reset the executor for the pop phase
        executor = Executors.newFixedThreadPool(numThreads);

        // Phase 2: Perform pop operations in parallel
        for (int i = 0; i < numThreads; i++) {
            executor.submit(() -> {
                for (int j = 0; j < NUM_OPERATIONS / numThreads; j++) {
                    try {
                        stack.pop();
                    } catch (Exception e) {
                        // Catch and log any stack underflow or contention issues
                        System.out.println("Error during pop: " + e.getMessage());
                    }
                }
            });
        }

        executor.shutdown();
        executor.awaitTermination(10, TimeUnit.MINUTES);
        long endTime = System.nanoTime();

        // Calculate and print the time taken for this run
        long timeTaken = (endTime - startTime) / 1_000_000; // Convert to milliseconds
        System.out.println("Time taken: " + timeTaken + " ms");
    }

    // Simple Stack interface for uniformity across implementations
    interface Stack<T> {
        void push(T value) throws Exception;
        T pop() throws Exception;
    }
}
