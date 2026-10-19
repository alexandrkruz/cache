
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.UnaryOperator;

public class Cache {

    static final String SYMBOL = "AAPL";
    static final int CLIENTS = 200;

    static final AtomicInteger calls = new AtomicInteger();

    static String quote(String symbol) {
        calls.incrementAndGet();
        try {
            Thread.sleep(300);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        return symbol + "=173.42";
    }

    static final class RacyCache {
        private final Map<String, String> map = new HashMap<>(64);

        String get(String symbol) {
            String hit = map.get(symbol);
            if (hit != null) return hit;
            String body = quote(symbol);
            map.put(symbol, body);
            return body;
        }
    }

    static final class SafeCache {
        private final ConcurrentHashMap<String, String> map =
                new ConcurrentHashMap<>();
        private final ConcurrentHashMap<String, Integer> hits =
                new ConcurrentHashMap<>();

        String get(String symbol) {
            hits.merge(symbol, 1, Integer::sum);
            return map.computeIfAbsent(symbol, s -> quote(s));
        }

        int hits(String symbol) {
            return hits.getOrDefault(symbol, 0);
        }
    }

    static final class Till {
        private int wallet = 1000;
        private int cashbox = 1000;

        synchronized void pay() {
            wallet--;
            cashbox++;
        }

        synchronized void refund() {
            wallet++;
            cashbox--;
        }

        synchronized int total() {
            return wallet + cashbox;
        }
    }

    public static void main(String[] args) throws InterruptedException {
        System.out.println(CLIENTS + " клієнтів просять " + SYMBOL
                + ", приходять з інтервалом 2 мс.");
        System.out.println("Котирування коштує ≈300 мс.\n");

        System.out.println("RacyCache, схема «подивився, немає, сходив, поклав»:");
        for (int run = 1; run <= 3; run++) {
            calls.set(0);
            hammer(new RacyCache()::get);
            System.out.println("  прогін " + run
                    + ": запитів до сервісу " + calls.get());
        }

        System.out.println("\nSafeCache, атомарні операції мапи:");
        for (int run = 1; run <= 3; run++) {
            calls.set(0);
            SafeCache cache = new SafeCache();
            String[] got = hammer(cache::get);
            System.out.println("  прогін " + run
                    + ": запитів до сервісу " + calls.get()
                    + ", звернень до кеша " + cache.hits(SYMBOL)
                    + ", однакове в усіх: " + same(got));
        }

        calls.set(0);
        SafeCache warm = new SafeCache();
        for (int i = 0; i < 5; i++) warm.get(SYMBOL);
        System.out.println("\nп'ять звернень до прогрітого кеша: запитів до сервісу "
                + calls.get() + " (має бути 1)");

        System.out.println("\nTill, переказ під захистом:");
        for (int run = 1; run <= 3; run++) {
            Till till = new Till();
            Thread pay = new Thread(() -> {
                for (int i = 0; i < 50_000; i++) till.pay();
            });
            Thread refund = new Thread(() -> {
                for (int i = 0; i < 50_000; i++) till.refund();
            });
            pay.start();
            refund.start();
            pay.join();
            refund.join();
            System.out.println("  прогін " + run
                    + ": гаманець+каса " + till.total()
                    + ", мало бути 2000");
        }
    }

    static String[] hammer(UnaryOperator<String> cache)
            throws InterruptedException {
        String[] out = new String[CLIENTS];
        Thread[] w = new Thread[CLIENTS];

        for (int i = 0; i < CLIENTS; i++) {
            final int me = i;
            w[i] = new Thread(() -> {
                try {
                    Thread.sleep(me * 2L);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                }
                out[me] = cache.apply(SYMBOL);
            });
        }

        for (Thread x : w) x.start();
        for (Thread x : w) x.join();

        return out;
    }

    static boolean same(String[] got) {
        for (String s : got) {
            if (s == null || !s.equals(got[0])) return false;
        }
        return true;
    }
}
