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
import java.util.HashSet;
import java.util.Set;

/**
 * FR-10: Unique Visitors Per Day
 * Computes distinct visitor hashes per date partition.
 */
public class UniqueVisitorsJob extends Configured implements Tool {

    public static class UniqueVisitorsMapper extends Mapper<LongWritable, Text, Text, Text> {
        private final Text dateKey = new Text();
        private final Text visitorVal = new Text();

        @Override
        protected void map(LongWritable key, Text value, Context context) throws IOException, InterruptedException {
            LogRecord record = LogRecord.fromTabSeparatedString(value.toString());
            if (record != null && record.getEventTime().length() >= 10) {
                // Extract date YYYY-MM-DD
                String date = record.getEventTime().substring(0, 10);
                dateKey.set(date);
                visitorVal.set(record.getVisitorIpHash());
                context.write(dateKey, visitorVal);
            }
        }
    }

    public static class UniqueVisitorsReducer extends Reducer<Text, Text, Text, LongWritable> {
        private final LongWritable countWritable = new LongWritable();

        @Override
        protected void reduce(Text key, Iterable<Text> values, Context context)
                throws IOException, InterruptedException {
            Set<String> distinctVisitors = new HashSet<>();
            for (Text val : values) {
                distinctVisitors.add(val.toString());
            }
            countWritable.set(distinctVisitors.size());
            context.write(key, countWritable);
        }
    }

    @Override
    public int run(String[] args) throws Exception {
        if (args.length < 2) {
            System.err.println("Usage: UniqueVisitorsJob <inputPath> <outputPath>");
            return 1;
        }

        Configuration conf = getConf();
        Job job = Job.getInstance(conf, "Website Visitor Analysis: Unique Visitors Per Day");
        job.setJarByClass(UniqueVisitorsJob.class);

        job.setInputFormatClass(TextInputFormat.class);
        job.setOutputFormatClass(TextOutputFormat.class);

        job.setMapperClass(UniqueVisitorsMapper.class);
        job.setReducerClass(UniqueVisitorsReducer.class);

        job.setMapOutputKeyClass(Text.class);
        job.setMapOutputValueClass(Text.class);
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
        int exitCode = ToolRunner.run(new Configuration(), new UniqueVisitorsJob(), args);
        System.exit(exitCode);
    }
}

