import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import java.util.concurrent.locks.*;
import java.util.*;



/**
 * ================================================================
 *  Smart Laundry Facility Simulation — CT074-3-2
 *  Asia Pacific University of Technology & Innovation
 * ================================================================
 *
 *  FACILITY:  6 washing machines | 4 dryers | 2 payment kiosks
 *  CUSTOMERS: 50, each running as an independent thread
 *  PIPELINE:  Arrive → Wash → Dry → Pay → Leave
 *
 * ----------------------------------------------------------------
 *  TIME SCALE  (simulation vs. real-world spec, scaled 1 : 5)
 * ----------------------------------------------------------------
 *    Event             Spec (real)    Simulated (ms)
 *    ──────────────    ───────────    ──────────────
 *    Customer arrival   0 – 3 s         0 – 600 ms
 *    Wash cycle         4 – 6 s       800 – 1200 ms
 *    Dry cycle          3 – 5 s       600 – 1000 ms
 *    Payment            1 – 2 s       200 –  400 ms
 *    Failure retry      2 s                400 ms
 *
 * ----------------------------------------------------------------
 *  CONCURRENCY FACILITIES USED
 * ----------------------------------------------------------------
 *  Semaphore(n, fair)   Counting semaphore — limits simultaneous
 *                       machine use. fair=true → FIFO, no starvation.
 *
 *  ReentrantLock(fair)  Mutual exclusion for console output so no
 *                       two threads interleave their printed lines.
 *
 *  AtomicInteger/Long   Lock-free statistics counters using CPU-level
 *                       CAS (compare-and-set). No synchronised block needed.
 *
 *  CountDownLatch(50)   Barrier — main blocks at await() until all 50
 *                       customer threads each call countDown().
 *
 *  volatile boolean     Congestion flag — volatile forces every read to
 *                       go to main memory so all threads see writes instantly.
 *
 *  ExecutorService      Cached thread pool manages thread lifecycle.
 * ================================================================
 */
public class LaundrySimulation {

    // ── Machine Pools ────────────────────────────────────────────────────────
    private static final Semaphore washerPool = new Semaphore(6, true);
    private static final Semaphore dryerPool  = new Semaphore(4, true);
    private static final Semaphore kioskPool  = new Semaphore(2, true);

    // ── Output Lock ──────────────────────────────────────────────────────────
    private static final ReentrantLock printLock = new ReentrantLock(true);

    // ── Statistics ───────────────────────────────────────────────────────────
    static final AtomicInteger customersServed   = new AtomicInteger(0);
    static final AtomicInteger customersFailed   = new AtomicInteger(0);
    static final AtomicLong    totalElapsedMs    = new AtomicLong(0);
    static final AtomicLong    totalWasherWaitMs = new AtomicLong(0);
    static final AtomicLong    totalDryerWaitMs  = new AtomicLong(0);
    static final AtomicLong    totalKioskWaitMs  = new AtomicLong(0);
    static final AtomicInteger washerFailCount   = new AtomicInteger(0);
    static final AtomicInteger kioskFailCount    = new AtomicInteger(0);

    // ── Peak / In-use Tracking ────────────────────────────────────────────────
    // inUse* is incremented after acquire() and decremented before release(),
    // so it is always an accurate count of machines currently occupied.
    static final AtomicInteger inUseWashers = new AtomicInteger(0);
    static final AtomicInteger inUseDryers  = new AtomicInteger(0);
    static final AtomicInteger inUseKiosks  = new AtomicInteger(0);
    static final AtomicInteger peakWashers  = new AtomicInteger(0);
    static final AtomicInteger peakDryers   = new AtomicInteger(0);
    static final AtomicInteger peakKiosks   = new AtomicInteger(0);

    // ── Bonus: Congestion Scenario ────────────────────────────────────────────
    // The 30th customer to reach payment sets congestionActive = true.
    // incrementAndGet() is atomic, so exactly one thread observes value == 30.
    // The first customer who then fails at a kiosk clears the flag (owner fixed).
    static final AtomicInteger paymentStageCount = new AtomicInteger(0);
    static volatile boolean    congestionActive  = false;

    // ── Constants ─────────────────────────────────────────────────────────────
    static final int    NUM_CUSTOMERS  = 50;
    static final int    CONGESTION_AT  = 30;
    static final double FAIL_CHANCE    = 0.05;
   static final int ARRIVAL_MAX_MS = 2500;

static final int WASH_MIN_MS    = 2500;
static final int WASH_MAX_MS    = 3500;

static final int DRY_MIN_MS     = 1800;
static final int DRY_MAX_MS     = 2600;

static final int PAY_MIN_MS     = 700;
static final int PAY_MAX_MS     = 1000;

static final int RETRY_WAIT_MS  = 1000;

    static final Random         rng      = new Random();
    static       long           simStart;        // set in runSimulation()
    static       CountDownLatch allDone;

    // ════════════════════════════════════════════════════════════════════════
    //  ENTRY POINT — delegates to GUI so running either class opens the window
    // ════════════════════════════════════════════════════════════════════════
    public static void main(String[] args) {
        LaundryGUI.main(args);
    }

    // ════════════════════════════════════════════════════════════════════════
    //  SIMULATION LOGIC — called by LaundryGUI on a background thread
    // ════════════════════════════════════════════════════════════════════════
    public static void runSimulation() throws InterruptedException {
        simStart = System.currentTimeMillis();
        allDone  = new CountDownLatch(NUM_CUSTOMERS);

        log("MAIN", "=== Smart Laundry Facility — Simulation Start ===");
        log("MAIN", "Resources : 6 washers | 4 dryers | 2 kiosks");
        log("MAIN", "Customers : " + NUM_CUSTOMERS + " threads");
        log("MAIN", "Time scale: 1 sim-second = 5 real seconds");
        log("MAIN", "-".repeat(52));

        // CachedThreadPool: creates threads on demand, reuses idle ones
        ExecutorService pool = Executors.newCachedThreadPool();

        for (int i = 1; i <= NUM_CUSTOMERS; i++) {
            pool.submit(new Customer(i));
            if (i < NUM_CUSTOMERS) {
                Thread.sleep(randBetween(0, ARRIVAL_MAX_MS));
            }
        }

        allDone.await();   // blocks until every customer calls countDown()
        pool.shutdown();
        printFinalStats();
    }

    // ════════════════════════════════════════════════════════════════════════
    //  UTILITIES
    // ════════════════════════════════════════════════════════════════════════

    static int randBetween(int min, int max) {
        return min + rng.nextInt(max - min + 1);
    }

    /**
     * Lock-free peak update using a CAS retry loop.
     * If another thread changes peak between our read and write,
     * compareAndSet fails and we retry — no lost updates, no lock needed.
     */
    static void updatePeak(AtomicInteger peak, int candidate) {
        int prev;
        do {
            prev = peak.get();
            if (candidate <= prev) return;
        } while (!peak.compareAndSet(prev, candidate));
    }

    /**
     * Thread-safe logger — ReentrantLock prevents two threads printing
     * at the same time and interleaving their output lines.
     */
    static void log(String actor, String msg) {
        printLock.lock();
        try {
            double t = (System.currentTimeMillis() - simStart) / 1000.0;
            System.out.printf("[%6.2fs] %-14s %s%n", t, "[" + actor + "]", msg);
        } finally {
            printLock.unlock();
        }
    }

    // ════════════════════════════════════════════════════════════════════════
    //  FINAL STATISTICS
    // ════════════════════════════════════════════════════════════════════════
    static void printFinalStats() {
        long elapsed = System.currentTimeMillis() - simStart;
        int  served  = customersServed.get();
        int  failed  = customersFailed.get();

        double avgTotal = served > 0 ? totalElapsedMs.get()    / (double) served / 1000.0 : 0;
        double avgWWait = served > 0 ? totalWasherWaitMs.get() / (double) served / 1000.0 : 0;
        double avgDWait = served > 0 ? totalDryerWaitMs.get()  / (double) served / 1000.0 : 0;
        double avgKWait = served > 0 ? totalKioskWaitMs.get()  / (double) served / 1000.0 : 0;

        log("MAIN", "-".repeat(52));
        log("MAIN", "=== FINAL STATISTICS ===");
        log("MAIN", String.format("Simulation duration          : %.1f s",  elapsed / 1000.0));
        log("MAIN", String.format("Customers completed          : %d / %d", served, NUM_CUSTOMERS));
        log("MAIN", String.format("Customers failed/interrupted : %d",      failed));
        log("MAIN", String.format("Total accounted for          : %d / %d", served + failed, NUM_CUSTOMERS));
        log("MAIN", "-- Timing (avg per completed customer) --");
        log("MAIN", String.format("  Avg total time             : %.2f s", avgTotal));
        log("MAIN", String.format("  Avg wait — washer          : %.2f s", avgWWait));
        log("MAIN", String.format("  Avg wait — dryer           : %.2f s", avgDWait));
        log("MAIN", String.format("  Avg wait — kiosk           : %.2f s", avgKWait));
        log("MAIN", "-- Peak Concurrency --");
        log("MAIN", String.format("  Peak washers in use        : %d / 6", peakWashers.get()));
        log("MAIN", String.format("  Peak dryers  in use        : %d / 4", peakDryers.get()));
        log("MAIN", String.format("  Peak kiosks  in use        : %d / 2", peakKiosks.get()));
        log("MAIN", "-- Failures --");
        log("MAIN", String.format("  Washer mid-cycle failures  : %d", washerFailCount.get()));
        log("MAIN", String.format("  Kiosk failures (retried)   : %d", kioskFailCount.get()));
        log("MAIN", String.format("  Congestion triggered       : %s",
                paymentStageCount.get() >= CONGESTION_AT ? "YES" : "NO"));
    }

    // ════════════════════════════════════════════════════════════════════════
    //  CUSTOMER — private static inner class
    //
    //  Declared INSIDE LaundrySimulation so it accesses all static fields
    //  directly (no LaundrySimulation.X prefix needed — clean, unambiguous).
    //  'static' means it does not hold a reference to an outer instance.
    //
    //  One Customer instance = one thread submitted to the pool.
    //  Pipeline: ARRIVE → WASH → DRY → PAY → LEAVE
    // ════════════════════════════════════════════════════════════════════════
    private static class Customer implements Runnable {

        private final String label;     // "Customer-07" — used in every log line

        // Local accumulators — only this thread writes them, no sync needed.
        private long    startTime;
        private long    washerWaitMs;
        private long    dryerWaitMs;
        private long    kioskWaitMs;
        private boolean completed;      // true only if all three stages finish

        Customer(int id) {
            this.label = String.format("Customer-%02d", id);
        }

        @Override
        public void run() {
            Thread.currentThread().setName(label);
            startTime = System.currentTimeMillis();
            log("ARRIVED");

            try {
                doWash();
                doDry();
                doPay();
                completed = true;
                log(String.format("LEFT  — total %.1fs  (washer-wait %.1fs, dryer-wait %.1fs, kiosk-wait %.1fs)",
                        (System.currentTimeMillis() - startTime) / 1000.0,
                        washerWaitMs / 1000.0, dryerWaitMs / 1000.0, kioskWaitMs / 1000.0));

            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log("INTERRUPTED — left without completing");

            } finally {
                // Runs whether completed or not — guarantees served+failed == 50
                long elapsed = System.currentTimeMillis() - startTime;
                if (completed) {
                    customersServed.incrementAndGet();
                    totalElapsedMs.addAndGet(elapsed);
                    totalWasherWaitMs.addAndGet(washerWaitMs);
                    totalDryerWaitMs.addAndGet(dryerWaitMs);
                    totalKioskWaitMs.addAndGet(kioskWaitMs);
                } else {
                    customersFailed.incrementAndGet();
                }
                allDone.countDown();   // always release latch, even on failure
            }
        }

        // ── Stage 1: WASH ─────────────────────────────────────────────────────
        //
        //  washerPool.acquire() blocks this thread until a machine is free.
        //
        //  On 5% failure: release the permit immediately (freeing the machine
        //  for others), wait, then re-enter the queue. We never hold a resource
        //  we are not actively using — correct concurrent resource management.
        // ─────────────────────────────────────────────────────────────────────
        private void doWash() throws InterruptedException {
            log("WAITING for washer");
            boolean done = false;

            while (!done) {
                long waitStart = System.currentTimeMillis();
                washerPool.acquire();                          // BLOCKS if all 6 busy
                washerWaitMs += System.currentTimeMillis() - waitStart;

                int using = inUseWashers.incrementAndGet();
                updatePeak(peakWashers, using);

                if (rng.nextDouble() < FAIL_CHANCE) {
                    // Failure — release machine before waiting to retry
                    washerFailCount.incrementAndGet();
                    inUseWashers.decrementAndGet();
                    washerPool.release();
                    log("WASHER FAILED — releasing machine, retrying in 2s...");
                    Thread.sleep(RETRY_WAIT_MS);
                    log("RE-JOINING washer queue");
                    // done stays false → loop re-acquires

                } else {
                    try {
                        int ms = randBetween(WASH_MIN_MS, WASH_MAX_MS);
                        log(String.format("WASHING  started (%.1fs) [%d/6 busy]",
                                ms / 1000.0, using));
                        Thread.sleep(ms);
                        log("WASHING  done ✓");
                        done = true;
                    } finally {
                        inUseWashers.decrementAndGet();
                        washerPool.release();
                    }
                }
            }
        }

        // ── Stage 2: DRY ──────────────────────────────────────────────────────
        //  acquire → increment inUse → use machine → decrement inUse → release
        // ─────────────────────────────────────────────────────────────────────
        private void doDry() throws InterruptedException {
            log("WAITING for dryer");

            long waitStart = System.currentTimeMillis();
            dryerPool.acquire();                               // BLOCKS if all 4 busy
            dryerWaitMs = System.currentTimeMillis() - waitStart;

            int using = inUseDryers.incrementAndGet();
            updatePeak(peakDryers, using);

            try {
                int ms = randBetween(DRY_MIN_MS, DRY_MAX_MS);
                log(String.format("DRYING   started (%.1fs) [%d/4 busy]",
                        ms / 1000.0, using));
                Thread.sleep(ms);
                log("DRYING   done ✓");
            } finally {
                inUseDryers.decrementAndGet();
                dryerPool.release();
            }
        }

        // ── Stage 3: PAY ──────────────────────────────────────────────────────
        //
        //  Normal: 5% kiosk failure → retry after 2s.
        //
        //  BONUS — Congestion:
        //    paymentStageCount.incrementAndGet() is atomic — exactly one thread
        //    will observe the return value == CONGESTION_AT and set the flag.
        //    The first customer who then hits a kiosk failure clears the flag
        //    (owner arrives). Trigger → one failure → immediate recovery.
        // ─────────────────────────────────────────────────────────────────────
        private void doPay() throws InterruptedException {
            int reached = paymentStageCount.incrementAndGet();

            if (reached == CONGESTION_AT) {
                congestionActive = true;
                log("FACILITY",
                        "!! CONGESTION: " + CONGESTION_AT
                        + " customers reached payment — kiosks failing! Owner called!");
            }

            log("WAITING for kiosk");
            long waitStart = System.currentTimeMillis();
            kioskPool.acquire();                               // BLOCKS if both busy
            kioskWaitMs = System.currentTimeMillis() - waitStart;

            int using = inUseKiosks.incrementAndGet();
            updatePeak(peakKiosks, using);

            try {
                boolean done  = false;
                int     attempt = 0;

                while (!done) {
                    attempt++;
                    boolean fails = congestionActive
                            || rng.nextDouble() < FAIL_CHANCE;

                    if (fails) {
                        kioskFailCount.incrementAndGet();
                        log("KIOSK FAILED (attempt " + attempt + ") — retrying in 2s...");

                        if (congestionActive) {
                            congestionActive = false;          // owner fixes kiosks
                            log("FACILITY",
                                    ">> Owner ARRIVED — kiosks restored, normal service resumed.");
                        }
                        Thread.sleep(RETRY_WAIT_MS);

                    } else {
                        int ms = randBetween(PAY_MIN_MS, PAY_MAX_MS);
                        log(String.format("PAYING   processing (%.1fs) [%d/2 busy]",
                                ms / 1000.0, using));
                        Thread.sleep(ms);
                        log("PAYMENT  successful ✓");
                        done = true;
                    }
                }
            } finally {
                inUseKiosks.decrementAndGet();
                kioskPool.release();
            }
        }

        // Shorthand — passes this customer's label as the log actor.
        // Accesses outer class's static log() directly — no prefix needed.
        private void log(String msg) {
            LaundrySimulation.log(label, msg);
        }

        // Two-arg version used for facility-level messages (congestion events)
        private void log(String actor, String msg) {
            LaundrySimulation.log(actor, msg);
        }
    }
}