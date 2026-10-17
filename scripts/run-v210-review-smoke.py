"""Temporary diagnosis branch only. This does not replace complete Android CI."""
import importlib.util,json,subprocess,time,traceback
from pathlib import Path
spec=importlib.util.spec_from_file_location('review_runner',Path(__file__).with_name('run-v210-review.py'))
runner=importlib.util.module_from_spec(spec);spec.loader.exec_module(runner)
spec=importlib.util.spec_from_file_location('entry',Path(__file__).with_name('run-entry-experience.py'))
entry=importlib.util.module_from_spec(spec);spec.loader.exec_module(entry)
root=Path('ci-v200/v210-visual');root.mkdir(parents=True,exist_ok=True)
result=dict(scope='focused-ui-only-full-suite-not-executed',sourceCommit=subprocess.check_output(['git','rev-parse','HEAD'],text=True).strip(),complete=False)
try:
 assert entry.adb('shell','getprop','ro.kernel.qemu').strip()=='1'
 entry.adb('install','-r','-t','app/build/outputs/apk/debug/app-debug.apk',timeout=120)
 entry.adb('install','-r','-t','app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk',timeout=120)
 # Create the private data directory using the real application.
 entry.adb('shell','am','start','-W','-n',entry.APP+'/'+entry.APP+'.MainActivity')
 directory=entry.adb('shell','run-as',entry.APP,'pwd').strip()
 feedback=entry.adb('shell','am','instrument','-w','-r','-e','class',entry.APP+'.v210.V210FeedbackLifecycleTest',entry.APP+'.test/androidx.test.runner.AndroidJUnitRunner',timeout=150)
 (root/'feedback-instrumentation.txt').write_text(feedback)
 print(feedback,flush=True)
 assert entry.successful_runner(feedback),'Repeated real UI feedback failed'
 visual_spec=importlib.util.spec_from_file_location('old_full',Path(__file__).with_name('run-v210-visual.py'))
 old_full=importlib.util.module_from_spec(visual_spec);visual_spec.loader.exec_module(old_full)
 for profile in ('narrow-light','narrow-dark','regular-light','regular-dark'):
  entry.adb('shell','am','force-stop',entry.APP)
  entry.adb('shell','wm','size','640x1136' if profile.startswith('narrow') else '720x1280')
  entry.adb('shell','wm','density','320')
  entry.adb('shell','settings','put','system','font_scale','1.3' if profile.startswith('narrow') else '1.0')
  entry.adb('shell','su','0','cmd','uimode','night','yes' if profile.endswith('dark') else 'no')
  entry.adb('shell','input','keyevent','KEYCODE_SLEEP');time.sleep(1)
  entry.adb('shell','input','keyevent','KEYCODE_WAKEUP');entry.adb('shell','wm','dismiss-keyguard');time.sleep(2)
  value=runner.run(35,profile,directory,root)
  print(json.dumps(value),flush=True)
  old_full.full_journey(35,profile,directory)
 result['complete']=True
except Exception:
 for log in root.glob('*instrumentation.txt'):print(log.name+'\n'+log.read_text()[-10000:],flush=True)
 raise
finally:
 (root/'FOCUSED-SOURCE.json').write_text(json.dumps(result,indent=2)+'\n')
