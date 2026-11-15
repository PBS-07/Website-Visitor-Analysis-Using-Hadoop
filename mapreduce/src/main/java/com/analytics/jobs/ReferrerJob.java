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
 * FR-14: Referrer Analysis
 * Identifies top external traffic referrers and direct visits.
 */
public class ReferrerJob extends Configured implements Tool {

    public static class ReferrerMapper extends Mapper<LongWritable, Text, Text, LongWritable> {
        private final static LongWritable ONE = new LongWritable(1);
        private final Text refKey = new Text();

        @Override
        protected void map(LongWritable key, Text value, Context context) throws IOException, InterruptedException {
            LogRecord record = LogRecord.fromTabSeparatedString(value.toString());
            if (record != null && !record.isStaticAsset()) {
                String ref = record.getReferrer();
                if (ref == null || ref.trim().isEmpty() || ref.equals("-")) {
                    ref = "Direct / None";
                }
                refKey.set(ref);
                context.write(refKey, ONE);
            }
        }
    }

    public static class ReferrerReducer extends Reducer<Text, LongWritable, Text, LongWritable> {
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
            System.err.println("Usage: ReferrerJob <inputPath> <outputPath>");
            return 1;
        }

        Configuration conf = getConf();
        Job job = Job.getInstance(conf, "Website Visitor Analysis: Referrers");
        job.setJarByClass(ReferrerJob.class);

        job.setInputFormatClass(TextInputFormat.class);
        job.setOutputFormatClass(TextOutputFormat.class);

        job.setMapperClass(ReferrerMapper.class);
        job.setCombinerClass(ReferrerReducer.class);
        job.setReducerClass(ReferrerReducer.class);

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
        int exitCode = ToolRunner.run(new Configuration(), new ReferrerJob(), args);
        System.exit(exitCode);
    }
}

