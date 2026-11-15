package com.analytics.clean;

import com.analytics.model.LogRecord;
import com.analytics.parser.LogParser;
import org.apache.hadoop.io.LongWritable;
import org.apache.hadoop.io.NullWritable;
import org.apache.hadoop.io.Text;
import org.apache.hadoop.mapreduce.Mapper;
import org.apache.hadoop.mapreduce.lib.output.MultipleOutputs;

import java.io.IOException;

/**
 * Mapper for CleanJob.
 * Parses raw logs with LogParser, extracts metadata, routes rejected malformed
 * records via MultipleOutputs, and emits valid records for deduplication.
 */
public class CleanMapper extends Mapper<LongWritable, Text, Text, NullWritable> {

    public enum DataQualityCounters {
        TOTAL_LINES,
        VALID_PARSED_LINES,
        MALFORMED_REJECTED_LINES,
        STATIC_ASSET_LINES
    }

    private MultipleOutputs<Text, NullWritable> multipleOutputs;
    private final Text outputKey = new Text();
    private final Text rejectedValue = new Text();

    @Override
    protected void setup(Context context) {
        multipleOutputs = new MultipleOutputs<>(context);
    }

    @Override
    protected void map(LongWritable key, Text value, Context context) throws IOException, InterruptedException {
        context.getCounter(DataQualityCounters.TOTAL_LINES).increment(1);
        String line = value.toString();

        LogRecord record = LogParser.parseLine(line);
        if (record == null) {
            // Malformed record: route to rejected output and count
            context.getCounter(DataQualityCounters.MALFORMED_REJECTED_LINES).increment(1);
            rejectedValue.set(line);
            multipleOutputs.write("rejected", rejectedValue, NullWritable.get(), "rejected/part");
            return;
        }

        context.getCounter(DataQualityCounters.VALID_PARSED_LINES).increment(1);
        if (record.isStaticAsset()) {
            context.getCounter(DataQualityCounters.STATIC_ASSET_LINES).increment(1);
        }

        // Emit tab-separated clean record as key for deduplication in reducer
        outputKey.set(record.toTabSeparatedString());
        context.write(outputKey, NullWritable.get());
    }

    @Override
    protected void cleanup(Context context) throws IOException, InterruptedException {
        if (multipleOutputs != null) {
            multipleOutputs.close();
        }
    }
}

