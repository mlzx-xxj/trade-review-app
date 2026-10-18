package com.tradereview.app.v200

import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import androidx.core.view.WindowCompat

/** Preserve the normal form layout; keep actions and input visible in a short IME viewport. */
@Composable internal fun ReviewForm(title:String,saveLabel:String,saveTag:String,error:String?,busy:Boolean,onDismiss:()->Unit,onSave:()->Unit,content:@Composable ColumnScope.()->Unit){
    Dialog(onDismissRequest=onDismiss,properties=DialogProperties(usePlatformDefaultWidth=false,decorFitsSystemWindows=false)){
        val view=LocalView.current;val dark=isSystemInDarkTheme()
        SideEffect{(view.parent as? DialogWindowProvider)?.window?.let{window->
            window.setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
            window.setDimAmount(0f);window.statusBarColor=android.graphics.Color.TRANSPARENT;window.navigationBarColor=android.graphics.Color.TRANSPARENT
            WindowCompat.getInsetsController(window,view).let{it.isAppearanceLightStatusBars=!dark;it.isAppearanceLightNavigationBars=!dark}
        }}
        Surface(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).windowInsetsPadding(WindowInsets.safeDrawing).imePadding(),color=MaterialTheme.colorScheme.background){
            BoxWithConstraints(Modifier.fillMaxSize()){
                // One action row leaves space for the focused field in a landscape IME viewport.
                val compact=maxHeight<200.dp
                Column(Modifier.fillMaxSize()){
                    Row(Modifier.fillMaxWidth().padding(horizontal=8.dp),verticalAlignment=Alignment.CenterVertically){
                        TextButton(onDismiss,Modifier.heightIn(min=LedgerUi.touch)){Text("取消")}
                        Text(title,Modifier.weight(1f).padding(horizontal=8.dp,vertical=if(compact)4.dp else 12.dp).semantics{heading()},style=MaterialTheme.typography.titleMedium,maxLines=if(compact)1 else Int.MAX_VALUE,overflow=if(compact)TextOverflow.Ellipsis else TextOverflow.Clip)
                        if(compact)Button(onSave,enabled=!busy,modifier=Modifier.heightIn(min=LedgerUi.touch).testTag(saveTag),shape=MaterialTheme.shapes.small,contentPadding=PaddingValues(horizontal=12.dp,vertical=8.dp)){Text(if(busy)"正在保存…"else saveLabel)}
                    }
                    HorizontalDivider(color=MaterialTheme.colorScheme.outlineVariant)
                    Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal=LedgerUi.inset,vertical=if(compact)4.dp else LedgerUi.inset),verticalArrangement=Arrangement.spacedBy(LedgerUi.gap)){
                        if(compact)error?.let{Text(it,Modifier.semantics{liveRegion=LiveRegionMode.Polite},color=MaterialTheme.colorScheme.error,style=MaterialTheme.typography.bodyMedium)}
                        content()
                    }
                    if(!compact)Column(Modifier.fillMaxWidth().padding(horizontal=LedgerUi.inset,vertical=8.dp),verticalArrangement=Arrangement.spacedBy(8.dp)){
                        error?.let{Text(it,Modifier.semantics{liveRegion=LiveRegionMode.Polite},color=MaterialTheme.colorScheme.error,style=MaterialTheme.typography.bodyMedium)}
                        Button(onSave,enabled=!busy,modifier=Modifier.fillMaxWidth().heightIn(min=52.dp).testTag(saveTag),shape=MaterialTheme.shapes.small){Text(if(busy)"正在保存…"else saveLabel)}
                    }
                }
            }
        }
    }
}
@Composable internal fun ReviewSection(title:String){
    HorizontalDivider(Modifier.padding(top=8.dp),color=MaterialTheme.colorScheme.outlineVariant)
    Text(title,Modifier.padding(top=4.dp).semantics{heading()},style=MaterialTheme.typography.titleSmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
}
@Composable internal fun ReviewNote(label:String,value:String,onValue:(String)->Unit,tag:String){
    OutlinedTextField(value,onValue,Modifier.fillMaxWidth().testTag(tag),label={Text(label)},minLines=5,maxLines=10,
        textStyle=MaterialTheme.typography.bodyLarge,shape=MaterialTheme.shapes.small,keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Text),
        supportingText={Text("${value.length} / 1000",style=MaterialTheme.typography.labelSmall)})
}
