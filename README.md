# Optimized Multithreaded Prime Number Sieve

A high-performance implementation of the Segmented Sieve of Eratosthenes in Java, optimized for finding prime numbers up to 100 billion using multiple advanced techniques.

> **Current Version**: Standard multithreaded implementation (no SIMD/Vector API)  
> **Performance**: ~200-400ms for 1 billion primes on modern multi-core processors  
> **Maximum N**: ~257.7 billion (limited by Java array size constraints)

## Features

### Core Optimizations

1. **Wheel Factorization (2, 3, 5)** - Reduces candidate numbers by 73% by only checking numbers of the form `30k + {1, 7, 11, 13, 17, 19, 23, 29}`
2. **Bit-Packed Array** - 8x memory reduction using bit-level storage with atomic operations for thread safety
3. **Cache-Friendly Segmentation** - Processes data in L2 cache-optimized segments (4MB) for better performance
4. **Multithreaded Sieving** - Utilizes all available CPU cores for parallel computation
5. **Smart Work Distribution** - Single-threaded phase up to √N, then multithreaded for the remaining range
6. **Parallel Prime Counting** - Counts primes in parallel using thread pools
7. **Buffered I/O** - 64KB buffer for fast file writing

## Requirements

- **Java Version**: Java 8 or higher (tested with Java 17)
- **Memory**: Approximately 100MB for N = 1 billion, ~8GB for N = 100 billion (recommended: 4-16GB heap)
- **CPU**: Multi-core processor recommended for optimal performance

## Usage

### Compilation

```bash
javac src/MultithreadedSieve.java
```

### Running

```bash
# Basic run
java -cp src MultithreadedSieve

# Recommended: with optimized memory settings
java -Xms4g -Xmx8g -cp src MultithreadedSieve
```

### Configuration

Edit the following constants in `MultithreadedSieve.java` to customize:

```java
private static final long N = 100_000_000_000L;    // Upper limit (max: ~257,698,036,740)
private static final int NUM_THREADS = Runtime.getRuntime().availableProcessors(); // Thread count
private static final int SEGMENT_SIZE = L2_CACHE_SIZE * 16; // Segment size
```

**Important**: The maximum supported value for `N` is approximately **257,698,036,740** (257.7 billion). This limit is due to Java's array size constraint (`Integer.MAX_VALUE`). Beyond this value, the internal bit-packed array would exceed the maximum array size.

## Output

The program generates a `primes.txt` file containing:

- **Performance Analysis**: Detailed timing breakdown and metrics
- **Configuration Details**: Range, threads, memory usage
- **Timing Breakdown**: Initialization, Phase 1 (√N), Phase 2 (multithreaded)
- **Performance Metrics**: Throughput, speed, parallel efficiency
- **Results**: Total prime count, prime density, largest prime found

### Console Output Example

```
Output written to primes.txt
Total primes: 50,847,534
Sieve time: 212.43 ms
Total time: 377.08 ms
```

## Algorithm Details

### Phase 1: Base Prime Generation (Single-threaded)

- Finds all primes up to √N using the wheel-based sieve
- These primes are used as the basis for marking composites in Phase 2
- Complexity: O(√N log log √N)

### Phase 2: Segmented Sieving (Multithreaded)

- Divides the range [√N+1, N] into cache-friendly segments
- Each segment is processed independently by worker threads
- Uses atomic bit operations for thread-safe marking
- Complexity: O((N - √N) log log N / P) where P is the number of threads

### Phase 3: Prime Counting (Parallel)

- Counts primes in parallel using thread pools
- Atomically updates global counters for total count and largest prime
- Minimizes contention with local counting and batch updates

## Performance Characteristics

### Time Complexity

- **Overall**: O(N log log N / P) where P is the number of processor cores
- **Space**: O(N / 240) due to wheel factorization and bit-packing

### Memory Usage

For N = 1,000,000,000:

- **Theoretical**: ~4.2 MB (with all optimizations)
- **Actual**: ~100 MB (including JVM overhead and auxiliary structures)

### Expected Performance

On a modern multi-core processor (8-10 cores):

- **N = 100 million**: ~50-150 ms
- **N = 1 billion**: ~200-400 ms
- **N = 10 billion**: ~2-5 seconds
- **N = 100 billion**: ~30-60 seconds (requires 8-16GB heap)

## Technical Implementation

### Data Structure

```
Bit-packed AtomicIntegerArray storing wheel candidates:
- Only stores numbers ≡ {1,7,11,13,17,19,23,29} (mod 30)
- Each bit represents whether a number is prime (0) or composite (1)
- Atomic operations ensure thread safety during concurrent marking
```

### Thread Safety

- Uses `AtomicIntegerArray` for lock-free concurrent access
- `getAndUpdate()` for atomic bit-setting operations
- `AtomicLong` for thread-safe counter updates
- Compare-and-swap (CAS) for finding the largest prime

## Limitations

- **Maximum N**: ~257,698,036,740 (257.7 billion) - constrained by Java's `Integer.MAX_VALUE` array size limit
  - Formula: `arraySize = ((N / 30 + 1) * 8 + 31) / 32 ≤ 2,147,483,647`
  - Current N = 100 billion uses ~39% of maximum capacity
- **Memory**: Requires sufficient heap space (use `-Xmx` flag if needed)
  - N = 100 billion requires ~3.3GB for the sieve array alone
- **Integer Overflow**: Uses `long` for calculations to avoid overflow

## Example Run Configuration

For optimal performance with large N values:

```bash
# Recommended settings for N = 1 billion
java -Xms4g -Xmx8g -cp src MultithreadedSieve

# For N = 10 billion
java -Xms8g -Xmx16g -cp src MultithreadedSieve

# For N = 100 billion (current default)
java -Xms8g -Xmx16g -cp src MultithreadedSieve

# For N approaching maximum (~257 billion)
java -Xms16g -Xmx32g -cp src MultithreadedSieve
```

## File Structure

```
PrimeNumbers-MultiThread/
├── src/
│   └── MultithreadedSieve.java    # Main implementation
├── .idea/                           # IntelliJ IDEA configuration
├── primes.txt                       # Output file (generated)
├── README.md                        # This file
├── README_SIMD.md                   # SIMD/Vector API documentation (future)
├── QUICK_START.md                   # Quick start guide
├── IDE_SETUP.md                     # IDE setup instructions
├── compile.sh                       # Compilation script
├── run_simd.sh                      # SIMD execution script (future)
└── PrimeNumbers-MultiThread.iml    # IntelliJ project file
```

## Benchmarking

The program includes built-in performance analysis:

- Initialization time
- Phase 1 (base primes) time
- Phase 2 (multithreaded sieving) time
- I/O time
- Parallel efficiency calculation
- Throughput metrics (numbers/second)

## Future Enhancements

Potential optimizations for even better performance:

- **SIMD/Vector API** (Java 16+) for vectorized operations
- GPU acceleration using CUDA/OpenCL
- Distributed computing across multiple machines
- Advanced wheel factorization (2,3,5,7,11)
- Memory-mapped files for extremely large N

## License

This project is provided as-is for educational and research purposes.

## Author

JoergKau + Windsurf 2.8.5 - Developed as a demonstration of advanced algorithmic optimization techniques in Java.

---

**Note**: Performance may vary based on hardware specifications, JVM version, and system load.
