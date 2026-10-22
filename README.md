# Lab-01: Multithreading in Java

Environment: macOS, Apple M3 Pro (12 cores: 6 performance + 6 efficiency),
Oracle JDK 25.0.4.1 LTS, Maven 3.10.0.

Build once, then run any task (replace `taskN`):

```bash
mvn -q compile
java -cp target/classes edu.itc.se.lab01.taskN.Main
java -cp target/classes edu.itc.se.lab01.task1.Main --sequential   # Task 1, run() mode
```

---

## Task 1 – Creating and running threads

Four threads, created in four different ways:

| Thread  | Technique                                                       |
|---------|-----------------------------------------------------------------|
| apollo  | subclass: `class Countdown extends Thread`                      |
| gemini  | `new Thread(new CountdownTask(), "gemini")` (Runnable)          |
| mercury | `new Thread(() -> new CountdownTask().run(), "mercury")` (lambda) |
| artemis | `Thread.ofVirtual().name("artemis").unstarted(...)` (virtual)   |

### Concurrent mode (`start()`), real output

```
Mission control: all launched
gemini: 3
mercury: 3
apollo: 3
artemis: 3
gemini: 2
...
apollo: liftoff!
Mission control: all landed
apollo   virtual=false state=TERMINATED
gemini   virtual=false state=TERMINATED
mercury  virtual=false state=TERMINATED
artemis  virtual=true  state=TERMINATED
```

### Sequential mode (`--sequential`, calls `run()`), real output

```
apollo: 3 … apollo: liftoff!
main: 3 … main: liftoff!      (gemini)
main: 3 … main: liftoff!      (mercury)
Mission control: all launched
Mission control: all landed
apollo   virtual=false state=NEW
gemini   virtual=false state=NEW
mercury  virtual=false state=NEW
artemis  virtual=true  state=NEW
```

### `start()` vs `run()`

- `start()` asks the JVM to create a **new thread** and run `run()` on it. It returns
  immediately, so `main` prints "all launched" while the countdowns are still going,
  and the four countdowns interleave in a different order on every run.
  `join()` makes `main` wait until each thread has finished, so "all landed" is always
  printed last. Afterwards every thread is `TERMINATED`.
- `run()` is just a **normal method call on the current (main) thread**. No new thread
  is created, so:
  - countdowns run one after another, and "all launched" appears only after all of them;
  - `CountdownTask` uses `Thread.currentThread().getName()`, which is `main`, so gemini
    and mercury print `main: …`; apollo still prints `apollo` because `Countdown`
    uses its own `getName()`;
  - artemis prints nothing: the JDK documents that calling `run()` directly on a
    virtual thread does nothing;
  - the threads were never started, so they stay in state `NEW`, and `join()` on them
    returns immediately.

---

## Task 2 – Fixing a race condition

8 threads × 1,000,000 increments each → expected value **8,000,000**.
Run with `java -cp target/classes edu.itc.se.lab01.task2.Main`.

### Real results (3 runs on my machine, 12 cores)

| Counter             | run 1 value | run 2 value | run 3 value | correct | time (ms) run 1 / 2 / 3 |
|---------------------|------------:|------------:|------------:|:-------:|------------------------:|
| UnsafeCounter       | 2,300,344   | 2,339,578   | 3,111,048   | ❌ false | 17 / 17 / 17            |
| SynchronizedCounter | 8,000,000   | 8,000,000   | 8,000,000   | ✅ true  | 91 / 90 / 93            |
| LockCounter         | 8,000,000   | 8,000,000   | 8,000,000   | ✅ true  | 75 / 76 / 76            |
| AtomicCounter       | 8,000,000   | 8,000,000   | 8,000,000   | ✅ true  | 282 / 280 / 297         |
| LongAdderCounter    | 8,000,000   | 8,000,000   | 8,000,000   | ✅ true  | 11 / 10 / 11            |

### Why UnsafeCounter is wrong

`count++` looks like one operation but is really three: **read** `count`, **add 1**,
**write** it back. If two threads read the same value (say 5) before either writes,
both write 6, and one increment is lost. With 8 threads hammering the same variable
this happens millions of times, so 61–71 % of the increments disappeared, and a
different amount each run.

### The three fixes (plus the bonus)

- **SynchronizedCounter** – `synchronized` methods: only one thread at a time may run
  `increment()` on this object, so read-add-write can't be interleaved.
- **LockCounter** – `ReentrantLock`; same idea, but locked/unlocked by hand.
  `unlock()` is in `finally` so the lock is released even if an exception happens.
- **AtomicCounter** – `AtomicLong.incrementAndGet()` uses a hardware
  compare-and-swap (CAS) instruction: "set to 6 only if it is still 5, otherwise retry".
  No lock.
- **LongAdderCounter** (bonus) – keeps several internal cells; colliding threads
  add to different cells, and `sum()` adds them up when read.

### Discussion of the timings

- **UnsafeCounter is fast but wrong**: speed means nothing if the answer is wrong.
- **LongAdder is the fastest correct counter** (≈10 ms), as the lab predicts: threads
  rarely touch the same memory, so there is almost no contention.
- **AtomicCounter was the slowest** on my machine (≈280 ms), which is *different from
  the lab sheet* (there Atomic beats synchronized). My explanation: with AtomicLong all
  12 cores constantly retry CAS on the *same* memory location, so the cache line holding
  `count` bounces between cores and many CAS attempts fail and retry. With a lock, the
  waiting threads are put to sleep, so effectively one thread at a time runs
  with the value hot in its own cache. This extreme benchmark (nothing but increments)
  is the worst case for CAS; results depend on CPU (mine is Apple Silicon) and JVM.
- **synchronized vs ReentrantLock** are close, and which one wins changes between runs
  (≈90 vs ≈75 ms in the table above, ≈50 vs ≈100 ms in later runs).

### Why is it still a bug if only `increment()` is synchronized but `get()` is not?

`synchronized` does two jobs: (1) **mutual exclusion** – one thread at a time, and
(2) **visibility** – when a thread *releases* a lock, everything it wrote becomes
visible to the next thread that *acquires the same lock* (a "happens-before" edge).

An unsynchronized `get()` never acquires the lock, so it gets none of job (2):
the Java Memory Model allows it to see a **stale (old) value**, e.g. one cached in a
CPU register or core cache, possibly forever in a loop like
`while (counter.get() < 100) {}`. Also, `count` is a `long` (64 bits): without
synchronization or `volatile`, Java does not even guarantee the read is atomic, so
in theory you could read half of an old value and half of a new one ("torn read").

In our `Bench` it *happens* to work because `main` calls `join()` on every thread
before calling `get()`, and `join()` itself creates a happens-before edge
("everything a thread did is visible after you join it"). But any reader that does
**not** join – e.g. a progress monitor reading the counter while the threads are still
running – has no such guarantee. That is why `get()` must be synchronized too
(or the field `volatile`, or use `AtomicLong`).

---

## Task 3 – Producer–consumer with `wait()`/`notify()` and `BlockingQueue`

Scenario: 2 producers × 50 orders, 3 consumers, buffer capacity 5. After both producers
have been joined, one poison pill (`Order` with id −1) is put per consumer. This is done
by a small `pill-sender` thread in `Scenario`, not by the producers (the simpler option
from the hints).

```bash
java -cp target/classes edu.itc.se.lab01.task3.Main      # BoundedBuffer, 1 verbose run
java -cp target/classes edu.itc.se.lab01.task3.Main 20   # 20 quiet runs
java -cp target/classes edu.itc.se.lab01.task3.Main2 20  # ArrayBlockingQueue, 20 runs
java -cp target/classes edu.itc.se.lab01.task3.NotifyIfExperiment
```

| Class | Role |
|---|---|
| `Order` | `record Order(int id, String item, int qty)` + the `POISON` pill |
| `OrderQueue` | small interface (`put`, `take`) that Producer/Consumer depend on |
| `BoundedBuffer` | hand-made buffer: `synchronized` + `wait()` in a `while` + `notifyAll()` |
| `BlockingOrderQueue` | adapter around `ArrayBlockingQueue<Order>(5)` |
| `Producer` / `Consumer` | the same classes are used, unchanged, with both buffers |
| `Scenario` | the shared 2×50 / 3 / capacity-5 run, used by `Main`, `Main2` and the experiment |

### Real output (abridged)

```
consumer-2 processed Order[id=51, item=item-51, qty=2]
consumer-1 processed Order[id=1, item=item-1, qty=5]
consumer-3 processed Order[id=52, item=item-52, qty=5]
...
consumer-2 received poison pill, stopping
consumer-3 received poison pill, stopping
consumer-1 received poison pill, stopping
Total processed: 100 (expected 100) in 813 ms
```

20 runs each: `BoundedBuffer: 20/20 runs correct`, `ArrayBlockingQueue: 20/20 runs correct`.

Each run checks that 100 orders were processed, that 100 *different* ids were seen
(so no order was processed twice or lost), and that all 3 consumers stopped because of a
poison pill. `BoundedBuffer` also checks on every call that it never holds more than
`capacity` items and never removes from an empty deque. These checks throw an exception,
so they are always on, unlike `assert`, which only runs with `-ea`.

### How much code disappears with `BlockingQueue`

`BoundedBuffer` needs a lock, two guarded `while` loops, `wait()` and `notifyAll()`.
`BlockingOrderQueue` is just `q.put(o)` and `q.take()`: `ArrayBlockingQueue` already
contains all of that logic (internally a `ReentrantLock` with two conditions,
`notFull` and `notEmpty`). `Producer` and `Consumer` did not change at all, because they
only know the `OrderQueue` interface.

### Experiment: `notify()` instead of `notifyAll()`, `if` instead of `while`

`NotifyIfExperiment` runs the same scenario 20 times with a copy of the buffer that uses
`if` + `notify()`. Real result:

```
run  1: CRASHED: java.util.NoSuchElementException
...
run  8: CRASHED: java.lang.IllegalStateException: buffer exceeded capacity: size=6
...
notify() + if over 20 runs: ok=0 hung=0 crashed=20 wrong=0
```

**Why `while` is required.** `wait()` gives up the lock. When a thread wakes up it has to
get the lock back, and by then another thread may already have changed the buffer.
Example: the buffer has 1 item and consumer-1 is waiting. A producer adds an item and
calls `notify()`, which wakes consumer-1. Before consumer-1 gets the lock back,
consumer-2 arrives, sees the item and takes it. Then consumer-1 continues. With `if` it
does **not** check again, calls `removeFirst()` on an empty deque, and gets
`NoSuchElementException` (19 of 20 runs). The same thing happened on the producer side:
two producers were woken for one free slot, both added, and the buffer reached 6 items
(run 8). Java also allows *spurious wake-ups* (waking with no `notify` at all). A `while`
loop re-checks the condition after every wake-up, so all these cases are safe.

**Why `notifyAll()` is required.** Producers and consumers wait on the **same** monitor.
`notify()` wakes **one** arbitrary waiter, and it may be the wrong kind. Example: the
buffer is empty and both a consumer and a producer are waiting. A consumer takes the last
item and its `notify()` wakes the other *consumer* instead of a producer. That consumer
finds the buffer still empty and waits again. The signal meant for the producer is lost,
and if every thread ends up waiting, the program hangs forever. In our runs the `if` bug
always crashed the program first, so we did not see the hang. It can still happen even
with `while` if you use `notify()`. `notifyAll()` wakes everyone, and the `while` loop
sends back to sleep the threads that cannot make progress.

---

## Task 4 – Thread pools, `Future` and virtual threads

```bash
java -cp target/classes edu.itc.se.lab01.task4.Main
```

| Class | Role |
|---|---|
| `PrimeCounter` | `Callable<Long>`: counts primes in `[from, to)` by trial division (CPU-bound) |
| `FakeDownload` | `Callable<String>`: sleeps 200 ms and returns `url -> length` (I/O-bound) |
| `Main` | primes, 500 downloads on two executors, failures, timeout, `CompletableFuture` bonus |

### Real output

```
single-threaded: 283146 primes in 327 ms
pool (12 threads): 283146 primes in 69 ms  (same result: true)

downloads on fixed(20) platform threads: ok=489 failed=10 timedOut=1 in 5295 ms   (failures: java.io.IOException: boom)
downloads on virtual threads           : ok=489 failed=10 timedOut=1 in 1024 ms   (failures: java.io.IOException: boom)

CompletableFuture + virtual threads : ok=489 failed=10 timedOut=1 in 1011 ms
  example fallbacks: FALLBACK https://example.com/file-1 (timed out) | FALLBACK https://example.com/file-50 (java.io.IOException: boom)
```

Timings over 3 runs:

| Part | run 1 | run 2 | run 3 |
|---|---:|---:|---:|
| primes, single thread | 327 ms | 330 ms | 338 ms |
| primes, pool of 12 | 69 ms | 66 ms | 77 ms |
| 500 downloads, fixed(20) platform | 5295 ms | 5294 ms | 5295 ms |
| 500 downloads, virtual threads | 1024 ms | 1026 ms | 1024 ms |
| bonus, `CompletableFuture` + virtual | 1011 ms | 1011 ms | 1011 ms |

The JVM exits by itself (exit code 0, no `System.exit`).

### CPU-bound part: about 4.7× faster

The range `[0, 4 000 000)` is split into 16 chunks (`from = i*LIMIT/CHUNKS`), each a
`PrimeCounter` submitted to `newFixedThreadPool(availableProcessors())` = 12 threads.
`submit()` returns a `Future<Long>` immediately, and `future.get()` waits for that chunk's
result. Both versions give **283 146** primes. The speed-up is about 4.7×, not 12×, because:
- the chunks are not equal: numbers near 4 000 000 need more trial divisions, so the
  last chunks take longer and some cores finish early and sit idle;
- the M3 Pro's 12 cores are 6 performance + 6 slower efficiency cores;
- starting the pool and threads has a small fixed cost.

For CPU-bound work, more threads than cores does not help: the cores are already busy.

### I/O-bound part: platform pool vs virtual threads

Each download only *sleeps* 200 ms. That is waiting, not computing.

- **fixed(20) platform threads:** only 20 downloads can run at once. 500 tasks / 20 =
  25 rounds × 200 ms = **about 5 s**. The other 480 tasks wait in the queue even though the
  CPU is idle.
- **virtual threads:** `newVirtualThreadPerTaskExecutor()` gives **each** task its own
  virtual thread, so all 500 sleep at the same time: about 200 ms in total. A virtual thread
  is very cheap. When it blocks (sleep, network, file), the JVM takes it off its carrier
  (OS) thread, which then runs another virtual thread. You can have thousands or millions
  of them, while every platform thread is a real OS thread with about 1 MB of stack.
- Our virtual run shows about **1 s**, not 200 ms, because we deliberately wait 1 s on the slow
  task first (the timeout). Everything else finishes during that second. Result: about
  **5× faster** than the platform pool.

Rule of thumb: CPU-bound work → pool sized to the number of cores. Blocking I/O-bound work
→ virtual threads.

### Failures and timeout

- Every 50th download throws `IOException("boom")`. `future.get()` wraps it in an
  `ExecutionException`, and `getCause()` gives back the original `IOException`. We count
  it and continue, so the program never crashes → `failed=10`.
- Download 1 sleeps 5 s and is the first future we wait on. `get(1, TimeUnit.SECONDS)`
  throws `TimeoutException`, and `cancel(true)` interrupts the task's `sleep()` so it
  stops → `timedOut=1`. That is why the virtual run takes about 1 s instead of 5 s.
- 500 − 10 − 1 = `ok=489`.

### Shutdown

Every executor is used in try-with-resources (`ExecutorService` is `AutoCloseable` since
JDK 19). `close()` calls `shutdown()` and waits for submitted tasks. If a pool were never
shut down, its non-daemon threads would keep the JVM running after `main` returns.

### Bonus: `CompletableFuture`

`supplyAsync(..., executor)` starts each download, `orTimeout(1, SECONDS)` adds the
timeout, and `handle((result, ex) -> ...)` turns any failure into a fallback string like
`FALLBACK https://example.com/file-50 (java.io.IOException: boom)`.
`allOf(...).join()` waits for all 500. `orTimeout` only completes the *future*. It does not
stop the 5-second task. So this part uses `shutdownNow()` (which interrupts it) +
`awaitTermination()` instead of try-with-resources, which would wait 5 s for it.

---

## Task 5 – Diagnosing a deadlock and shutting down gracefully

```bash
java -cp target/classes edu.itc.se.lab01.task5.Bank   # broken: hangs (Ctrl+C)
java -cp target/classes edu.itc.se.lab01.task5.Main   # OrderedBank, TryLockBank, graceful shutdown
```

| Class | Role |
|---|---|
| `Bank` | the deliberately broken starter + the watchdog |
| `Watchdog` | daemon thread: `ThreadMXBean.findDeadlockedThreads()` every second |
| `OrderedBank` | Fix A: always lock the smaller account id first |
| `TryLockBank` | Fix B: `ReentrantLock.tryLock` + release + random back-off, counts retries |
| `GracefulShutdown` | workers loop `while (!isInterrupted())`, main interrupts after 2 s |

### 1. The deadlock

`transfer(from, to)` locks `from` and then `to`. If transfer-0 runs `transfer(acc-1, acc-2)`
while transfer-4 runs `transfer(acc-2, acc-1)`, then transfer-0 holds acc-1 and waits for
acc-2, while transfer-4 holds acc-2 and waits for acc-1. Neither can continue, so both
wait forever. Real run:

```
[watchdog] DEADLOCK detected between: transfer-0, transfer-4
(process hangs)
```

Terminal capture of another run, where the cycle had three threads
(transfer-0 → transfer-2 → transfer-5 → transfer-0):

![watchdog detecting a deadlock](screenshots/task5-deadlock-watchdog.png)

`jcmd -l` gave the pid, and `jstack <pid>` printed the report saved in
[deadlock.txt](deadlock.txt):

```
Found one Java-level deadlock:
=============================
"transfer-0":
  waiting to lock monitor 0x000000756ece2920 (object 0x0000007012434b90, a edu.itc.se.lab01.task5.Bank$Account),
  which is held by "transfer-4"

"transfer-4":
  waiting to lock monitor 0x000000756ece2ae0 (object 0x0000007012434ba8, a edu.itc.se.lab01.task5.Bank$Account),
  which is held by "transfer-0"
```

The stack part shows each thread `locked <...4ba8>` / `waiting to lock <...4b90>` and the
reverse, which is the circular wait.

All four **Coffman conditions** hold in `Bank`: mutual exclusion (a monitor has one
owner), hold-and-wait (a thread holds `from` while waiting for `to`), no preemption (Java
cannot take a monitor away from a thread), and circular wait (opposite lock orders). Each
fix removes one of them.

### 2. Fixes – real results

```
# OrderedBank
transfers=800000 total=4000 (expected 4000) in 76 ms

# TryLockBank
transfers=800000 retries=249 total=4000 (expected 4000) in 25 ms
```

Both complete 8 workers × 100 000 = 800 000 transfers (the lab text says "8 × 10 000" in
step 4 but expects 800 000 in the output and checklist; I used 100 000 per worker, as in the
starter). The two accounts of each transfer are always different, so every iteration is a
real transfer. The total stays 4000 and the watchdog reports nothing.

- **Fix A – lock ordering** removes **circular wait**. Every thread locks the smaller id
  first, so no thread can hold acc-2 while waiting for acc-1, and a cycle cannot form.
- **Fix B – `tryLock` + back-off** removes **hold-and-wait** (and gives a kind of
  preemption: a thread gives up its own lock). If the second lock is not free, the thread
  releases the first, sleeps a random 0.01–1 ms, and tries again. The random back-off makes
  two threads unlikely to collide again in exactly the same way.

**A measured detail about Fix B.** The lab says `tryLock(10, MILLISECONDS)` on both locks.
I tried that first, in a separate test with the same 8 × 100 000 transfers:

| Variant | retries | time |
|---|---:|---:|
| `tryLock(10 ms)` on **both** locks | 26 956 | **65 862 ms** |
| `tryLock(10 ms)` on the first, `tryLock()` (no wait) on the second | 424 | **43 ms** |

With a timed wait on the *second* lock, a thread still holds the first lock while it
waits: hold-and-wait, only limited to 10 ms. Two threads locking in opposite order
deadlock for the full 10 ms, then both give up. This happened tens of thousands of times,
and the watchdog even reported these short deadlocks. Taking the second lock without
waiting means a thread never waits while it holds a lock, so `TryLockBank` uses that.

### 3. Graceful shutdown – real output

```
[main] interrupting 8 workers after 2000 ms
transfer-2 stopped after 12950131 transfers (interrupt flag=true)
transfer-1 stopped after 14093613 transfers (interrupt flag=true)
...
transfer-6 stopped after 13306851 transfers (interrupt flag=true)
all workers stopped=true total=4000 (expected 4000)
```

- `interrupt()` does not kill a thread. It sets a flag. Each worker checks
  `while (!Thread.currentThread().isInterrupted())` before every transfer and stops by
  itself, **between** transfers, never in the middle of moving money. That is why the
  total is still exactly 4000 after shutdown. (`Thread.stop()` could kill a thread
  half-way through a transfer and break the total.)
- If the interrupt arrives while a worker is blocked in `tryLock(10 ms)` or the back-off
  `sleep`, those methods throw `InterruptedException`. Throwing it **clears** the flag, so
  the worker calls `Thread.currentThread().interrupt()` to set it again. Otherwise the
  `while` check would never see it and the worker would keep running. Every worker prints
  `interrupt flag=true` at the end.
- `main` uses `join(1000)` and then `isAlive()` for each worker: all 8 stopped within the
  1-second limit.
