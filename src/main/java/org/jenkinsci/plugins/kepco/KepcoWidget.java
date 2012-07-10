package org.jenkinsci.plugins.kepco;

import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import hudson.Extension;
import hudson.model.Hudson;
import hudson.model.PeriodicWork;
import hudson.widgets.Widget;
import org.apache.commons.io.IOUtils;

import static java.util.regex.Pattern.compile;

@Extension
public class KepcoWidget extends Widget {

    private Date lastUpdated;

    private List<UsageCondition> usages;

    private int maxPowerOfDay;

    public Date getLastUpdated() {
        return lastUpdated;
    }

    public void setLastUpdated(Date lastUpdated) {
        this.lastUpdated = lastUpdated;
    }

    public List<UsageCondition> getUsages() {
        return usages;
    }

    public void setUsages(List<UsageCondition> usages) {
        this.usages = usages;
    }

    public static class UsageCondition {

        private Date date;
        private int today;
        private int rate;

        public UsageCondition(Date date, int today, int rate) {
            this.date = date;
            this.today = today;
            this.rate = rate;
        }

        public Date getDate() {
            return date;
        }

        public int getToday() {
            return today;
        }

        public int getRate() {
            return rate;
        }
    }

    @Extension
    public static class CsvDownloader extends PeriodicWork {

        private static final String CSV_URL = "http://www.kepco.co.jp/yamasou/juyo1_kansai.csv";

        private static final Pattern HEADER_PATTERN = compile("(\\d{4}/\\d{1,2}/\\d{1,2} \\d{1,2}:\\d{1,2}) UPDATE,,,");

        private static final Pattern DATA_PATTERN = compile("(\\d{4}/\\d{1,2}/\\d{1,2}),(\\d{1,2}:\\d{1,2}),(\\d+),(\\d+),(\\d+),(\\d+)");

        @Override
        public long getRecurrencePeriod() {
            return 10 * 60 * 1000;
        }

        @Override
        public long getInitialDelay() {
            return 0;
        }

        @Override
        protected void doRun() throws Exception {
            boolean isFirst = true;
            DateFormat fmt = new SimpleDateFormat("yyyy/M/d H:m");
            Date lastUpdated = null;
            Integer maxPowerOfDay = null;
            List<UsageCondition> usages = new ArrayList<UsageCondition>();

            for (String line : loadCsv().split("\r?\n")) {
                if (isFirst) {
                    Matcher h = HEADER_PATTERN.matcher(line);
                    if (h.matches()) {
                        lastUpdated = fmt.parse(h.group(1));
                    }
                    isFirst = false;
                } else {
                    Matcher d = DATA_PATTERN.matcher(line);
                    if (d.matches()) {
                        Date date = fmt.parse(String.format("%s %s", d.group(1), d.group(2)));
                        Date start = fmt.parse(String.format("%s 9:00", d.group(1)));
                        if (date.before(start)) {
                            continue;
                        }

                        int actual = Integer.parseInt(d.group(3));
                        usages.add(new UsageCondition(
                                date,
                                actual == 0 ? Integer.parseInt(d.group(4)) : actual,
                                Integer.parseInt(d.group(6))));
                    }
                }
            }
            if (lastUpdated != null) {
                for (Widget w : Hudson.getInstance().getWidgets()) {
                    if (w instanceof KepcoWidget) {
                        KepcoWidget tw = (KepcoWidget) w;
                        tw.setLastUpdated(lastUpdated);
                        tw.setUsages(usages);
                    }
                }
            }
        }

        protected String loadCsv() {
            InputStream is = null;
            try {
                is = new URL(CSV_URL).openStream();
                return IOUtils.toString(is, "Shift_JIS");
            } catch (MalformedURLException e) {
                throw new RuntimeException(e);
            } catch (IOException e) {
                throw new RuntimeException(e);
            } finally {
                IOUtils.closeQuietly(is);
            }
        }
    }
}
