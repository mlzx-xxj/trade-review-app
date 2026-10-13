package com.tradereview.app.device

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Atomic, device-local UX metadata; no record, URI, OCR text, image or file name is stored. */
class DeviceExperienceStore(context:Context,name:String="device-experience-v1"):AutoCloseable {
    private val prefs=context.applicationContext.getSharedPreferences(name,Context.MODE_PRIVATE)
    private val _state=MutableStateFlow(read())
    val state=_state.asStateFlow()
    private val _error=MutableStateFlow<String?>(null)
    val error=_error.asStateFlow()
    fun initializationFailed(){_error.value="引导与备份操作状态暂不可保存；请检查手机空间，业务记录未被修改"}
    private val listener=SharedPreferences.OnSharedPreferenceChangeListener{_,_-> _state.value=read() }
    init{prefs.registerOnSharedPreferenceChangeListener(listener)}
    private fun read()=ExperienceState(prefs.getBoolean("initialized",false),prefs.getBoolean("onboardingDone",false),
        prefs.getLong("firstSeen",0),prefs.getBoolean("reminders",true),prefs.getLong("snoozed",0),
        prefs.getLong("zipWritten",0),prefs.getString("zipVerification","none") ?: "none",prefs.getLong("zipVerified",0),
        prefs.getLong("jsonWritten",0),prefs.getString("jsonVerification","none") ?: "none",prefs.getLong("jsonVerified",0))
    private fun commit(editor:SharedPreferences.Editor){check(editor.commit()){ "本机操作状态保存失败；交易数据未改变" };_state.value=read()}
    @Synchronized fun initialize(returningUser:Boolean,now:Long=System.currentTimeMillis()){
        if(prefs.getBoolean("initialized",false))return
        require(now>0)
        commit(prefs.edit().putBoolean("initialized",true).putBoolean("onboardingDone",returningUser || prefs.getBoolean("onboardingDone",false)).putLong("firstSeen",now))
    }
    @Synchronized fun finishOnboarding(){commit(prefs.edit().putBoolean("onboardingDone",true))}
    @Synchronized fun reminders(enabled:Boolean){commit(prefs.edit().putBoolean("reminders",enabled))}
    @Synchronized fun snooze(days:Int,now:Long=System.currentTimeMillis()){
        require(days in listOf(7,30));commit(prefs.edit().putLong("snoozed",Math.addExact(now,days*24L*60*60*1000)))
    }
    /** Called after the output has really closed; a mismatched readable file never reaches this. */
    @Synchronized fun exported(kind:String,verified:Boolean,now:Long=System.currentTimeMillis()){
        require(kind in listOf("zip","json") && now>0)
        val editor=prefs.edit().putLong(kind+"Written",now).putString(kind+"Verification",if(verified)"sha256"else"unavailable")
        if(verified)editor.putLong(kind+"Verified",now)
        commit(editor)
    }
    override fun close(){prefs.unregisterOnSharedPreferenceChangeListener(listener)}
}
