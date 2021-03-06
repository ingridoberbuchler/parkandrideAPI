// Copyright © 2015 HSL <https://www.hsl.fi>
// This program is dual-licensed under the EUPL v1.2 and AGPLv3 licenses.

package fi.hsl.parkandride.core.domain.prediction;

import org.joda.time.DateTime;
import org.joda.time.Duration;
import org.springframework.format.annotation.DateTimeFormat;

import javax.validation.constraints.Pattern;
import java.util.regex.Matcher;

import static org.joda.time.Duration.standardHours;
import static org.joda.time.Duration.standardMinutes;

public class PredictionRequest {

    public static final String HHMM_PATTERN = "((\\d+):)?(\\d+)";

    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
    public DateTime at;

    @Pattern(regexp = HHMM_PATTERN)
    public String after;

    public DateTime requestedTime() {
        if (at != null) {
            return at;
        }
        if (after != null) {
            return DateTime.now().plus(parseRelativeTime(after));
        }
        return DateTime.now();
    }

    static Duration parseRelativeTime(String relativeTime) {
        Matcher matcher = java.util.regex.Pattern.compile(HHMM_PATTERN).matcher(relativeTime);
        if (matcher.matches()) {
            int hours = parseOptionalInt(matcher.group(2));
            int minutes = Integer.parseInt(matcher.group(3));
            return standardHours(hours).plus(standardMinutes(minutes));
        } else {
            return Duration.ZERO;
        }
    }

    private static int parseOptionalInt(String s) {
        return s == null ? 0 : Integer.parseInt(s);
    }


    // generated getters and setters

    public DateTime getAt() {
        return at;
    }

    public void setAt(DateTime at) {
        this.at = at;
    }

    public String getAfter() {
        return after;
    }

    public void setAfter(String after) {
        this.after = after;
    }
}
