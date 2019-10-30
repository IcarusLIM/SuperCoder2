package coder.utils.sort;

import java.util.concurrent.ForkJoinPool;

public class MultiLongArrays {

    static final int UNIT = 2;
    static final int MIN_ARRAY_SORT_GRAN = 1 << 13;

    static boolean isEqual(long[] a, int i1, int i2) {
        return isEqual(a, i1, a, i2);
    }

    static boolean isEqual(long[] a, int ia, long[] b, int ib) {
        return compareTo(a, ia, b, ib) == 0;
    }

    static boolean gt(long[] a, int i1, int i2) {
        return gt(a, i1, a, i2);
    }

    static boolean gt(long[] a, int ia, long[] b, int ib) {
        return compareTo(a, ia, b, ib) > 0;
    }

    static boolean gte(long[] a, int i1, int i2) {
        return gte(a, i1, a, i2);
    }

    static boolean gte(long[] a, int ia, long[] b, int ib) {
        return compareTo(a, ia, b, ib) >= 0;
    }

    static boolean lt(long[] a, int i1, int i2) {
        return lt(a, i1, a, i2);
    }

    static boolean lt(long[] a, int ia, long[] b, int ib) {
        return compareTo(a, ia, b, ib) < 0;
    }

    static boolean lte(long[] a, int i1, int i2) {
        return lte(a, i1, a, i2);
    }

    static boolean lte(long[] a, int ia, long[] b, int ib) {
        return compareTo(a, ia, b, ib) <= 0;
    }

    static int compareTo(long[] a, int ia, long[] b, int ib) {
        ia *= UNIT;
        ib *= UNIT;
        for (int j = 0; j < UNIT; j++) {
            if (a[ia + j] < b[ib + j]) {
                return -1;
            } else if (a[ia + j] > b[ib + j]) {
                return 1;
            } else {
                continue;
            }
        }
        return 0;
    }

    static void swap(long[] a, int i1, int i2) {
        i1 *= UNIT;
        i2 *= UNIT;
        for (int j = 0; j < UNIT; j++) {
            long tmp = a[i1 + j];
            a[i1 + j] = a[i2 + j];
            a[i2 + j] = tmp;
        }
    }

    static void assign(long[] r, long[] l) {
        assign(r, 0, l, 0);
    }

    static void assign(long[] a, int il, int ir) {
        assign(a, il, a, ir);
    }

    static void assign(long[] r, int ir, long[] l, int il) {
        ir *= UNIT;
        il *= UNIT;
        for (int j = 0; j < UNIT; j++) {
            r[ir + j] = l[il + j];
        }
    }

    static long[] copyTemp(long[] a, int i, long[] work) {
        i *= UNIT;
        for (int j = 0; j < UNIT; j++) {
            work[j] = a[i + j];
        }
        return work;
    }

    public static void parallelSort(long[] a) {
        int n = a.length / UNIT, p, g;
        if (n <= MIN_ARRAY_SORT_GRAN || (p = ForkJoinPool.getCommonPoolParallelism()) == 1) {
            DualPivotMultiQuickSort.sort(a);
        } else {
            new ParallelSortHelpers.FJLong.Sorter(
                    null, a, new long[a.length], 0, n, 0,
                    (g = n / (p << 2)) <= MIN_ARRAY_SORT_GRAN ? MIN_ARRAY_SORT_GRAN : g).invoke();
        }
    }

    public static int binarySearch(long[] a, long[] key) {
        return binarySearch(a, 0, a.length, key);
    }

    public static int binarySearch(long[] a, int fromIndex, int toIndex, long[] key) {
        if (a.length % UNIT != 0) {
            throw new RuntimeException("Length of the array to be sorted, must be n times of " + UNIT);
        }
        if(a.length==0){
            return -1;
        }
        return binarySearch0(a, fromIndex / UNIT, toIndex / UNIT, key);
    }

    private static int binarySearch0(long[] a, int fromIndex, int toIndex, long[] key) {
        int low = fromIndex;
        int high = toIndex - 1;
        while (low <= high) {
            int mid = (low + high) >>> 1;
            if (lt(a, mid, key, 0)) {
                low = mid + 1;
            } else if (gt(a, mid, key, 0)) {
                high = mid - 1;
            } else
                return mid * UNIT;
        }
        return -(low + 1) * UNIT;
    }


    public static int binarySearch(long[] a, long[] key, int[][] fastTable, int accuracy, int mask) {
        if (fastTable == null) {
            return binarySearch(a, key);
        }
        int value = (int) (key[0] >>> 32);
        int offset = Integer.MIN_VALUE; // offset = 2^23
        int index = ((value & mask) + offset) >>> (32 - accuracy);
        return binarySearch(a, fastTable[0][index], fastTable[1][index] + UNIT, key);
    }

    public static int[][] buildFastSearchTable(long[] a, int accuracy, int mask) {
        int step = 1 << (32 - accuracy);

        int[] headFastTable = new int[1 << accuracy];
        // init leftmost
        int last = Integer.MIN_VALUE & mask; // Integer.MIN_VALUE & 0xffff0000 (for accuracy == 16)
        headFastTable[0] = 0;
        for (int i = 0, j = 0; i < a.length; i += UNIT) {
            int current = (int) (a[i] >> 32);
            int differ = (last ^ current) >>> (32 - accuracy);
            if (differ == 0) {
                continue;
            }
            while (++j < headFastTable.length) {
                last += step;
                if (((last ^ current) >>> (32 - accuracy)) == 0) {
                    headFastTable[j] = i;
                    break;
                } else {
                    headFastTable[j] = headFastTable[j - 1];
                }
            }
            if (i + UNIT >= a.length) {
                while (++j < headFastTable.length) {
                    headFastTable[j] = headFastTable[j - 1];
                }
            }
        }

        int[] tailFastTable = new int[1 << accuracy];// init leftmost
        last = Integer.MAX_VALUE & mask;
        tailFastTable[tailFastTable.length - 1] = a.length - UNIT;
        for (int i = a.length - UNIT, j = tailFastTable.length - 1; i >= 0; i -= UNIT) {
            int current = (int) (a[i] >> 32);
            int differ = (last ^ current) >>> (32 - accuracy);
            if (differ == 0) {
                continue;
            }
            while (--j >= 0) {
                last -= step;
                if (((last ^ current) >>> (32 - accuracy)) == 0) {
                    tailFastTable[j] = i;
                    break;
                } else {
                    tailFastTable[j] = tailFastTable[j + 1];
                }
            }
            if (i == 0) {
                while (--j >= 0) {
                    tailFastTable[j] = tailFastTable[j + 1];
                }
            }
        }
        return new int[][]{headFastTable, tailFastTable};
    }
}
