package com.analytics.clean;

import org.apache.hadoop.io.NullWritable;
import org.apache.hadoop.io.Text;
import org.apache.hadoop.mapreduce.Reducer;

import java.io.IOException;

/**
 * Reducer for CleanJob.
 * Removes duplicate log records and outputs clean records into /weblogs/clean/.
 */
public class CleanReducer extends Reducer<Text, NullWritable, Text, NullWritable> {

    public enum DeduplicationCounters {
        UNIQUE_CLEAN_LINES,
        DUPLICATE_LINES_REMOVED
    }

    @Override
    protected void reduce(Text key, Iterable<NullWritable> values, Context context)
            throws IOException, InterruptedException {
        long count = 0;
        for (NullWritable ignored : values) {
            count++;
        }

        if (count > 1) {
            context.getCounter(DeduplicationCounters.DUPLICATE_LINES_REMOVED).increment(count - 1);
        }

        context.getCounter(DeduplicationCounters.UNIQUE_CLEAN_LINES).increment(1);
        context.write(key, NullWritable.get());
    }
}

