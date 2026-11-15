package com.analytics.clean;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.conf.Configured;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.io.NullWritable;
import org.apache.hadoop.io.Text;
import org.apache.hadoop.mapreduce.Counters;
import org.apache.hadoop.mapreduce.Job;
import org.apache.hadoop.mapreduce.lib.input.FileInputFormat;
import org.apache.hadoop.mapreduce.lib.input.TextInputFormat;
import org.apache.hadoop.mapreduce.lib.output.FileOutputFormat;
import org.apache.hadoop.mapreduce.lib.output.MultipleOutputs;
import org.apache.hadoop.mapreduce.lib.output.TextOutputFormat;
import org.apache.hadoop.util.Tool;
import org.apache.hadoop.util.ToolRunner;

/**
 * CleanJob Driver.
 * Parses raw logs from HDFS /weblogs/raw/, writes cleaned records to /weblogs/clean/,
 * routes malformed records to /weblogs/rejected/, and tracks data quality counters.
 */
public class CleanJob extends Configured implements Tool {

    @Override
    public int run(String[] args) throws Exception {
        if (args.length < 2) {
            System.err.println("Usage: CleanJob <inputPath> <outputPath>");
            return 1;
        }

        String inputPathStr = args[0];
        String outputPathStr = args[1];

        Configuration conf = getConf();
        Job job = Job.getInstance(conf, "Website Visitor Analysis: Log Clean & Parse");
        job.setJarByClass(CleanJob.class);

        job.setInputFormatClass(TextInputFormat.class);
        job.setOutputFormatClass(TextOutputFormat.class);

        job.setMapperClass(CleanMapper.class);
        job.setReducerClass(CleanReducer.class);

        job.setMapOutputKeyClass(Text.class);
        job.setMapOutputValueClass(NullWritable.class);
        job.setOutputKeyClass(Text.class);
        job.setOutputValueClass(NullWritable.class);

        // Configure MultipleOutputs for rejected/malformed lines
        MultipleOutputs.addNamedOutput(job, "rejected", TextOutputFormat.class, Text.class, NullWritable.class);

        Path inputPath = new Path(inputPathStr);
        Path outputPath = new Path(outputPathStr);

        FileInputFormat.addInputPath(job, inputPath);
        FileOutputFormat.setOutputPath(job, outputPath);

        // Idempotency: Remove existing output path if present
        FileSystem fs = outputPath.getFileSystem(conf);
        if (fs.exists(outputPath)) {
            System.out.println("Output path " + outputPath + " already exists. Removing for idempotency...");
            fs.delete(outputPath, true);
        }

        boolean success = job.waitForCompletion(true);
        if (success) {
            Counters counters = job.getCounters();
            System.out.println("=================================================");
            System.out.println("CleanJob Data Quality & Deduplication Metrics:");
            System.out.println("  TOTAL_LINES:               " +
                    counters.findCounter(CleanMapper.DataQualityCounters.TOTAL_LINES).getValue());
            System.out.println("  VALID_PARSED_LINES:        " +
                    counters.findCounter(CleanMapper.DataQualityCounters.VALID_PARSED_LINES).getValue());
            System.out.println("  MALFORMED_REJECTED_LINES:  " +
                    counters.findCounter(CleanMapper.DataQualityCounters.MALFORMED_REJECTED_LINES).getValue());
            System.out.println("  STATIC_ASSET_LINES:        " +
                    counters.findCounter(CleanMapper.DataQualityCounters.STATIC_ASSET_LINES).getValue());
            System.out.println("  UNIQUE_CLEAN_LINES:        " +
                    counters.findCounter(CleanReducer.DeduplicationCounters.UNIQUE_CLEAN_LINES).getValue());
            System.out.println("  DUPLICATE_LINES_REMOVED:   " +
                    counters.findCounter(CleanReducer.DeduplicationCounters.DUPLICATE_LINES_REMOVED).getValue());
            System.out.println("=================================================");
            return 0;
        } else {
            System.err.println("CleanJob failed!");
            return 1;
        }
    }

    public static void main(String[] args) throws Exception {
        int exitCode = ToolRunner.run(new Configuration(), new CleanJob(), args);
        System.exit(exitCode);
    }
}

