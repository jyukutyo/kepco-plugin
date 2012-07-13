package org.jenkinsci.plugins.kepco;

import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.net.MalformedURLException;
import java.net.Proxy;
import java.net.URL;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import hudson.Extension;
import hudson.model.Hudson;
import hudson.model.PeriodicWork;
import hudson.widgets.Widget;
import net.sf.json.JSONSerializer;
import org.apache.commons.io.IOUtils;

import static java.util.regex.Pattern.compile;

@Extension
public class KepcoWidget extends Widget {

    private String current;

    private String today;

    public String getCurrent() {
        return current;
    }

    public void setCurrent(String current) {
        this.current = current;
    }

    public String getToday() {
        return today;
    }

    public void setToday(String today) {
        this.today = today;
    }

    public static class UsageCondition {

        private int hour;
        private int forecastPeakPeriod;
        private int month;
        private String usageUpdated;
        private int forecastPeakUsage;
        private int year;
        private int usage;
        private int capacity;
        private int day;
        private int percentage;
        private int forecastUsage;

        public int getForecastUsage() {
            return forecastUsage;
        }

        public void setForecastUsage(int forecastUsage) {
            this.forecastUsage = forecastUsage;
        }

        public int getPercentage() {
            return percentage;
        }

        public void setPercentage(int percentage) {
            this.percentage = percentage;
        }

        public int getHour() {
            return hour;
        }

        public void setHour(int hour) {
            this.hour = hour;
        }

        public int getForecastPeakPeriod() {
            return forecastPeakPeriod;
        }

        public void setForecastPeakPeriod(int forecastPeakPeriod) {
            this.forecastPeakPeriod = forecastPeakPeriod;
        }

        public int getMonth() {
            return month;
        }

        public void setMonth(int month) {
            this.month = month;
        }

        public String getUsageUpdated() {
            return usageUpdated;
        }

        public void setUsageUpdated(String usageUpdated) {
            this.usageUpdated = usageUpdated;
        }

        public int getForecastPeakUsage() {
            return forecastPeakUsage;
        }

        public void setForecastPeakUsage(int forecastPeakUsage) {
            this.forecastPeakUsage = forecastPeakUsage;
        }

        public int getYear() {
            return year;
        }

        public void setYear(int year) {
            this.year = year;
        }

        public int getUsage() {
            return usage;
        }

        public void setUsage(int usage) {
            this.usage = usage;
        }

        public int getCapacity() {
            return capacity;
        }

        public void setCapacity(int capacity) {
            this.capacity = capacity;
        }

        public int getDay() {
            return day;
        }

        public void setDay(int day) {
            this.day = day;
        }
    }

    @Extension
    public static class CsvDownloader extends PeriodicWork {

        private static final String CSV_URL = "http://www.kepco.co.jp/yamasou/juyo1_kansai.csv";

        private static final Pattern HEADER_PATTERN = compile("(\\d{4}/\\d{1,2}/\\d{1,2} \\d{1,2}:\\d{1,2}) UPDATE,,,");

        private static final Pattern PEAK_PATTERN = compile("(\\d+),(\\d{1,2}:\\d{1,2})〜(\\d{1,2}:\\d{1,2}),\\d{1,2}/\\d{1,2},(\\d{1,2}:\\d{1,2}),(\\d+),(\\d+),(\\d+),(\\d+),(\\d+),(\\d+),(\\d+),(\\d+),(\\d+),(\\d+),(\\d+),(\\d+),(\\d+),(\\d+)");

        private static final Pattern FORECAST_PATTERN = compile("(\\d+),(\\d{1,2}:\\d{1,2})〜(\\d{1,2}:\\d{1,2}),\\d{1,2}/\\d{1,2},(\\d{1,2}:\\d{1,2})");

        private static final Pattern HOURLY_DATA_PATTERN = compile("(\\d{4}/\\d{1,2}/\\d{1,2}),(\\d{1,2}:\\d{1,2}),(\\d+),(\\d+),(\\d+),(\\d+)");

        private static final Pattern MOMENTARY_DATA_PATTERN = compile("(\\d{4}/\\d{1,2}/\\d{1,2}),(\\d{1,2}:\\d{1,2}),(\\d+)");

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
            Date lastUpdated = null;
            int peakCapacity = 0;
            int forecastPeakUsage = 0;
            int forecastPeakPeriod = 0;
            DateFormat fmt = new SimpleDateFormat("yyyy/M/d H:m");
            List<UsageCondition> usages = new ArrayList<UsageCondition>();
            UsageCondition current = new UsageCondition();

            for (String line : loadCsv().split("\r?\n")) {

                Matcher m = HEADER_PATTERN.matcher(line);
                if (m.matches()) {
                    lastUpdated = fmt.parse(m.group(1));
                    continue;
                }

                m = PEAK_PATTERN.matcher(line);
                if (m.matches()) {
                    peakCapacity = Integer.parseInt(m.group(1));
                    continue;
                }

                m = FORECAST_PATTERN.matcher(line);
                if (m.matches() && forecastPeakUsage == 0) {
                    forecastPeakUsage = Integer.parseInt(m.group(1));
                    String forecastPeakTime = m.group(2);
                    forecastPeakPeriod = Integer.parseInt(forecastPeakTime.substring(0, forecastPeakTime.indexOf(':')));
                    continue;
                }

                m = HOURLY_DATA_PATTERN.matcher(line);
                if (m.matches()) {
                    UsageCondition u = new UsageCondition();
                    u.setCapacity(peakCapacity);
                    int usage = Integer.parseInt(m.group(3));
                    if (0 < usage) {
                        u.setUsage(usage);
                    } else {
                        u.setForecastUsage(Integer.parseInt(m.group(4)));
                    }
                    u.setForecastPeakUsage(forecastPeakUsage);
                    u.setForecastPeakPeriod(forecastPeakPeriod);
                    u.setUsageUpdated(fmt.format(lastUpdated));
                    Date date = fmt.parse(String.format("%s %s", m.group(1), m.group(2)));
                    Calendar c = Calendar.getInstance();
                    c.setTime(date);
                    u.setYear(c.get(Calendar.YEAR));
                    u.setHour(c.get(Calendar.HOUR_OF_DAY));
                    u.setMonth(c.get(Calendar.MONTH) + 1);
                    u.setDay(c.get(Calendar.DATE));
                    u.setPercentage(Integer.parseInt(m.group(6)));
                    usages.add(u);
                }

                m = MOMENTARY_DATA_PATTERN.matcher(line);
                if (m.matches()) {
                    if (m.group(3).isEmpty()) {
                        break;
                    }
                    current.setUsage(Integer.parseInt(m.group(3)));
                    current.setUsageUpdated(m.group(1) + " " + m.group(2));
                    current.setCapacity(peakCapacity);
                    current.setForecastPeakUsage(forecastPeakUsage);
                    current.setForecastPeakPeriod(forecastPeakPeriod);

                }


            }
            if (lastUpdated != null) {
                for (Widget w : Hudson.getInstance().getWidgets()) {
                    if (w instanceof KepcoWidget) {
                        KepcoWidget kw = (KepcoWidget) w;
                        kw.setCurrent(JSONSerializer.toJSON(current).toString());
                        kw.setToday(JSONSerializer.toJSON(usages).toString());
                    }
                }
            }
        }

        protected String loadCsv() {
            InputStream is = null;
            try {
                URL url = new URL(CSV_URL);

                String proxyHost = System.getProperty("proxyHost");
                if (proxyHost == null) {
                    is = url.openStream();
                    return IOUtils.toString(is, "Shift_JIS");
                }

                Proxy proxy = new Proxy(Proxy.Type.HTTP,
                        new InetSocketAddress(proxyHost, Integer.parseInt(System.getProperty("proxyPort"))));

                return IOUtils.toString(url.openConnection(proxy).getInputStream(), "Shift_JIS");

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
