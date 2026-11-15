package com.analytics;

import com.analytics.clean.CleanJob;
import com.analytics.jobs.*;
import org.apache.hadoop.util.ProgramDriver;

/**
 * Main ProgramDriver routing commands to specific MapReduce jobs.
 */
public class Driver {

    public static void main(String[] args) {
        ProgramDriver pgd = new ProgramDriver();
        try {
            pgd.addClass("clean", CleanJob.class, "Parses raw logs into cleaned format and routes malformed records to /weblogs/rejected");
            pgd.addClass("hits", HitsPerPageJob.class, "FR-9: Computes hit counts per page (Top-N)");
            pgd.addClass("visitors", UniqueVisitorsJob.class, "FR-10: Computes unique visitors per day");
            pgd.addClass("traffic", TrafficOverTimeJob.class, "FR-11: Computes traffic volumes over hourly buckets");
            pgd.addClass("status", StatusCodeJob.class, "FR-12: Analyzes HTTP status codes and top error URLs");
            pgd.addClass("geodevice", GeoDeviceJob.class, "FR-13: Breakdown across Country, Device, Browser, and OS");
            pgd.addClass("referrers", ReferrerJob.class, "FR-14: Analyzes top traffic sources and referrers");
            pgd.addClass("sessions", SessionAnalysisJob.class, "FR-15 & FR-16: Performs 30-min sessionization and computes bounce rate");

            pgd.driver(args);
        } catch (Throwable e) {
            e.printStackTrace();
            System.exit(1);
        }
    }
}

