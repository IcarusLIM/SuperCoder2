package coder.utils.sort;

import static coder.utils.sort.MultiLongArrays.*;

public class DualPivotMultiQuickSort {
    private static final int MAX_RUN_COUNT = 67;
    private static final int QUICKSORT_THRESHOLD = 286;
    private static final int INSERTION_SORT_THRESHOLD = 47;


    public static void sort(long[] a) {
        if (a.length % UNIT != 0) {
            throw new RuntimeException("Length of the array to be sorted, must be n times of " + UNIT);
        }
        sort(a, 0, a.length / UNIT - 1, null, 0, 0);
    }

    public static void sort(long[] a, int left, int right) {
        if (a.length % UNIT != 0 || left % UNIT != 0 || right % UNIT != 0) {
            throw new RuntimeException("Length of the array to be sorted, must be n times of " + UNIT);
        }
        sort(a, left / UNIT, right / UNIT, null, 0, 0);
    }

    // all index and length shrink UNIT times
    static void sort(long[] a, int left, int right, long[] work, int workBase, int workLen) {
        // Use Quicksort on small arrays
        if (right - left < QUICKSORT_THRESHOLD) {
            sort(a, left, right, true, new long[UNIT], new long[UNIT], new long[UNIT]);
            return;
        }

        /*
         * Index run[i] is the start of i-th run
         * (ascending or descending sequence).
         */
        int[] run = new int[MAX_RUN_COUNT + 1];
        int count = 0;
        run[0] = left;

        // Check if the array is nearly sorted
        for (int k = left; k < right; run[count] = k) {
            // Equal items in the beginning of the sequence
            while (k < right && isEqual(a, k, k + 1))
                k++;
            if (k == right) break; // Sequence finishes with equal items
            if (lt(a, k, k + 1)) { // ascending
                while (++k <= right && lte(a, k - 1, k)) ;
            } else if (gt(a, k, k + 1)) { // descending
                while (++k <= right && gte(a, k - 1, k)) ;
                // Transform into an ascending sequence
                for (int lo = run[count] - 1, hi = k; ++lo < --hi; ) {
                    swap(a, lo, hi);
                }
            }

            // Merge a transformed descending sequence followed by an
            // ascending sequence
            if (run[count] > left && gte(a, run[count], run[count] - 1)) {
                count--;
            }

            /*
             * The array is not highly structured,
             * use Quicksort instead of merge sort.
             */
            if (count++ == MAX_RUN_COUNT) {
                sort(a, left, right, true, new long[UNIT], new long[UNIT], new long[UNIT]);
                return;
            }
        }

        // These invariants should hold true:
        //    run[0] = 0
        //    run[<last>] = right + 1; (terminator)
        if (count == 0) {
            // A single equal run
            return;
        } else if (count == 1 && run[count] > right) {
            // Either a single ascending or a transformed descending run.
            // Always check that a final run is a proper terminator, otherwise
            // we have an unterminated trailing run, to handle downstream.
            return;
        }
        right++;
        if (run[count] < right) {
            // Corner case: the final run is not a terminator. This may happen
            // if a final run is an equals run, or there is a single-element run
            // at the end. Fix up by adding a proper terminator at the end.
            // Note that we terminate with (right + 1), incremented earlier.
            run[++count] = right;
        }

        // Determine alternation base for merge
        byte odd = 0;
        for (int n = 1; (n <<= 1) < count; odd ^= 1) ;

        // Use or create temporary array b for merging
        long[] b;                 // temp array; alternates with a
        int ao, bo;              // array offsets from 'left'
        int blen = right - left; // space needed for b
        if (work == null || workLen < blen || workBase + blen > work.length / UNIT) {
            work = new long[blen * UNIT];
            workBase = 0;
        }
        if (odd == 0) {
            System.arraycopy(a, left * UNIT, work, workBase * UNIT, blen * UNIT);
            b = a;
            bo = 0;
            a = work;
            ao = workBase - left;
        } else {
            b = work;
            ao = 0;
            bo = workBase - left;
        }

        // Merging
        for (int last; count > 1; count = last) {
            for (int k = (last = 0) + 2; k <= count; k += 2) {
                int hi = run[k], mi = run[k - 1];
                for (int i = run[k - 2], p = i, q = mi; i < hi; ++i) {
                    if (q >= hi || p < mi && lte(a, p + ao, q + ao)) {
                        assign(b, i + bo, a, p++ + ao);
                    } else {
                        assign(b, i + bo, a, q++ + ao);
                    }
                }
                run[++last] = hi;
            }
            if ((count & 1) != 0) {
                for (int i = right, lo = run[count - 1]; --i >= lo;
                     assign(b, i + bo, a, i + ao)
                )
                    ;
                run[++last] = right;
            }
            long[] t = a;
            a = b;
            b = t;
            int o = ao;
            ao = bo;
            bo = o;
        }
    }

    // be very careful with these three work array
    private static void sort(long[] a, int left, int right, boolean leftmost, long[] w1, long[] w2, long[] w3) {
        int length = right - left + 1;

        // Use insertion sort on tiny arrays
        if (length < INSERTION_SORT_THRESHOLD) {
            if (leftmost) {
                /*
                 * Traditional (without sentinel) insertion sort,
                 * optimized for server VM, is used in case of
                 * the leftmost part.
                 */
                for (int i = left, j = i; i < right; j = ++i) {
                    long[] ai = copyTemp(a, i + 1, w1);
                    while (lt(ai, 0, a, j)) {
                        assign(a, j + 1, a, j);
                        if (j-- == left) {
                            break;
                        }
                    }
                    assign(a, j + 1, ai, 0);
                }
            } else {
                /*
                 * Skip the longest ascending sequence.
                 */
                do {
                    if (left >= right) {
                        return;
                    }
                } while (gte(a, ++left, a, left - 1));

                /*
                 * Every element from adjoining part plays the role
                 * of sentinel, therefore this allows us to avoid the
                 * left range check on each iteration. Moreover, we use
                 * the more optimized algorithm, so called pair insertion
                 * sort, which is faster (in the context of Quicksort)
                 * than traditional implementation of insertion sort.
                 */
                for (int k = left; ++left <= right; k = ++left) {
                    long[] a1 = copyTemp(a, k, w1), a2 = copyTemp(a, left, w2);
                    if (lt(a1, 0, a2, 0)) {
                        assign(a2, a1);
                        assign(a1, 0, a, left);
                    }
                    while (lt(a1, 0, a, --k)) {
                        assign(a, k + 2, a, k);
                    }
                    assign(a, ++k + 1, a1, 0);

                    while (lt(a2, 0, a, --k)) {
                        assign(a, k + 1, a, k);
                    }
                    assign(a, k + 1, a2, 0);
                }
                long[] last = copyTemp(a, right, w1);

                while (lt(last, 0, a, --right)) {
                    assign(a, right + 1, a, right);
                }
                assign(a, right + 1, last, 0);
            }
            return;
        }

        // Inexpensive approximation of length / 7
        int seventh = (length >> 3) + (length >> 6) + 1;

        /*
         * Sort five evenly spaced elements around (and including) the
         * center element in the range. These elements will be used for
         * pivot selection as described below. The choice for spacing
         * these elements was empirically determined to work well on
         * a wide variety of inputs.
         */
        int e3 = (left + right) >>> 1; // The midpoint
        int e2 = e3 - seventh;
        int e1 = e2 - seventh;
        int e4 = e3 + seventh;
        int e5 = e4 + seventh;

        // Sort these elements using insertion sort
        if (lt(a, e2, e1)) {
            swap(a, e1, e2);
        }
        if (lt(a, e3, e2)) {
            swap(a, e2, e3);
            if (lt(a, e2, e1)) {
                swap(a, e1, e2);
            }
        }
        if (lt(a, e4, e3)) {
            swap(a, e3, e4);
            if (lt(a, e3, e2)) {
                swap(a, e2, e3);
                if (lt(a, e2, e1)) {
                    swap(a, e1, e2);
                }
            }
        }
        if (lt(a, e5, e4)) {
            swap(a, e4, e5);
            if (lt(a, e4, e3)) {
                swap(a, e3, e4);
                if (lt(a, e3, e2)) {
                    swap(a, e2, e3);
                    if (lt(a, e2, e1)) {
                        swap(a, e1, e2);
                    }
                }
            }
        }

        // Pointers
        int less = left;  // The index of the first element of center part
        int great = right; // The index before the first element of right part

        if (!isEqual(a, e1, e2) && !isEqual(a, e2, e3) && !isEqual(a, e3, e4) && !isEqual(a, e4, e5)) {
            /*
             * Use the second and fourth of the five sorted elements as pivots.
             * These values are inexpensive approximations of the first and
             * second terciles of the array. Note that pivot1 <= pivot2.
             */
            long[] pivot1 = copyTemp(a, e2, w1);
            long[] pivot2 = copyTemp(a, e4, w2);

            /*
             * The first and the last elements to be sorted are moved to the
             * locations formerly occupied by the pivots. When partitioning
             * is complete, the pivots are swapped back into their final
             * positions, and excluded from subsequent sorting.
             */
            assign(a, e2, left);
            assign(a, e4, right);

            /*
             * Skip elements, which are less or greater than pivot values.
             */
            while (lt(a, ++less, pivot1, 0)) ;
            while (gt(a, --great, pivot2, 0)) ;

            outer:
            for (int k = less - 1; ++k <= great; ) {
                long[] ak = copyTemp(a, k, w3);
                if (lt(ak, 0, pivot1, 0)) { // Move a[k] to left part
                    swap(a, k, less);
                    ++less;
                } else if (gt(ak, 0, pivot2, 0)) { // Move a[k] to right part
                    while (gt(a, great, pivot2, 0)) {
                        if (great-- == k) {
                            break outer;
                        }
                    }
                    if (lt(a, great, pivot1, 0)) { // a[great] <= pivot2
                        assign(a, k, less);
                        assign(a, less, great);
                        ++less;
                    } else { // pivot1 <= a[great] <= pivot2
                        assign(a, k, great);
                    }
                    /*
                     * Here and below we use "a[i] = b; i--;" instead
                     * of "a[i--] = b;" due to performance issue.
                     */
                    assign(a, great, ak, 0);
                    --great;
                }
            }

            // Swap pivots into their final positions
            assign(a, left, less - 1);
            assign(a, less - 1, pivot1, 0);
            assign(a, right, great + 1);
            assign(a, great + 1, pivot2, 0);

            // Sort left and right parts recursively, excluding known pivots
            sort(a, left, less - 2, leftmost, w1, w2, w3);
            sort(a, great + 2, right, false, w1, w2, w3);

            /*
             * If center part is too large (comprises > 4/7 of the array),
             * swap internal pivot values to ends.
             */
            if (less < e1 && e5 < great) {
                /*
                 * Skip elements, which are equal to pivot values.
                 */
                while (isEqual(a, less, pivot1, 0)) {
                    ++less;
                }

                while (isEqual(a, great, pivot2, 0)) {
                    --great;
                }

                // exclude equals
                outer:
                for (int k = less - 1; ++k <= great; ) {
                    long[] ak = copyTemp(a, k, w3);
                    if (isEqual(ak, 0, pivot1, 0)) { // Move a[k] to left part
                        swap(a, less, k);
                        ++less;
                    } else if (isEqual(ak, 0, pivot2, 0)) { // Move a[k] to right part
                        while (isEqual(a, great, pivot2, 0)) {
                            if (great-- == k) {
                                break outer;
                            }
                        }
                        if (isEqual(a, great, pivot1, 0)) { // a[great] < pivot2
                            assign(a, k, less);
                            /*
                             * Even though a[great] equals to pivot1, the
                             * assignment a[less] = pivot1 may be incorrect,
                             * if a[great] and pivot1 are floating-point zeros
                             * of different signs. Therefore in float and
                             * double sorting methods we have to use more
                             * accurate assignment a[less] = a[great].
                             */
                            assign(a, less, pivot1, 0);
                            ++less;
                        } else { // pivot1 < a[great] < pivot2
                            assign(a, k, great);
                        }
                        assign(a, great, ak, 0);
                        --great;
                    }
                }
            }

            // Sort center part recursively
            sort(a, less, great, false, w1, w2, w3);

        } else { // Partitioning with one pivot
            /*
             * Use the third of the five sorted elements as pivot.
             * This value is inexpensive approximation of the median.
             */
            long[] pivot = copyTemp(a, e3, w1);

            for (int k = less; k <= great; ++k) {
                if (isEqual(a, k, pivot, 0)) {
                    continue;
                }
                long[] ak = copyTemp(a, k, w3);
                if (lt(a, k, pivot, 0)) { // Move a[k] to left part
                    swap(a, k, less);
                    ++less;
                } else { // a[k] > pivot - Move a[k] to right part
                    while (gt(a, great, pivot, 0)) {
                        --great;
                    }
                    if (lt(a, great, pivot, 0)) { // a[great] <= pivot
                        assign(a, k, less);
                        assign(a, less, great);
                        ++less;
                    } else { // a[great] == pivot
                        /*
                         * Even though a[great] equals to pivot, the
                         * assignment a[k] = pivot may be incorrect,
                         * if a[great] and pivot are floating-point
                         * zeros of different signs. Therefore in float
                         * and double sorting methods we have to use
                         * more accurate assignment a[k] = a[great].
                         */
                        assign(a, k, pivot, 0);
                    }
                    assign(a, great, ak, 0);
                    --great;
                }
            }

            /*
             * Sort left and right parts recursively.
             * All elements from center part are equal
             * and, therefore, already sorted.
             */
            sort(a, left, less - 1, leftmost, w1, w2, w3);
            sort(a, great + 1, right, false, w1, w2, w3);
        }
    }
}

