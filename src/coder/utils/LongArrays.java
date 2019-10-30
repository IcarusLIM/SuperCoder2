package coder.utils;

import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.ForkJoinTask;
import java.util.concurrent.RecursiveAction;

public class LongArrays {

    public static long[] mergeArray(long[][] a, int size) {
        long[] res = new long[size];
        ForkJoinPool.commonPool().invoke(new MergeTask(a, res, size));
//        int rowLength = a[0].length;
//        int rows = size / rowLength;
//        for (int i = 0; i<rows; i++){
//            System.arraycopy(a[i], 0, res, i*rowLength, rowLength);
//        }
//        if(a[rows]!=null){
//            System.arraycopy(a[rows], 0, res, rows*rowLength, size%rowLength);
//        }
        return res;
    }

    private static class MergeTask extends RecursiveAction{
        long[][] src;
        long[] dest;
        int size, row, offset, len;
        boolean outer;
        public MergeTask(long[][] src, long[] dest, int size){
            this.src = src;
            this.dest = dest;
            this.size = size;
            outer = true;
        }

        public MergeTask(long[][] src, long[] dest, int row, int offset, int len){
            this.src = src;
            this.dest = dest;
            this.row = row;
            this.offset = offset;
            this.len = len;
            outer =false;
        }

        @Override
        protected void compute() {
            if(outer){
                int rowLength = src[0].length;
                int rows = size / rowLength;
                ForkJoinTask[] tasks = new ForkJoinTask[rows];
                for (int i = 0; i<rows; i++){
                    tasks[i] = new MergeTask(src, dest, i, i*rowLength, rowLength).fork();
                }
                if(src[rows]!=null){
                    System.arraycopy(src[rows], 0, dest, rows*rowLength, size%rowLength);
                }
                for(int i=0; i<rows; i++){
                    tasks[i].join();
                }
            }else {
                System.arraycopy(src[row], 0, dest, offset, len);
            }
        }
    }

}
