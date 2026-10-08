package com.karaokeflow.app;
import android.app.*;import android.os.*;import android.content.*;import android.graphics.Color;import android.graphics.Typeface;import android.graphics.drawable.GradientDrawable;import android.net.Uri;import android.view.*;import android.widget.*;import android.text.*;import java.io.*;import java.util.*;
public class MainActivity extends Activity {
 final LibraryController lib=new LibraryController(); final ArrayList<LibraryController.Song> imported=new ArrayList<>(); final int BG=0xff090f1d,PANEL=0xff172238,ACCENT=0xff1877ef,WHITE=Color.WHITE,MUTED=0xff9eacc4;
 LinearLayout root,body,nav; EditText search; String page="Home"; LibraryController.Song selected; final Handler handler=new Handler(); Runnable pending; int maxResults=80;
 int dp(int n){return (int)(n*getResources().getDisplayMetrics().density+.5f);} GradientDrawable shape(int color){GradientDrawable g=new GradientDrawable();g.setColor(color);g.setCornerRadius(dp(14));return g;}
 TextView text(String s,int size,int color){TextView v=new TextView(this);v.setText(s);v.setTextSize(size);v.setTextColor(color);v.setPadding(dp(10),dp(11),dp(10),dp(11));return v;}
 LinearLayout column(){LinearLayout l=new LinearLayout(this);l.setOrientation(1);return l;}
 LinearLayout row(){LinearLayout l=new LinearLayout(this);l.setOrientation(0);l.setGravity(Gravity.CENTER_VERTICAL);return l;}
 void panel(LinearLayout parent,View v){v.setBackground(shape(PANEL));LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);lp.setMargins(0,0,0,dp(9));parent.addView(v,lp);}
 TextView action(String s,Runnable click){TextView v=text(s,15,WHITE);v.setBackground(shape(PANEL));v.setOnClickListener(w->click.run());return v;}
 @Override public void onCreate(Bundle b){super.onCreate(b);imported.addAll(AutoLibrary.cached(this));lib.setLibrary(imported);show("Home");}
 void show(String p){page=p;root=column();root.setBackgroundColor(BG);root.setPadding(dp(12),dp(12),dp(12),dp(4));setContentView(root);TextView title=text("KaraokeFlow   ·   "+p,23,WHITE);title.setTypeface(null,Typeface.BOLD);root.addView(title);
 ScrollView scroll=new ScrollView(this);body=column();scroll.addView(body);root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
 if(p.equals("Home"))home();else if(p.equals("Songs"))songs();else if(p.equals("Queue"))queue();else if(p.equals("Favorites"))favorites();else if(p.equals("Player"))player();else settings();
 nav=row();for(String x:new String[]{"Home","Songs","Queue","Favorites","More"}){TextView b=text(x,12,x.equals(p)?0xff73b6ff:MUTED);b.setGravity(Gravity.CENTER);nav.addView(b,new LinearLayout.LayoutParams(0,dp(54),1));b.setOnClickListener(v->show(x));}root.addView(nav);}
 void home(){TextView hero=text("Your songs. Your stage.\n\nFind a song and make it yours.",20,WHITE);hero.setBackground(shape(PANEL));panel(body,hero);panel(body,action("⌕   Search songs",()->show("Songs")));for(String x:new String[]{"Songs","Queue","Favorites","More"})panel(body,action("♫   "+x,()->show(x)));panel(body,action("Choose karaoke folder / auto-detect",this::pickFolder));panel(body,action("Import song catalog (CSV)",this::pickCsv));panel(body,action("Import MIDI / KAR files",this::pickMidi));panel(body,action("Play included demo",this::playDemo));body.addView(text("Folder-based MIDI/KAR discovery enabled. Custom SF2 synthesis is not yet connected.",13,MUTED));}
 void songs(){search=new EditText(this);search.setSingleLine(true);search.setTextColor(WHITE);search.setHintTextColor(MUTED);search.setHint("Search title, artist, or song number");search.setBackground(shape(PANEL));search.setPadding(dp(14),0,dp(14),0);body.addView(search,new LinearLayout.LayoutParams(-1,dp(54)));panel(body,action("Import CSV catalog",this::pickCsv));panel(body,action("Import MIDI / KAR",this::pickMidi));LinearLayout results=column();body.addView(results);search.addTextChangedListener(new TextWatcher(){public void beforeTextChanged(CharSequence s,int st,int c,int a){}public void onTextChanged(CharSequence s,int st,int before,int count){if(pending!=null)handler.removeCallbacks(pending);String q=s.toString();pending=()->renderSongs(results,q);handler.postDelayed(pending,130);}public void afterTextChanged(Editable e){}});renderSongs(results,"");}
 void renderSongs(LinearLayout target,String query){target.removeAllViews();List<LibraryController.Song> found=lib.search(query,maxResults);if(found.isEmpty()){target.addView(text("No songs found. Import your catalog first.",15,MUTED));return;}for(LibraryController.Song s:found){LinearLayout r=row();LinearLayout detail=column();detail.addView(text(s.title.isEmpty()?"Untitled":s.title,16,WHITE));detail.addView(text(s.artist+"   ·   "+String.format(Locale.ROOT,"%06d",s.number),12,MUTED));r.addView(detail,new LinearLayout.LayoutParams(0,-2,1));TextView plus=text("+",24,0xff79b8ff);r.addView(plus);plus.setOnClickListener(v->{lib.enqueue(s);Toast.makeText(this,"Added to queue",Toast.LENGTH_SHORT).show();});r.setOnClickListener(v->options(s));panel(target,r);}}
 void options(LibraryController.Song s){String fav=lib.isFavorite(s.number)?"Remove favorite":"Add to favorites";new AlertDialog.Builder(this).setTitle(s.title).setItems(new String[]{"Play now","Add to queue",fav},(d,n)->{if(n==0){selected=s;playSong(s);}else if(n==1){lib.enqueue(s);Toast.makeText(this,"Added to queue",0).show();}else lib.toggleFavorite(s.number);}).show();}
 void queue(){List<LibraryController.Song> q=lib.queue();panel(body,action("Clear queue",()->new AlertDialog.Builder(this).setMessage("Clear all queued songs?").setPositiveButton("Clear",(a,b)->{lib.clearQueue();show("Queue");}).setNegativeButton("Cancel",null).show()));if(q.isEmpty())body.addView(text("Your queue is empty.",15,MUTED));for(int i=0;i<q.size();i++){final int idx=i;LibraryController.Song s=q.get(i);panel(body,action((i+1)+". "+s.title+"    ×",()->{lib.removeQueued(idx);show("Queue");}));}}
 void favorites(){List<LibraryController.Song> q=lib.favorites();if(q.isEmpty())body.addView(text("No favorites yet. Save songs from the library.",15,MUTED));for(LibraryController.Song s:q)panel(body,action("♥   "+s.title+" — "+s.artist,()->options(s)));}
 void player(){body.setGravity(Gravity.CENTER);TextView t=text(selected==null?"No song selected":selected.title,21,WHITE);t.setGravity(Gravity.CENTER);body.addView(t);TextView artist=text(selected==null?"":selected.artist,14,MUTED);artist.setGravity(Gravity.CENTER);body.addView(artist);TextView lyrics=text("♪\n\nLyrics will appear here\nwhen the v0.3 lyric engine is integrated.\n\n♪",26,WHITE);lyrics.setGravity(Gravity.CENTER);body.addView(lyrics,new LinearLayout.LayoutParams(-1,0,1));body.addView(text("Choose a MIDI/KAR song from Songs to play.",13,MUTED));panel(body,action("Add to queue",()->{if(selected!=null)lib.enqueue(selected);}));}
 void settings(){
 panel(body,action("Select KaraokeFlow / SongHub folder",this::pickFolder));
 panel(body,action("Rescan folders for MIDI/KAR/SF2",this::scanFolders));
 panel(body,action("Select SoundFont (.sf2/.sf3)",this::pickSoundfont));
 panel(body,action("Import CSV catalog",this::pickCsv));
 panel(body,action("Import MIDI / KAR",this::pickMidi));
 String root=AutoLibrary.root(this),sf=AutoLibrary.soundfont(this);
 body.addView(text("Folder: "+(root.isEmpty()?"Not selected":root),12,MUTED));
 body.addView(text("SoundFont: "+(sf.isEmpty()?"Not selected":sf),12,MUTED));
 body.addView(text("Note: SoundFont selection and discovery are configured; Android MediaPlayer does not render MIDI using external SF2 banks. A compatible synth engine is still required.",13,MUTED));
}
 void pickFolder(){
  Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
  i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION|Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
  startActivityForResult(i,12);
 }
 void pickSoundfont(){
  Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT);
  i.addCategory(Intent.CATEGORY_OPENABLE);i.setType("*/*");
  i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION|Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
  startActivityForResult(i,13);
 }
 void scanFolders(){
  Toast.makeText(this,"Scanning karaoke folders…",Toast.LENGTH_SHORT).show();
  new Thread(()->{
   try{
    AutoLibrary.Scan found=AutoLibrary.scan(this);
    runOnUiThread(()->{
      imported.clear();imported.addAll(found.songs);lib.setLibrary(imported);
      if(AutoLibrary.soundfont(this).isEmpty()&&!found.soundfontUri.isEmpty())
        AutoLibrary.setSoundfont(this,Uri.parse(found.soundfontUri));
      Toast.makeText(this,"Found "+found.songs.size()+" MIDI/KAR files in "+found.folders+" folders",Toast.LENGTH_LONG).show();
      show("Songs");
    });
   }catch(Exception e){
    runOnUiThread(()->new AlertDialog.Builder(this).setTitle("Folder scan failed").setMessage(e.getMessage()).setPositiveButton("OK",null).show());
   }
  }).start();
 }

 void pickMidi(){Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT);i.addCategory(Intent.CATEGORY_OPENABLE);i.setType("*/*");i.putExtra(Intent.EXTRA_ALLOW_MULTIPLE,true);startActivityForResult(i,11);}
 void playDemo(){playUri("Welcome to KaraokeFlow • Demo","demo:welcome.mid");}
 void playSong(LibraryController.Song s){if(s.midiPath.startsWith("content:")||s.midiPath.startsWith("demo:")){playUri(s.title,s.midiPath);}else new AlertDialog.Builder(this).setMessage("This catalog entry has a filename, not an accessible file URI. Import its MIDI/KAR file using Import MIDI / KAR.").setPositiveButton("OK",null).show();}
 void playUri(String title,String uri){try{org.json.JSONArray a=new org.json.JSONArray();org.json.JSONObject o=new org.json.JSONObject();o.put("title",title);o.put("uri",uri);o.put("kind","midi");a.put(o);getSharedPreferences("playback",0).edit().putString("playlist",a.toString()).apply();startActivity(new Intent(this,PlayerActivity.class));}catch(Exception e){Toast.makeText(this,e.getMessage(),Toast.LENGTH_LONG).show();}}
 void pickCsv(){Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT);i.addCategory(Intent.CATEGORY_OPENABLE);i.setType("*/*");startActivityForResult(i,10);}
 @Override protected void onActivityResult(int r,int c,Intent d){super.onActivityResult(r,c,d);if(c!=RESULT_OK||d==null)return;if(r==12){
   Uri u=d.getData();if(u==null)return;
   try{getContentResolver().takePersistableUriPermission(u,Intent.FLAG_GRANT_READ_URI_PERMISSION);}catch(Exception ignored){}
   AutoLibrary.setRoot(this,u);scanFolders();return;
  }
  if(r==13){
   Uri u=d.getData();if(u==null)return;
   String name=u.getLastPathSegment();
   if(name==null||!(name.toLowerCase(Locale.ROOT).endsWith(".sf2")||name.toLowerCase(Locale.ROOT).endsWith(".sf3"))){
    Toast.makeText(this,"Please select an SF2 or SF3 file",Toast.LENGTH_LONG).show();return;
   }
   try{getContentResolver().takePersistableUriPermission(u,Intent.FLAG_GRANT_READ_URI_PERMISSION);}catch(Exception ignored){}
   AutoLibrary.setSoundfont(this,u);
   Toast.makeText(this,"SoundFont location saved (synth integration pending)",Toast.LENGTH_LONG).show();show("More");return;
  }
  if(r==11){ArrayList<Uri> picked=new ArrayList<>();if(d.getData()!=null)picked.add(d.getData());if(d.getClipData()!=null)for(int j=0;j<d.getClipData().getItemCount();j++)picked.add(d.getClipData().getItemAt(j).getUri());for(Uri u:picked){try{getContentResolver().takePersistableUriPermission(u,Intent.FLAG_GRANT_READ_URI_PERMISSION);}catch(Exception ignored){}String name=u.getLastPathSegment();if(name==null)name="Imported MIDI";imported.add(new LibraryController.Song(100000+imported.size(),name,"Imported",u.toString()));}lib.setLibrary(imported);Toast.makeText(this,"Imported "+picked.size()+" file(s)",0).show();show("Songs");return;}if(r!=10)return;ArrayList<LibraryController.Song> songs=new ArrayList<>();try(BufferedReader br=new BufferedReader(new InputStreamReader(getContentResolver().openInputStream(d.getData())))){br.readLine();String line;while((line=br.readLine())!=null){List<String> a=parse(line);if(a.size()<6)continue;try{songs.add(new LibraryController.Song(Integer.parseInt(a.get(0).trim()),a.get(2),a.get(3),a.get(5)));}catch(Exception ignored){}}songs.addAll(imported);lib.setLibrary(songs);Toast.makeText(this,"Loaded "+songs.size()+" songs",0).show();show("Songs");}catch(Exception e){new AlertDialog.Builder(this).setMessage("Import failed: "+e.getMessage()).setPositiveButton("OK",null).show();}}
 List<String> parse(String s){ArrayList<String> o=new ArrayList<>();StringBuilder b=new StringBuilder();boolean quote=false;for(int i=0;i<s.length();i++){char c=s.charAt(i);if(c=='"'){if(quote&&i+1<s.length()&&s.charAt(i+1)=='"'){b.append('"');i++;}else quote=!quote;}else if(c==','&&!quote){o.add(b.toString());b.setLength(0);}else b.append(c);}o.add(b.toString());return o;}
 @Override public void onBackPressed(){if(page.equals("Home"))super.onBackPressed();else show("Home");}
}