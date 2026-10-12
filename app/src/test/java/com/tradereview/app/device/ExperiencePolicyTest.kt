package com.tradereview.app.device

import org.junit.Assert.*
import org.junit.Test

class ExperiencePolicyTest {
    private val start=1000L
    private val week=7L*24*60*60*1000
    @Test fun noReminderUntilInitializedAndGuideFinished(){assertFalse(ExperienceState(firstSeenMillis=start).reminderDue(start+week));assertFalse(ExperienceState(initialized=true,firstSeenMillis=start).reminderDue(start+week))}
    @Test fun exactlySevenDaysAndOnlyVerifiedZipResetsReminder(){val s=ExperienceState(true,true,start);assertFalse(s.reminderDue(start+week-1));assertTrue(s.reminderDue(start+week));assertTrue(s.copy(zipWrittenMillis=start+week,jsonWrittenMillis=start+week,verifiedJsonMillis=start+week).reminderDue(start+week));assertFalse(s.copy(verifiedZipMillis=start+week).reminderDue(start+week))}
    @Test fun disabledSnoozedAndBackwardClockDoNotNag(){val s=ExperienceState(true,true,start);assertFalse(s.copy(remindersEnabled=false).reminderDue(start+week));assertFalse(s.copy(snoozedUntilMillis=start+week+1).reminderDue(start+week));assertTrue(s.copy(snoozedUntilMillis=start+week).reminderDue(start+week));assertFalse(s.reminderDue(start-1))}
}
