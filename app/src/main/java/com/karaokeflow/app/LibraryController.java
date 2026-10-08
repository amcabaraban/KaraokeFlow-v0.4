package com.karaokeflow.app;

import java.util.*;

/** Pure Java library and queue state; independent of the MIDI synthesizer. */
public final class LibraryController {
  public static final class Song {
    public final int number;
    public final String title, artist, midiPath;
    public Song(int number, String title, String artist, String midiPath) {
      this.number=number; this.title=title==null?"":title; this.artist=artist==null?"":artist;
      this.midiPath=midiPath==null?"":midiPath;
    }
  }
  private final ArrayList<Song> library=new ArrayList<>();
  private final ArrayList<Song> queue=new ArrayList<>();
  private final LinkedHashSet<Integer> favorites=new LinkedHashSet<>();
  private final HashMap<String,ArrayList<Song>> index=new HashMap<>();
  public synchronized void setLibrary(Collection<Song> songs) {
    library.clear(); index.clear();
    library.addAll(songs);
    for(Song s:library) for(String token:tokens(s.title+" "+s.artist+" "+s.number))
      index.computeIfAbsent(token,k->new ArrayList<>()).add(s);
  }
  private static String normalize(String s) {return s.toLowerCase(Locale.ROOT).trim();}
  private static Set<String> tokens(String s) {
    LinkedHashSet<String> t=new LinkedHashSet<>();
    for(String x:normalize(s).split("[^\\p{L}\\p{N}]+")) if(!x.isEmpty())t.add(x);
    return t;
  }
  public synchronized List<Song> search(String query, int limit) {
    String q=normalize(query);
    if(limit<=0)return Collections.emptyList();
    ArrayList<Song> out=new ArrayList<>();
    // Substring matching preserves the familiar song-number and partial-title behavior.
    for(Song s:library) {
      if(q.isEmpty() || normalize(s.title).contains(q) || normalize(s.artist).contains(q)
          || String.format(Locale.ROOT,"%06d",s.number).contains(q)) {
        out.add(s); if(out.size()==limit)break;
      }
    }
    return out;
  }
  public synchronized void enqueue(Song song){queue.add(song);}
  public synchronized Song dequeue(){return queue.isEmpty()?null:queue.remove(0);}
  public synchronized void removeQueued(int index){if(index>=0&&index<queue.size())queue.remove(index);}
  public synchronized void clearQueue(){queue.clear();}
  public synchronized List<Song> queue(){return new ArrayList<>(queue);}
  public synchronized void toggleFavorite(int number){if(!favorites.add(number))favorites.remove(number);}
  public synchronized boolean isFavorite(int number){return favorites.contains(number);}
  public synchronized List<Song> favorites(){ArrayList<Song> out=new ArrayList<>();for(Song s:library)if(favorites.contains(s.number))out.add(s);return out;}
}
