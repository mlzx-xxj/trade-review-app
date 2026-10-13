package com.tradereview.app.device

/** Device operations, not trading facts. Never restore these as proof of a backup on this phone. */
data class ExperienceState(
    val initialized:Boolean=false,val onboardingDone:Boolean=false,val firstSeenMillis:Long=0,
    val remindersEnabled:Boolean=true,val snoozedUntilMillis:Long=0,
    val zipWrittenMillis:Long=0,val zipVerification:String="none",val verifiedZipMillis:Long=0,
    val jsonWrittenMillis:Long=0,val jsonVerification:String="none",val verifiedJsonMillis:Long=0,
) {
    fun reminderDue(now:Long):Boolean {
        if(!initialized || !onboardingDone || !remindersEnabled || now<snoozedUntilMillis)return false
        val baseline=if(verifiedZipMillis>0)verifiedZipMillis else firstSeenMillis
        return baseline>0 && now>=baseline && now-baseline>=7L*24*60*60*1000
    }
}
