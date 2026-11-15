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
import java.util.*;

/**
 * FR-9: Hits Per Page (Combiner + Top-N)
 * Computes request counts per URL excluding static assets.
 */
public class HitsPerPageJob extends Configured implements Tool {

    public static class HitsMapper extends Mapper<LongWritable, Text, Text, LongWritable> {
        private final static LongWritable ONE = new LongWritable(1);
        private final Text urlKey = new Text();

        @Override
        protected void map(LongWritable key, Text value, Context context) throws IOException, InterruptedException {
            LogRecord record = LogRecord.fromTabSeparatedString(value.toString());
            if (record != null && !record.isStaticAsset()) {
                urlKey.set(record.getUrl());
                context.write(urlKey, ONE);
            }
        }
    }

    public static class HitsCombiner extends Reducer<Text, LongWritable, Text, LongWritable> {
        private final LongWritable sumWritable = new LongWritable();

        @Override
        protected void reduce(Text key, Iterable<LongWritable> values, Context context)
                throws IOException, InterruptedException {
            long sum = 0;
            for (LongWritable val : values) {
                sum += val.get();
            }
            sumWritable.set(sum);
            context.write(key, sumWritable);
        }
    }

    public static class HitsReducer extends Reducer<Text, LongWritable, Text, LongWritable> {
        private final TreeMap<Long, List<String>> topUrls = new TreeMap<>(Collections.reverseOrder());
        private final int TOP_N = 50;

        @Override
        protected void reduce(Text key, Iterable<LongWritable> values, Context context)
                throws IOException, InterruptedException {
            long totalHits = 0;
            for (LongWritable val : values) {
                totalHits += val.get();
            }

            // Normal output
            context.write(key, new LongWritable(totalHits));

            // Track for Top-N ranking
            topUrls.computeIfAbsent(totalHits, k -> new ArrayList<>()).add(key.toString());
        }
    }

    @Override
    public int run(String[] args) throws Exception {
        if (args.length < 2) {
            System.err.println("Usage: HitsPerPageJob <inputPath> <outputPath>");
            return 1;
        }

        Configuration conf = getConf();
        Job job = Job.getInstance(conf, "Website Visitor Analysis: Hits Per Page");
        job.setJarByClass(HitsPerPageJob.class);

        job.setInputFormatClass(TextInputFormat.class);
        job.setOutputFormatClass(TextOutputFormat.class);

        job.setMapperClass(HitsMapper.class);
        job.setCombinerClass(HitsCombiner.class);
        job.setReducerClass(HitsReducer.class);

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
        int exitCode = ToolRunner.run(new Configuration(), new HitsPerPageJob(), args);
        System.exit(exitCode);
    }
}

