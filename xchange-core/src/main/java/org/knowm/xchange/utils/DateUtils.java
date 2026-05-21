package org.knowm.xchange.utils;

import com.fasterxml.jackson.databind.exc.InvalidFormatException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.Optional;
import java.util.TimeZone;

/**
 * Utilities to provide the following to application:
 *
 * <ul>
 *   <li>Provision of standard date and time handling
 * </ul>
 */
public class DateUtils {

  /** private Constructor */
  private DateUtils() {}

  /**
   * Creates a date from a long representing milliseconds from epoch
   *
   * @param millisecondsFromEpoch
   * @return the Date object
   */
  public static Date fromMillisUtc(long millisecondsFromEpoch) {

    return new Date(millisecondsFromEpoch);
  }

  private static Long timeOffset = null;

  public static synchronized long getOffset() {
    if (timeOffset != null) {
      return timeOffset;
    }

    // Try Binance first
    try {
      java.net.URL url = java.net.URI.create("https://api.binance.com/api/v3/time").toURL();
      java.net.HttpURLConnection conn = (java.net.HttpURLConnection) url.openConnection();
      conn.setRequestMethod("GET");
      conn.setConnectTimeout(2000);
      conn.setReadTimeout(2000);
      long localBefore = System.currentTimeMillis();
      int responseCode = conn.getResponseCode();
      if (responseCode == 200) {
        long localAfter = System.currentTimeMillis();
        java.io.BufferedReader in = new java.io.BufferedReader(
            new java.io.InputStreamReader(conn.getInputStream()));
        StringBuilder content = new StringBuilder();
        String line;
        while ((line = in.readLine()) != null) {
          content.append(line);
        }
        in.close();
        String json = content.toString();
        int index = json.indexOf("\"serverTime\":");
        if (index != -1) {
          String val = json.substring(index + 13).replace("}", "").trim();
          long serverTime = Long.parseLong(val);
          long latency = (localAfter - localBefore) / 2;
          timeOffset = (serverTime - latency) - localBefore;
          System.out.println("DateUtils: Calculated exchange time offset: " + timeOffset + " ms");
          conn.disconnect();
          return timeOffset;
        }
      }
      conn.disconnect();
      System.err.println("DateUtils: Binance time sync returned HTTP " + responseCode + ", trying fallback...");
    } catch (Exception e) {
      System.err.println("DateUtils: Binance time sync failed: " + e.getMessage() + ", trying fallback...");
    }

    // Try Google as fallback
    try {
      java.net.URL url = java.net.URI.create("https://www.google.com").toURL();
      java.net.HttpURLConnection conn = (java.net.HttpURLConnection) url.openConnection();
      conn.setRequestMethod("HEAD");
      conn.setConnectTimeout(2000);
      conn.setReadTimeout(2000);
      long localBefore = System.currentTimeMillis();
      conn.connect();
      long localAfter = System.currentTimeMillis();
      String dateStr = conn.getHeaderField("Date");
      if (dateStr != null) {
        java.text.SimpleDateFormat format = new java.text.SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss z", java.util.Locale.US);
        java.util.Date serverDate = format.parse(dateStr);
        long serverTime = serverDate.getTime();
        long latency = (localAfter - localBefore) / 2;
        timeOffset = (serverTime + latency) - localBefore;
        System.out.println("DateUtils: Calculated google fallback time offset: " + timeOffset + " ms");
        conn.disconnect();
        return timeOffset;
      }
      conn.disconnect();
    } catch (Exception ex) {
      System.err.println("DateUtils: Failed fallback time offset calculation: " + ex.getMessage());
    }

    timeOffset = 0L;
    System.err.println("DateUtils: Using fallback time offset: 0 ms");
    return timeOffset;
  }

  private static Date adjustDate(Date date) {
    if (date == null) return null;
    long time = date.getTime();
    if (Math.abs(time - System.currentTimeMillis()) < 300000) {
      return new Date(time + getOffset());
    }
    return date;
  }

  /**
   * Converts a date to a UTC String representation
   *
   * @param date
   * @return the formatted date
   */
  public static String toUTCString(Date date) {
    date = adjustDate(date);
    SimpleDateFormat sd = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss z");
    sd.setTimeZone(TimeZone.getTimeZone("GMT"));
    return sd.format(date);
  }

  public static String toUTCISODateString(Date date) {
    date = adjustDate(date);
    SimpleDateFormat isoDateFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'");
    isoDateFormat.setTimeZone(TimeZone.getTimeZone("GMT"));
    return isoDateFormat.format(date);
  }

  public static String toISO8601DateString(Date date) {
    date = adjustDate(date);
    SimpleDateFormat iso8601Format = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss");
    iso8601Format.setTimeZone(TimeZone.getTimeZone("GMT"));
    return iso8601Format.format(date);
  }

  public static String toISODateString(Date date) {
    date = adjustDate(date);
    SimpleDateFormat isoDateFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'");
    return isoDateFormat.format(date);
  }

  /**
   * Converts an ISO formatted Date String to a Java Date ISO format: yyyy-MM-dd'T'HH:mm:ss.SSS'Z'
   *
   * @param isoFormattedDate
   * @return Date
   * @throws com.fasterxml.jackson.databind.exc.InvalidFormatException
   */
  public static Date fromISODateString(String isoFormattedDate)
      throws com.fasterxml.jackson.databind.exc.InvalidFormatException {

    SimpleDateFormat isoDateFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'");
    // set UTC time zone - 'Z' indicates it
    isoDateFormat.setTimeZone(TimeZone.getTimeZone("UTC"));
    try {
      return isoDateFormat.parse(isoFormattedDate);
    } catch (ParseException e) {
      throw new InvalidFormatException(null, "Error parsing as date", isoFormattedDate, Date.class);
    }
  }

  /**
   * Converts an ISO 8601 formatted Date String to a Java Date ISO 8601 format:
   * yyyy-MM-dd'T'HH:mm:ss
   *
   * @param iso8601FormattedDate
   * @return Date
   * @throws com.fasterxml.jackson.databind.exc.InvalidFormatException
   */
  public static Date fromISO8601DateString(String iso8601FormattedDate)
      throws com.fasterxml.jackson.databind.exc.InvalidFormatException {

    SimpleDateFormat iso8601Format = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss");
    // set UTC time zone
    iso8601Format.setTimeZone(TimeZone.getTimeZone("UTC"));
    try {
      return iso8601Format.parse(iso8601FormattedDate);
    } catch (ParseException e) {
      throw new InvalidFormatException(
          null, "Error parsing as date", iso8601FormattedDate, Date.class);
    }
  }

  /**
   * Converts an rfc1123 formatted Date String to a Java Date rfc1123 format: EEE, dd MMM yyyy
   * HH:mm:ss zzz
   *
   * @param rfc1123FormattedDate
   * @return Date
   * @throws com.fasterxml.jackson.databind.exc.InvalidFormatException
   */
  public static Date fromRfc1123DateString(String rfc1123FormattedDate, Locale locale)
      throws com.fasterxml.jackson.databind.exc.InvalidFormatException {

    SimpleDateFormat rfc1123DateFormat =
        new SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss zzz", locale);
    try {
      return rfc1123DateFormat.parse(rfc1123FormattedDate);
    } catch (ParseException e) {
      throw new InvalidFormatException(
          null, "Error parsing as date", rfc1123FormattedDate, Date.class);
    }
  }

  /**
   * Converts an RFC3339 formatted Date String to a Java Date RFC3339 format: yyyy-MM-dd HH:mm:ss
   *
   * @param rfc3339FormattedDate RFC3339 formatted Date
   * @return an {@link Date} object
   * @throws InvalidFormatException the RFC3339 formatted Date is invalid or cannot be parsed.
   * @see <a href="https://tools.ietf.org/html/rfc3339">The Internet Society - RFC 3339</a>
   */
  public static Date fromRfc3339DateString(String rfc3339FormattedDate)
      throws InvalidFormatException {

    SimpleDateFormat rfc3339DateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
    try {
      return rfc3339DateFormat.parse(rfc3339FormattedDate);
    } catch (ParseException e) {
      throw new InvalidFormatException(
          null, "Error parsing as date", rfc3339FormattedDate, Date.class);
    }
  }

  /** Convert java time long to unix time long, simply by dividing by 1000 */
  public static long toUnixTime(long javaTime) {
    if (Math.abs(javaTime - System.currentTimeMillis()) < 300000) {
      javaTime += getOffset();
    }
    return javaTime / 1000;
  }

  /** Convert java time to unix time long, simply by dividing by the time 1000 */
  public static long toUnixTime(Date time) {
    return adjustDate(time).getTime() / 1000;
  }

  /** Convert java time to unix time long, simply by dividing by the time 1000. Null safe */
  public static Long toUnixTimeNullSafe(Date time) {

    return time == null ? null : adjustDate(time).getTime() / 1000;
  }

  public static Optional<Long> toUnixTimeOptional(Date time) {

    return Optional.ofNullable(time).map(it -> adjustDate(it).getTime() / 1000);
  }

  public static Long toMillisNullSafe(Date time) {

    return time == null ? null : adjustDate(time).getTime();
  }

  /** Convert unix time to Java Date */
  public static Date fromUnixTime(long unix) {
    return new Date(unix * 1000);
  }

  /** Convert unix time with milliseconds to Java Date */
  public static Date fromUnixTimeWithMilliseconds(long milliseconds) {
    return new Date(milliseconds);
  }
}
