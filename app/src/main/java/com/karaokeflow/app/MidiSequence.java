package com.karaokeflow.app;
import java.io.*;import java.util.*;
/** Type 0/1 MIDI channel events, merged into a tempo-correct millisecond timeline. */
public final class MidiSequence {
 public static final class Event {public long ms,tick;public int status,a,b,tempo;Event(long t,int s,int a,int b,int tempo){tick=t;status=s;this.a=a;this.b=b;this.tempo=tempo;}}
 public final ArrayList<Event> events=new ArrayList<>();public long durationMs;
 static final class R {byte[] b;int p,end;R(byte[] b,int p,int e){this.b=b;this.p=p;end=e;}int u()throws IOException{if(p>=end)throw new EOFException();return b[p++]&255;}int w()throws IOException{return (u()<<8)|u();}long d()throws IOException{return ((long)w()<<16)|w();}long v()throws IOException{long n=0;for(int i=0;i<4;i++){int x=u();n=(n<<7)|(x&127);if((x&128)==0)return n;}throw new IOException("Invalid delta");}void skip(long n)throws IOException{if(n<0||n>end-p)throw new EOFException();p+=(int)n;}}
 public static MidiSequence parse(byte[] data)throws IOException{
  if(data.length>16*1024*1024)throw new IOException("MIDI too large");R r=new R(data,0,data.length);if(r.d()!=0x4d546864L)throw new IOException("Not a MIDI file");long header=r.d();if(header<6||header>data.length-8)throw new IOException("Bad header");int format=r.w(),tracks=r.w(),division=r.w();if(format>1||tracks<1||tracks>512||division==0)throw new IOException("Unsupported MIDI");r.skip(header-6);
  MidiSequence seq=new MidiSequence();long endTick=0;int count=0;
  for(int i=0;i<tracks;i++){if(r.d()!=0x4d54726bL)throw new IOException("Missing track");long len=r.d();if(len>r.end-r.p)throw new EOFException();R t=new R(data,r.p,r.p+(int)len);r.p=t.end;long tick=0;int running=0;
   while(t.p<t.end){if(++count>1000000)throw new IOException("Too many events");tick+=t.v();endTick=Math.max(endTick,tick);int status=t.u();if(status<128){if(running==0)throw new IOException("Running status missing");t.p--;status=running;}
    if(status<240){running=status;int kind=status&240;int a=t.u(),b=(kind==192||kind==208)?0:t.u();if(a>127||b>127)throw new IOException("Invalid MIDI data");seq.events.add(new Event(tick,status,a,b,0));}
    else if(status==255){int kind=t.u();long size=t.v();int start=t.p;t.skip(size);if(kind==81&&size==3){int tempo=((data[start]&255)<<16)|((data[start+1]&255)<<8)|(data[start+2]&255);if(tempo>0)seq.events.add(new Event(tick,255,0,0,tempo));}if(kind==47)break;}
    else if(status==240||status==247){running=0;t.skip(t.v());}else throw new IOException("Invalid status");
   }
  }
  seq.events.sort((a,b)->{int cmp=Long.compare(a.tick,b.tick);return cmp!=0?cmp:Integer.compare(b.tempo,a.tempo);});
  long last=0;double us=0;int tempo=500000;double fps=0;if((division&32768)!=0){int frame=-(byte)(division>>8);int sub=division&255;fps=(frame==29?29.97:frame)*sub;if(fps<=0)throw new IOException("Invalid SMPTE");}
  for(Event e:seq.events){us+=(e.tick-last)*(fps>0?1000000./fps:(double)tempo/division);e.ms=Math.round(us/1000);last=e.tick;if(e.tempo>0)tempo=e.tempo;}
  seq.durationMs=Math.max(1000,Math.round((us+(endTick-last)*(fps>0?1000000./fps:(double)tempo/division))/1000)+1500);return seq;
 }
}
