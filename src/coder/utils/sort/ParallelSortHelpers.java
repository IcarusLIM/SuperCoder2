package coder.utils.sort;

import jdk.jfr.Unsigned;

import java.util.concurrent.CountedCompleter;

import static coder.utils.sort.MultiLongArrays.*;

public class ParallelSortHelpers {

    static final class EmptyCompleter extends CountedCompleter<Void> {
        static final long serialVersionUID = 2446542900576103244L;

        EmptyCompleter(CountedCompleter<?> p) {
            super(p);
        }

        public final void compute() {
        }
    }

    static final class Relay extends CountedCompleter<Void> {
        static final long serialVersionUID = 2446542900576103244L;
        final CountedCompleter<?> task;

        Relay(CountedCompleter<?> task) {
            super(null, 1);
            this.task = task;
        }

        public final void compute() {
        }

        public final void onCompletion(CountedCompleter<?> t) {
            task.compute();
        }
    }

    static final class FJLong {
        static final class Sorter extends CountedCompleter<Void> {
            static final long serialVersionUID = 2446542900576103244L;
            final long[] a, w;
            final int base, size, wbase, gran;

            Sorter(CountedCompleter<?> par, long[] a, long[] w, int base,
                   int size, int wbase, int gran) {
                super(par);
                this.a = a;
                this.w = w;
                this.base = base;
                this.size = size;
                this.wbase = wbase;
                this.gran = gran;
            }

            public final void compute() {
                CountedCompleter<?> s = this;
                long[] a = this.a, w = this.w; // localize all params
                int b = this.base, n = this.size, wb = this.wbase, g = this.gran;
                while (n > g) {
                    int h = n >>> 1, q = h >>> 1, u = h + q; // quartiles
                    Relay fc = new Relay(new Merger(s, w, a, wb, h,
                            wb + h, n - h, b, g));
                    Relay rc = new Relay(new Merger(fc, a, w, b + h, q,
                            b + u, n - u, wb + h, g));
                    new Sorter(rc, a, w, b + u, n - u, wb + u, g).fork();
                    new Sorter(rc, a, w, b + h, q, wb + h, g).fork();
                    Relay bc = new Relay(new Merger(fc, a, w, b, q,
                            b + q, h - q, wb, g));
                    new Sorter(bc, a, w, b + q, h - q, wb + q, g).fork();
                    s = new EmptyCompleter(bc);
                    n = q;
                }
                DualPivotMultiQuickSort.sort(a, b, b + n - 1, w, wb, n);
                s.tryComplete();
            }
        }

        static final class Merger extends CountedCompleter<Void> {
            static final long serialVersionUID = 2446542900576103244L;
            final long[] a, w; // main and workspace arrays
            final int lbase, lsize, rbase, rsize, wbase, gran;

            Merger(CountedCompleter<?> par, long[] a, long[] w,
                   int lbase, int lsize, int rbase,
                   int rsize, int wbase, int gran) {
                super(par);
                this.a = a;
                this.w = w;
                this.lbase = lbase;
                this.lsize = lsize;
                this.rbase = rbase;
                this.rsize = rsize;
                this.wbase = wbase;
                this.gran = gran;
            }

            public final void compute() {
                long[] a = this.a, w = this.w; // localize all params
                int lb = this.lbase, ln = this.lsize, rb = this.rbase,
                        rn = this.rsize, k = this.wbase, g = this.gran;
                if (a == null || w == null || lb < 0 || rb < 0 || k < 0)
                    throw new IllegalStateException(); // hoist checks
                long[] copyWorker = new long[UNIT];
                for (int lh, rh; ; ) {  // split larger, find point in smaller
                    if (ln >= rn) {
                        if (ln <= g)
                            break;
                        rh = rn;
                        lh = ln >>> 1;
                        long[] split = copyTemp(a, lh + lb, copyWorker);
                        for (int lo = 0; lo < rh; ) {
                            int rm = (lo + rh) >>> 1;
                            if (lte(split, 0, a, rm + rb))
                                rh = rm;
                            else
                                lo = rm + 1;
                        }
                    } else {
                        if (rn <= g)
                            break;
                        lh = ln;
                        rh = rn >>> 1;
                        long[] split = copyTemp(a, rh + rb, copyWorker);
                        for (int lo = 0; lo < lh; ) {
                            int lm = (lo + lh) >>> 1;
                            if (lte(split, 0, a, lm + lb))
                                lh = lm;
                            else
                                lo = lm + 1;
                        }
                    }
                    ParallelSortHelpers.FJLong.Merger m = new ParallelSortHelpers.FJLong.Merger(this, a, w, lb + lh, ln - lh,
                            rb + rh, rn - rh,
                            k + lh + rh, g);
                    rn = rh;
                    ln = lh;
                    addToPendingCount(1);
                    m.fork();
                }

                int lf = lb + ln, rf = rb + rn; // index bounds
                while (lb < lf && rb < rf) {
                    if (lte(a, lb, rb)) {
                        assign(w, k, a, lb);
                        lb++;
                    } else {
                        assign(w, k, a, rb);
                        rb++;
                    }
                    k++;
                }
                if (rb < rf)
                    System.arraycopy(a, rb * UNIT, w, k * UNIT, (rf - rb) * UNIT);
                else if (lb < lf)
                    System.arraycopy(a, lb * UNIT, w, k * UNIT, (lf - lb) * UNIT);
                tryComplete();
            }
        }
    } // FJLong

}
