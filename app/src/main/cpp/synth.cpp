#include <jni.h>
#include <mutex>
#define TSF_IMPLEMENTATION
#include "tsf.h"
static tsf* synth=nullptr;
static std::mutex guard;
extern "C" JNIEXPORT jboolean JNICALL Java_com_karaokeflow_app_SfSynth_load(JNIEnv* env,jclass,jstring path){
 const char* p=env->GetStringUTFChars(path,nullptr);std::lock_guard<std::mutex> lock(guard);
 if(synth){tsf_close(synth);synth=nullptr;}synth=tsf_load_filename(p);
 env->ReleaseStringUTFChars(path,p);if(synth){tsf_set_output(synth,TSF_STEREO_INTERLEAVED,44100,0);tsf_channel_set_bank_preset(synth,9,128,0);}return synth!=nullptr;
}
extern "C" JNIEXPORT void JNICALL Java_com_karaokeflow_app_SfSynth_event(JNIEnv*,jclass,jint status,jint d1,jint d2){
 std::lock_guard<std::mutex> lock(guard);if(!synth)return;
 int ch=status&15;switch(status&0xf0){
 case 0x80:tsf_channel_note_off(synth,ch,d1);break;
 case 0x90:if(d2)tsf_channel_note_on(synth,ch,d1,d2/127.f);else tsf_channel_note_off(synth,ch,d1);break;
 case 0xb0:tsf_channel_midi_control(synth,ch,d1,d2);break;
 case 0xc0:tsf_channel_set_presetnumber(synth,ch,d1,ch==9);break;
 case 0xe0:tsf_channel_set_pitchwheel(synth,ch,d1|(d2<<7));break;
 }}
extern "C" JNIEXPORT void JNICALL Java_com_karaokeflow_app_SfSynth_render(JNIEnv* env,jclass,jshortArray buffer,jint frames){
 std::lock_guard<std::mutex> lock(guard);if(!synth)return;
 jshort* out=env->GetShortArrayElements(buffer,nullptr);tsf_render_short(synth,out,frames,0);env->ReleaseShortArrayElements(buffer,out,0);
}
extern "C" JNIEXPORT void JNICALL Java_com_karaokeflow_app_SfSynth_reset(JNIEnv*,jclass){std::lock_guard<std::mutex> lock(guard);if(synth)tsf_reset(synth);}
extern "C" JNIEXPORT void JNICALL Java_com_karaokeflow_app_SfSynth_close(JNIEnv*,jclass){std::lock_guard<std::mutex> lock(guard);if(synth){tsf_close(synth);synth=nullptr;}}
