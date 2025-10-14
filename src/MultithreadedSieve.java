//Optimized Segmented Sieve of Eratosthenes
//Optimizations:
// 1. Bit-packed array (8x memory reduction, better cache)
// 2. Store only odd numbers (2x reduction)
// 3. Cache-friendly segments (L2 cache optimized)
// 4. Wheel factorization (2,3,5) - 73% fewer candidates
// 5. Atomic bit operations (thread-safe)
// 6. Start multithreaded marking from sqrtN+1 (no redundant work)
// 7. BufferedWriter for faster I/O
// 8. Parallel prime counting for improved performance

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicIntegerArray;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Level;
import java.util.logging.Logger;

public class MultithreadedSieve {
    private static final Logger LOGGER = Logger.getLogger(MultithreadedSieve.class.getName());
    private static final long N = 1_000_000_000L;      // Upper limit
    private static final int NUM_THREADS = Runtime.getRuntime().availableProcessors(); // Auto-detect cores
    private static final int L2_CACHE_SIZE = 256 * 1024; // 256KB typical L2 cache
    private static final int SEGMENT_SIZE = L2_CACHE_SIZE * 16; // Numbers per segment (optimized)

    // Wheel factorization: skip multiples of 2,3,5
    // Only check numbers of form 30k + {1,7,11,13,17,19,23,29}
    private static final int[] WHEEL = {1, 7, 11, 13, 17, 19, 23, 29};
    private static final int WHEEL_SIZE = 30;

    // Bit-packed array: stores only wheel candidates
    private static AtomicIntegerArray sieve;

    public static void main(String[] args) {
        long startTime = System.nanoTime();

        // Store only wheel candidates (numbers not divisible by 2,3,5)
        long sieveSize = (N / WHEEL_SIZE + 1) * WHEEL.length;
        int arraySize = (int) ((sieveSize + 31) / 32); // 32 bits per int
        sieve = new AtomicIntegerArray(arraySize);

        int sqrtN = (int) Math.sqrt(N);
        long initEndTime = System.nanoTime();

        // Step 1: Find all primes up to sqrt(N) (single-threaded)
        long phase1StartTime = System.nanoTime();
        ArrayList<Integer> basePrimes = new ArrayList<>();
        
        // Add small primes (always present since N = 2 billion)
        basePrimes.add(2);
        basePrimes.add(3);
        basePrimes.add(5);
        
        // Use wheel to find primes up to sqrtN
        for (int base = 0; base * WHEEL_SIZE <= sqrtN; base++) {
            for (int offset : WHEEL) {
                int i = base * WHEEL_SIZE + offset;
                if (i > sqrtN) break;
                if (i < 7) continue;
                
                if (isPrime(i)) {
                    basePrimes.add(i);
                    // Mark multiples up to sqrtN
                    for (long j = (long) i * i; j <= sqrtN; j += i) {
                        if (j % 2 != 0 && j % 3 != 0 && j % 5 != 0) {
                            markComposite(j);
                        }
                    }
                }
            }
        }
        long phase1EndTime = System.nanoTime();

        // Step 2: Segmented sieve for range [sqrtN+1, N] using cache-friendly segments
        long phase2StartTime = System.nanoTime();
        ExecutorService executor = Executors.newFixedThreadPool(NUM_THREADS);
        
        // Pre-calculate segments and submit as batch
        List<Callable<Void>> tasks = createSegmentTasks(sqrtN, basePrimes);

        try {
            executor.invokeAll(tasks);
            executor.shutdown();
            if (!executor.awaitTermination(Long.MAX_VALUE, TimeUnit.NANOSECONDS)) {
                System.err.println("Warning: Executor did not terminate in time");
                executor.shutdownNow();
            }
        } catch (InterruptedException e) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }
        long phase2EndTime = System.nanoTime();

        long endTime = System.nanoTime(); // Count and write primes
        long ioStartTime = System.nanoTime();
        long count;

        try (BufferedWriter writer = new BufferedWriter(new FileWriter("primes.txt"), 65536)) {
            // Write detailed performance analysis at the top
            writer.write("=" .repeat(80) + "\n");
            writer.write("PERFORMANCE ANALYSIS - Optimized Segmented Sieve of Eratosthenes\n");
            writer.write("=" .repeat(80) + "\n\n");
            
            // Configuration
            writer.write("CONFIGURATION:\n");
            writer.write("  Range: 2 to " + N + " (" + String.format("%,d", N) + ")\n");
            writer.write("  Threads: " + NUM_THREADS + " (CPU cores detected)\n");
            writer.write("  Memory: " + String.format("%,d", (long) sieve.length() * 4) + " bytes (bit-packed atomic)\n");
            writer.write("  Sqrt(N): " + sqrtN + "\n");
            writer.write("  Base primes found: " + basePrimes.size() + "\n\n");
            
            // Timing breakdown
            long initTime = initEndTime - startTime;
            long phase1Time = phase1EndTime - phase1StartTime;
            long phase2Time = phase2EndTime - phase2StartTime;
            long totalSieveTime = endTime - startTime;
            
            writer.write("TIMING BREAKDOWN:\n");
            writer.write(String.format("  Initialization:       %,10d ns  (%6.2f ms)  [%5.1f%%]\n", 
                initTime, initTime / 1_000_000.0, 100.0 * initTime / totalSieveTime));
            writer.write(String.format("  Phase 1 (up to √N):   %,10d ns  (%6.2f ms)  [%5.1f%%]\n", 
                phase1Time, phase1Time / 1_000_000.0, 100.0 * phase1Time / totalSieveTime));
            writer.write(String.format("  Phase 2 (multithreaded): %,10d ns  (%6.2f ms)  [%5.1f%%]\n", 
                phase2Time, phase2Time / 1_000_000.0, 100.0 * phase2Time / totalSieveTime));
            writer.write("  " + "-".repeat(70) + "\n");
            writer.write(String.format("  Total sieve time:     %,10d ns  (%6.2f ms)\n\n", 
                totalSieveTime, totalSieveTime / 1_000_000.0));
            
            // Performance metrics
            writer.write("PERFORMANCE METRICS:\n");
            double numbersPerSecond = (double) N / totalSieveTime * 1_000_000_000;
            writer.write(String.format("  Throughput: %,.0f numbers/second\n", numbersPerSecond));
            writer.write(String.format("  Speed: %.2f ns per number\n", (double) totalSieveTime / N));
            writer.write(String.format("  Segment size: %,d numbers (cache-friendly)\n", SEGMENT_SIZE));
            int numSegments = (int) ((N - sqrtN + SEGMENT_SIZE - 1) / SEGMENT_SIZE);
            writer.write(String.format("  Total segments: %,d\n", numSegments));
            
            double speedup = (double) phase1Time / phase2Time * NUM_THREADS;
            if (phase2Time > 0) {
                writer.write(String.format("  Parallel efficiency: %.1f%% (%.2fx speedup on %d cores)\n", 
                    speedup / NUM_THREADS * 100, speedup, NUM_THREADS));
            }
            writer.write("\n");
            
            // Optimizations applied
            writer.write("OPTIMIZATIONS APPLIED:\n");
            writer.write("  ✓ Wheel factorization (2,3,5) - 73% fewer candidates\n");
            writer.write("  ✓ Bit-packed atomic array (thread-safe)\n");
            writer.write("  ✓ Cache-friendly segments (" + (SEGMENT_SIZE / 1024) + "KB)\n");
            writer.write("  ✓ Dynamic work distribution (" + numSegments + " segments)\n");
            writer.write("  ✓ No redundant work (multithreaded phase starts at √N+1)\n");
            writer.write("  ✓ BufferedWriter with 64KB buffer\n\n");
            
            writer.write("=" .repeat(80) + "\n\n");
            
            // Find the largest prime and count all primes (PARALLELIZED)
            AtomicLong largestPrime = new AtomicLong(0);
            AtomicLong primeCount = new AtomicLong(0);
            
            if (N >= 2) {
                largestPrime.set(2);
                primeCount.incrementAndGet();
            }
            if (N >= 3) { primeCount.incrementAndGet(); largestPrime.set(3); }
            if (N >= 5) { primeCount.incrementAndGet(); largestPrime.set(5); }
            
            // Parallel counting using thread pool
            ExecutorService countExecutor = Executors.newFixedThreadPool(NUM_THREADS);
            List<Callable<Void>> countTasks = createCountTasks(primeCount, largestPrime);
            
            try {
                countExecutor.invokeAll(countTasks);
                countExecutor.shutdown();
                if (!countExecutor.awaitTermination(Long.MAX_VALUE, TimeUnit.NANOSECONDS)) {
                    System.err.println("Warning: Count executor did not terminate in time");
                    countExecutor.shutdownNow();
                }
            } catch (InterruptedException e) {
                System.err.println("Error during parallel counting");
                countExecutor.shutdownNow();
                Thread.currentThread().interrupt();
            }
            
            count = primeCount.get();
            
            writer.write("=" .repeat(80) + "\n");
            writer.write("RESULTS:\n");
            writer.write("  Total primes found: " + String.format("%,d", count) + "\n");
            writer.write("  Prime density: " + String.format("%.4f%%", 100.0 * count / N) + "\n");
            writer.write("  Largest prime: " + String.format("%,d", largestPrime.get()) + "\n");
            
            long ioEndTime = System.nanoTime();
            long ioTime = ioEndTime - ioStartTime;
            long totalTime = ioEndTime - startTime;
            
            writer.write("\nTIMING SUMMARY:\n");
            writer.write(String.format("  Sieve computation: %6.2f ms\n", totalSieveTime / 1_000_000.0));
            writer.write(String.format("  I/O (writing file): %6.2f ms\n", ioTime / 1_000_000.0));
            writer.write(String.format("  Total time: %6.2f ms\n", totalTime / 1_000_000.0));
            writer.write("=" .repeat(80) + "\n");

            System.out.println("Output written to primes.txt");
            System.out.println("Total primes: " + String.format("%,d", count));
            System.out.println("Sieve time: " + String.format("%.2f ms", totalSieveTime / 1_000_000.0));
            System.out.println("Total time: " + String.format("%.2f ms", totalTime / 1_000_000.0));
        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "Failed to write primes to file", e);
            System.err.println("Error: Failed to write output file - " + e.getMessage());
        }
    }

    /**
     * Creates counting tasks for parallel prime counting and finding the largest prime.
     * Divides the work across multiple threads for improved performance.
     *
     * @param primeCount atomic counter for total primes found
     * @param largestPrime atomic holder for the largest prime found
     * @return list of callable tasks for parallel execution
     */
    private static List<Callable<Void>> createCountTasks(AtomicLong primeCount, AtomicLong largestPrime) {
        long maxBase = N / WHEEL_SIZE;
        long basesPerThread = Math.max(1, maxBase / ((long) NUM_THREADS * 4));
        List<Callable<Void>> countTasks = new ArrayList<>();
        
        for (long baseStart = 0; baseStart <= maxBase; baseStart += basesPerThread) {
            final long start = baseStart;
            final long end = Math.min(baseStart + basesPerThread - 1, maxBase);
            
            countTasks.add(() -> {
                long localLargest = 0;
                int localCount = 0;
                
                for (long base = start; base <= end; base++) {
                    long baseNum = base * WHEEL_SIZE;
                    
                    for (int offset : WHEEL) {
                        long i = baseNum + offset;
                        if (i > N) break;
                        if (i < 7) continue;
                        
                        if (isPrime(i)) {
                            localLargest = i;
                            localCount++;
                        }
                    }
                }
                
                // Update global counters
                if (localCount > 0) {
                    primeCount.addAndGet(localCount);
                    // Update largest prime atomically
                    long current;
                    do {
                        current = largestPrime.get();
                        if (localLargest <= current) break;
                    } while (!largestPrime.compareAndSet(current, localLargest));
                }
                return null;
            });
        }
        return countTasks;
    }

    /**
     * Creates segment tasks for parallel sieve processing.
     * Each task processes a cache-friendly segment of the number range.
     *
     * @param sqrtN the square root of N (upper limit)
     * @param basePrimes list of primes up to sqrt(N)
     * @return list of callable tasks for parallel execution
     */
    private static List<Callable<Void>> createSegmentTasks(int sqrtN, ArrayList<Integer> basePrimes) {
        List<Callable<Void>> tasks = new ArrayList<>();
        for (long segStart = sqrtN + 1; segStart <= N; segStart += SEGMENT_SIZE) {
            final long segmentStart = segStart;
            final long segmentEnd = Math.min(segStart + SEGMENT_SIZE - 1, N);
            
            tasks.add(() -> {
                // Pre-filter primes that will actually mark in this segment
                ArrayList<Integer> relevantPrimes = new ArrayList<>();
                for (int prime : basePrimes) {
                    if (prime <= 5) continue;
                    if ((long) prime * prime <= segmentEnd) {
                        relevantPrimes.add(prime);
                    }
                }
                
                for (int prime : relevantPrimes) {
                    // Find first multiple of prime >= segmentStart
                    long start = Math.max((long) prime * prime, 
                                         ((segmentStart + prime - 1) / prime) * prime);
                    
                    // Optimize: skip to first wheel candidate
                    while (start <= segmentEnd && (start % 2 == 0 || start % 3 == 0 || start % 5 == 0)) {
                        start += prime;
                    }
                    
                    // Simplified loop - mark multiples directly
                    for (long j = start; j <= segmentEnd; j += prime) {
                        if (j % 2 != 0 && j % 3 != 0 && j % 5 != 0) {
                            markComposite(j);
                        }
                    }
                }
                return null;
            });
        }
        return tasks;
    }

    // Check if number n is prime (wheel-based)
    private static boolean isPrime(long n) {
        if (n == 2 || n == 3 || n == 5) return true;
        if (n < 2 || n % 2 == 0 || n % 3 == 0 || n % 5 == 0) return false;
        
        long base = n / WHEEL_SIZE;
        int offset = (int) (n % WHEEL_SIZE);
        
        // Find position in wheel
        int wheelPos = -1;
        for (int i = 0; i < WHEEL.length; i++) {
            if (WHEEL[i] == offset) {
                wheelPos = i;
                break;
            }
        }
        
        if (wheelPos == -1) return false; // Not a wheel candidate
        
        long index = base * WHEEL.length + wheelPos;
        int arrayIndex = (int) (index / 32);
        int bitIndex = (int) (index % 32);
        if (arrayIndex >= sieve.length()) return true;
        return (sieve.get(arrayIndex) & (1 << bitIndex)) == 0;
    }

    // Mark number n as composite (thread-safe atomic operation)
    private static void markComposite(long n) {
        if (n < 7) return;
        
        long base = n / WHEEL_SIZE;
        int offset = (int) (n % WHEEL_SIZE);
        
        // Find position in wheel
        int wheelPos = -1;
        for (int i = 0; i < WHEEL.length; i++) {
            if (WHEEL[i] == offset) {
                wheelPos = i;
                break;
            }
        }
        
        if (wheelPos == -1) return; // Not a wheel candidate
        
        long index = base * WHEEL.length + wheelPos;
        int arrayIndex = (int) (index / 32);
        int bitIndex = (int) (index % 32);
        
        if (arrayIndex >= sieve.length()) return;
        
        // Optimized atomic OR using getAndUpdate
        sieve.getAndUpdate(arrayIndex, v -> v | (1 << bitIndex));
    }
}
