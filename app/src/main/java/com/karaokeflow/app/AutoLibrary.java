package com.karaokeflow.app;

import android.content.*;
import android.database.Cursor;
import android.net.Uri;
import android.provider.DocumentsContract;
import org.json.*;
import java.util.*;

/** Scoped-storage library discovery. No broad storage permission required. */
public final class AutoLibrary {
    private static final String PREF="auto_library", ROOT="root", CACHE="cache", SF="soundfont";
    public static final class Scan {
        public final ArrayList<LibraryController.Song> songs=new ArrayList<>();
        public String soundfontUri="";
        public int folders=0;
    }
    public static String root(Context c){return c.getSharedPreferences(PREF,0).getString(ROOT,"");}
    public static String soundfont(Context c){return c.getSharedPreferences(PREF,0).getString(SF,"");}
    public static void setRoot(Context c,Uri uri){
        c.getSharedPreferences(PREF,0).edit().putString(ROOT,uri.toString()).apply();
    }
    public static void setSoundfont(Context c,Uri uri){
        c.getSharedPreferences(PREF,0).edit().putString(SF,uri.toString()).apply();
    }
    public static Scan scan(Context c) throws Exception {
        String root=root(c);
        if(root.isEmpty())throw new IllegalStateException("Choose a karaoke folder first");
        Uri tree=Uri.parse(root);
        Scan result=new Scan();
        HashSet<String> seen=new HashSet<>();
        ArrayDeque<String[]> todo=new ArrayDeque<>();
        todo.add(new String[]{DocumentsContract.getTreeDocumentId(tree),"", "0"});
        int nextId=1;
        while(!todo.isEmpty()){
            String[] entry=todo.removeFirst();
            String folderId=entry[0], folderPath=entry[1];
            int depth=Integer.parseInt(entry[2]);
            if(depth>16 || !seen.add(folderId))continue;
            result.folders++;
            Uri children=DocumentsContract.buildChildDocumentsUriUsingTree(tree,folderId);
            String[] columns={DocumentsContract.Document.COLUMN_DOCUMENT_ID,DocumentsContract.Document.COLUMN_DISPLAY_NAME,DocumentsContract.Document.COLUMN_MIME_TYPE};
            try(Cursor cursor=c.getContentResolver().query(children,columns,null,null,null)){
                if(cursor==null)continue;
                while(cursor.moveToNext()){
                    String id=cursor.getString(0), name=cursor.getString(1), mime=cursor.getString(2);
                    if(id==null||name==null)continue;
                    if(DocumentsContract.Document.MIME_TYPE_DIR.equals(mime)){
                        todo.add(new String[]{id,folderPath+"/"+name,Integer.toString(depth+1)});
                        continue;
                    }
                    String lower=name.toLowerCase(Locale.ROOT);
                    Uri file=DocumentsContract.buildDocumentUriUsingTree(tree,id);
                    if(lower.endsWith(".mid")||lower.endsWith(".midi")||lower.endsWith(".kar")){
                        String title=name.substring(0,name.lastIndexOf('.')).replace('_',' ');
                        result.songs.add(new LibraryController.Song(nextId++,title,"",file.toString()));
                    } else if((lower.endsWith(".sf2")||lower.endsWith(".sf3")) &&
                            (result.soundfontUri.isEmpty()||lower.equals("default.sf2"))){
                        result.soundfontUri=file.toString();
                    }
                }
            }
        }
        result.songs.sort((a,b)->a.title.compareToIgnoreCase(b.title));
        save(c,result);
        return result;
    }
    private static void save(Context c,Scan scan)throws JSONException{
        JSONArray songs=new JSONArray();
        for(LibraryController.Song s:scan.songs){
            JSONObject j=new JSONObject();
            j.put("number",s.number);j.put("title",s.title);j.put("artist",s.artist);j.put("uri",s.midiPath);
            songs.put(j);
        }
        c.getSharedPreferences(PREF,0).edit().putString(CACHE,songs.toString()).apply();
    }
    public static ArrayList<LibraryController.Song> cached(Context c){
        ArrayList<LibraryController.Song> songs=new ArrayList<>();
        try{
            JSONArray a=new JSONArray(c.getSharedPreferences(PREF,0).getString(CACHE,"[]"));
            for(int i=0;i<a.length();i++){
                JSONObject j=a.getJSONObject(i);
                songs.add(new LibraryController.Song(j.getInt("number"),j.optString("title"),j.optString("artist"),j.getString("uri")));
            }
        }catch(Exception ignored){}
        return songs;
    }
}
