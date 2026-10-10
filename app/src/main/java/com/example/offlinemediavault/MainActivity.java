package com.example.offlinemediavault;

import android.app.*;
import android.os.*;
import android.content.*;
import android.database.Cursor;
import android.graphics.*;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.provider.OpenableColumns;
import android.view.*;
import android.view.inputmethod.InputMethodManager;
import android.widget.*;
import org.json.*;
import java.io.*;
import java.util.*;
import java.util.zip.*;

public class MainActivity extends Activity {
    private final int BG=0xFF18191D, PANEL=0xFF25272E, FIELD=0xFF343741, FG=0xFFF0F0F4, MUTED=0xFFA7AAB5, ACCENT=0xFF777FE8;
    private LinearLayout root, listBox;
    private EditText search;
    private ArrayList<MediaItem> items = new ArrayList<>();
    private File mediaDir;
    private static final int PICK_MEDIA=101, PICK_ZIP=102;

    static class MediaItem {
        String name, path, tags;
        MediaItem(String n,String p,String t){name=n;path=p;tags=t==null?"":t;}
    }

    @Override public void onCreate(Bundle b) {
        super.onCreate(b);
        getWindow().setStatusBarColor(BG); getWindow().setNavigationBarColor(BG);
        mediaDir=new File(getFilesDir(),"media"); if(!mediaDir.exists()) mediaDir.mkdirs();
        load();
        buildUi();
        render();
    }

    private void buildUi() {
        ScrollView outer=new ScrollView(this); outer.setFillViewport(true); outer.setBackgroundColor(BG);
        root=new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL); root.setPadding(dp(16),dp(16),dp(16),dp(18)); root.setBackgroundColor(BG);
        outer.addView(root); setContentView(outer);

        TextView title=new TextView(this); title.setText("МЕДИА-БИБЛИОТЕКА"); title.setTextColor(FG); title.setTextSize(21); title.setTypeface(null,1);
        root.addView(title,params(-1,-2)); TextView subtitle=new TextView(this); subtitle.setText("Оффлайн · фото, видео и теги"); subtitle.setTextColor(MUTED); subtitle.setTextSize(13);
        LinearLayout.LayoutParams sp=params(-1,-2); sp.topMargin=dp(4); sp.bottomMargin=dp(16); root.addView(subtitle,sp);

        search=new EditText(this); search.setSingleLine(true); search.setHint("Поиск по названию или тегам"); styleEdit(search);
        root.addView(search,params(-1,dp(48))); search.addTextChangedListener(new android.text.TextWatcher(){
            public void beforeTextChanged(CharSequence s,int st,int c,int a){} public void onTextChanged(CharSequence s,int st,int before,int count){render();}
            public void afterTextChanged(android.text.Editable e){}
        });
        LinearLayout buttons=new LinearLayout(this); buttons.setOrientation(LinearLayout.HORIZONTAL);
        TextView add=button("+ Файлы"), zip=button("Импорт ZIP");
        buttons.addView(add,new LinearLayout.LayoutParams(0,dp(48),1)); LinearLayout.LayoutParams zp=params(0,dp(48)); zp.weight=1; zp.leftMargin=dp(8); buttons.addView(zip,zp);
        LinearLayout.LayoutParams bp=params(-1,-2); bp.topMargin=dp(10); bp.bottomMargin=dp(14); root.addView(buttons,bp);
        add.setOnClickListener(v->pickMedia()); zip.setOnClickListener(v->pickZip());

        TextView label=new TextView(this); label.setText("ТВОЯ БИБЛИОТЕКА"); label.setTextColor(MUTED); label.setTextSize(12); label.setTypeface(null,1);
        LinearLayout.LayoutParams lp=params(-1,-2); lp.bottomMargin=dp(8); root.addView(label,lp);
        listBox=new LinearLayout(this); listBox.setOrientation(LinearLayout.VERTICAL); root.addView(listBox,params(-1,-2));
        TextView foot=new TextView(this); foot.setText("Файлы сохраняются только на этом устройстве."); foot.setTextColor(MUTED); foot.setTextSize(12);
        LinearLayout.LayoutParams fp=params(-1,-2); fp.topMargin=dp(16); root.addView(foot,fp);
    }

    private void render() {
        if(listBox==null)return; listBox.removeAllViews();
        String q=search==null?"":search.getText().toString().trim().toLowerCase(Locale.ROOT);
        int count=0;
        for(MediaItem it:new ArrayList<>(items)) {
            if(!q.isEmpty() && !it.name.toLowerCase(Locale.ROOT).contains(q) && !it.tags.toLowerCase(Locale.ROOT).contains(q)) continue;
            count++;
            LinearLayout card=new LinearLayout(this); card.setOrientation(LinearLayout.HORIZONTAL); card.setGravity(Gravity.CENTER_VERTICAL); card.setPadding(dp(10),dp(9),dp(8),dp(9)); card.setBackground(round(PANEL,14));
            LinearLayout.LayoutParams cp=params(-1,-2); cp.bottomMargin=dp(8); listBox.addView(card,cp);
            TextView thumb=new TextView(this); thumb.setText(isVideo(it.name)?"▶":"▣"); thumb.setTextSize(24); thumb.setTextColor(ACCENT); thumb.setGravity(Gravity.CENTER); thumb.setBackground(round(FIELD,10));
            card.addView(thumb,new LinearLayout.LayoutParams(dp(52),dp(52)));
            LinearLayout details=new LinearLayout(this); details.setOrientation(LinearLayout.VERTICAL); details.setPadding(dp(10),0,dp(4),0);
            card.addView(details,new LinearLayout.LayoutParams(0,-2,1));
            TextView name=new TextView(this); name.setText(it.name); name.setTextColor(FG); name.setTextSize(14); name.setMaxLines(2); name.setEllipsize(android.text.TextUtils.TruncateAt.END); name.setTypeface(null,1); details.addView(name);
            TextView tag=new TextView(this); tag.setText(it.tags.isEmpty()?"Нажми ✎, чтобы добавить теги":"# "+it.tags); tag.setTextColor(MUTED); tag.setTextSize(12); tag.setMaxLines(2); tag.setEllipsize(android.text.TextUtils.TruncateAt.END);
            LinearLayout.LayoutParams tp=params(-1,-2); tp.topMargin=dp(4); details.addView(tag,tp);
            TextView edit=new TextView(this); edit.setText("✎"); edit.setTextSize(22); edit.setTextColor(FG); edit.setGravity(Gravity.CENTER); card.addView(edit,new LinearLayout.LayoutParams(dp(40),dp(48)));
            card.setOnClickListener(v->preview(it)); thumb.setOnClickListener(v->preview(it)); name.setOnClickListener(v->preview(it));
            edit.setOnClickListener(v->editTags(it));
            card.setOnLongClickListener(v->{new AlertDialog.Builder(this).setTitle("Удалить файл?").setMessage(it.name+"\nФайл будет удалён с устройства.").setNegativeButton("Отмена",null).setPositiveButton("Удалить",(d,w)->deleteItem(it)).show();return true;});
        }
        if(count==0){TextView empty=new TextView(this);empty.setText(items.isEmpty()?"Пока пусто. Добавь фото, видео или ZIP-архив.":"Ничего не найдено. Попробуй другие теги.");empty.setTextColor(MUTED);empty.setTextSize(14);empty.setGravity(Gravity.CENTER);empty.setPadding(dp(16),dp(32),dp(16),dp(32));empty.setBackground(round(PANEL,14));listBox.addView(empty,params(-1,-2));}
    }

    private void pickMedia() {
        Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT); i.setType("*/*"); i.putExtra(Intent.EXTRA_MIME_TYPES,new String[]{"image/*","video/*"}); i.putExtra(Intent.EXTRA_ALLOW_MULTIPLE,true); i.addCategory(Intent.CATEGORY_OPENABLE);
        startActivityForResult(i,PICK_MEDIA);
    }
    private void pickZip() {
        Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT); i.setType("application/zip"); i.addCategory(Intent.CATEGORY_OPENABLE); startActivityForResult(i,PICK_ZIP);
    }
    @Override protected void onActivityResult(int requestCode,int resultCode,Intent data) {
        super.onActivityResult(requestCode,resultCode,data); if(resultCode!=RESULT_OK||data==null)return;
        if(requestCode==PICK_MEDIA) {
            int added=0;
            if(data.getClipData()!=null) {
                for(int n=0;n<data.getClipData().getItemCount();n++) { Uri u=data.getClipData().getItemAt(n).getUri(); if(importUri(u))added++; }
            } else if(data.getData()!=null && importUri(data.getData())) added++;
            save(); render(); toast("Добавлено файлов: "+added);
        } else if(requestCode==PICK_ZIP && data.getData()!=null) {
            try { int n=importZip(data.getData()); save(); render(); toast("Импортировано медиафайлов: "+n); }
            catch(Exception e){toast("Не удалось прочитать ZIP: "+e.getMessage());}
        }
    }
    private boolean importUri(Uri uri) {
        String name=getName(uri); if(!isMedia(name)) {toast("Пропущен неподдерживаемый файл: "+name);return false;}
        try(InputStream in=getContentResolver().openInputStream(uri)) {
            File out=unique(name); copy(in,new FileOutputStream(out)); items.add(new MediaItem(name,out.getAbsolutePath(),"")); return true;
        } catch(Exception e){toast("Не удалось импортировать "+name);return false;}
    }
    private int importZip(Uri uri) throws Exception {
        int count=0;
        try(ZipInputStream zin=new ZipInputStream(getContentResolver().openInputStream(uri))) {
            ZipEntry entry;
            while((entry=zin.getNextEntry())!=null) {
                if(!entry.isDirectory()) {
                    String name=new File(entry.getName()).getName();
                    if(isMedia(name)) {
                        File out=unique(name); try(FileOutputStream fout=new FileOutputStream(out)){byte[] buf=new byte[8192];int n;while((n=zin.read(buf))>0)fout.write(buf,0,n);}
                        items.add(new MediaItem(name,out.getAbsolutePath(),""));count++;
                    }
                }
                zin.closeEntry();
            }
        }
        return count;
    }
    private void preview(MediaItem it) {
        File f=new File(it.path); if(!f.exists()){toast("Файл не найден");return;}
        if(isVideo(it.name)) {
            Dialog dialog=new Dialog(this); LinearLayout box=new LinearLayout(this); box.setOrientation(LinearLayout.VERTICAL); box.setPadding(dp(12),dp(12),dp(12),dp(12)); box.setBackgroundColor(BG);
            VideoView video=new VideoView(this); video.setVideoPath(f.getAbsolutePath()); box.addView(video,new LinearLayout.LayoutParams(-1,dp(360)));
            TextView close=button("Закрыть"); box.addView(close,params(-1,dp(46))); close.setOnClickListener(v->{video.stopPlayback();dialog.dismiss();});
            dialog.setContentView(box); Window w=dialog.getWindow(); if(w!=null){w.setBackgroundDrawableResource(android.R.color.transparent);w.setLayout(-1,-2);} dialog.show(); if(dialog.getWindow()!=null)dialog.getWindow().setLayout(-1,-2); video.start();
        } else {
            Dialog dialog=new Dialog(this); LinearLayout box=new LinearLayout(this); box.setOrientation(LinearLayout.VERTICAL); box.setPadding(dp(10),dp(10),dp(10),dp(10)); box.setBackgroundColor(BG);
            ImageView image=new ImageView(this); image.setAdjustViewBounds(true); image.setScaleType(ImageView.ScaleType.FIT_CENTER);
            android.graphics.Bitmap bitmap=android.graphics.BitmapFactory.decodeFile(f.getAbsolutePath());
            if(bitmap!=null)image.setImageBitmap(bitmap); else {toast("Формат фото не поддерживается для предпросмотра");return;}
            box.addView(image,new LinearLayout.LayoutParams(-1,0,1));
            TextView close=button("Закрыть"); box.addView(close,params(-1,dp(46))); close.setOnClickListener(v->dialog.dismiss());
            dialog.setContentView(box); dialog.show(); if(dialog.getWindow()!=null)dialog.getWindow().setLayout(-1,(int)(getResources().getDisplayMetrics().heightPixels*.85));
        }
    }
    private void editTags(MediaItem it) {
        EditText input=new EditText(this); input.setText(it.tags); input.setTextColor(FG); input.setHintTextColor(MUTED); input.setHint("например: horror, animation, tiktok"); input.setSingleLine(false);
        input.setPadding(dp(12),dp(10),dp(12),dp(10)); input.setBackground(round(FIELD,10));
        LinearLayout box=new LinearLayout(this); box.setPadding(dp(20),dp(8),dp(20),0); box.addView(input,params(-1,dp(56)));
        new AlertDialog.Builder(this).setTitle("Теги для файла").setMessage(it.name).setView(box).setNegativeButton("Отмена",null).setPositiveButton("Сохранить",(d,w)->{it.tags=input.getText().toString().trim().replace("\n",", ");save();render();}).show();
    }
    private void deleteItem(MediaItem it) {items.remove(it);new File(it.path).delete();save();render();}
    private String getName(Uri uri) {
        String result=null; try(Cursor c=getContentResolver().query(uri,null,null,null,null)){if(c!=null&&c.moveToFirst()){int ix=c.getColumnIndex(OpenableColumns.DISPLAY_NAME);if(ix>=0)result=c.getString(ix);}}
        if(result==null)result=uri.getLastPathSegment(); if(result==null)result="media_"+System.currentTimeMillis(); return new File(result).getName();
    }
    private File unique(String name) {
        String clean=new File(name).getName(),base=clean,ext="";int dot=clean.lastIndexOf('.');if(dot>0){base=clean.substring(0,dot);ext=clean.substring(dot);}
        File f=new File(mediaDir,clean);int n=1;while(f.exists())f=new File(mediaDir,base+"_"+(n++)+ext);return f;
    }
    private boolean isMedia(String name) {String s=name.toLowerCase(Locale.ROOT);return s.matches(".*\\.(jpg|jpeg|png|gif|bmp|webp|mp4|mov|m4v|3gp|mkv|webm|avi)$");}
    private boolean isVideo(String name) {return name.toLowerCase(Locale.ROOT).matches(".*\\.(mp4|mov|m4v|3gp|mkv|webm|avi)$");}
    private void save() {
        try {JSONArray a=new JSONArray();for(MediaItem it:items){JSONObject o=new JSONObject();o.put("name",it.name);o.put("path",it.path);o.put("tags",it.tags);a.put(o);}getSharedPreferences("library",0).edit().putString("items",a.toString()).apply();}
        catch(Exception ignored){}
    }
    private void load() {
        try {JSONArray a=new JSONArray(getSharedPreferences("library",0).getString("items","[]"));for(int i=0;i<a.length();i++){JSONObject o=a.getJSONObject(i);File f=new File(o.getString("path"));if(f.exists())items.add(new MediaItem(o.getString("name"),o.getString("path"),o.optString("tags","")));}}
        catch(Exception ignored){}
    }
    private void copy(InputStream in,OutputStream out)throws IOException {try(OutputStream o=out){byte[] b=new byte[8192];int n;while((n=in.read(b))!=-1)o.write(b,0,n);}}
    private TextView button(String text) {TextView b=new TextView(this);b.setText(text);b.setTextColor(FG);b.setTextSize(14);b.setTypeface(null,1);b.setGravity(Gravity.CENTER);b.setBackground(round(0xFF414552,12));b.setPadding(dp(8),0,dp(8),0);return b;}
    private GradientDrawable round(int color,int radius){GradientDrawable d=new GradientDrawable();d.setColor(color);d.setCornerRadius(dp(radius));return d;}
    private LinearLayout.LayoutParams params(int w,int h){return new LinearLayout.LayoutParams(w<0?w:dp(w),h<0?h:dp(h));}
    private int dp(int n){return (int)(n*getResources().getDisplayMetrics().density+.5f);}
    private void styleEdit(EditText e){e.setTextColor(FG);e.setHintTextColor(MUTED);e.setTextSize(14);e.setPadding(dp(12),0,dp(12),0);e.setBackground(round(FIELD,12));}
    private void toast(String s){Toast.makeText(this,s,Toast.LENGTH_SHORT).show();}
}
