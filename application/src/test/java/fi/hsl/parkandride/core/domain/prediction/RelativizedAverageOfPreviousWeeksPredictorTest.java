// Copyright © 2015 HSL <https://www.hsl.fi>
// This program is dual-licensed under the EUPL v1.2 and AGPLv3 licenses.

package fi.hsl.parkandride.core.domain.prediction;

import org.junit.After;
import org.junit.Test;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@Transactional
public class RelativizedAverageOfPreviousWeeksPredictorTest extends AbstractPredictorTest {

    public RelativizedAverageOfPreviousWeeksPredictorTest() {
        super(new RelativizedAverageOfPreviousWeeksPredictor());
    }

    // the predictor is independent of system time, so the latest utilization defines "now"

    @After
    public void checkUpdatesLatestUtilization() {
        if (latestInsertedUtilization.isPresent()) {
            assertThat(predictorState.latestUtilization).isEqualTo(latestInsertedUtilization.get().timestamp);
        }
    }

    @Test
    public void when_less_than_6_days_of_history_then_no_predictions() {
        insertUtilization(now.minusDays(6).plusSeconds(1), 666);

        List<Prediction> predictions = predict();

        assertThat(predictions).isEmpty();
    }

    @Test
    public void when_1_week_old_history_exists_and_recent_utilization_is_at_same_level_then_predicts_similar_to_last_weeks_utilization() {
        insertUtilization(now.minusDays(7), 10);
        insertUtilization(now.minusDays(7).plusMinutes(5), 11);
        insertUtilization(now.minusDays(7).plusMinutes(10), 12);
        insertUtilization(now.minusDays(7).plusMinutes(15), 13);
        insertUtilization(now.minusDays(7).plusMinutes(20), 14);
        insertUtilization(now, 10);

        List<Prediction> predictions = predict();

        assertThat(predictions).containsSubsequence(
                new Prediction(now, 10),
                new Prediction(now.plusMinutes(5), 11),
                new Prediction(now.plusMinutes(10), 12),
                new Prediction(now.plusMinutes(15), 13),
                new Prediction(now.plusMinutes(20), 14));
    }

    @Test
    public void when_1_week_old_and_recent_history_have_same_trend_but_different_level_then_prediction_adapts_to_current_utilization_level() {
        insertUtilization(now.minusDays(7).minusMinutes(20), 6);
        insertUtilization(now.minusDays(7).minusMinutes(15), 7);
        insertUtilization(now.minusDays(7).minusMinutes(10), 8);
        insertUtilization(now.minusDays(7).minusMinutes(5), 9);
        insertUtilization(now.minusDays(7), 10);
        insertUtilization(now.minusDays(7).plusMinutes(5), 11);
        insertUtilization(now.minusDays(7).plusMinutes(10), 12);
        insertUtilization(now.minusDays(7).plusMinutes(15), 13);
        insertUtilization(now.minusDays(7).plusMinutes(20), 14);

        insertUtilization(now.minusMinutes(20), 16);
        insertUtilization(now.minusMinutes(15), 17);
        insertUtilization(now.minusMinutes(10), 18);
        insertUtilization(now.minusMinutes(5), 19);

        List<Prediction> predictions = predict();

        assertThat(utilizationHistory.getLatest())
                .isPresent();
        assertThat(utilizationHistory.getLatest().get().spacesAvailable).isEqualTo(19);
        assertThat(predictions).containsSubsequence(
                new Prediction(now, 20),
                new Prediction(now.plusMinutes(5), 21),
                new Prediction(now.plusMinutes(10), 22),
                new Prediction(now.plusMinutes(15), 23),
                new Prediction(now.plusMinutes(20), 24));
    }
}
