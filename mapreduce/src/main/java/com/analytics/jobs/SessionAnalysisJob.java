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
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * FR-15 & FR-16: Session Analysis & Bounce Rate
 * Sessionizes visitor requests using a 30-minute inactivity window.
 * Computes average pages per session, average session duration, and bounce rate.
 */
public class SessionAnalysisJob extends Configured implements Tool {

    private static final DateTimeFormatter TIME_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    public static class SessionMapper extends Mapper<LongWritable, Text, Text, Text> {
        private final Text visitorKey = new Text();
        private final Text eventVal = new Text();

        @Override
        protected void map(LongWritable key, Text value, Context context) throws IOException, InterruptedException {
            LogRecord record = LogRecord.fromTabSeparatedString(value.toString());
            // Filter out bots and static assets for accurate human user session analytics
            if (record != null && !record.isStaticAsset() && !"Bot".equals(record.getDeviceType())) {
                visitorKey.set(record.getVisitorIpHash());
                eventVal.set(record.getEventTime() + "|" + record.getUrl());
                context.write(visitorKey, eventVal);
            }
        }
    }

    public static class SessionReducer extends Reducer<Text, Text, Text, Text> {
        private long grandTotalSessions = 0;
        private long grandTotalBounces = 0;
        private long grandTotalPageViews = 0;
        private long grandTotalDurationSeconds = 0;

        @Override
        protected void reduce(Text key, Iterable<Text> values, Context context) {
            List<String> events = new ArrayList<>();
            for (Text val : values) {
                events.add(val.toString());
            }

            // Sort events by timestamp
            Collections.sort(events);

            long sessionPages = 0;
            long sessionStartTime = 0;
            long lastEventTime = 0;

            for (String event : events) {
                String[] parts = event.split("\\|");
                if (parts.length < 2) continue;

                LocalDateTime dt = LocalDateTime.parse(parts[0], TIME_FORMATTER);
                long epochSecond = dt.toEpochSecond(ZoneOffset.UTC);

                if (sessionStartTime == 0) {
                    // First session for this visitor
                    sessionStartTime = epochSecond;
                    lastEventTime = epochSecond;
                    sessionPages = 1;
                } else if (epochSecond - lastEventTime > 1800) {
                    // 30-minute timeout reached: complete previous session
                    grandTotalSessions++;
                    grandTotalPageViews += sessionPages;
                    grandTotalDurationSeconds += (lastEventTime - sessionStartTime);
                    if (sessionPages == 1) {
                        grandTotalBounces++;
                    }

                    // Start new session
                    sessionStartTime = epochSecond;
                    lastEventTime = epochSecond;
                    sessionPages = 1;
                } else {
                    sessionPages++;
                    lastEventTime = epochSecond;
                }
            }

            // Close trailing session
            if (sessionStartTime > 0) {
                grandTotalSessions++;
                grandTotalPageViews += sessionPages;
                grandTotalDurationSeconds += (lastEventTime - sessionStartTime);
                if (sessionPages == 1) {
                    grandTotalBounces++;
                }
            }
        }

        @Override
        protected void cleanup(Context context) throws IOException, InterruptedException {
            double bounceRate = grandTotalSessions > 0 ? (double) grandTotalBounces / grandTotalSessions : 0.0;
            double avgPages = grandTotalSessions > 0 ? (double) grandTotalPageViews / grandTotalSessions : 0.0;
            double avgDuration = grandTotalSessions > 0 ? (double) grandTotalDurationSeconds / grandTotalSessions : 0.0;

            context.write(new Text("TOTAL_SESSIONS"), new Text(String.valueOf(grandTotalSessions)));
            context.write(new Text("TOTAL_BOUNCES"), new Text(String.valueOf(grandTotalBounces)));
            context.write(new Text("BOUNCE_RATE"), new Text(String.format("%.4f", bounceRate)));
            context.write(new Text("AVG_PAGES_PER_SESSION"), new Text(String.format("%.2f", avgPages)));
            context.write(new Text("AVG_SESSION_DURATION_SECONDS"), new Text(String.format("%.1f", avgDuration)));
        }
    }

    @Override
    public int run(String[] args) throws Exception {
        if (args.length < 2) {
            System.err.println("Usage: SessionAnalysisJob <inputPath> <outputPath>");
            return 1;
        }

        Configuration conf = getConf();
        Job job = Job.getInstance(conf, "Website Visitor Analysis: Sessionization & Bounce Rate");
        job.setJarByClass(SessionAnalysisJob.class);

        job.setInputFormatClass(TextInputFormat.class);
        job.setOutputFormatClass(TextOutputFormat.class);

        job.setMapperClass(SessionMapper.class);
        job.setReducerClass(SessionReducer.class);

        job.setOutputKeyClass(Text.class);
        job.setOutputValueClass(Text.class);

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
        int exitCode = ToolRunner.run(new Configuration(), new SessionAnalysisJob(), args);
        System.exit(exitCode);
    }
}

