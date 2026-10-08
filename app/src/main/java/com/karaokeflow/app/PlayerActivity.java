package com.karaokeflow.app;

import android.app.*;
import android.os.*;
import android.net.Uri;
import android.graphics.Color;
import android.media.*;
import android.view.*;
import android.widget.*;
import android.text.*;
import android.text.style.ForegroundColorSpan;
import org.json.*;
import java.io.*;
import java.util.*;
import java.util.concurrent.*;

public class PlayerActivity extends Activity implements SurfaceHolder.Callback {
    JSONArray playlist;int index=0, restorePosition=0, generation=0, lyricOffset=0;
    MediaPlayer player; boolean ready=false, foreground=false, autoStart=true, seeking=false, videoMode=false;
    TextView title,lyrics,nextLine,time,offsetLabel;Button toggle;SeekBar seek;
    SurfaceView surface;LinearLayout lyricPanel;MidiLyrics midi;
    final Handler handler=new Handler(Looper.getMainLooper());
    final ExecutorService worker=Executors.newSingleThreadExecutor();
    AudioManager audio;boolean hasFocus=false;
    final AudioManager.OnAudioFocusChangeListener focus=change->{if(change<0)pausePlayback();};
    final Runnable progress=new Runnable(){public void run(){if(ready&&player!=null){try{int pos=player.getCurrentPosition();if(!seeking)seek.setProgress(pos);time.setText(stamp(pos)+" / "+stamp(player.getDuration()));showLyrics(pos+lyricOffset);}catch(IllegalStateException ignored){}}handler.postDelayed(this,80);}};
    @Override public void onCreate(Bundle state){
        super.onCreate(state);getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        audio=(AudioManager)getSystemService(AUDIO_SERVICE);
        try{playlist=new JSONArray(getSharedPreferences("playback",0).getString("playlist","[]"));}catch(Exception e){finish();return;}
        if(playlist.length()==0){finish();return;}
        if(state!=null){index=state.getInt("index");restorePosition=state.getInt("position");autoStart=false;}
        lyricOffset=getSharedPreferences("playback",0).getInt("lyricOffset",0);
        LinearLayout root=new LinearLayout(this);root.setOrientation(1);root.setBackgroundColor(Color.rgb(9,15,29));
        root.setOnApplyWindowInsetsListener((v,w)->{v.setPadding(w.getSystemWindowInsetLeft()+12,w.getSystemWindowInsetTop()+6,w.getSystemWindowInsetRight()+12,w.getSystemWindowInsetBottom()+6);return w;});setContentView(root);
        title=label(17);root.addView(title);
        FrameLayout stage=new FrameLayout(this);root.addView(stage,new LinearLayout.LayoutParams(-1,0,1));
        surface=new SurfaceView(this);surface.getHolder().addCallback(this);stage.addView(surface,new FrameLayout.LayoutParams(-1,-1));
        lyricPanel=new LinearLayout(this);lyricPanel.setOrientation(1);lyricPanel.setGravity(Gravity.CENTER);
        lyrics=label(30);nextLine=label(18);nextLine.setTextColor(Color.rgb(153,170,198));lyricPanel.addView(lyrics);lyricPanel.addView(nextLine);stage.addView(lyricPanel,new FrameLayout.LayoutParams(-1,-1));
        time=label(12);root.addView(time);seek=new SeekBar(this);root.addView(seek);
        seek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){public void onStartTrackingTouch(SeekBar s){seeking=true;}public void onProgressChanged(SeekBar s,int p,boolean user){if(user)showLyrics(p+lyricOffset);}public void onStopTrackingTouch(SeekBar s){if(ready)player.seekTo(s.getProgress());seeking=false;}});
        LinearLayout controls=new LinearLayout(this);root.addView(controls);
        button(controls,"Library",()->finish());toggle=button(controls,"Loading…",()->{if(!ready)return;if(player.isPlaying())pausePlayback();else startPlayback();});button(controls,"Restart",()->{if(ready){player.seekTo(0);startPlayback();}});button(controls,"Next",()->next());
        LinearLayout timing=new LinearLayout(this);root.addView(timing);
        button(timing,"Lyrics −0.2s",()->adjust(-200));offsetLabel=label(12);timing.addView(offsetLabel,new LinearLayout.LayoutParams(0,-2,1));button(timing,"Lyrics +0.2s",()->adjust(200));updateOffset();
        handler.post(progress);open();
    }
    TextView label(int size){TextView t=new TextView(this);t.setTextColor(Color.WHITE);t.setGravity(Gravity.CENTER);t.setTextSize(size);t.setPadding(8,3,8,3);return t;}
    Button button(LinearLayout row,String s,Runnable r){Button b=new Button(this);b.setText(s);b.setAllCaps(false);b.setTextSize(13);b.setOnClickListener(v->r.run());row.addView(b,new LinearLayout.LayoutParams(0,-2,1));return b;}
    void adjust(int n){lyricOffset=Math.max(-5000,Math.min(5000,lyricOffset+n));getSharedPreferences("playback",0).edit().putInt("lyricOffset",lyricOffset).apply();updateOffset();}
    void updateOffset(){offsetLabel.setText(String.format(Locale.ROOT,"Lyrics: %+.1fs",lyricOffset/1000.0));}
    static String stamp(int ms){return String.format(Locale.ROOT,"%d:%02d",ms/60000,(ms/1000)%60);}
    void open(){
        release();final int token=++generation;midi=null;
        JSONObject song=playlist.optJSONObject(index);if(song==null){finish();return;}
        title.setText(song.optString("title"));
        videoMode=!song.optString("kind","video").equals("midi");surface.setVisibility(videoMode?View.VISIBLE:View.GONE);lyricPanel.setVisibility(videoMode?View.GONE:View.VISIBLE);
        lyrics.setText("Preparing your song…");nextLine.setText("");toggle.setText("Loading…");seek.setEnabled(false);seek.setProgress(0);time.setText("");
        if(videoMode){prepare(Uri.parse(song.optString("uri")),null,token);return;}
        worker.execute(()->{
            try{
                Uri uri=Uri.parse(song.optString("uri"));byte[] bytes;
                try(InputStream in="demo".equals(uri.getScheme())?getAssets().open("welcome.mid"):getContentResolver().openInputStream(uri)){
                    if(in==null)throw new IOException("Cannot open song");ByteArrayOutputStream out=new ByteArrayOutputStream();byte[] buf=new byte[8192];int n;
                    while((n=in.read(buf))!=-1){if(out.size()+n>16*1024*1024)throw new IOException("MIDI file exceeds 16 MB");out.write(buf,0,n);}bytes=out.toByteArray();
                }
                MidiLyrics parsed=MidiLyrics.parse(bytes);File file=new File(getCacheDir(),"midi-"+token+".mid");try(FileOutputStream out=new FileOutputStream(file)){out.write(bytes);}
                handler.post(()->{if(token!=generation||isFinishing()){file.delete();return;}midi=parsed;prepare(null,file,token);});
            }catch(Exception e){handler.post(()->{if(token==generation&&!isFinishing())error("Cannot read this MIDI/KAR file. "+e.getMessage());});}
        });
    }
    void prepare(Uri uri,File file,int token){
        if(isFinishing()||token!=generation)return;
        MediaPlayer current=new MediaPlayer();player=current;
        current.setAudioAttributes(new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build());
        current.setOnPreparedListener(mp->{if(player!=mp)return;ready=true;seek.setEnabled(true);seek.setMax(mp.getDuration());if(restorePosition>0){mp.seekTo(restorePosition);restorePosition=0;}toggle.setText("Play");if(autoStart&&foreground)startPlayback();showLyrics(0);});
        current.setOnCompletionListener(mp->{if(player==mp)next();});
        current.setOnErrorListener((mp,w,e)->{if(player==mp)error("This file cannot be played on this device. Check that it is a local standard MIDI/KAR or H.264/AAC MP4 file.");return true;});
        current.setOnVideoSizeChangedListener((mp,w,h)->fitVideo(w,h));
        try{
            if(videoMode&&surface.getHolder().getSurface().isValid())current.setDisplay(surface.getHolder());
            if(file!=null){try(FileInputStream in=new FileInputStream(file)){current.setDataSource(in.getFD());}finally{file.delete();}}
            else current.setDataSource(this,uri);
            current.prepareAsync();
        }catch(Exception e){error("Could not open this song. The file may have moved or be unavailable offline.");}
    }
    void fitVideo(int w,int h){if(w<=0||h<=0)return;surface.post(()->{View parent=(View)surface.getParent();int pw=parent.getWidth(),ph=parent.getHeight();if(pw<=0||ph<=0)return;double ratio=Math.min((double)pw/w,(double)ph/h);FrameLayout.LayoutParams lp=new FrameLayout.LayoutParams((int)(w*ratio),(int)(h*ratio),Gravity.CENTER);surface.setLayoutParams(lp);});}
    void showLyrics(long pos){
        if(videoMode||midi==null)return;
        if(midi.cues.isEmpty()){lyrics.setText("Instrumental");nextLine.setText("No timed lyrics in this file");return;}
        int at=midi.at(pos);
        if(at<0){lyrics.setText("Get ready…");nextLine.setText(midi.cues.get(0).text);return;}
        String sung=midi.cues.get(at).text,full=sung;int last=at;
        while(last+1<midi.cues.size()&&midi.cues.get(last+1).text.startsWith(full)&&!midi.cues.get(last+1).text.equals(full)){full=midi.cues.get(++last).text;}
        SpannableString styled=new SpannableString(full.isEmpty()?"♪":full);
        styled.setSpan(new ForegroundColorSpan(Color.rgb(67,223,192)),0,Math.min(sung.length(),styled.length()),Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        lyrics.setText(styled);
        if(last+1<midi.cues.size()){String upcoming=midi.cues.get(last+1).text;int n=last+1;while(n+1<midi.cues.size()&&midi.cues.get(n+1).text.startsWith(upcoming)&&!midi.cues.get(n+1).text.equals(upcoming))upcoming=midi.cues.get(++n).text;nextLine.setText(upcoming);}else nextLine.setText("");
    }
    void startPlayback(){if(!ready||player==null)return;hasFocus=audio.requestAudioFocus(focus,AudioManager.STREAM_MUSIC,AudioManager.AUDIOFOCUS_GAIN)==AudioManager.AUDIOFOCUS_REQUEST_GRANTED;if(!hasFocus){Toast.makeText(this,"Audio is in use by another app",Toast.LENGTH_SHORT).show();return;}player.start();toggle.setText("Pause");}
    void pausePlayback(){autoStart=false;if(ready&&player!=null){try{player.pause();toggle.setText("Play");}catch(IllegalStateException ignored){}}}
    void error(String message){release();toggle.setText("Unavailable");new AlertDialog.Builder(this).setTitle("Song unavailable").setMessage(message).setPositiveButton("Next song",(d,w)->next()).setNegativeButton("Library",(d,w)->finish()).setCancelable(false).show();}
    void next(){if(++index>=playlist.length()){Toast.makeText(this,"Playlist finished",Toast.LENGTH_SHORT).show();finish();return;}restorePosition=0;autoStart=true;open();}
    void release(){ready=false;if(player!=null){player.release();player=null;}if(hasFocus){audio.abandonAudioFocus(focus);hasFocus=false;}}
    @Override protected void onResume(){super.onResume();foreground=true;}
    @Override protected void onPause(){foreground=false;pausePlayback();super.onPause();}
    @Override protected void onSaveInstanceState(Bundle out){out.putInt("index",index);out.putInt("position",ready?player.getCurrentPosition():restorePosition);super.onSaveInstanceState(out);}
    @Override protected void onDestroy(){generation++;handler.removeCallbacksAndMessages(null);worker.shutdownNow();release();super.onDestroy();}
    @Override public void surfaceCreated(SurfaceHolder h){if(player!=null&&videoMode)player.setDisplay(h);}
    @Override public void surfaceChanged(SurfaceHolder h,int format,int w,int height){}
    @Override public void surfaceDestroyed(SurfaceHolder h){if(player!=null&&videoMode)player.setDisplay(null);}
}
