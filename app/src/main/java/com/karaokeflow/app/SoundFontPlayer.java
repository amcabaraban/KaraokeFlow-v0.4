package com.karaokeflow.app;
import android.content.*;import android.media.*;import android.net.Uri;import java.io.*;import java.util.*;
/** 44.1 kHz stereo AudioTrack streaming TinySoundFont, with seek via MIDI replay. */
public final class SoundFontPlayer {
 private volatile boolean playing=false,closed=false;private volatile long seekTo=-1;private volatile float speed=1;
 private Thread thread;private MidiSequence sequence;private volatile long position=0;private Runnable completed;
 public long position(){return position;}public long duration(){return sequence==null?0:sequence.durationMs;}
 public boolean isPlaying(){return playing;}public void play(){playing=true;}public void pause(){playing=false;}
 public void seek(long ms){seekTo=Math.max(0,Math.min(duration(),ms));}
 public void setSpeed(float speed){this.speed=Math.max(.5f,Math.min(1.5f,speed));}
 public void open(Context context,byte[] midi,Uri sf2,Runnable onEnd)throws Exception{
  close();closed=false;sequence=MidiSequence.parse(midi);completed=onEnd;
  File file=new File(context.getCacheDir(),"active_soundfont.sf2");try(InputStream in=context.getContentResolver().openInputStream(sf2);OutputStream out=new FileOutputStream(file)){if(in==null)throw new IOException("Cannot open SoundFont");byte[] b=new byte[65536];int n;long total=0;while((n=in.read(b))!=-1){total+=n;if(total>250L*1024*1024)throw new IOException("SoundFont exceeds 250MB");out.write(b,0,n);}}
  if(!SfSynth.load(file.getAbsolutePath()))throw new IOException("Unsupported or corrupt SoundFont");
  int bufferSize=AudioTrack.getMinBufferSize(44100,AudioFormat.CHANNEL_OUT_STEREO,AudioFormat.ENCODING_PCM_16BIT);
  final AudioTrack track=new AudioTrack(AudioManager.STREAM_MUSIC,44100,AudioFormat.CHANNEL_OUT_STEREO,AudioFormat.ENCODING_PCM_16BIT,Math.max(bufferSize,8192),AudioTrack.MODE_STREAM);
  thread=new Thread(()->{
   short[] pcm=new short[2048];int index=0;long frames=0;track.play();
   try{while(!closed){if(!playing){try{Thread.sleep(25);}catch(InterruptedException e){break;}continue;}
    long jump=seekTo;if(jump>=0){seekTo=-1;SfSynth.reset();index=0;while(index<sequence.events.size()&&sequence.events.get(index).ms<=jump){MidiSequence.Event e=sequence.events.get(index++);if(e.tempo==0)SfSynth.event(e.status,e.a,e.b);}frames=(long)(jump*44.1/speed);position=jump;track.pause();track.flush();track.play();}
    long now=(long)(frames*1000.0/44100*speed);position=now;
    while(index<sequence.events.size()&&sequence.events.get(index).ms<=now){MidiSequence.Event e=sequence.events.get(index++);if(e.tempo==0)SfSynth.event(e.status,e.a,e.b);}
    if(now>sequence.durationMs){playing=false;if(completed!=null)completed.run();break;}
    SfSynth.render(pcm,1024);int written=track.write(pcm,0,pcm.length);if(written<0)break;frames+=written/2;
   }}finally{track.stop();track.release();}
  },"karaokeflow-audio");thread.start();
 }
 public void close(){closed=true;playing=false;if(thread!=null){thread.interrupt();try{thread.join(600);}catch(InterruptedException ignored){}thread=null;}SfSynth.close();}
}
