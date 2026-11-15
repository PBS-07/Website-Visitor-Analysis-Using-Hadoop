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
 * FR-12: Status Code and Error Analysis
 * Aggregates counts by HTTP status code, status class (2xx, 3xx, 4xx, 5xx),
 * and tracks frequencies of failing error URLs.
 */
public class StatusCodeJob extends Configured implements Tool {

    public static class StatusMapper extends Mapper<LongWritable, Text, Text, LongWritable> {
        private final static LongWritable ONE = new LongWritable(1);
        private final Text outKey = new Text();

        @Override
        protected void map(LongWritable key, Text value, Context context) throws IOException, InterruptedException {
            LogRecord record = LogRecord.fromTabSeparatedString(value.toString());
            if (record != null) {
                int status = record.getStatusCode();
                String statusClass = (status / 100) + "xx";

                // Emit status code
                outKey.set("STATUS:" + status);
                context.write(outKey, ONE);

                // Emit status class
                outKey.set("CLASS:" + statusClass);
                context.write(outKey, ONE);

                // If error (4xx or 5xx), emit failing URL
                if (status >= 400) {
                    outKey.set("ERROR_URL:" + record.getUrl() + " [Status " + status + "]");
                    context.write(outKey, ONE);
                }
            }
        }
    }

    public static class StatusReducer extends Reducer<Text, LongWritable, Text, LongWritable> {
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
            System.err.println("Usage: StatusCodeJob <inputPath> <outputPath>");
            return 1;
        }

        Configuration conf = getConf();
        Job job = Job.getInstance(conf, "Website Visitor Analysis: Status Codes & Error URLs");
        job.setJarByClass(StatusCodeJob.class);

        job.setInputFormatClass(TextInputFormat.class);
        job.setOutputFormatClass(TextOutputFormat.class);

        job.setMapperClass(StatusMapper.class);
        job.setCombinerClass(StatusReducer.class);
        job.setReducerClass(StatusReducer.class);

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
        int exitCode = ToolRunner.run(new Configuration(), new StatusCodeJob(), args);
        System.exit(exitCode);
    }
}

