package com.karaokeflow.app;

import java.io.IOException;
import java.nio.charset.*;
import java.util.*;

/** Bounded Standard MIDI File parser. All timestamps derive from the merged tempo map. */
public final class MidiLyrics {
    public static final class Cue {
        public final long ms; public final String text;
        Cue(long ms,String text){this.ms=ms;this.text=text;}
    }
    public final List<Cue> cues;
    private MidiLyrics(List<Cue> cues){this.cues=Collections.unmodifiableList(cues);}
    static final class Event {
        long tick; int kind,tempo; String text;
        Event(long t,int k,int v,String s){tick=t;kind=k;tempo=v;text=s;}
    }
    static final class Reader {
        byte[] b;int p,end;
        Reader(byte[] b,int p,int end){this.b=b;this.p=p;this.end=end;}
        int one()throws IOException{if(p>=end)throw new IOException("Truncated MIDI data");return b[p++]&255;}
        int word()throws IOException{return (one()<<8)|one();}
        long dword()throws IOException{return ((long)word()<<16)|word();}
        long variable()throws IOException{long n=0;for(int i=0;i<4;i++){int x=one();n=(n<<7)|(x&127);if((x&128)==0)return n;}throw new IOException("Invalid MIDI delta time");}
        void skip(long n)throws IOException{if(n<0||n>end-p)throw new IOException("Truncated MIDI event");p+=(int)n;}
    }
    public static MidiLyrics parse(byte[] bytes)throws IOException {
        if(bytes.length>16*1024*1024)throw new IOException("MIDI file exceeds 16 MB limit");
        Reader r=new Reader(bytes,0,bytes.length);
        if(r.dword()!=0x4d546864L)throw new IOException("Choose a standard MID or KAR file");
        long h=r.dword();if(h<6||h>bytes.length-8)throw new IOException("Invalid MIDI header");
        int format=r.word(),tracks=r.word(),division=r.word();
        if(format>1||tracks==0||tracks>512||(format==0&&tracks!=1))throw new IOException("Only MIDI type 0 and 1 are supported");
        if(division==0)throw new IOException("Invalid MIDI timing");
        r.skip(h-6);List<Event> events=new ArrayList<>();int count=0;
        for(int tr=0;tr<tracks;tr++){
            if(r.dword()!=0x4d54726bL)throw new IOException("Missing MIDI track");
            long size=r.dword();if(size>r.end-r.p)throw new IOException("Truncated MIDI track");
            int end=r.p+(int)size;Reader t=new Reader(bytes,r.p,end);r.p=end;long tick=0;int running=0;
            while(t.p<t.end){
                if(++count>1000000)throw new IOException("Too many MIDI events");
                tick+=t.variable();int s=t.one();
                if(s<128){if(running==0)throw new IOException("Invalid MIDI running status");t.p--;s=running;}
                if(s>=128&&s<240){running=s;int len=((s&240)==192||(s&240)==208)?1:2;for(int i=0;i<len;i++)if(t.one()>127)throw new IOException("Invalid MIDI channel data");}
                else if(s==255){
                    int kind=t.one();long len=t.variable();int start=t.p;t.skip(len);
                    if(kind==81&&len==3){int tempo=((bytes[start]&255)<<16)|((bytes[start+1]&255)<<8)|(bytes[start+2]&255);if(tempo==0)throw new IOException("Invalid tempo");events.add(new Event(tick,81,tempo,null));}
                    else if((kind==1||kind==5)&&len>0){String text=decode(bytes,start,(int)len).replace("\u0000","");if(!text.startsWith("@"))events.add(new Event(tick,kind,0,text));}
                    if(kind==47)break;
                }else if(s==240||s==247){running=0;t.skip(t.variable());}
                else throw new IOException("Unsupported MIDI event");
            }
        }
        Collections.sort(events,(a,b)->Long.compare(a.tick,b.tick));
        boolean explicit=false;for(Event e:events)if(e.kind==5&&!e.text.trim().isEmpty())explicit=true;
        double micros=0;long last=0;int tempo=500000;List<Cue> result=new ArrayList<>();
        double smpte=0;
        if((division&32768)!=0){int fps=-(byte)(division>>8);int sub=division&255;if(sub==0||(fps!=24&&fps!=25&&fps!=29&&fps!=30))throw new IOException("Invalid SMPTE timing");smpte=(fps==29?30000.0/1001:fps)*sub;}
        String line="";
        for(Event e:events){
            micros+=(e.tick-last)*(smpte>0?1000000.0/smpte:(double)tempo/division);last=e.tick;
            if(e.kind==81){tempo=e.tempo;continue;}
            if(e.kind!=(explicit?5:1))continue;
            String s=e.text.replace('\r','\n').replace('\\','\n').replace('/','\n');
            int newline=s.lastIndexOf('\n');
            if(newline>=0)line=s.substring(newline+1);else line+=s;
            if(line.length()>240)line=s;
            result.add(new Cue(Math.round(micros/1000.0),line));
        }
        return new MidiLyrics(result);
    }
    static String decode(byte[] b,int off,int len){
        try{return StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).decode(java.nio.ByteBuffer.wrap(b,off,len)).toString();}
        catch(CharacterCodingException e){return new String(b,off,len,Charset.forName("windows-1252"));}
    }
    public int at(long ms){int low=0,high=cues.size()-1,result=-1;while(low<=high){int mid=(low+high)>>>1;if(cues.get(mid).ms<=ms){result=mid;low=mid+1;}else high=mid-1;}return result;}
}
