package com.analytics.jobs;

import com.analytics.model.LogRecord;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.conf.Configured;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.io.LongWritable;
import org.apache.hadoop.io.Text;
import org.apache.hadoop.mapreduce.Job;
import org.apache.hadoop.mapreduce.Mapper;
import org.apache.hadoop.mapreduce.Reducer;
import org.apache.hadoop.mapreduce.lib.input.FileInputFormat;
import org.apache.hadoop.mapreduce.lib.input.TextInputFormat;
import org.apache.hadoop.mapreduce.lib.output.FileOutputFormat;
import org.apache.hadoop.mapreduce.lib.output.TextOutputFormat;
import org.apache.hadoop.util.Tool;
import org.apache.hadoop.util.ToolRunner;

import java.io.IOException;

/**
 * FR-11: Traffic Over Time
 * Computes request volumes grouped by hourly time buckets.
 */
public class TrafficOverTimeJob extends Configured implements Tool {

    public static class TrafficMapper extends Mapper<LongWritable, Text, Text, LongWritable> {
        private final static LongWritable ONE = new LongWritable(1);
        private final Text bucketKey = new Text();

        @Override
        protected void map(LongWritable key, Text value, Context context) throws IOException, InterruptedException {
            LogRecord record = LogRecord.fromTabSeparatedString(value.toString());
            if (record != null && record.getEventTime().length() >= 13) {
                // Hourly bucket format: YYYY-MM-DD HH:00
                String hourBucket = record.getEventTime().substring(0, 13) + ":00";
                bucketKey.set(hourBucket);
                context.write(bucketKey, ONE);
            }
        }
    }

    public static class TrafficReducer extends Reducer<Text, LongWritable, Text, LongWritable> {
        private final LongWritable sumWritable = new LongWritable();

        @Override
        protected void reduce(Text key, Iterable<LongWritable> values, Context context)
                throws IOException, InterruptedException {
            long total = 0;
            for (LongWritable val : values) {
                total += val.get();
            }
            sumWritable.set(total);
            context.write(key, sumWritable);
        }
    }

    @Override
    public int run(String[] args) throws Exception {
        if (args.length < 2) {
            System.err.println("Usage: TrafficOverTimeJob <inputPath> <outputPath>");
            return 1;
        }

        Configuration conf = getConf();
        Job job = Job.getInstance(conf, "Website Visitor Analysis: Traffic Over Time");
        job.setJarByClass(TrafficOverTimeJob.class);

        job.setInputFormatClass(TextInputFormat.class);
        job.setOutputFormatClass(TextOutputFormat.class);

        job.setMapperClass(TrafficMapper.class);
        job.setCombinerClass(TrafficReducer.class);
        job.setReducerClass(TrafficReducer.class);

        job.setOutputKeyClass(Text.class);
        job.setOutputValueClass(LongWritable.class);

        Path inputPath = new Path(args[0]);
        Path outputPath = new Path(args[1]);

        FileInputFormat.addInputPath(job, inputPath);
        FileOutputFormat.setOutputPath(job, outputPath);

        // Idempotency
        FileSystem fs = outputPath.getFileSystem(conf);
        if (fs.exists(outputPath)) {
            fs.delete(outputPath, true);
        }

        return job.waitForCompletion(true) ? 0 : 1;
    }

    public static void main(String[] args) throws Exception {
        int exitCode = ToolRunner.run(new Configuration(), new TrafficOverTimeJob(), args);
        System.exit(exitCode);
    }
}

